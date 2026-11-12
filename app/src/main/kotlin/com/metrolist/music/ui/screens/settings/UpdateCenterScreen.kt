/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.constants.CheckForUpdatesKey
import com.metrolist.music.updater.AppUpdateViewModel
import com.metrolist.music.updater.UpdateFailureReasons
import com.metrolist.music.updater.UpdateInfo
import com.metrolist.music.updater.UpdateInstaller
import com.metrolist.music.updater.UpdateStatus
import com.metrolist.music.utils.rememberPreference
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings → About MELORA → Update Center.
 *
 * Renders the full lifecycle of an in-app update: check availability,
 * download with progress, verification, and the official install hand-off.
 * State comes from the shared [AppUpdateViewModel] so this screen, the
 * update dialog and the "new version" badges always agree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCenterScreen(
    appUpdateViewModel: AppUpdateViewModel,
    navController: NavHostController,
) {
    val status by appUpdateViewModel.status.collectAsStateWithLifecycle()
    val lastChecked by appUpdateViewModel.lastCheckedAt.collectAsStateWithLifecycle()
    val (checkForUpdates, setCheckForUpdates) = rememberPreference(CheckForUpdatesKey, defaultValue = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_center)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            StatusCard(
                status = status,
                lastCheckedAt = lastChecked,
                onCheckNow = { appUpdateViewModel.manualCheck() },
                onDownload = { appUpdateViewModel.downloadAndVerify() },
                onCancel = { appUpdateViewModel.cancelDownload() },
                onInstall = { appUpdateViewModel.requestInstall() },
                onGrantPermission = { appUpdateViewModel.requestUnknownSourcesScreen() },
            )

            val info =
                when (val s = status) {
                    is UpdateStatus.Available -> s.info
                    is UpdateStatus.ReadyToInstall -> s.info
                    else -> null
                }

            info?.let {
                Spacer(modifier = Modifier.height(16.dp))
                WhatsNewCard(it)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preference toggle + reassurance about data safety
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.check_for_updates),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.update_center_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = checkForUpdates, onCheckedChange = { setCheckForUpdates(it) })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.uc_data_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(
    status: UpdateStatus,
    lastCheckedAt: Long?,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.melora_logo),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = Color.Unspecified,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_center),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            StatusContent(
                status = status,
                onCheckNow = onCheckNow,
                onDownload = onDownload,
                onCancel = onCancel,
                onInstall = onInstall,
                onGrantPermission = onGrantPermission,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text =
                    lastCheckedAt?.let {
                        stringResource(R.string.uc_last_checked, formatDate(it))
                    } ?: stringResource(R.string.uc_never_checked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusContent(
    status: UpdateStatus,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
) {
    when (status) {
        UpdateStatus.Idle ->
            StatusBlock(
                statusText = stringResource(R.string.uc_status_idle),
                statusColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                ActionButton(text = stringResource(R.string.uc_check_now), onClick = onCheckNow)
            }

        is UpdateStatus.Checking ->
            StatusBlock(
                statusText = stringResource(R.string.uc_status_checking),
                statusColor = MaterialTheme.colorScheme.primary,
                leading = { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) },
            )

        is UpdateStatus.UpToDate ->
            StatusBlock(
                statusText = stringResource(R.string.uc_status_up_to_date),
                statusColor = MaterialTheme.colorScheme.primary,
            ) {
                ActionButton(text = stringResource(R.string.uc_check_now), onClick = onCheckNow)
            }

        is UpdateStatus.Available -> {
            StatusBlock(
                statusText = stringResource(R.string.uc_status_available),
                statusColor = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            NewVersionDetails(status.info)
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(text = stringResource(R.string.uc_download_update), onClick = onDownload, primary = true)
            Spacer(modifier = Modifier.height(8.dp))
            // Let users pick up a re-published release without waiting for
            // the next periodic check.
            ActionButton(text = stringResource(R.string.uc_check_now), onClick = onCheckNow, primary = false)
        }

        is UpdateStatus.Downloading -> {
            StatusBlock(
                statusText = stringResource(R.string.uc_status_downloading),
                statusColor = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
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
            ActionButton(text = stringResource(R.string.uc_cancel), onClick = onCancel, primary = false)
        }

        is UpdateStatus.Verifying ->
            StatusBlock(
                statusText = stringResource(R.string.uc_status_verifying),
                statusColor = MaterialTheme.colorScheme.primary,
                leading = { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) },
            )

        is UpdateStatus.ReadyToInstall -> {
            StatusBlock(
                statusText = stringResource(R.string.uc_status_ready),
                statusColor = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.uc_verified_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(text = stringResource(R.string.uc_install_update), onClick = onInstall, primary = true)
        }

        UpdateStatus.Installing ->
            StatusBlock(
                statusText = stringResource(R.string.uc_status_installing),
                statusColor = MaterialTheme.colorScheme.primary,
                leading = { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) },
            )

        UpdateStatus.InstallPermissionRequired -> {
            StatusBlock(
                statusText = stringResource(R.string.uc_status_install_permission),
                statusColor = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.install_unknown_needed_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            // The system decides at click time whether we may install now or
            // must first pass through the official unknown-sources page.
            val context = LocalContext.current
            PermissionButton(onInstall = onInstall, onGrant = onGrantPermission, canInstall = { UpdateInstaller.canInstall(context) })
        }

        is UpdateStatus.CheckFailed ->
            StatusBlock(
                statusText = failureMessage(status.reason),
                statusColor = MaterialTheme.colorScheme.error,
            ) {
                // Download-stage failures retry the download; check-stage
                // failures re-run the check. A check is always reachable so
                // users can pick up a fixed release without restarting.
                val retry = if (reasonIsDownloadStage(status.reason)) onDownload else onCheckNow
                ActionButton(text = stringResource(R.string.uc_retry), onClick = retry)
                if (reasonIsDownloadStage(status.reason)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionButton(text = stringResource(R.string.uc_check_now), onClick = onCheckNow)
                }
            }
    }
}

/** Reasons that occur after (or during) the APK download itself. */
private fun reasonIsDownloadStage(reason: String): Boolean =
    reason in
        setOf(
            UpdateFailureReasons.DOWNLOAD,
            UpdateFailureReasons.STORAGE,
            UpdateFailureReasons.VERIFY_CHECKSUM,
            UpdateFailureReasons.VERIFY_PACKAGE,
            UpdateFailureReasons.VERIFY_VERSION,
            UpdateFailureReasons.VERIFY_SIGNATURE,
            UpdateFailureReasons.VERIFY_APK_NOT_FOUND,
        )

@Composable
private fun NewVersionDetails(info: UpdateInfo) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.uc_latest_version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = info.manifest.versionName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        info.apkSizeBytes.takeIf { it > 0 }?.let { size ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.uc_size, formatBytes(size)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        info.publishedAt?.let { published ->
            formatDateIso(published)?.let { formatted ->
                Spacer(modifier = Modifier.height(2.dp))
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
private fun PermissionButton(onInstall: () -> Unit, onGrant: () -> Unit, canInstall: () -> Boolean) {
    Button(
        onClick = { if (canInstall()) onInstall() else onGrant() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.install_unknown_grant))
    }
}

@Composable
private fun StatusBlock(
    statusText: String,
    statusColor: Color,
    leading: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        leading?.let {
            it()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleSmall,
            color = statusColor,
        )
    }
    content?.let {
        Column { it() }
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    if (primary) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text)
        }
    }
}

@Composable
internal fun failureMessage(reason: String): String =
    when (reason) {
        UpdateFailureReasons.OFFLINE -> stringResource(R.string.uc_err_offline)
        UpdateFailureReasons.RATE_LIMIT -> stringResource(R.string.uc_err_rate_limit)
        UpdateFailureReasons.MANIFEST_UNAVAILABLE -> stringResource(R.string.uc_err_manifest_unavailable)
        UpdateFailureReasons.NETWORK_TIMEOUT -> stringResource(R.string.uc_err_timeout)
        UpdateFailureReasons.HTTP -> stringResource(R.string.uc_err_http)
        UpdateFailureReasons.MARSHAL_FAILURE -> stringResource(R.string.uc_err_marshal)
        UpdateFailureReasons.DOWNLOAD -> stringResource(R.string.uc_err_download)
        UpdateFailureReasons.STORAGE -> stringResource(R.string.uc_err_storage)
        UpdateFailureReasons.VERIFY_CHECKSUM -> stringResource(R.string.uc_err_verify_checksum)
        UpdateFailureReasons.VERIFY_PACKAGE -> stringResource(R.string.uc_err_verify_package)
        UpdateFailureReasons.VERIFY_VERSION -> stringResource(R.string.uc_err_verify_version)
        UpdateFailureReasons.VERIFY_SIGNATURE -> stringResource(R.string.uc_err_verify_signature)
        UpdateFailureReasons.VERIFY_APK_NOT_FOUND -> stringResource(R.string.uc_err_verify_apk_not_found)
        else -> stringResource(R.string.uc_err_generic)
    }

@Composable
private fun WhatsNewCard(info: UpdateInfo) {
    val notes = info.manifest.releaseNotes
    val body = info.manifest.releaseBody ?: info.releaseBody

    if (notes.isEmpty() && body.isNullOrBlank()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.uc_whats_new),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (notes.isNotEmpty()) {
                notes.forEach { note ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = stringResource(R.string.list_bullet),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                Text(
                    text = body.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    DateTimeFormatter
        .ofPattern("MMM d, yyyy • HH:mm")
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

private fun formatDateIso(iso: String): String? =
    runCatching {
        DateTimeFormatter
            .ofPattern("MMM d, yyyy")
            .withLocale(Locale.getDefault())
            .format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
    }.getOrNull()

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "--"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (value == value.toLong().toDouble() && index == 0) {
        "${value.toLong()} ${units[index]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }
}
