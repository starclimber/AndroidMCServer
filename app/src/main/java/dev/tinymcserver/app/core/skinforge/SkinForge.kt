/*
 * SkinForge · Minecraft Java 版 64×64 皮肤程序化生成器（纯 Kotlin 移植版）
 * =====================================================================
 * 本文件是 /sandbox/workspace/skinforge/skinforge_reference.py 的逐函数机械移植：
 *   · 随机源（xorshift32 + crc32 播种）、随机调用顺序、全部区间常量完全照搬；
 *   · 颜色仍用 [r, g, b, a] 四元组表示，其中 a=0 表示不透明、a=127 表示全透明
 *     （与 Python 的 TRANSP / ALPHA_CUT 语义一致），仅在输出时映射为 ARGB；
 *   · round() 使用与 Python 一致的「银行家舍入」，避免逐像素比对出现 ±1 偏差；
 *   · 零第三方依赖、零 Android API，可直接用 kotlinc 编译为 class/jar。
 *
 * 对应关系见每个函数上方的注释（`Python: xxx()`）。
 */

package dev.tinymcserver.app.core.skinforge

import java.util.Locale
import java.util.zip.CRC32
import dev.tinymcserver.app.core.i18n.t

// ==================================================================
// 1. 颜色工具（HSL 空间）—— Python: clamp/hsl2rgb/rgb2hsl/shade/setL/mix/hexc
// ==================================================================

/** Python: clamp(v, a, b) → max(a, min(b, v)) */
private fun clamp(v: Double, a: Double, b: Double): Double = maxOf(a, minOf(b, v))

/**
 * Python: round(x) —— 银行家舍入（round-half-to-even）。
 * Java/Kotlin 的 Math.round 是四舍五入，直接用会引入 ±1 偏差，故自行实现。
 */
private fun pyRound(x: Double): Long {
    if (!x.isFinite() || x == 0.0) return x.toLong()
    val a = Math.abs(x)
    var r = Math.floor(a + 0.5)
    if (r - a == 0.5 && r % 2.0 != 0.0) r -= 1.0 // 恰好半值 → 取偶
    return (if (x < 0) -r else r).toLong()
}

/** Python: hsl2rgb(h, s, l) —— h 单位度(可超出 0~360)，s/l 为 0~1，返回 [r,g,b] */
private fun hsl2rgb(h0: Double, s: Double, l: Double): IntArray {
    val h = (((h0 % 360.0) + 360.0) % 360.0) / 360.0
    if (s <= 0) {
        val v = pyRound(l * 255).toInt()
        return intArrayOf(v, v, v)
    }
    val q = if (l < 0.5) l * (1 + s) else l + s - l * s
    val p = 2 * l - q

    fun f(t0: Double): Double {
        var t = t0
        if (t < 0) t += 1.0
        if (t > 1) t -= 1.0
        return when {
            t < 1.0 / 6.0 -> p + (q - p) * 6.0 * t
            t < 0.5 -> q
            t < 2.0 / 3.0 -> p + (q - p) * (2.0 / 3.0 - t) * 6.0
            else -> p
        }
    }

    return intArrayOf(
        pyRound(f(h + 1.0 / 3.0) * 255).toInt(),
        pyRound(f(h) * 255).toInt(),
        pyRound(f(h - 1.0 / 3.0) * 255).toInt()
    )
}

/** Python: rgb2hsl(r, g, b) —— 返回 [h(度), s, l] */
private fun rgb2hsl(r0: Int, g0: Int, b0: Int): DoubleArray {
    val r = r0 / 255.0
    val g = g0 / 255.0
    val b = b0 / 255.0
    val mx = maxOf(r, g, b)
    val mn = minOf(r, g, b)
    val l = (mx + mn) / 2.0
    if (mx == mn) return doubleArrayOf(0.0, 0.0, l)
    val d = mx - mn
    val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
    val h = if (mx == r) {
        (g - b) / d + (if (g < b) 6.0 else 0.0)
    } else if (mx == g) {
        (b - r) / d + 2.0
    } else {
        (r - g) / d + 4.0
    }
    return doubleArrayOf(h * 60.0, s, l)
}

/** Python: TRANSP = [0, 0, 0, 127]（a=127 表示全透明） */
private const val ALPHA_CUT = 127

private fun transp() = intArrayOf(0, 0, 0, ALPHA_CUT)

/** Python: hslc(h, s, l) —— 生成不透明色 */
private fun hslc(h: Double, s: Double, l: Double): IntArray {
    val c = hsl2rgb(h, s, l)
    return intArrayOf(c[0], c[1], c[2], 0)
}

/** Python: is_transp(c) */
private fun isTransp(c: IntArray): Boolean = c[3] >= ALPHA_CUT

/** Python: L(c) —— 取明度 */
private fun L(c: IntArray): Double = rgb2hsl(c[0], c[1], c[2])[2]

/** Python: shade(c, dl, dh, sm) —— 明暗调整：dl 明度增量，dh 色相偏移，sm 饱和度倍率 */
private fun shade(c: IntArray, dl: Double = 0.0, dh: Double = 0.0, sm: Double = 1.0): IntArray {
    if (isTransp(c)) return c.copyOf()
    val hsl = rgb2hsl(c[0], c[1], c[2])
    val o = hsl2rgb(hsl[0] + dh, clamp(hsl[1] * sm, 0.0, 1.0), clamp(hsl[2] + dl, 0.02, 0.98))
    return intArrayOf(o[0], o[1], o[2], c[3])
}

/** Python: setL(c, l) —— 保持色相饱和度，重设明度 */
private fun setL(c: IntArray, l: Double): IntArray {
    if (isTransp(c)) return c.copyOf()
    val hsl = rgb2hsl(c[0], c[1], c[2])
    val o = hsl2rgb(hsl[0], hsl[1], clamp(l, 0.02, 0.98))
    return intArrayOf(o[0], o[1], o[2], c[3])
}

/** Python: mix(a, b, t) */
private fun mix(a: IntArray, b: IntArray, t: Double): IntArray = intArrayOf(
    pyRound(a[0] + (b[0] - a[0]) * t).toInt(),
    pyRound(a[1] + (b[1] - a[1]) * t).toInt(),
    pyRound(a[2] + (b[2] - a[2]) * t).toInt(),
    a[3]
)

/** Python: hexc(c) → '#RRGGBB' */
private fun hexc(c: IntArray): String = String.format(Locale.ROOT, "#%02X%02X%02X", c[0], c[1], c[2])

