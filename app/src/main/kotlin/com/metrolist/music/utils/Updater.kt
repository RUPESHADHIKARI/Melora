/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.music.BuildConfig
import com.metrolist.music.updater.GitHubRelease
import com.metrolist.music.updater.GitHubAsset
import com.metrolist.music.updater.UpdateFailureReasons
import com.metrolist.music.updater.UpdateInfo
import com.metrolist.music.updater.UpdateManifest
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import com.metrolist.music.updater.UpdateDownloader
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * MELORA updater — GitHub Releases as the update source.
 *
 * Security model:
 *  - All metadata comes from api.github.com over HTTPS.
 *  - The *trusted* source for the APK URL, versionCode and SHA-256 checksum is
 *    the `latest.json` manifest attached to the latest release. Without a
 *    valid manifest no update is offered and no APK is ever installed.
 *  - Version decisions use versionCode comparison only; a build newer than
 *    the manifest is never offered a downgrade.
 *
 * `checkForUpdate` is cached in memory per process; the ViewModel additionally
 * persists the manifest in DataStore so app restarts within 24 h do not touch
 * GitHub at all.
 *
 * Note: [compareVersions] is retained only for the changelog sheet (ordering
 * past releases). Update decisions never rely on it.
 */
data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>,
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
)

object Updater {
    private val checkClient =
        HttpClient {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            install(UserAgent) { agent = "Melora-Updater/${BuildConfig.VERSION_NAME}" }
        }

