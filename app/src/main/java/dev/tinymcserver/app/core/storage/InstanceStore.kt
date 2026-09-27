package dev.tinymcserver.app.core.storage

import android.content.Context
import dev.tinymcserver.app.core.model.ServerInstance
import dev.tinymcserver.app.core.model.ServerType
import dev.tinymcserver.app.core.model.InstanceConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 实例清单持久化（instances.json） */
object InstanceStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private fun file(ctx: Context) = File(ctx.filesDir, "instances.json")

    fun load(ctx: Context): MutableList<ServerInstance> {
        val f = file(ctx)
        if (!f.exists()) return mutableListOf()
        return try {
            json.decodeFromString<List<ServerInstance>>(f.readText()).toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(ctx: Context, list: List<ServerInstance>) {
        file(ctx).writeText(json.encodeToString(list))
    }

    fun add(ctx: Context, inst: ServerInstance): MutableList<ServerInstance> {
        val l = load(ctx).apply { add(inst) }
        save(ctx, l); return l
    }

    fun update(ctx: Context, inst: ServerInstance): MutableList<ServerInstance> {
        val l = load(ctx)
        val i = l.indexOfFirst { it.id == inst.id }
        if (i >= 0) l[i] = inst
        save(ctx, l); return l
    }

    fun remove(ctx: Context, id: String): MutableList<ServerInstance> {
        val l = load(ctx).filterNot { it.id == id }.toMutableList()
        save(ctx, l); return l
    }

    fun newId(): String = "srv_" + System.currentTimeMillis().toString(36) +
        "_" + (1000..9999).random()

    fun defaultConfig(): InstanceConfig = InstanceConfig(
        name = "新服务器",
        typeKey = ServerType.PAPER.key,
        jreMajor = 21,
    )
}
