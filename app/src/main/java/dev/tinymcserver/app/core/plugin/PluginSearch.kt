package dev.tinymcserver.app.core.plugin

import dev.tinymcserver.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/** Modrinth 插件搜索与下载 */
object PluginSearch {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val API = "https://api.modrinth.com/v2"

    data class Hit(
        val projectId: String,
        val title: String,
        val description: String,
        val downloads: Long,
        val iconUrl: String?,
        val slug: String,
    )

    /** [size] 为上游声明的字节数，下载完成后用于完整性校验 */
    data class Download(val url: String, val filename: String, val size: Long)

    /**
     * @param loader Bukkit 系插件在 Modrinth 中对应的 loader 标识：paper / purpur / folia / spigot / bukkit
     */
    suspend fun search(query: String, mcVersion: String, loader: String): List<Hit> =
        withContext(Dispatchers.IO) {
            val facets = buildString {
                append("[")
                append("[\"project_type:plugin\"]")
                if (mcVersion.isNotBlank()) append(",[\"versions:$mcVersion\"]")
                if (loader.isNotBlank()) append(",[\"categories:$loader\"]")
                append("]")
            }
            val url = "$API/search?query=${enc(query)}&limit=30&index=downloads&facets=${enc(facets)}"
            val root = json.parseToJsonElement(Http.get(url)).jsonObject
            root["hits"]?.jsonArray?.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["project_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                Hit(
                    projectId = id,
                    title = o["title"]?.jsonPrimitive?.content ?: id,
                    description = o["description"]?.jsonPrimitive?.content ?: "",
                    downloads = o["downloads"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    iconUrl = o["icon_url"]?.jsonPrimitive?.content,
                    slug = o["slug"]?.jsonPrimitive?.content ?: id,
                )
            } ?: emptyList()
        }

    /**
     * 选择与 MC 版本/加载器兼容的最新文件。
     * 优先取 Modrinth 标记为 primary 的主文件；没有标记时排除 sources/dev/javadoc 等附属包，
     * 避免把非运行文件当成插件下载（这会让服务端报 "Failed to open plugin jar"）。
     */
    suspend fun pickDownload(projectId: String, mcVersion: String, loader: String): Download? =
        withContext(Dispatchers.IO) {
            val gv = enc("[\"$mcVersion\"]")
            val ld = enc("[\"$loader\"]")
            val url = "$API/project/$projectId/version?game_versions=$gv&loaders=$ld"
            val arr = runCatching {
                json.parseToJsonElement(Http.get(url)).jsonArray
            }.getOrNull() ?: return@withContext null

            val first = arr.firstOrNull()?.jsonObject ?: return@withContext null
            val objs = first["files"]?.jsonArray?.map { it.jsonObject } ?: return@withContext null

            val f = objs.firstOrNull { it["primary"]?.jsonPrimitive?.content?.toBoolean() == true }
                ?: objs.firstOrNull { o ->
                    val n = o["filename"]?.jsonPrimitive?.content ?: ""
                    val lower = n.lowercase()
                    !lower.contains("sources") && !lower.contains("-dev") && !lower.contains("javadoc")
                }
                ?: objs.firstOrNull()
                ?: return@withContext null

            val dl = f["url"]?.jsonPrimitive?.content ?: return@withContext null
            val fn = f["filename"]?.jsonPrimitive?.content ?: "plugin.jar"
            val sz = f["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            Download(dl, fn, sz)
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
