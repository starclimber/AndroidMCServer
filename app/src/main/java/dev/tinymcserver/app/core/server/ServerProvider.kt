package dev.tinymcserver.app.core.server

import dev.tinymcserver.app.core.model.ServerType
import dev.tinymcserver.app.core.net.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 各服务端发行版的版本列表与下载地址解析。
 *  - Paper / Folia : fill.papermc.io v3
 *  - Purpur        : api.purpurmc.org v2
 *  - Vanilla       : Mojang launchermeta
 */
object ServerProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val PAPER_API = "https://fill.papermc.io/v3"
    private const val PURPUR_API = "https://api.purpurmc.org/v2"
    private const val MOJANG_MANIFEST =
        "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

    @Volatile
    private var vanillaManifest: JsonObject? = null

    fun listVersions(type: ServerType): List<String> = when (type) {
        ServerType.VANILLA -> vanillaVersions()
        ServerType.PAPER -> paperVersions("paper")
        ServerType.FOLIA -> paperVersions("folia")
        ServerType.PURPUR -> purpurVersions()
    }.filter { VersionUtil.isStable(it) }

    fun serverJarUrl(type: ServerType, version: String): String = when (type) {
        ServerType.VANILLA -> vanillaJar(version)
        ServerType.PAPER -> paperJar("paper", version)
        ServerType.FOLIA -> paperJar("folia", version)
        ServerType.PURPUR -> "$PURPUR_API/purpur/$version/latest/download"
    }

    // ---------------- Vanilla ----------------
    private fun manifest(): JsonObject {
        vanillaManifest?.let { return it }
        val root = json.parseToJsonElement(Http.get(MOJANG_MANIFEST)).jsonObject
        vanillaManifest = root
        return root
    }

    private fun vanillaVersions(): List<String> {
        val arr = manifest()["versions"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            if (o["type"]?.jsonPrimitive?.content == "release") o["id"]?.jsonPrimitive?.content else null
        }
    }

    private fun vanillaJar(version: String): String {
        val arr = manifest()["versions"]?.jsonArray ?: throw RuntimeException("no manifest")
        val url = arr.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == version }
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: throw RuntimeException("version $version not found")
        val vjson = json.parseToJsonElement(Http.get(url)).jsonObject
        return vjson["downloads"]?.jsonObject?.get("server")?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
            ?: throw RuntimeException("vanilla $version 无服务端下载")
    }

    // ---------------- Paper / Folia (fill v3) ----------------
    private fun paperVersions(project: String): List<String> {
        val root = json.parseToJsonElement(Http.get("$PAPER_API/projects/$project")).jsonObject
        val versions = root["versions"]?.jsonObject ?: return emptyList()
        val out = ArrayList<String>()
        for ((_, v) in versions) {
            v.jsonArray.forEach { out.add(it.jsonPrimitive.content) }
        }
        return out
    }

    private fun paperJar(project: String, version: String): String {
        val arr = json.parseToJsonElement(
            Http.get("$PAPER_API/projects/$project/versions/$version/builds")
        ).jsonArray
        val latest = arr.maxByOrNull { it.jsonObject["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1 }
            ?: throw RuntimeException("$project $version 无构建")
        return latest.jsonObject["downloads"]?.jsonObject?.get("server:default")
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: throw RuntimeException("$project $version 无下载地址")
    }

    // ---------------- Purpur ----------------
    private fun purpurVersions(): List<String> {
        val root = json.parseToJsonElement("${Http.get("$PURPUR_API/purpur")}").jsonObject
        val arr = root["versions"]?.jsonArray ?: return emptyList()
        return arr.map { it.jsonPrimitive.content }.reversed()
    }
}