// ==================================================================
// 2. 皮肤 UV 布局 —— Python: box_faces / UV
// ==================================================================

/** Python: box_faces(ox, oy, w, h, d) */
private fun boxFaces(ox: Int, oy: Int, w: Int, h: Int, d: Int): Map<String, IntArray> = linkedMapOf(
    "top" to intArrayOf(ox + d, oy, w, d),
    "bottom" to intArrayOf(ox + d + w, oy, w, d),
    "right" to intArrayOf(ox, oy + d, d, h),
    "front" to intArrayOf(ox + d, oy + d, w, h),
    "left" to intArrayOf(ox + d + w, oy + d, d, h),
    "back" to intArrayOf(ox + d + w + d, oy + d, w, h)
)

/** Python: UV 字典（64×64 标准分区） */
private val UV: Map<String, Map<String, IntArray>> = linkedMapOf(
    "head" to boxFaces(0, 0, 8, 8, 8),        // 头（底层）
    "hat" to boxFaces(32, 0, 8, 8, 8),        // 头（第二层：头发/帽子）
    "body" to boxFaces(16, 16, 8, 12, 4),     // 躯干
    "jacket" to boxFaces(16, 32, 8, 12, 4),   // 躯干第二层
    "armR" to boxFaces(40, 16, 4, 12, 4),     // 右臂
    "sleeveR" to boxFaces(40, 32, 4, 12, 4),
    "legR" to boxFaces(0, 16, 4, 12, 4),      // 右腿
    "pantsR" to boxFaces(0, 32, 4, 12, 4),
    "armL" to boxFaces(32, 48, 4, 12, 4),     // 左臂（非镜像布局）
    "sleeveL" to boxFaces(48, 48, 4, 12, 4),
    "legL" to boxFaces(16, 48, 4, 12, 4),     // 左腿
    "pantsL" to boxFaces(0, 48, 4, 12, 4)
)

private val BOX_ORDER = listOf("top", "bottom", "right", "front", "left", "back")
private val SIDE_ORDER = listOf("right", "front", "left", "back")

// ==================================================================
// 3. 可复现随机源 —— Python: class RNG
// ==================================================================

private class Rng(seed: String) {
    private var s: Int

    init {
        val crc = CRC32()
        crc.update(seed.toByteArray(Charsets.UTF_8))
        // Python: zlib.crc32(seed.encode('utf-8')) & 0x7fffffff
        val v = (crc.value and 0x7fffffffL).toInt()
        s = if (v != 0) v else 0x2545F491 // Python: (seed & 0x7fffffff) or 0x2545f491
    }

    /** Python: RNG.n() —— xorshift32，返回 [0,1) */
    fun n(): Double {
        var x = s
        x = x xor (x shl 13)
        x = x and 0x7fffffff
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        x = x and 0x7fffffff
        s = if (x != 0) x else 0x2545F491
        return s / 2147483648.0
    }

    /** Python: RNG.f(a, b) */
    fun f(a: Double = 0.0, b: Double = 1.0): Double = a + (b - a) * n()

    /** Python: RNG.i(a, b) —— 闭区间整数 */
    fun i(a: Int, b: Int): Int = a + Math.floor(n() * (b - a + 1)).toInt()

    /** Python: RNG.pick(arr) */
    fun <T> pick(arr: List<T>): T = arr[i(0, arr.size - 1)]

    /** Python: RNG.chance(p) */
    fun chance(p: Double): Boolean = n() < p

    /** Python: RNG.sample(arr, k) —— 不重复抽样（pop 顺序照搬） */
    fun sample(arr: List<Int>, k: Int): List<Int> {
        val a = arr.toMutableList()
        val out = ArrayList<Int>()
        repeat(minOf(k, a.size)) { out.add(a.removeAt(i(0, a.size - 1))) }
        return out
    }
}

// ==================================================================
// 4. 风格表 —— Python: STYLES / HAIR_COVER / *_CN
// ==================================================================

private class StyleSpec(
    val base: String,
    val jacket: String?,
    val jacketC: String?,
    val trim: String?,
    val sleeve: String,
    val lower: String,
    val belt: Boolean,
    val pauldron: Boolean,
    val patterns: List<String>,
    val head: Map<String, Any?>,
    val label: String
)

private val STYLE_ORDER = listOf(
    "adventurer", "hoodie", "knight", "mage", "ranger", "cyber", "ninja", "street"
)

private val STYLE_MAP: Map<String, StyleSpec> get() = linkedMapOf(
    "adventurer" to StyleSpec(
        base = "top", jacket = null, jacketC = null, trim = "accent", sleeve = "long",
        lower = "pants", belt = true, pauldron = false,
        patterns = listOf("plain", "emblem", "stripe"), head = emptyMap(), label = t("旅人")
    ),
    "hoodie" to StyleSpec(
        base = "top", jacket = "hoodie", jacketC = "top", trim = "sub", sleeve = "long",
        lower = "pants", belt = false, pauldron = false,
        patterns = listOf("plain", "plain", "stripe"), head = mapOf("hood" to true), label = t("兜帽")
    ),
    "knight" to StyleSpec(
        base = "sub", jacket = "armor", jacketC = null, trim = "accent", sleeve = "long",
        lower = "pants", belt = true, pauldron = true,
        patterns = listOf("plain"), head = mapOf("helmet" to true, "bald" to true), label = t("铁卫")
    ),
    "mage" to StyleSpec(
        base = "top", jacket = "robe", jacketC = "top", trim = "accent", sleeve = "long",
        lower = "robe", belt = false, pauldron = false,
        patterns = listOf("plain", "panel"), head = mapOf("hat" to "pointy"), label = t("秘术")
    ),
    "ranger" to StyleSpec(
        base = "top", jacket = "vest", jacketC = "sub", trim = "accent", sleeve = "short",
        lower = "pants", belt = true, pauldron = false,
        patterns = listOf("plain", "panel"), head = mapOf("hood" to "?"), label = t("游侠")
    ),
    "cyber" to StyleSpec(
        base = "pants", jacket = "tech", jacketC = "top", trim = "accent", sleeve = "long",
        lower = "pants", belt = true, pauldron = false,
        patterns = listOf("plain"), head = mapOf("visor" to true, "headphones" to true), label = t("义体")
    ),
    "ninja" to StyleSpec(
        base = "pants", jacket = "vest", jacketC = "top", trim = "accent", sleeve = "long",
        lower = "pants", belt = true, pauldron = false,
        patterns = listOf("plain"), head = mapOf("mask" to true, "band" to true), label = t("影刃")
    ),
    "street" to StyleSpec(
        base = "top", jacket = null, jacketC = null, trim = "accent", sleeve = "short",
        lower = "shorts", belt = false, pauldron = false,
        patterns = listOf("stripe", "emblem", "plain"), head = mapOf("hat" to "?"), label = t("街头")
    )
)

