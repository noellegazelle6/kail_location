package com.kail.location.views.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kail.location.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kail.location.models.UpdateInfo

/**
 * Composable function to display an update dialog.
 *
 * @param info The update information to display.
 * @param onDismiss Callback when the dialog is dismissed.
 * @param onConfirm Callback when the confirm button (Download) is clicked.
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_title)) },
        text = {
             Text(info.content)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 更新下载对话框，支持显示下载进度。
 *
 * @param info 更新信息。
 * @param downloading 是否正在下载。
 * @param progress 下载进度（0-100）。
 * @param onDismiss 关闭回调。
 * @param onStartDownload 开始下载回调。
 */
@Composable
fun UpdateDownloadDialog(
    info: UpdateInfo,
    downloading: Boolean,
    progress: Int,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit,
    progressIndeterminate: Boolean = false
) {
    AlertDialog(
        onDismissRequest = {
            if (!info.forceUpdate) onDismiss()
        },
        title = {
            Text(stringResource(R.string.update_title) + " v" + info.version.removePrefix("v"))
        },
        text = {
            Column {
                Text(info.content)
                if (info.fileSize > 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.update_file_size, formatFileSize(info.fileSize)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (downloading) {
                    if (progressIndeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.update_downloading))
                    } else {
                        LinearProgressIndicator(
                            progress = progress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.download_progress, progress))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!downloading) onStartDownload()
            }) {
                Text(if (downloading) stringResource(R.string.update_downloading) else stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            if (!info.forceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
