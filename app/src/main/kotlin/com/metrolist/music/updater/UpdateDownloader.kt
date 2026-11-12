/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.updater

import com.metrolist.music.BuildConfig
import android.content.Context
import android.os.StatFs
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import io.ktor.http.HttpHeaders
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Streams the release APK from its verified GitHub asset URL into the app's
 * private files dir (`files/updates/`).
 *
 *  - Resumes interrupted downloads with an HTTP Range request.
 *  - Refuses to fill the drive (checks free space first, keeps a margin).
 *  - Computes the SHA-256 while streaming; the `.part` file is only moved to
 *    the final path after it matches the checksum, so a corrupted download
 *    never reaches installation or gets resumed as corrupt data.
 */
object UpdateDownloader {
    private const val BUFFER_SIZE = 64 * 1024
    private const val PROGRESS_INTERVAL_MS = 200L

    /** Unbounded-request client dedicated to the APK stream. */
    private val client =
        HttpClient(OkHttp) {
            // Status codes are handled manually so failures map to a
            // stable DOWNLOAD reason instead of a raw HTTP exception.
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 60_000
                requestTimeoutMillis = 15L * 60 * 1000
            }
        }

    class DownloadCancelled : Exception()

    class DownloadException(
        val reason: String,
        message: String,
    ) : Exception(message)

    fun updateApkFile(context: Context, versionCode: Long): File =
        File(File(context.filesDir, "updates"), "Melora-v$versionCode.apk")

    fun partialFileFor(apkFile: File): File = File(apkFile.parentFile, apkFile.name + ".part")

    fun isTargetUrlAllowed(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https") return false
        val host = uri.host ?: return false
        // Mirrors the manifest guard in utils/Updater: only a stable GitHub
        // release-download asset of this repository is ever fetched.
        return host == "github.com" && uri.path?.contains("/releases/download/") == true
    }

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Download [url] into [apkFile], verifying against [expectedSha256].
     * Returns the completed APK file on success.
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        expectedSha256: String,
        apkFile: File,
        isCancelled: () -> Boolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            if (!isTargetUrlAllowed(url)) {
                throw DownloadException(UpdateFailureReasons.DOWNLOAD, "APK URL is not a trusted GitHub release asset")
            }

            apkFile.parentFile?.mkdirs() ?: throw DownloadException(UpdateFailureReasons.DOWNLOAD, "Cannot create update folder")

            // A completed file that already matches the manifest checksum is
            // reused instead of downloading the same APK again.
            if (apkFile.exists() && expectedSha256.isNotBlank()) {
                if (sha256Of(apkFile).equals(expectedSha256, ignoreCase = true)) {
                    onProgress(apkFile.length(), apkFile.length())
                    return@withContext apkFile
                }
                apkFile.delete()
            }

            val partFile = partialFileFor(apkFile)
            streamToPartFile(url, partFile, expectedSha256, isCancelled, onProgress)

            promoteVerifiedPartFile(partFile, apkFile, expectedSha256)
        }

    private suspend fun streamToPartFile(
        url: String,
        partFile: File,
        expectedSha256: String,
        isCancelled: () -> Boolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        coroutineContext.ensureActive()

        val partDir =
            partFile.parentFile
                ?: throw DownloadException(UpdateFailureReasons.DOWNLOAD, "Update folder is missing")

        val hadPartial = partFile.exists() && partFile.length() > 0

        client
            .prepareGet(url) {
                headers {
                    append(HttpHeaders.UserAgent, "Melora-Updater/${BuildConfig.VERSION_NAME}")
                    if (hadPartial) {
                        append(HttpHeaders.Range, "bytes=${partFile.length()}-")
                    }
                }
            }.execute { response ->
                val status = response.status.value
                if (status !in 200..299) {
                    throw DownloadException(UpdateFailureReasons.DOWNLOAD, "Download failed: HTTP $status")
                }

                val resumes = status == 206 && hadPartial

                val alreadyOnDisk = if (resumes) partFile.length() else 0L
                val bodyLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                if (bodyLength <= 0L) {
                    // Without a length there is no progress and no storage
                    // safety, and a resume is impossible: restart clean.
                    partFile.delete()
                    throw DownloadException(UpdateFailureReasons.DOWNLOAD, "File size is unknown")
                }
                val total = alreadyOnDisk + bodyLength

                // Refuse to fill the drive: keep a 32 MB safety margin.
                val available =
                    runCatching { StatFs(partDir.absolutePath).availableBytes }
                        .getOrDefault(Long.MAX_VALUE)
                val margin = 32L * 1024 * 1024
                if (available < total + margin) {
                    throw DownloadException(UpdateFailureReasons.STORAGE, "Not enough storage for the update")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var written = alreadyOnDisk

                if (resumes) {
                    // Digest the resumed prefix so the final digest covers
                    // the entire file, then keep appending to it. If the
                    // server ignored Range it responded 200 and we restart
                    // from zero (guaranteed by [resumes]).
                    partFile.inputStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                        }
                    }
                } else if (partFile.exists()) {
                    // Fresh (non-resume) download: drop any stale partial.
                    partFile.delete()
                }

                var lastProgress = 0L
                onProgress(written, total)

                FileOutputStream(partFile, /* append = */ resumes).use { output ->
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (!channel.isClosedForRead) {
                        coroutineContext.ensureActive()
                        if (isCancelled()) throw DownloadCancelled()

                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastProgress > PROGRESS_INTERVAL_MS) {
                            lastProgress = now
                            onProgress(written, total)
                        }
                    }
                    output.flush()
                }

                onProgress(written, total)

                // The stream is complete: this part file only advances if
                // its checksum matches the trusted manifest checksum; a
                // partial/corrupted download is dropped and retried fresh.
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!sha256.equals(expectedSha256, ignoreCase = true)) {
                    partFile.delete()
                    throw DownloadException(UpdateFailureReasons.VERIFY_CHECKSUM, "Downloaded APK failed the SHA-256 check")
                }
            }
    }

    /** Atomic last step: checksum-verified `.part` becomes the real APK. */
    private fun promoteVerifiedPartFile(partFile: File, finalFile: File, expectedSha256: String): File {
        if (!partFile.renameTo(finalFile)) {
            finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                throw DownloadException(UpdateFailureReasons.DOWNLOAD, "Could not persist the downloaded APK")
            }
        }
        if (expectedSha256.isNotBlank() && !sha256Of(finalFile).equals(expectedSha256, ignoreCase = true)) {
            finalFile.delete()
            throw DownloadException(UpdateFailureReasons.VERIFY_CHECKSUM, "Saved APK failed the SHA-256 check")
        }
        return finalFile
    }
}