/** Python: HAIR_COVER */
private val HAIR_COVER = mapOf(
    "short" to intArrayOf(6, 4, 2),
    "messy" to intArrayOf(5, 4, 2),
    "long" to intArrayOf(8, 7, 2),
    "bob" to intArrayOf(7, 6, 2),
    "afro" to intArrayOf(7, 6, 3)
)

/** Python: HAIR_CN */
private val HAIR_CN: Map<String, String> get() = mapOf(
    "short" to t("短发"), "messy" to t("乱发"), "long" to t("长发"), "bob" to t("姬发式"),
    "afro" to t("蓬发"), "bald" to t("光头")
)

/** Python: MOOD_CN */
private val MOOD_CN: Map<String, String> get() = mapOf(
    "vivid" to t("鲜艳"), "muted" to t("沉稳"), "dark" to t("暗调"), "pastel" to t("柔粉"), "neon" to t("霓虹")
)

/** Python: SCHEME_CN */
private val SCHEME_CN: Map<String, String> get() = mapOf(
    "analogous" to t("邻近色"), "complement" to t("互补色"), "split" to t("分裂互补"),
    "triad" to t("三角配色"), "mono" to t("单色系")
)

/** Python: 中文名（adventurer_ 系列）—— 对应 _make_name 里的 noun 表 */
private val STYLE_NOUN: Map<String, String> get() = mapOf(
    "adventurer" to t("旅人"), "hoodie" to t("夜行者"), "knight" to t("铁卫"), "mage" to t("秘术师"),
    "ranger" to t("游侠"), "cyber" to t("义体客"), "ninja" to t("影刃"), "street" to t("街角少年")
)

// ==================================================================
// 5. 生成器主体 —— Python: class Forge
// ==================================================================

/** Python: P[role] = {'b': 基色, 'd': 暗部, 'l': 亮部}；eye 额外有 b2（异色瞳） */
private class Role(var b: IntArray, var d: IntArray, var l: IntArray, var b2: IntArray? = null)

/** Python: D 设计规格字典 */
private class Design {
    lateinit var style: String
    lateinit var label: String
    lateinit var hair: String
    lateinit var eye: String
    var brows = false
    var blush = false
    lateinit var mouth: String
    var socks = false
    lateinit var torsoBase: IntArray
    var jacket: String? = null
    lateinit var sleeve: String
    var pauldron = false
    lateinit var lower: String
    lateinit var shoe: IntArray
    var belt = false
    lateinit var pattern: String
    val head = LinkedHashMap<String, Any?>()
    var jacketC: IntArray? = null
    lateinit var trimC: IntArray
    lateinit var name: String
}

/** 模拟 Python 真值判断（用于 D['head'] 的 .get(key) 分支） */
private fun truthy(v: Any?): Boolean = when (v) {
    null -> false
    is Boolean -> v
    is String -> v.isNotEmpty()
    else -> true
}

/** 情绪档位的饱和/明度区间 —— Python: _make_palette 里的 B 字典 */
private class MoodBounds(val s: DoubleArray, val l: DoubleArray, val ps: DoubleArray, val pl: DoubleArray)

private class Forge(seed: String, style: String?) {
    val rng = Rng(seed)
    val buf: Array<Array<IntArray>> = Array(64) { Array(64) { transp() } }
    private val P = HashMap<String, Role>()
    private var mood = ""
    private var scheme = ""
    private var h1 = 0.0
    private var h2 = 0.0
    private var D: Design

    init {
        makePalette()
        D = makeDesign(style)
        enforceContrast()
    }

    // ---------------- 绘制原语（Python: px / rect / face / box / sides / rows / rows_sides） ----------------

    private fun px(x: Int, y: Int, c: IntArray) {
        if (x in 0..63 && y in 0..63) buf[y][x] = c.copyOf()
    }

    /**
     * Python: rect(x, y, w, h, c, alt=None, p=0.0)
     * 注意：alt 为 None 时「不调用」rng.n()（Python 的短路求值），random 顺序必须一致。
     */
    private fun rect(x: Int, y: Int, w: Int, h: Int, c: IntArray, alt: IntArray? = null, p: Double = 0.0) {
        for (j in 0 until h) {
            for (i in 0 until w) {
                val cc = if (alt != null && rng.n() < p) alt else c
                px(x + i, y + j, cc)
            }
        }
    }

    private fun face(f: IntArray, c: IntArray, alt: IntArray? = null, p: Double = 0.0) =
        rect(f[0], f[1], f[2], f[3], c, alt, p)

    private fun box(B: Map<String, IntArray>, c: IntArray, alt: IntArray? = null, p: Double = 0.0) {
        for (k in BOX_ORDER) face(B[k]!!, c, alt, p)
    }

    private fun sides(B: Map<String, IntArray>, c: IntArray, alt: IntArray? = null, p: Double = 0.0) {
        for (k in SIDE_ORDER) if (B.containsKey(k)) face(B[k]!!, c, alt, p)
    }

    private fun rows(f: IntArray, y0: Int, y1: Int, c: IntArray, alt: IntArray? = null, p: Double = 0.0) =
        rect(f[0], f[1] + y0, f[2], y1 - y0, c, alt, p)

    private fun rowsSides(B: Map<String, IntArray>, y0: Int, y1: Int, c: IntArray, alt: IntArray? = null, p: Double = 0.0) {
        for (k in SIDE_ORDER) if (B.containsKey(k)) rows(B[k]!!, y0, y1, c, alt, p)
    }

    private fun role(name: String): Role = P[name]!!

    // ---------------- 调色板 ---------------- Python: Forge._make_palette()

