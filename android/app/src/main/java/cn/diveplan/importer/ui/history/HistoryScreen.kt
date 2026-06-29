package cn.diveplan.importer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.diveplan.importer.data.DumpEntry
import cn.diveplan.importer.data.network.DiveImportJobSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P6 上传历史屏幕。
 *
 * 两个分区：
 *  ① 本地文件（.bin RFCOMM dump / .fit Garmin FIT）+ 上传状态
 *  ② 服务端 dive-import-jobs 解析状态（轮询：pending/running 时 5s 刷新）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("上传历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        if (state.loading && state.localFiles.isEmpty() && state.serverJobs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ── 本地文件 ──────────────────────────────────────
            item {
                SectionHeader(
                    title = "本地文件",
                    detail = "${state.localFiles.size} 个文件 · ${formatBytes(state.diskUsageBytes)}"
                )
            }
            if (state.localFiles.isEmpty()) {
                item {
                    EmptyHint("暂无本地文件")
                }
            } else {
                items(state.localFiles, key = { it.file.absolutePath }) { entry ->
                    LocalFileRow(entry)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
            }

            // ── 服务端解析任务 ────────────────────────────────
            item {
                SectionHeader(
                    title = "解析任务",
                    detail = "${state.serverJobs.size} 条记录（服务端）"
                )
            }
            if (state.serverJobs.isEmpty()) {
                item {
                    EmptyHint("暂无解析任务")
                }
            } else {
                items(state.serverJobs, key = { it.id }) { job ->
                    ServerJobRow(job)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
            }
        }
    }
}

// ── 子 Composable ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun LocalFileRow(entry: DumpEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (entry.uploaded) Icons.Filled.CheckCircle else Icons.Filled.UploadFile,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (entry.uploaded) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val dirName = entry.file.parentFile?.name ?: ""
            Text(dirName, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    entry.file.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    formatBytes(entry.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (entry.uploaded) "已上传" else "待上传",
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.uploaded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun ServerJobRow(job: DiveImportJobSummary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 状态图标
        val (icon, tint) = when (job.status) {
            "completed" -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
            "failed"    -> Icons.Filled.Error to MaterialTheme.colorScheme.error
            "running"   -> Icons.Filled.Upload to MaterialTheme.colorScheme.tertiary
            else        -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sourceLabel(job.source), style = MaterialTheme.typography.bodyMedium)
                Text(statusLabel(job.status), style = MaterialTheme.typography.labelSmall,
                    color = tint)
            }
            if (job.status == "completed") {
                Text(
                    buildString {
                        if (job.stagedCount > 0)  append("暂存 ${job.stagedCount}  ")
                        if (job.createdCount > 0) append("新建 ${job.createdCount}  ")
                        if (job.duplicateCount > 0) append("重复 ${job.duplicateCount}")
                    }.trim().ifBlank { "已完成" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (job.status == "failed" && !job.errorMessage.isNullOrBlank()) {
                Text(
                    job.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
            }
            Text(
                formatTimestamp(job.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatBytes(job.rawSizeBytes.toLong()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── 工具 ────────────────────────────────────────────────────────────────────

private fun sourceLabel(source: String) = when (source) {
    "classic_bt_dump" -> "RFCOMM dump"
    "garmin_fit"      -> "Garmin FIT"
    else              -> source
}

private fun statusLabel(status: String) = when (status) {
    "pending"   -> "排队中"
    "running"   -> "解析中"
    "completed" -> "完成"
    "failed"    -> "失败"
    else        -> status
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0         -> "0 B"
    bytes < 1024       -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else               -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatTimestamp(iso: String): String = runCatching {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val date = fmt.parse(iso.take(19)) ?: return@runCatching iso
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(date)
}.getOrDefault(iso.take(16))
