package dev.tinymcserver.app.core.server

/**
 * 日志脱敏：分享 / 提交 issue 前，抹掉日志里的隐私信息。
 *
 * 处理内容：
 *  - IPv4 / IPv6 地址  → x.x.x.x（端口号保留）
 *  - UUID             → 只保留前 8 位与后 4 位，中段以 * 覆盖
 *  - 玩家名           → 统一替换为 Player1 / Player2 …（同一玩家始终同一化名）
 *  - 命令参数         → /op <名字>、/ban <名字> 等一并替换
 *
 * 玩家名先"发现"再替换：只替换那些确实出现在玩家语境（进服/退服/聊天/连接/成就/死亡/管理命令）
 * 中的名字，避免误伤日志里的普通英文单词。
 */
object LogSanitizer {

    // ---------- 隐私模式 ----------

    private val IPV4 = Regex("""(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])""")

    // 注意：至少 4 组才判定为 IPv6，否则日志时间戳 [12:00:00] 会被误伤
    private val IPV6 = Regex(
        """(?<![0-9A-Fa-f:])(?:[0-9A-Fa-f]{1,4}:){3,7}[0-9A-Fa-f]{1,4}(?![0-9A-Fa-f:])"""
    )

    private val UUID_RE = Regex(
        """(?<![0-9A-Fa-f])[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}(?![0-9A-Fa-f])"""
    )

    // ---------- 玩家名线索 ----------

    private val P_JOIN = Regex("""(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16}) (?:joined|left) the game""")
    private val P_CHAT = Regex("""<([A-Za-z0-9_]{3,16})>""")
    private val P_ADDR = Regex("""(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})\[/""")
    private val P_CONN = Regex("""(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16}) (?:lost connection|logged in with entity)""")
    private val P_UUIDLINE = Regex("""(?<![A-Za-z0-9_])UUID of player ([A-Za-z0-9_]{3,16}) is""")
    private val P_CMDUSER = Regex("""(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16}) issued server command""")
    private val P_ADV = Regex("""(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16}) has made the advancement""")
    private val P_DEATH = Regex(
        """(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16}) (?:was |drowned|blew up|fell |burned|burned to|went up|hit the ground|tried to swim|experienced kinetic|withered|starved|suffocated|was impaled|was pummeled|was fireballed|was stung|froze to death|was skewered|was doomed|died)"""
    )

    /** 命令 + 其后的玩家名参数（如 `/op Notch`、`/whitelist add Steve`） */
    private val P_CMD = Regex(
        """(/(?:op|deop|ban|ban-ip|pardon|pardon-ip|kick|whitelist add|whitelist remove|mute|unmute|playtime) )([A-Za-z0-9_]{3,16})"""
    )

    /** 明显不是玩家名的词，避免误伤 */
    private val WHITELIST_NOISE = setOf(
        "Server", "Done", "Starting", "Stopping", "Preparing", "Loading", "Saving",
        "Unknown", "Failed", "Could", "Error", "Warning", "Info", "There", "Player",
    )

    private val PLAYER_PATTERNS = listOf(P_JOIN, P_CHAT, P_ADDR, P_CONN, P_UUIDLINE, P_CMDUSER, P_ADV, P_DEATH)

    /**
     * 对一段文本做脱敏。
     *
     * @param extraPlayers 额外已知的玩家名（例如从实例的 usercache.json / ops.json 读到）
     */
    fun sanitize(text: String, extraPlayers: Collection<String> = emptyList()): String {
        if (text.isBlank()) return text

        // 1) 收集玩家名
        val names = LinkedHashSet<String>()
        names.addAll(extraPlayers.filter { it.isNotBlank() })
        for (p in PLAYER_PATTERNS) p.findAll(text).forEach { names.add(it.groupValues[1]) }
        P_CMD.findAll(text).forEach { names.add(it.groupValues[2]) }
        names.removeAll(WHITELIST_NOISE)
        names.removeAll { it.length < 3 }

        // 2) 分配稳定化名（按名称排序，保证同一批日志里映射一致）
        val alias = LinkedHashMap<String, String>()
        names.sorted().forEachIndexed { i, n -> alias[n] = "Player${i + 1}" }

        var out = text

        // 3) UUID：保留前 8 位 + 后 4 位
        out = UUID_RE.replace(out) { m ->
            val v = m.value
            v.substring(0, 8) + "-****-****-****-" + v.takeLast(4)
        }

        // 4) IP
        out = IPV4.replace(out) { "x.x.x.x" }
        out = IPV6.replace(out) { "x:x:x:x" }

        // 5) 玩家名（长的先替换，避免短名是长名前缀时被切碎）
        for ((name, a) in alias.entries.sortedByDescending { it.key.length }) {
            val re = Regex("""(?<![A-Za-z0-9_])""" + Regex.escape(name) + """(?![A-Za-z0-9_])""")
            out = re.replace(out, a)
        }

        return out
    }

    /** 返回本次会命中的玩家名数量，供 UI 提示用。 */
    fun playerNameCount(text: String): Int {
        val names = LinkedHashSet<String>()
        for (p in PLAYER_PATTERNS) p.findAll(text).forEach { names.add(it.groupValues[1]) }
        P_CMD.findAll(text).forEach { names.add(it.groupValues[2]) }
        names.removeAll(WHITELIST_NOISE)
        names.removeAll { it.length < 3 }
        return names.size
    }
}
