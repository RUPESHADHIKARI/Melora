/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.updater

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Structured release manifest (latest.json attached to a GitHub release).
 * This is the trusted source for the APK URL, versionCode decision and the
 * SHA-256 checksum. versionCode drives every comparison so updates are never
 * decided by text version comparisons.
 */
@Serializable
data class UpdateManifest(
    val versionName: String = "",
    val versionCode: Long = -1L,
    val apkUrl: String = "",
    val sha256: String = "",
    val releaseNotes: List<String> = emptyList(),
    val releaseTitle: String? = null,
    val publishedAt: String? = null,
    val tagName: String? = null,
    val releaseBody: String? = null,
    val mandatory: Boolean = false,
)

/** GitHub Releases API response (only the fields the updater consumes). */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L,
)

/** A parsed permission, ready to drive the UI and the download pipeline. */
data class UpdateInfo(
    val manifest: UpdateManifest,
    val releaseTitle: String,
    val publishedAt: String?,
    val releaseBody: String?,
    val tag: String,
    /** Size of the APK asset the manifest points at (for the size labels). */
    val apkSizeBytes: Long = -1L,
)

/**
 * Lifecycle of an update, from discovery to installation. Drives every part
 * of the UI (dialog, Update Center, badges).
 */
sealed interface UpdateStatus {
    /** Nothing happened yet - silent logs only, e.g. startup checks failure. */
    data object Idle : UpdateStatus

    /** network/manifest lookup running. */
    data object Checking : UpdateStatus

    /** A valid, newer manifest exists. */
    data class Available(val info: UpdateInfo) : UpdateStatus

    /** No newer manifest (or the download was cancelled back to this). */
    data class UpToDate(val lastCheckedAt: Long?) : UpdateStatus

    /** check failed (offline, rate limit, bad manifest, ...). */
    data class CheckFailed(val reason: String) : UpdateStatus

    /** apk download running. */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long, val progress: Float) : UpdateStatus

    /** apk fully downloaded, SHA-256 + package + signature verification running. */
    data object Verifying : UpdateStatus

    /** apk verified against the manifest; a single tap installs it. */
    data class ReadyToInstall(val info: UpdateInfo, val apkPath: String) : UpdateStatus

    /** system install confirmation is on screen (or just closed). */
    data object Installing : UpdateStatus

    /** the user must grant "install unknown apps" first. */
    data object InstallPermissionRequired : UpdateStatus
}

/** Known failure modes used by the caller to show a friendly message. */
object UpdateFailureReasons {
    const val OFFLINE = "offline"
    const val RATE_LIMIT = "rate_limit"
    const val MANIFEST_UNAVAILABLE = "manifest_unavailable"
    const val MARSHAL_FAILURE = "marshal"
    const val NETWORK_TIMEOUT = "timeout"
    const val HTTP = "http"
    const val DOWNLOAD = "download"
    const val STORAGE = "storage"
    const val VERIFY_CHECKSUM = "verify_checksum"
    const val VERIFY_PACKAGE = "verify_package"
    const val VERIFY_VERSION = "verify_version"
    const val VERIFY_SIGNATURE = "verify_signature"
    const val VERIFY_APK_NOT_FOUND = "verify_apk_not_found"
}
