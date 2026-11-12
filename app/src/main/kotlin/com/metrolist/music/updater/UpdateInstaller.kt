/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a fully verified APK to Android's package installer flow:
 *
 *  - If "install unknown apps" is still required, the user is sent to the
 *    official system settings page for MELORA (no silent installs, no
 *    security bypasses — see [needsInstallPermission]).
 *  - Otherwise an [Intent.ACTION_INSTALL_PACKAGE] with the verified APK is
 *    built; the system confirmation screen lets the user approve or cancel.
 *
 * In-place installation only: the APK keeps package id, signature and
 * upgradeable versionCode, so Android updates the app and user data
 * (playlists, preferences, theme, database) is never cleared.
 */
object UpdateInstaller {
    /** True when MELORA may install app packages directly. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            // Without the manifest permission the platform call itself throws
            // SecurityException; degrade to "permission required" instead of
            // crashing the app.
            runCatching { context.packageManager.canRequestPackageInstalls() }
                .getOrDefault(false)

    /**
     * Opens the official "Allow from this source" page for MELORA.
     * Returns false when the activity is not resolvable.
     */
    fun openUnknownSourcesSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * The install intent for a verified APK. The APK lives in the private
     * `files/updates/` dir; [FileProvider] gives the installer read access.
     */
    fun installIntent(context: Context, apkFile: File): Intent? {
        if (!apkFile.exists()) return null
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                apkFile,
            )
        // ACTION_INSTALL_PACKAGE is deprecated in favor of the raw
        // PackageInstaller API, but the intent route gives us the official
        // confirmation UI + unknown-source handling for free and remains the
        // documented approach for sideloading updates from app-internal code.
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return intent
    }

    /**
     * Removes leftover APKs of *older* update attempts on startup.
     *
     * A fully downloaded APK for a NEWER versionCode is the pending install:
     * it must survive process death so a restart can re-verify and re-offer
     * it. Only artifacts at or below the installed versionCode are stale —
     * they can never be installed again, because updates are strictly newer.
     */
    fun cleanupOldApks(context: Context, installedVersionCode: Long) {
        val dir = File(context.filesDir, "updates")
        dir.listFiles()
            ?.filter { file ->
                val versionCode = apkVersionCode(file.name)
                file.name.endsWith(".apk") && (versionCode == null || versionCode <= installedVersionCode)
            }
            ?.forEach { it.delete() }
        // stale partials never make sense to keep across launches; HTTP
        // resume only helps within the same session.
        dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    /** Extracts the versionCode from `Melora-v<versionCode>.apk` (naming owned by [UpdateDownloader]). */
    private fun apkVersionCode(fileName: String): Long? =
        fileName
            .removeSuffix(".apk")
            .takeIf { it.startsWith("Melora-v") }
            ?.removePrefix("Melora-v")
            ?.toLongOrNull()
}