    private fun makePalette() {
        val r = rng
        mood = r.pick(listOf("vivid", "vivid", "muted", "muted", "dark", "pastel", "neon"))
        val B = when (mood) {
            "vivid" -> MoodBounds(doubleArrayOf(0.55, 0.85), doubleArrayOf(0.40, 0.60), doubleArrayOf(0.35, 0.60), doubleArrayOf(0.20, 0.38))
            "muted" -> MoodBounds(doubleArrayOf(0.22, 0.45), doubleArrayOf(0.34, 0.55), doubleArrayOf(0.18, 0.34), doubleArrayOf(0.16, 0.32))
            "dark" -> MoodBounds(doubleArrayOf(0.30, 0.58), doubleArrayOf(0.26, 0.44), doubleArrayOf(0.22, 0.45), doubleArrayOf(0.12, 0.28))
            "pastel" -> MoodBounds(doubleArrayOf(0.32, 0.55), doubleArrayOf(0.60, 0.78), doubleArrayOf(0.25, 0.45), doubleArrayOf(0.30, 0.46))
            else -> MoodBounds(doubleArrayOf(0.70, 0.95), doubleArrayOf(0.28, 0.45), doubleArrayOf(0.55, 0.80), doubleArrayOf(0.14, 0.30))
        }

        h1 = r.f(0.0, 360.0)                                    // 主色相
        scheme = r.pick(listOf("analogous", "complement", "split", "triad", "mono"))
        h2 = when (scheme) {
            "analogous" -> h1 + r.f(20.0, 55.0)
            "complement" -> h1 + 180 + r.f(-15.0, 15.0)
            "split" -> h1 + 180 + r.pick(listOf(-1, 1)) * r.f(25.0, 45.0)
            "triad" -> h1 + r.pick(listOf(120, 240)) + r.f(-12.0, 12.0)
            else -> h1 + r.f(-14.0, 14.0)                        // 次要色相
        }

        // 肤色：80% 自然肤 / 20% 奇幻肤
        val skin: IntArray
        if (r.chance(0.20)) {
            val hh = r.pick(listOf(h1 + r.f(-20.0, 20.0), 285.0, 200.0, 160.0, 320.0))
            skin = hslc(hh, r.f(0.20, 0.42), r.f(0.55, 0.74))
        } else {
            skin = hslc(r.f(16.0, 40.0), r.f(0.22, 0.48), r.f(0.52, 0.74))
        }

        // 发色
        var hair: IntArray
        when (r.pick(listOf("natural", "natural", "themed", "themed", "light", "vivid"))) {
            "natural" -> hair = hslc(r.f(15.0, 42.0), r.f(0.18, 0.62), r.f(0.10, 0.32))
            "themed" -> hair = hslc(h1 + r.f(-24.0, 24.0), r.f(0.35, 0.75), r.f(0.16, 0.40))
            "light" -> hair = hslc(r.f(35.0, 60.0), r.f(0.20, 0.45), r.f(0.58, 0.80))
            else -> hair = hslc(h2, r.f(0.60, 0.90), r.f(0.30, 0.50))
        }

        var top = hslc(h1, r.f(B.s[0], B.s[1]), r.f(B.l[0], B.l[1]))          // 上衣
        val ph = if (r.chance(0.62)) h2 else h1 + r.f(-14.0, 14.0)
        var pants = hslc(ph, r.f(B.ps[0], B.ps[1]), r.f(B.pl[0], B.pl[1]))    // 裤
        val sub = if (r.chance(0.5)) hslc(h2, r.f(0.10, 0.35), r.f(0.55, 0.86))
        else hslc(h1, r.f(0.05, 0.25), r.f(0.32, 0.58))                       // 内衬/副色
        val ps = rgb2hsl(pants[0], pants[1], pants[2])
        var shoe = hslc(
            ph + r.f(-10.0, 10.0),
            clamp(ps[1] * 0.85, 0.05, 0.5),
            clamp(minOf(L(pants) * 0.78, r.f(0.12, 0.26)), 0.08, 0.26)
        )
        val accent = hslc(h1 + 180 + r.f(-20.0, 20.0), r.f(0.65, 0.95), r.f(0.45, 0.62))
        val metal = hslc(r.pick(listOf(45, 42, 210, 25, 220)).toDouble(), r.f(0.15, 0.50), r.f(0.45, 0.68))

        // ---- 明度层级护栏 ----
        if (kotlin.math.abs(L(pants) - L(top)) < 0.14) {
            pants = setL(pants, if (L(top) > 0.5) L(top) - 0.24 else L(top) + 0.24)
        }
        if (kotlin.math.abs(L(hair) - L(skin)) < 0.20) {
            hair = setL(hair, if (L(skin) > 0.55) L(skin) - 0.30 else L(skin) + 0.30)
        }
        if (L(shoe) > L(pants) - 0.05) {
            shoe = setL(shoe, maxOf(0.08, L(pants) - 0.14))
        }
        if (L(shoe) > 0.28) {
            shoe = setL(shoe, 0.24)
        }
        if (L(top) < 0.26) {
            top = setL(top, r.f(0.28, 0.40))
        }
        if (L(top) > 0.80) {
            top = setL(top, r.f(0.62, 0.76))
        }

        for ((k, c) in listOf(
            "skin" to skin, "hair" to hair, "top" to top, "sub" to sub,
            "pants" to pants, "shoe" to shoe, "accent" to accent, "metal" to metal
        )) {
            P[k] = Role(c, shade(c, -0.13, -8.0, 0.92), shade(c, 0.11, 7.0, 1.0))
        }

        // 眼睛（6% 异色瞳）
        if (r.chance(0.06)) {
            val e1 = role("accent").b
            val b2 = hslc(r.f(0.0, 360.0), r.f(0.50, 0.85), r.f(0.30, 0.55))
            P["eye"] = Role(e1.copyOf(), shade(e1, -0.12, -6.0), intArrayOf(246, 244, 240, 0), b2)
        } else {
            val eh = if (r.chance(0.6)) r.pick(listOf(h2, 205.0, 150.0, 25.0, 260.0, 190.0)) else r.f(0.0, 360.0)
            val eb = hslc(eh, r.f(0.45, 0.85), r.f(0.28, 0.50))
            val ed = hslc(eh, r.f(0.50, 0.90), r.f(0.16, 0.30))
            P["eye"] = Role(eb, ed, intArrayOf(246, 244, 240, 0), null)
        }
    }

    // ---------------- 设计规格 ---------------- Python: Forge._make_design()

