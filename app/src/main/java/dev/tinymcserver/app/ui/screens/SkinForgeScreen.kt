package dev.tinymcserver.app.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.skinforge.SkinForge
import dev.tinymcserver.app.ui.components.FieldCard
import dev.tinymcserver.app.ui.components.LabeledField
import dev.tinymcserver.app.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 皮肤工坊：按「种子 + 风格」确定性生成 64×64 的 Minecraft Java 版皮肤。
 * 同一种子必得同一张皮肤，可直接导出 PNG 上传。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinForgeScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var seed by remember { mutableStateOf(SkinForge.randomSeed()) }
    var styleKey by remember { mutableStateOf<String?>(null) }   // null = 自动
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var info by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }

    // 种子或风格一变就重新生成（64×64 很快，放后台线程）
    LaunchedEffect(seed, styleKey) {
        busy = true
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val px = SkinForge.generate(seed, styleKey)
                val bm = Bitmap.createBitmap(px, 64, 64, Bitmap.Config.ARGB_8888)
                val big = Bitmap.createScaledBitmap(bm, 320, 320, false) // false = 保持像素锐利
                Triple(bm, big, SkinForge.describe(seed, styleKey))
            }.getOrNull()
        }
        if (result != null) {
            bitmap = result.first
            preview = result.second
            info = result.third
        } else {
            info = "生成失败"
            bitmap = null
            preview = null
        }
        busy = false
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bm = bitmap ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { os ->
                        bm.compress(Bitmap.CompressFormat.PNG, 100, os)
                    } ?: false
                }.getOrDefault(false)
            }
            Toast.makeText(
                ctx, if (ok != false) "已导出 PNG" else "导出失败", Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("皮肤工坊") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            SectionTitle("预览")
            FieldCard {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    when {
                        busy && preview == null -> CircularProgressIndicator()
                        preview != null -> Image(
                            bitmap = preview!!.asImageBitmap(),
                            contentDescription = "皮肤预览",
                            modifier = Modifier.size(256.dp),
                            filterQuality = FilterQuality.None,
                        )
                        else -> Text("无法生成")
                    }
                }
                if (info.isNotBlank()) {
                    Text(
                        info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Text(
                    "64×64 Java 版皮肤（双层），同种子必得同一张。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("种子")
            FieldCard {
                LabeledField("种子（可自定义，同种子同结果）", seed, { seed = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { seed = SkinForge.randomSeed() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Casino, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Text("  换一个")
                    }
                    OutlinedButton(
                        onClick = { saveLauncher.launch("skin_$seed.png") },
                        enabled = bitmap != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Text("  导出 PNG")
                    }
                }
                Text(
                    "提示：把种子告诉朋友，对方输入同样的种子 + 同样的风格，就能得到一模一样的皮肤。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("风格")
            FieldCard {
                val chips = listOf("" to "自动") + SkinForge.STYLES
                // 两行流式排布
                chips.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { (key, label) ->
                            val selected = (styleKey ?: "") == key
                            FilterChip(
                                selected = selected,
                                onClick = { styleKey = key.ifBlank { null } },
                                label = { Text(if (key.isBlank()) label else "$label") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) {} }
                    }
                }
                Text(
                    "「自动」= 由种子决定风格；选定具体风格后，种子只决定配色与五官。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "皮肤由 SkinForge 算法程序化生成（约束式随机 + 明度护栏），非真人绘制。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}
