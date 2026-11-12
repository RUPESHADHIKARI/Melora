/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.updater.UpdateInfo
import com.metrolist.music.updater.UpdateStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * MELORA-branded "A new update is available" dialog.
 *
 * Shows current → latest version, size, release date, What's New, and morphs
 * into a progress/verify/install surface while [status] moves through the
 * download pipeline, so the user never leaves context between "Update Now"
 * and the official installer.
 */
@Composable
fun MeloraUpdateDialog(
    info: UpdateInfo,
    status: UpdateStatus,
    onUpdateNow: () -> Unit,
    onInstallNow: () -> Unit,
    onCancelDownload: () -> Unit,
    onGrantPermission: () -> Unit,
    onRemindLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnClickOutside = status is UpdateStatus.Available,
                dismissOnBackPress = status is UpdateStatus.Available,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                HeaderRow()

                Spacer(modifier = Modifier.height(20.dp))

                // Current → Latest versions
                VersionFlow(info)

                Spacer(modifier = Modifier.height(12.dp))

                MetaRow(info)

                Spacer(modifier = Modifier.height(16.dp))

                WhatsNew(info)

                Spacer(modifier = Modifier.height(20.dp))

                ActionRow(
                    status = status,
                    onUpdateNow = onUpdateNow,
                    onInstallNow = onInstallNow,
                    onCancelDownload = onCancelDownload,
                    onGrantPermission = onGrantPermission,
                    onRemindLater = onRemindLater,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                            ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.melora_logo),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = Color.Unspecified,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.update_dialog_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VersionFlow(info: UpdateInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VersionChip(stringResource(R.string.uc_current_version), BuildConfig.VERSION_NAME)
        Text(
            text = "→",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        VersionChip(stringResource(R.string.uc_latest_version), info.manifest.versionName, accent = true)
    }
}

@Composable
private fun VersionChip(
    label: String,
    version: String,
    accent: Boolean = false,
) {
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = version,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color =
                if (accent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
private fun MetaRow(info: UpdateInfo) {
    Column {
        info.apkSizeBytes.takeIf { it > 0 }?.let { size ->
            Text(
                text = formatBytes(size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        info.publishedAt?.let { published ->
            formatDateIso(published)?.let { formatted ->
                Text(
                    text = stringResource(R.string.uc_release_date, formatted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WhatsNew(info: UpdateInfo) {
    val notes = info.manifest.releaseNotes
    val body = info.manifest.releaseBody ?: info.releaseBody
    if (notes.isEmpty() && body.isNullOrBlank()) return

    Column {
        Text(
            text = stringResource(R.string.uc_whats_new),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
            if (notes.isNotEmpty()) {
                notes.forEach { note ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            text = stringResource(R.string.list_bullet),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = body.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    status: UpdateStatus,
    onUpdateNow: () -> Unit,
    onInstallNow: () -> Unit,
    onCancelDownload: () -> Unit,
    onGrantPermission: () -> Unit,
    onRemindLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (status) {
        is UpdateStatus.Downloading -> {
            Column {
                LinearProgressIndicator(
                    progress = { status.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        stringResource(
                            R.string.uc_downloaded_of,
                            formatBytes(status.downloadedBytes),
                            formatBytes(status.totalBytes),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.uc_cancel))
                }
            }
        }

        is UpdateStatus.Verifying -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.upd_verifying),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is UpdateStatus.ReadyToInstall -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.upd_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onInstallNow) {
                    Text(stringResource(R.string.upd_install))
                }
            }
        }

        is UpdateStatus.Installing -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.uc_status_installing),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        UpdateStatus.InstallPermissionRequired -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.install_unknown_needed_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.install_unknown_needed_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.uc_close))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Button(
                        onClick = {
                            // The system decides at click time whether we may
                            // install now or must first pass through the
                            // official unknown-sources page.
                            val can = com.metrolist.music.updater.UpdateInstaller.canInstall(context)
                            if (can) onInstallNow() else onGrantPermission()
                        },
                    ) {
                        Text(stringResource(R.string.install_unknown_grant))
                    }
                }
            }
        }

        is UpdateStatus.CheckFailed -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = failureText(status.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.uc_close))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onUpdateNow) {
                        Text(stringResource(R.string.uc_retry))
                    }
                }
            }
        }

        is UpdateStatus.Checking,
        is UpdateStatus.Available,
        is UpdateStatus.UpToDate,
        UpdateStatus.Idle,
        -> {
            if (status is UpdateStatus.Checking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.uc_status_checking), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row {
                TextButton(onClick = onRemindLater) {
                    Text(stringResource(R.string.upd_remind_later))
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onUpdateNow) {
                    Text(stringResource(R.string.upd_update_now))
                }
            }
        }
    }
}

@Composable
private fun failureText(reason: String): String =
    when (reason) {
        "offline" -> stringResource(R.string.uc_err_offline)
        "rate_limit" -> stringResource(R.string.uc_err_rate_limit)
        "manifest_unavailable" -> stringResource(R.string.uc_err_manifest_unavailable)
        "timeout" -> stringResource(R.string.uc_err_timeout)
        "http" -> stringResource(R.string.uc_err_http)
        "marshal" -> stringResource(R.string.uc_err_marshal)
        "download" -> stringResource(R.string.uc_err_download)
        "storage" -> stringResource(R.string.uc_err_storage)
        "verify_checksum" -> stringResource(R.string.uc_err_verify_checksum)
        "verify_package" -> stringResource(R.string.uc_err_verify_package)
        "verify_version" -> stringResource(R.string.uc_err_verify_version)
        "verify_signature" -> stringResource(R.string.uc_err_verify_signature)
        "verify_apk_not_found" -> stringResource(R.string.uc_err_verify_apk_not_found)
        else -> stringResource(R.string.uc_err_generic)
    }

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "--"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
}

private fun formatDateIso(iso: String): String? =
    runCatching {
        DateTimeFormatter
            .ofPattern("MMM d, yyyy")
            .withLocale(Locale.getDefault())
            .format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
    }.getOrNull()
