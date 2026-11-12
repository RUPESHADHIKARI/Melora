/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.BuildConfig
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.toUpdateFailureReason
import com.metrolist.music.updater.UpdateFailureReasons
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Single orchestration point of the whole update flow:
 *
 *  app start → [startupCheck] (24 h cadence, cached, silent) →
 *      ├─ up to date → [UpdateStatus.UpToDate]
 *      ├─ trusted manifest newer → [UpdateStatus.Available] (dialog/badges)
 *      └─ error → silent, [UpdateStatus.Idle]
 *
 * "Update Now" → [downloadAndVerify] (progress, resume, cancel) →
 *      [UpdateStatus.Verifying] → [UpdateStatus.ReadyToInstall] →
 *      [requestInstall] (official system installer) → in-place update that
 *      preserves all user data (package id + signature are untouched).
 *
 * Every network step runs on Dispatchers.IO; the player and playback are
 * never touched while states change.
 */
@HiltViewModel
class AppUpdateViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val _lastCheckedAt = MutableStateFlow<Long?>(null)
    val lastCheckedAt: StateFlow<Long?> = _lastCheckedAt.asStateFlow()

    /** Newest trusted manifest seen by a check; retry after failures relies on it. */
    private val _availableInfo = MutableStateFlow<UpdateInfo?>(null)
    val availableInfo: StateFlow<UpdateInfo?> = _availableInfo.asStateFlow()

    /** Intent awaiting the system installer; the UI consumes and launches it. */
    private val _pendingInstall = MutableStateFlow<Intent?>(null)
    val pendingInstall: StateFlow<Intent?> = _pendingInstall.asStateFlow()

    private var downloadJob: Job? = null

    @Volatile
    private var downloadCancelled = false

    /** Info + path of the last verified APK, to recover from cancelled installs. */
    private var lastReady: Pair<UpdateInfo, String>? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Stale artifacts of older update attempts never make sense to
            // keep; a pending (newer) APK survives so a restart can restore
            // the "ready to install" offer.
            UpdateInstaller.cleanupOldApks(context, BuildConfig.VERSION_CODE.toLong())
            _lastCheckedAt.value = context.readLastCheckedAt()
            restoreVerifiedApkIfAny()
        }
    }

    /**
     * Startup gate: refresh at most once every [Updater.CHECK_INTERVAL_MILLIS];
     * otherwise serve the persisted snapshot without touching the network.
     * Failures are silent so a broken/offline start never bothers the user.
     */
    fun startupCheck(autoEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!autoEnabled) {
                return@launch
            }

            val lastChecked = context.readLastCheckedAt().also { _lastCheckedAt.value = it }
            val stale =
                lastChecked == null ||
                    System.currentTimeMillis() - lastChecked >= Updater.CHECK_INTERVAL_MILLIS

            if (!stale) {
                applyCachedSnapshot(lastChecked)
            } else {
                performNetworkCheck(silent = true)
            }
        }
    }

    /** Explicit "Check for updates" — always hits the network and reports errors. */
    fun manualCheck() {
        viewModelScope.launch(Dispatchers.IO) { performNetworkCheck(silent = false) }
    }

    private suspend fun applyCachedSnapshot(lastChecked: Long?) {
        if (lastChecked == null || _status.value !is UpdateStatus.Idle) return
        val info = context.readCachedUpdateInfo() ?: return
        if (Updater.hasUpdate(info)) {
            _availableInfo.value = info
            _status.value = UpdateStatus.Available(info)
        } else {
            _status.value = UpdateStatus.UpToDate(lastChecked)
        }
    }

    private suspend fun performNetworkCheck(silent: Boolean) {
        // Never rewind a download/install that is already in flight.
        if (_status.value is UpdateStatus.ReadyToInstall || _status.value is UpdateStatus.Installing) return

        _status.value = UpdateStatus.Checking
        try {
            val (info, hasUpdate) = Updater.checkForUpdate(forceRefresh = true).getOrThrow()
            context.storeUpdateCheckpoint(info)
            _lastCheckedAt.value = System.currentTimeMillis()
            if (hasUpdate) {
                _availableInfo.value = info
                _status.value = UpdateStatus.Available(info)
            } else {
                _availableInfo.value = null
                _status.value = UpdateStatus.UpToDate(_lastCheckedAt.value)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = e.toUpdateFailureReason()
            Timber.w(e, "Update check failed: %s", reason)
            when {
                silent && _status.value is UpdateStatus.Checking -> _status.value = UpdateStatus.Idle
                !silent -> _status.value = UpdateStatus.CheckFailed(reason)
                // Silent with a non-Checking status: leave the state alone.
            }
        }
    }

    /**
     * Download → verify pipeline. Reuses an already-verified APK, resumes an
     * interrupted download, and reports progress for the dialog/Update Center.
     */
    fun downloadAndVerify() {
        if (downloadJob?.isActive == true) return
        val info =
            (_status.value as? UpdateStatus.Available)?.info ?: _availableInfo.value ?: return

        downloadCancelled = false
        val apkFile = UpdateDownloader.updateApkFile(context, info.manifest.versionCode)
        _status.value = UpdateStatus.Downloading(downloadedBytes = 0L, totalBytes = 0L, progress = 0f)

        downloadJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val verified =
                        UpdateDownloader.downloadApk(
                            context = context,
                            url = info.manifest.apkUrl,
                            expectedSha256 = info.manifest.sha256,
                            apkFile = apkFile,
                            isCancelled = { downloadCancelled },
                        ) { downloaded, total ->
                            _status.value =
                                UpdateStatus.Downloading(
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    progress = if (total > 0) downloaded.toFloat() / total else 0f,
                                )
                        }

                    _status.value = UpdateStatus.Verifying
                    val result = UpdateVerifier.verify(context, verified, info.manifest)
                    if (!result.isVerified) {
                        Timber.w("Update rejected: %s (%s)", result.code, result.details)
                        verified.delete()
                        context.clearVerifiedApk()
                        _status.value = UpdateStatus.CheckFailed(result.code.toFailureReason())
                        return@launch
                    }

                    context.storeVerifiedApk(verified.absolutePath)
                    lastReady = info to verified.absolutePath
                    _status.value = UpdateStatus.ReadyToInstall(info, verified.absolutePath)
                } catch (e: UpdateDownloader.DownloadCancelled) {
                    // Keep the partial file so a retry resumes from where the
                    // download stopped.
                    _status.value = UpdateStatus.Available(info)
                } catch (e: CancellationException) {
                    if (downloadCancelled) {
                        // cancelDownload() cancels the job (the engine surfaces
                        // that as CancellationException from the blocked read)
                        // and flags [downloadCancelled]; a user cancel is not a
                        // scope teardown and must restore the offer instead of
                        // leaving a stale "downloading" screen.
                        _status.value = UpdateStatus.Available(info)
                    } else {
                        throw e
                    }
                } catch (e: Exception) {
                    val reason = e.toUpdateFailureReason()
                    Timber.w(e, "Update download failed: %s", reason)
                    _status.value = UpdateStatus.CheckFailed(reason)
                }
            }
    }

    fun cancelDownload() {
        downloadCancelled = true
        downloadJob?.cancel()
        downloadJob = null
    }

    /**
     * Hands the verified APK to the official installer. If the platform
     * requires the unknown-sources grant first, the state switches so the UI
     * can coach the user; nothing is ever installed silently.
     */
    fun requestInstall() {
        // The coaching state (unknown-sources permission) re-enters here after
        // the user grants it; resume from the remembered ReadyToInstall pair.
        val current =
            when (val st = _status.value) {
                is UpdateStatus.ReadyToInstall -> st
                is UpdateStatus.InstallPermissionRequired -> {
                    val (info, path) =
                        lastReady ?: run {
                            Timber.w("requestInstall: coaching state without lastReady")
                            return
                        }
                    UpdateStatus.ReadyToInstall(info, path)
                }
                else -> {
                    Timber.w("requestInstall: unexpected status=%s", st)
                    return
                }
            }
        val can = UpdateInstaller.canInstall(context)
        if (!can) {
            _status.value = UpdateStatus.InstallPermissionRequired
            return
        }
        val intent = UpdateInstaller.installIntent(context, File(current.apkPath))
        if (intent == null) {
            _status.value = UpdateStatus.CheckFailed(UpdateFailureReasons.VERIFY_APK_NOT_FOUND)
            return
        }
        _status.value = UpdateStatus.Installing
        _pendingInstall.value = intent
    }

    /** Opens the official "Allow from this source" page for MELORA. */
    fun requestUnknownSourcesScreen() {
        UpdateInstaller.openUnknownSourcesSettings(context)
    }

    /** Called with the result code of the system installer dialog. */
    fun onInstallLauncherResult(resultCode: Int) {
        if (_pendingInstall.value == null) return
        _pendingInstall.value = null

        if (resultCode == Activity.RESULT_OK) {
            // The installer replaces the process; when we are still alive,
            // reset so the next check collapses into "up to date".
            lastReady = null
            _status.value = UpdateStatus.Idle
        } else {
            // User cancelled (RESULT_CANCELED) or the installer aborted: the
            // verified APK stays usable, offer Install again.
            val ready = lastReady
            _status.value =
                if (ready != null && File(ready.second).exists()) {
                    UpdateStatus.ReadyToInstall(ready.first, ready.second)
                } else {
                    UpdateStatus.Idle
                }
        }
    }

    /** "Remind me later": the auto dialog stays hidden until the next tag. */
    fun remindLater() {
        val info =
            (_status.value as? UpdateStatus.Available)?.info ?: _availableInfo.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            context.storeDismissedTag(info.tag)
        }
    }

    /**
     * Re-asserts "Ready to install" from disk when a previous session had a
     * fully verified APK, so a process death never loses download work.
     */
    private suspend fun restoreVerifiedApkIfAny() {
        val path = context.readVerifiedApkPath()
        val file = path?.let(::File)
        val info = context.readCachedUpdateInfo()

        if (file == null || !file.exists() || info == null || !Updater.hasUpdate(info)) {
            if (file != null && file.exists() && info != null) {
                // Already running this (or a newer) build: drop the leftover.
                file.delete()
            }
            context.clearVerifiedApk()
            return
        }

        val result = UpdateVerifier.verify(context, file, info.manifest)
        if (result.isVerified) {
            _availableInfo.value = info
            lastReady = info to path
            _status.value = UpdateStatus.ReadyToInstall(info, path)
        } else {
            Timber.w("Stored update APK no longer verifies (%s): removing", result.code)
            file.delete()
            context.clearVerifiedApk()
        }
    }

    /** Maps a verification failure to a stable, UI-representable reason. */
    private fun UpdateVerifier.ResultCode.toFailureReason(): String =
        when (this) {
            UpdateVerifier.ResultCode.CHECKSUM_MISMATCH -> UpdateFailureReasons.VERIFY_CHECKSUM
            UpdateVerifier.ResultCode.PACKAGE_MISMATCH -> UpdateFailureReasons.VERIFY_PACKAGE
            UpdateVerifier.ResultCode.VERSION_MISMATCH -> UpdateFailureReasons.VERIFY_VERSION
            UpdateVerifier.ResultCode.SIGNATURE_MISMATCH,
            UpdateVerifier.ResultCode.APKS_MISSING_SIGNING_INFO,
            -> UpdateFailureReasons.VERIFY_SIGNATURE
            UpdateVerifier.ResultCode.INVALID_APK -> UpdateFailureReasons.VERIFY_APK_NOT_FOUND
            UpdateVerifier.ResultCode.OK -> UpdateFailureReasons.DOWNLOAD
        }
}