    val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
        }

    val githubApiBase = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}"

    const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
    const val MANIFEST_ASSET_NAME = "latest.json"

    /** last successful network check within this process. */
    internal var cachedInfo: UpdateInfo? = null

    /**
     * Update decision, **versionCode based**: resolves the trusted
     * [UpdateManifest] and reports `hasUpdate = versionCode > installed`.
     * An installed build that already matches the manifest yields
     * `hasUpdate = false` (never a downgrade or a re-download).
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<UpdateInfo, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                cachedInfo?.takeIf { !forceRefresh }?.let { cached ->
                    return@runCatching cached to hasUpdate(cached)
                }
                val info = fetchUpdateInfo()
                cachedInfo = info
                info to hasUpdate(info)
            }
        }

    fun hasUpdate(info: UpdateInfo): Boolean = info.manifest.versionCode > BuildConfig.VERSION_CODE

    /** Latest MELORA release (raw listing used by the changelog sheet). */
    suspend fun getLatestRelease(): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching { newReleaseInfo(fetchLatestRelease()) }
        }

    /** All releases (oldest pages appended last) for the changelog sheet. */
    suspend fun getAllReleases(): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val releases = mutableListOf<ReleaseInfo>()
                var page = 1
                while (page <= 5) {
                    val body = checkClient.get("$githubApiBase/releases?per_page=30&page=$page").bodyAsText()
                    val parsed = json.decodeFromString<List<GitHubRelease>>(body)
                    parsed.forEach { releases.add(newReleaseInfo(it)) }
                    if (parsed.size < 30) break
                    page++
                }
                releases
            }
        }

    /**
     * Changelog-only helper retained for ordering *past* releases in the
     * changelog sheet. Never used for update decisions.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = v1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = v2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    private fun newReleaseInfo(release: GitHubRelease): ReleaseInfo =
        ReleaseInfo(
            tagName = release.tagName,
            versionName = release.name ?: release.tagName.removePrefix("v"),
            description = release.body.orEmpty(),
            releaseDate = release.publishedAt ?: "",
            assets =
                release.assets
                    .filter { it.name.endsWith(".apk") }
                    .map { ReleaseAsset(it.name, it.browserDownloadUrl, it.size) },
        )

    private suspend fun fetchLatestRelease(): GitHubRelease {
        val response = checkClient.get("$githubApiBase/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }
        return json.decodeFromString<GitHubRelease>(response.bodyAsText())
    }

    /** Fresh network check → trusted [UpdateInfo]. Throws [UpdateException] with a stable reason. */
    internal suspend fun fetchUpdateInfo(): UpdateInfo {
        val release = fetchLatestRelease()

        val manifestAsset =
            release.assets.firstOrNull { it.name.equals(MANIFEST_ASSET_NAME, ignoreCase = true) }
                ?: throw UpdateException(
                    UpdateFailureReasons.MANIFEST_UNAVAILABLE,
                    "No $MANIFEST_ASSET_NAME manifest attached to the latest release",
                )

        val manifestText = checkClient.get(manifestAsset.browserDownloadUrl).bodyAsText()
        val manifest =
            try {
                json.decodeFromString<UpdateManifest>(manifestText)
            } catch (e: SerializationException) {
                throw UpdateException(UpdateFailureReasons.MANIFEST_UNAVAILABLE, "Malformed release manifest", e)
            }

        validateManifest(manifest, release)

        val apkAsset = release.assets.firstOrNull { it.browserDownloadUrl == manifest.apkUrl }

        return UpdateInfo(
            manifest = manifest,
            releaseTitle = release.name ?: release.tagName,
            publishedAt = release.publishedAt,
            releaseBody = release.body,
            tag = release.tagName,
            apkSizeBytes = apkAsset?.size ?: -1L,
        )
    }

    /**
     * Guardrails on the manifest: APK must be a GitHub release asset of this
     * repository (never a foreign host), checksum must be a full 64-hex
     * SHA-256. A manifest whose versionCode is not newer than the installed
     * build simply yields `hasUpdate = false`; it is never an error.
     */
    private fun validateManifest(manifest: UpdateManifest, release: GitHubRelease) {
        if (manifest.versionCode <= 0L) {
            throw UpdateException(UpdateFailureReasons.MANIFEST_UNAVAILABLE, "Manifest has no valid versionCode")
        }

        val apkUri =
            runCatching { java.net.URI(manifest.apkUrl) }
                .getOrNull()
                ?: throw UpdateException(UpdateFailureReasons.MANIFEST_UNAVAILABLE, "Manifest has no valid apkUrl")

        val hostOk = apkUri.host == "github.com"
        val pathOk = apkUri.path.orEmpty().contains("/releases/download/")
        if (!hostOk || !pathOk) {
            throw UpdateException(UpdateFailureReasons.MANIFEST_UNAVAILABLE, "APK URL is outside the trusted GitHub release host")
        }

        if (!manifest.sha256.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            throw UpdateException(UpdateFailureReasons.MANIFEST_UNAVAILABLE, "Manifest has an invalid SHA-256 checksum")
        }

        if (release.assets.none { it.browserDownloadUrl == manifest.apkUrl }) {
            throw UpdateException(UpdateFailureReasons.MANIFEST_UNAVAILABLE, "Manifest apkUrl does not match any release asset")
        }
    }
}

/** Wraps a check failure with a stable, UI-mappable reason code. */
class UpdateException(
    val reason: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Maps exceptions from this module to stable failure codes for the UI. */
internal fun Throwable.toUpdateFailureReason(): String {
    var e: Throwable = this
    while (e.cause != null && e.cause != e) e = e.cause!!
    return when (e) {
        is UpdateException -> e.reason
        is UpdateDownloader.DownloadException -> e.reason
        is UpdateDownloader.DownloadCancelled -> UpdateFailureReasons.DOWNLOAD
        is ClientRequestException -> {
            when (e.response.status.value) {
                403 -> UpdateFailureReasons.RATE_LIMIT
                404 -> UpdateFailureReasons.MANIFEST_UNAVAILABLE
                else -> UpdateFailureReasons.HTTP
            }
        }
        is HttpRequestTimeoutException -> UpdateFailureReasons.NETWORK_TIMEOUT
        is ConnectTimeoutException -> UpdateFailureReasons.NETWORK_TIMEOUT
        is SocketTimeoutException -> UpdateFailureReasons.NETWORK_TIMEOUT
        is IOException -> UpdateFailureReasons.OFFLINE
        else -> UpdateFailureReasons.MARSHAL_FAILURE
    }
}