    private fun makeDesign(force: String?): Design {
        val r = rng
        val style = if (force != null && STYLE_MAP.containsKey(force)) force else r.pick(STYLE_ORDER)
        val S = STYLE_MAP[style]!!
        val d = Design()
        d.style = style
        d.label = S.label
        d.hair = r.pick(listOf("short", "messy", "long", "bob", "afro", "short", "long"))
        d.eye = if (r.chance(0.55)) "anime" else "classic"
        d.brows = r.chance(0.75)
        d.blush = r.chance(0.30)
        d.mouth = r.pick(listOf("smile", "neutral", "smirk", "small"))
        d.socks = r.chance(0.35)
        d.torsoBase = role(S.base).b
        d.jacket = S.jacket
        d.sleeve = S.sleeve
        d.pauldron = S.pauldron
        d.lower = S.lower
        d.shoe = role("shoe").b
        d.belt = S.belt
        d.pattern = r.pick(S.patterns)
        d.head.putAll(S.head)
        if (d.head["hat"] == "?") d.head["hat"] = r.pick(listOf(null, "pointy", "cap", "beanie"))
        if (d.head["hood"] == "?") d.head["hood"] = r.chance(0.5)
        d.jacketC = if (S.jacketC != null) role(S.jacketC).b else null
        d.trimC = if (S.trim != null) role(S.trim).b else role("accent").b
        d.name = makeName(style)
        return d
    }

    // ---------------- 设计后对比度收口 ---------------- Python: Forge._enforce_contrast()

    private fun enforceContrast() {
        val torso = D.jacketC ?: (if (D.jacket == "armor") role("metal").b else D.torsoBase)
        val whole = D.lower == "robe" || D.style == "cyber" || D.style == "ninja"
        if (!whole && kotlin.math.abs(L(role("pants").b) - L(torso)) < 0.14) {
            val lt = L(torso)
            val target = clamp(if (lt > 0.5) lt - 0.22 else lt + 0.22, 0.10, 0.78)
            role("pants").b = setL(role("pants").b, target)
            role("pants").d = shade(role("pants").b, -0.13, -8.0, 0.92)
            role("pants").l = shade(role("pants").b, 0.11, 7.0, 1.0)
        }
        if (L(role("shoe").b) > L(role("pants").b) - 0.06) {
            role("shoe").b = setL(role("shoe").b, maxOf(0.08, L(role("pants").b) - 0.14))
            role("shoe").d = shade(role("shoe").b, -0.13, -8.0, 0.92)
            role("shoe").l = shade(role("shoe").b, 0.11, 7.0, 1.0)
        }
        D.shoe = role("shoe").b
    }

    /** Python: Forge._make_name() */
    private fun makeName(style: String): String {
        val h = ((h1 % 360.0) + 360.0) % 360.0
        val adj = listOf(t("绯红"), t("赤铜"), t("琥珀"), t("沙金"), t("苔绿"), t("翠森"), t("霜青"), t("湛蓝"), t("靛夜"), t("紫曜"), t("霓虹"), t("樱绯"))
        val a = adj[(h / 30.0).toInt() % 12]
        return a + (STYLE_NOUN[style] ?: "")
    }

    // ---------------- 绘制管线 ---------------- Python: Forge.build()

    fun build() {
        headBase()
        torso()
        arms()
        legs()
        hair()             // 以下四步写入第二层（overlay）
        headwear()
        torsoOverlay()
        limbOverlay()
    }

    /** Python: Forge._head_base() */
    private fun headBase() {
        val H = UV["head"]!!
        val sk = role("skin").b
        box(H, sk)
        face(H["back"]!!, shade(sk, -0.05, -5.0))
        face(H["right"]!!, shade(sk, -0.05, -5.0))
        face(H["left"]!!, shade(sk, -0.05, -5.0))
        face(H["bottom"]!!, shade(sk, -0.24, -10.0, 0.9))
        face(H["top"]!!, shade(sk, -0.08, -3.0))
        for (k in listOf("right", "left")) {                       // 耳
            rect(H[k]!![0] + 3, H[k]!![1] + 4, 2, 2, shade(sk, -0.12, -6.0))
        }
        val fx = H["front"]!![0]
        val fy = H["front"]!![1]
        if (D.brows) {
            val bc = shade(role("hair").b, -0.06, -4.0)
            rect(fx + 1, fy + 2, 2, 1, bc)
            rect(fx + 5, fy + 2, 2, 1, bc)
        }
        for (pair in listOf(1 to 2, 5 to 6)) {                     // 眼
            val x0 = pair.first
            val x1 = pair.second
            val eye = role("eye")
            val ib = if (x0 == 5) (eye.b2 ?: eye.b) else eye.b
            if (D.eye == "anime") {
                rect(fx + x0, fy + 3, 2, 2, ib)
                px(fx + (if (x0 == 1) x0 else x1), fy + 3, eye.l)
                px(fx + (if (x0 == 1) x1 else x0), fy + 4, shade(ib, -0.12, -6.0))
            } else {
                rect(fx + x0, fy + 3, 2, 2, eye.l)
                rect(fx + (if (x0 == 1) x1 else x0), fy + 3, 1, 2, ib)
            }
        }
        if (rng.chance(0.5)) {                                     // 鼻
            px(fx + rng.pick(listOf(3, 4)), fy + 5, shade(role("skin").b, -0.10, -4.0))
        }
        val m = D.mouth
        val md = shade(role("skin").b, -0.26, -8.0, 0.9)
        when (m) {
            "smile" -> {
                rect(fx + 3, fy + 6, 2, 1, md)
                px(fx + 2, fy + 5, md)
                px(fx + 5, fy + 5, md)
            }
            "smirk" -> {
                px(fx + 3, fy + 6, md)
                px(fx + 4, fy + 6, md)
            }
            "small" -> rect(fx + 3, fy + 6, 2, 1, md)
            else -> rect(fx + 3, fy + 6, 2, 1, shade(role("skin").b, -0.18, -6.0))
        }
        if (D.blush) {
            val bl = mix(role("skin").b, role("accent").b, 0.35)
            px(fx + 1, fy + 5, bl)
            px(fx + 6, fy + 5, bl)
        }
    }

