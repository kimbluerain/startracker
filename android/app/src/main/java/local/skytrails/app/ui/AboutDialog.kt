package local.skytrails.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.domob.heliodos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class LicenseDocument(val id: String, val title: String, val path: String)

/** Bundled text only: the entire attribution and license route works offline. */
@Composable fun AboutDialog(onDismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    val documents by produceState<List<LicenseDocument>?>(null, assets) {
        value = withContext(Dispatchers.IO) {
            val index = assets.open("licenses/index.json").bufferedReader().use { JSONObject(it.readText()) }
            val list = index.getJSONArray("documents")
            List(list.length()) { i ->
                val item = list.getJSONObject(i)
                LicenseDocument(item.getString("id"), item.getString("title"), item.getString("path"))
            }
        }
    }
    var selected by remember { mutableStateOf<LicenseDocument?>(null) }
    val content by produceState<String?>(null, selected) {
        value = null
        selected?.let { document ->
            value = withContext(Dispatchers.IO) {
                assets.open(document.path).bufferedReader().use { it.readText() }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (selected != null) selected = null else onDismiss() },
        title = { Text(selected?.title ?: "关于 startracker") },
        text = {
            // Recreate scroll state when moving between documents and the index.
            key(selected?.id) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("license_scroll"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (selected == null) {
                        Text("startracker ${BuildConfig.VERSION_NAME}", Modifier.testTag("about_version"))
                        Text("实验预览 · AR 现场精度尚未验收。方向校准不等于镜头标定。")
                        Text("核心计算、城市数据和下列许可均可离线使用。基于 Heliodos 的 GPL 修改版，完整源码：github.com/kimbluerain/startracker。", fontSize = 14.sp)
                        if (documents == null) Text("正在读取离线许可…")
                        documents?.forEach { document ->
                            TextButton(onClick = { selected = document }, modifier = Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp).testTag("license_${document.id}")) {
                                Text(document.title)
                            }
                        }
                    } else {
                        Text(content ?: "正在读取…", Modifier.testTag("license_text"), fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (selected != null) selected = null else onDismiss() },
                modifier = Modifier.testTag("about_back")) { Text(if (selected != null) "返回许可列表" else "返回设置") }
        }
    )
}
