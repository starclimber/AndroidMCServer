package dev.tinymcserver.app.core.storage

import android.content.Context
import java.io.File

/** 应用私有目录布局 */
object Paths {

    fun instancesRoot(ctx: Context): File =
        File(ctx.filesDir, "instances").apply { mkdirs() }

    fun instanceDir(ctx: Context, id: String): File =
        File(instancesRoot(ctx), id).apply { mkdirs() }

    fun jreRoot(ctx: Context): File = File(ctx.filesDir, "jre").apply { mkdirs() }

    fun jreHome(ctx: Context, major: Int): File = File(jreRoot(ctx), "jre$major")

    fun jreJava(ctx: Context, major: Int): File = File(jreHome(ctx, major), "bin/java")

    fun isJreInstalled(ctx: Context, major: Int): Boolean = jreJava(ctx, major).exists()

    fun backupsDir(ctx: Context, id: String): File =
        File(instanceDir(ctx, id), "backups").apply { mkdirs() }
}