    /** Python: Forge._hair() */
    private fun hair() {
        val H = UV["hat"]!!
        if (truthy(D.head["bald"]) || D.hair == "bald") return
        val cover = HAIR_COVER[D.hair] ?: HAIR_COVER["short"]!!
        val backN = cover[0]
        val sideN = cover[1]
        val frontN = cover[2]
        val hb = role("hair").b
        val hd = role("hair").d
        val hl = role("hair").l
        val tex = if (D.hair == "afro") 0.30 else 0.18
        face(H["top"]!!, hb, hd, tex)
        rows(H["back"]!!, 0, backN, hb, hd, tex)
        rows(H["right"]!!, 0, sideN, hb, hd, tex)
        rows(H["left"]!!, 0, sideN, hb, hd, tex)
        if (sideN < 7) {                                           // 后侧发略长
            rect(H["right"]!![0], H["right"]!![1] + sideN, 3, 2, hb, hd, tex)
            rect(H["left"]!![0] + 5, H["left"]!![1] + sideN, 3, 2, hb, hd, tex)
        }
        rect(H["front"]!![0], H["front"]!![1], 8, frontN, hb, hd, tex)
        if (D.hair == "long") {
            rect(H["front"]!![0], H["front"]!![1], 1, 5, hb, hd, tex)
            rect(H["front"]!![0] + 7, H["front"]!![1], 1, 5, hb, hd, tex)
        } else if (D.hair == "messy") {
            var i = 0
            while (i < 8) {
                px(H["front"]!![0] + i + rng.i(0, 1), H["front"]!![1] + frontN, hb)
                i += 2
            }
        }
        repeat(6) {                                                // 高光发丝
            px(
                H["back"]!![0] + rng.i(0, 7),
                H["back"]!![1] + rng.i(0, maxOf(0, backN - 1)),
                hl
            )
        }
        repeat(4) {
            val x = rng.i(0, 7)
            val y = rng.i(0, maxOf(0, sideN - 1))
            px(H["right"]!![0] + x, H["right"]!![1] + y, hl)
            px(H["left"]!![0] + x, H["left"]!![1] + y, hl)
        }
    }

    /** Python: Forge._headwear() */
    private fun headwear() {
        val H = UV["hat"]!!
        val hd = D.head
        if (truthy(hd["hood"])) {
            val c = D.jacketC ?: role("top").b
            val cd = shade(c, -0.12, -6.0)
            face(H["top"]!!, c, cd, 0.15)
            rows(H["back"]!!, 0, 7, c, cd, 0.15)
            rows(H["right"]!!, 0, 6, c, cd, 0.15)
            rows(H["left"]!!, 0, 6, c, cd, 0.15)
            rows(H["front"]!!, 0, 1, c, cd, 0.15)
            rect(H["front"]!![0], H["front"]!![1] + 1, 8, 1, cd)
        }
        if (truthy(hd["helmet"])) {
            val m = role("metal").b
            val md = role("metal").d
            val ml = role("metal").l
            face(H["top"]!!, ml, m, 0.20)
            box(H, m, md, 0.12)
            rect(H["front"]!![0], H["front"]!![1] + 3, 8, 2, transp())        // 眼缝
            rect(H["front"]!![0], H["front"]!![1] + 2, 8, 1, shade(m, -0.34, -8.0, 0.6))
            rect(H["front"]!![0] + 2, H["front"]!![1] + 5, 4, 3, shade(m, -0.16, -6.0))
            rect(H["top"]!![0] + 3, H["top"]!![1], 2, 6, D.trimC)             // 盔脊
        }
        if (hd["hat"] == "pointy") {
            val c = D.jacketC ?: role("top").b
            val cd = shade(c, -0.14, -6.0)
            face(H["top"]!!, c, cd, 0.15)
            for (item in listOf(3 to 8, 2 to 6, 1 to 4, 0 to 2)) {
                val i = item.first
                val wdt = item.second
                val x0 = (8 - wdt) / 2
                for (k in listOf("front", "back", "right", "left")) {
                    rect(H[k]!![0] + x0, H[k]!![1] + i, wdt, 1, c, cd, 0.12)
                }
            }
            rect(H["front"]!![0], H["front"]!![1] + 4, 8, 1, cd)
            px(H["front"]!![0] + 3, H["front"]!![1], D.trimC)
        }
        if (hd["hat"] == "cap" || hd["hat"] == "beanie") {
            val c = D.jacketC ?: role("top").b
            val cd = shade(c, -0.14, -6.0)
            face(H["top"]!!, c, cd, 0.15)
            rowsSides(H, 0, 3, c, cd, 0.12)
            rows(H["front"]!!, 3, 4, cd)
            if (hd["hat"] == "cap") {
                rect(H["front"]!![0], H["front"]!![1] + 3, 8, 1, shade(c, -0.25, -8.0))
            }
        }
        if (truthy(hd["mask"])) {
            val c = D.jacketC ?: role("top").b
            val cd = shade(c, -0.12, -6.0)
            for (k in listOf("front", "right", "left")) rows(H[k]!!, 5, 8, c, cd, 0.10)
        }
        if (truthy(hd["visor"])) {
            val a = role("accent").b
            val al = shade(role("accent").b, 0.18, 6.0)
            rect(H["front"]!![0], H["front"]!![1] + 3, 8, 2, a)
            rect(H["front"]!![0], H["front"]!![1] + 3, 8, 1, al)
            rect(H["right"]!![0], H["right"]!![1] + 3, 8, 2, shade(a, -0.12))
            rect(H["left"]!![0], H["left"]!![1] + 3, 8, 2, shade(a, -0.12))
        } else if (truthy(hd["glasses"])) {
            val f = shade(role("metal").b, -0.25, -6.0)
            for (x0 in listOf(0, 4)) {
                rect(H["front"]!![0] + x0, H["front"]!![1] + 2, 4, 1, f)
                rect(H["front"]!![0] + x0, H["front"]!![1] + 5, 4, 1, f)
                for (dy in listOf(3, 4)) {
                    px(H["front"]!![0] + x0, H["front"]!![1] + dy, f)
                    px(H["front"]!![0] + x0 + 3, H["front"]!![1] + dy, f)
                }
            }
            rect(H["front"]!![0] + 3, H["front"]!![1] + 3, 2, 1, f)
        }
        if (truthy(hd["headphones"])) {
            val m = shade(role("metal").b, -0.15, -4.0)
            rect(H["right"]!![0] + 2, H["right"]!![1] + 3, 3, 3, m)
            rect(H["left"]!![0] + 3, H["left"]!![1] + 3, 3, 3, m)
            rect(H["top"]!![0] + 3, H["top"]!![1], 2, 8, m)
        }
        if (truthy(hd["band"])) {
            rowsSides(H, 1, 2, D.trimC)
        }
    }

