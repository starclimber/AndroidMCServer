package dev.tinymcserver.app.core.i18n

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.util.Locale

/** 应用支持的界面语言。 */
enum class AppLanguage(val code: String, val displayName: String) {
    ZH("zh", "简体中文"),
    EN("en", "English"),
    FR("fr", "Français"),
    ES("es", "Español"),
    RU("ru", "Русский");

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 轻量国际化：以「简体中文原文」为键查表。
 *
 * - 启动时 [init] 读取用户偏好；若从未设置过，则按系统语言自动选择并写回。
 * - 切换语言时 [revision] 自增；Compose 读取它即可自动重组，无需重建 Activity，因此不会闪烁。
 * - 未收录的文案回退为中文原文。
 */
object I18n {
    private const val PREF = "tinymc_settings"
    private const val KEY_LANG = "language"

    @Volatile
    private var table: Map<String, String> = emptyMap()

    private val _rev = mutableIntStateOf(0)

    /** 语言修订号：Compose 侧读取即可订阅语言变化。 */
    val revision: Int get() = _rev.intValue

    var current: AppLanguage = AppLanguage.ZH
        private set

    /** 在 Application/Activity 启动早期调用，保证首帧即为目标语言。 */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val saved = AppLanguage.fromCode(prefs.getString(KEY_LANG, null))
        val lang = saved ?: detectSystemLanguage()
        apply(lang)
        if (saved == null) prefs.edit().putString(KEY_LANG, lang.code).apply()
    }

    /** 依据系统语言推断初始语言。 */
    fun detectSystemLanguage(): AppLanguage {
        val c = Locale.getDefault().language.lowercase(Locale.ROOT)
        return when {
            c.startsWith("zh") -> AppLanguage.ZH
            c.startsWith("fr") -> AppLanguage.FR
            c.startsWith("es") -> AppLanguage.ES
            c.startsWith("ru") -> AppLanguage.RU
            else -> AppLanguage.EN
        }
    }

    /** 用户显式切换语言并持久化。 */
    fun setLanguage(context: Context, lang: AppLanguage) {
        apply(lang)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, lang.code).apply()
    }

    private fun apply(lang: AppLanguage) {
        current = lang
        table = when (lang) {
            AppLanguage.ZH -> emptyMap()
            AppLanguage.EN -> EN_TABLE
            AppLanguage.FR -> FR_TABLE
            AppLanguage.ES -> ES_TABLE
            AppLanguage.RU -> RU_TABLE
        }
        _rev.intValue = _rev.intValue + 1
    }

    fun translate(zh: String): String =
        if (current == AppLanguage.ZH) zh else table[zh] ?: zh
}

/**
 * 取词：传入简体中文原文，返回当前语言文案。
 *
 * 在 @Composable 中调用会读取 [I18n.revision]，从而在切换语言时自动重组。
 */
fun t(zh: String): String {
    // 读取修订号以建立（Compose 运行时层面的）订阅
    I18n.revision
    return I18n.translate(zh)
}

/** 带占位符的取词：译文中的 %s 会按顺序被 args 替换。 */
fun t(zh: String, vararg args: Any?): String {
    I18n.revision
    var s = I18n.translate(zh)
    for (a in args) s = s.replaceFirst("%s", a?.toString() ?: "")
    return s
}