    /** Python: Forge._torso() */
    private fun torso() {
        val T = UV["body"]!!
        val c = D.torsoBase
        val cd = shade(c, -0.12, -6.0)
        val cl = shade(c, 0.09, 5.0)
        box(T, c, cd, 0.08)
        face(T["top"]!!, shade(c, 0.07, 4.0))
        face(T["right"]!!, shade(c, -0.09, -5.0))
        face(T["left"]!!, shade(c, -0.09, -5.0))
        face(T["back"]!!, shade(c, -0.06, -4.0))
        rowsSides(T, 0, 2, shade(c, -0.16, -8.0))                            // 颈部投影
        rect(T["top"]!![0] + 2, T["top"]!![1], 4, 4, shade(c, -0.20, -8.0))
        rect(T["front"]!![0] + 2, T["front"]!![1], 4, 1, shade(role("skin").b, -0.12, -5.0))
        rect(T["front"]!![0] + 3, T["front"]!![1] + 1, 2, 1, shade(role("skin").b, -0.10, -5.0))
        val pt = D.pattern
        if (pt == "stripe") {
            for (y in rng.sample(listOf(4, 5, 6, 7, 8, 9), 2)) {
                rect(T["front"]!![0], T["front"]!![1] + y, 8, 1, role("sub").b)
                rect(T["back"]!![0], T["back"]!![1] + y, 8, 1, role("sub").b)
            }
        } else if (pt == "emblem") {
            val m = role("accent").b
            rect(T["front"]!![0] + 3, T["front"]!![1] + 4, 2, 3, m)
            rect(T["front"]!![0] + 2, T["front"]!![1] + 5, 4, 1, m)
        } else if (pt == "panel") {
            rect(T["front"]!![0] + 3, T["front"]!![1] + 2, 1, 9, shade(c, -0.14, -6.0))
            rect(T["front"]!![0] + 4, T["front"]!![1] + 2, 1, 9, cl)
        }
        if (rng.chance(0.35)) {                                              // 背带
            rect(T["back"]!![0] + 2, T["back"]!![1] + 2, 1, 8, role("sub").b)
            rect(T["back"]!![0] + 5, T["back"]!![1] + 2, 1, 8, role("sub").b)
        }
        if (D.belt) {
            rowsSides(T, 7, 9, shade(D.shoe, 0.06, 0.0))
            rect(T["front"]!![0] + 3, T["front"]!![1] + 7, 2, 2, D.trimC)
        }
        rowsSides(T, 11, 12, shade(c, -0.15, -7.0))                          // 下摆阴影
    }

    /** Python: Forge._torso_overlay() */
    private fun torsoOverlay() {
        if (D.jacket == null) return
        val J = UV["jacket"]!!
        val c = D.jacketC ?: role("top").b
        val cd = shade(c, -0.13, -6.0)
        val cl = shade(c, 0.10, 5.0)
        val tr = D.trimC
        val F = linkedMapOf(
            "front" to J["front"]!!, "back" to J["back"]!!,
            "right" to J["right"]!!, "left" to J["left"]!!
        )
        when (D.jacket) {
            "open" -> {
                rowsSides(F, 0, 10, c, cd, 0.08)
                rowsSides(F, 10, 11, cd)
                rect(J["front"]!![0] + 3, J["front"]!![1], 2, 10, transp())   // 开襟露出内衬
                rect(J["front"]!![0] + 3, J["front"]!![1], 1, 10, tr)
                rect(J["front"]!![0] + 4, J["front"]!![1], 1, 10, shade(c, -0.18, -6.0))
                rect(J["front"]!![0] + 1, J["front"]!![1], 2, 3, cl)          // 翻领
                rect(J["front"]!![0] + 5, J["front"]!![1], 2, 3, cl)
            }
            "hoodie" -> {
                rowsSides(F, 0, 11, c, cd, 0.10)
                face(J["top"]!!, c, cd, 0.10)
                rect(J["front"]!![0] + 1, J["front"]!![1] + 7, 6, 3, shade(c, -0.10, -5.0))
                rect(J["front"]!![0] + 1, J["front"]!![1] + 7, 6, 1, cd)
                rect(J["front"]!![0] + 3, J["front"]!![1] + 2, 1, 4, shade(c, 0.20, 6.0))
                rect(J["front"]!![0] + 4, J["front"]!![1] + 2, 1, 4, shade(c, 0.20, 6.0))
                rowsSides(F, 0, 1, cd)
            }
            "armor" -> {
                val m = role("metal").b
                val md = role("metal").d
                val ml = role("metal").l
                rowsSides(F, 0, 9, m, md, 0.10)
                face(J["top"]!!, ml, m, 0.10)
                rect(J["front"]!![0] + 3, J["front"]!![1], 2, 9, tr)
                rowsSides(F, 9, 10, md)
                rect(J["front"]!![0] + 2, J["front"]!![1] + 3, 4, 1, ml)
            }
            "robe" -> {
                rowsSides(F, 0, 12, c, cd, 0.08)
                face(J["top"]!!, c, cd, 0.08)
                rowsSides(F, 6, 7, tr)
                rect(J["front"]!![0] + 1, J["front"]!![1] + 1, 6, 1, tr)
            }
            "vest" -> {
                val v = linkedMapOf("back" to J["back"]!!, "right" to J["right"]!!, "left" to J["left"]!!)
                rowsSides(v, 0, 9, c, cd, 0.08)
                rect(J["front"]!![0], J["front"]!![1], 2, 9, c, cd, 0.08)
                rect(J["front"]!![0] + 6, J["front"]!![1], 2, 9, c, cd, 0.08)
                px(J["front"]!![0] + 1, J["front"]!![1] + 9, cd)
                px(J["front"]!![0] + 6, J["front"]!![1] + 9, cd)
            }
            "tech" -> {
                rowsSides(F, 0, 10, c, cd, 0.10)
                rect(J["front"]!![0] + 1, J["front"]!![1] + 2, 6, 1, tr)
                rect(J["front"]!![0] + 1, J["front"]!![1] + 5, 2, 2, tr)
                rect(J["front"]!![0] + 5, J["front"]!![1] + 3, 1, 5, shade(tr, -0.15))
                rect(J["back"]!![0] + 2, J["back"]!![1] + 3, 4, 1, tr)
                rowsSides(F, 10, 11, cd)
            }
        }
    }

    /** Python: Forge._arms() */
    private fun arms() {
        for (k in listOf("armR", "armL")) {
            val A = UV[k]!!
            val sk = role("skin").b
            box(A, sk)
            face(A["back"]!!, shade(sk, -0.05, -5.0))
            face(A["right"]!!, shade(sk, -0.04, -4.0))
            face(A["left"]!!, shade(sk, -0.04, -4.0))
            if (D.sleeve != "none") {
                val sc = D.jacketC ?: D.torsoBase
                val scd = shade(sc, -0.12, -6.0)
                val n = if (D.sleeve == "long") 10 else 5
                rowsSides(A, 0, n, sc, scd, 0.08)
                face(A["top"]!!, shade(sc, 0.08, 4.0))
                rowsSides(A, n - 1, n, shade(sc, -0.20, -8.0))                // 袖口
            }
            rowsSides(A, 10, 12, sk)
            rowsSides(A, 10, 11, shade(sk, -0.08, -4.0))
            face(A["bottom"]!!, shade(sk, -0.25, -10.0, 0.9))
            if (D.style == "ranger" || D.style == "ninja") {
                if (rng.chance(0.7)) rowsSides(A, 8, 10, D.trimC)
            }
        }
    }

    /** Python: Forge._limb_overlay() */
    private fun limbOverlay() {
        for (o in listOf("sleeveR", "sleeveL")) {
            val S = UV[o]!!
            if (D.sleeve == "none") continue
            val sc = D.jacketC ?: D.torsoBase
            val scd = shade(sc, -0.12, -6.0)
            val n = if (D.sleeve == "long") 10 else 5
            rowsSides(S, 0, n, sc, scd, 0.08)
            face(S["top"]!!, shade(sc, 0.08, 4.0))
            rowsSides(S, n - 1, n, shade(sc, -0.20, -8.0))
            if (D.pauldron) {
                rowsSides(S, 0, 3, role("metal").b, role("metal").d, 0.10)
                face(S["top"]!!, role("metal").l)
            }
            if (D.style == "cyber") {
                rowsSides(S, 5, 6, role("accent").b)
            }
        }
        for (o in listOf("pantsR", "pantsL")) {
            val S = UV[o]!!
            val lo = D.lower
            if (lo == "shorts") {
                rowsSides(S, 0, 6, role("pants").b, role("pants").d, 0.08)
                rowsSides(S, 9, 12, D.shoe)
            } else if (lo == "robe") {
                val c = D.jacketC ?: D.torsoBase
                rowsSides(S, 0, 10, c, shade(c, -0.12, -6.0), 0.08)
                rowsSides(S, 10, 12, shade(c, -0.30, -8.0))
            } else {
                rowsSides(S, 0, 9, role("pants").b, role("pants").d, 0.08)
                rowsSides(S, 9, 12, D.shoe)
            }
            val tp = if (lo == "robe") (D.jacketC ?: D.torsoBase) else role("pants").b
            face(S["top"]!!, tp)
        }
    }

    /** Python: Forge._legs() */
    private fun legs() {
        for (k in listOf("legR", "legL")) {
            val lg = UV[k]!!
            if (D.lower == "robe") {
                val c = D.jacketC ?: D.torsoBase
                val cd = shade(c, -0.12, -6.0)
                box(lg, c, cd, 0.08)
                rowsSides(lg, 10, 12, shade(c, -0.32, -8.0))
                face(lg["bottom"]!!, shade(c, -0.38, -10.0, 0.8))
                continue
            }
            val sk = role("skin").b
            box(lg, sk)
            val pn = role("pants").b
            val pnd = role("pants").d
            val pr = if (D.lower == "pants") 9 else 6
            rowsSides(lg, 0, pr, pn, pnd, 0.08)
            face(lg["top"]!!, shade(pn, 0.05, 3.0))
            face(lg["right"]!!, shade(pn, -0.08, -5.0))
            face(lg["left"]!!, shade(pn, -0.08, -5.0))
            if (D.lower == "shorts") {
                rowsSides(lg, 6, 9, sk)
            }
            if (D.socks && D.lower != "shorts") {
                rowsSides(lg, 7, 9, role("sub").b)
            }
            val sh = D.shoe
            rowsSides(lg, 9, 12, sh)
            rowsSides(lg, 11, 12, shade(sh, -0.15, -8.0))
            face(lg["bottom"]!!, shade(sh, -0.30, -10.0, 0.8))
            rect(lg["front"]!![0], lg["front"]!![1] + 9, 4, 1, shade(sh, 0.12, 4.0))
            if (rng.chance(0.4)) {
                px(lg["front"]!![0] + 1, lg["front"]!![1] + 10, shade(sh, 0.26, 6.0))
                px(lg["front"]!![0] + 2, lg["front"]!![1] + 10, shade(sh, 0.26, 6.0))
            }
        }
    }

    // ---------------- 输出与元信息 ---------------- Python: Forge.rgba_rows() / Forge.meta()

    /** Python: Forge.rgba_rows() → ARGB_8888 的 64×64 像素（index = y*64 + x） */
    fun toArgb(): IntArray {
        val out = IntArray(64 * 64)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val c = buf[y][x]
                val a = if (isTransp(c)) 0x00 else 0xFF
                out[y * 64 + x] = (a shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2]
            }
        }
        return out
    }

    /** Python: Forge.meta(seed) 的可读版（用于界面展示） */
    fun describe(): String {
        val tags = ArrayList<String>()
        tags.add(D.label)
        tags.add(MOOD_CN[mood] ?: mood)
        tags.add(SCHEME_CN[scheme] ?: scheme)
        tags.add(HAIR_CN[D.hair] ?: D.hair)
        for ((key, cn) in listOf(
            "hood" to t("兜帽"), "helmet" to t("头盔"), "visor" to t("目镜"), "mask" to t("面罩"),
            "headphones" to t("耳机"), "hat" to t("帽子")
        )) {
            if (truthy(D.head[key])) tags.add(cn)
        }
        if (D.socks) tags.add(t("长袜"))
        val seen = HashSet<String>()
        val uniq = tags.filter { seen.add(it) }
        return D.name + " · " + uniq.joinToString(" · ") + t(" · 肤色 ") + hexc(role("skin").b)
    }
}

// ==================================================================
// 6. 对外 API
// ==================================================================

object SkinForge {

    /** 风格 key（与 Python STYLES 的键完全一致）→ 中文标签 */
    val STYLES: List<Pair<String, String>>
        get() = STYLE_ORDER.map { it to (STYLE_MAP[it]!!.label) }

    /** Python: random_seed() —— '%06X' % random.getrandbits(24) */
    fun randomSeed(): String =
        String.format(Locale.ROOT, "%06X", java.util.Random().nextInt(1 shl 24))

    /** 皮肤的中文信息，供界面展示 */
    fun describe(seed: String, style: String? = null): String = Forge(seed, style).describe()

    /**
     * 按 seed（+可选 style）生成 64×64 皮肤。
     * 返回 IntArray(64*64)，index = y*64 + x，值为 ARGB_8888（0xAARRGGBB）。
     */
    fun generate(seed: String, style: String? = null): IntArray {
        val f = Forge(seed, style)
        f.build()
        return f.toArgb()
    }
}
