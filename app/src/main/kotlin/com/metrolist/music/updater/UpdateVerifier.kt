/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Pre-installation verification of a downloaded update APK. Every check must
 * pass before the system installer is ever invoked:
 *
 *  1. SHA-256 of the file equals the checksum from the trusted release
 *     manifest (never from the APK location alone).
 *  2. The APK's package id equals the running MELORA package id.
 *  3. The APK's versionCode equals the manifest versionCode and is strictly
 *     greater than the installed versionCode.
 *  4. The APK is signed with the same certificate as the installed MELORA.
 *
 * A download that points at a *different* package will not pass check 2 —
 * that keeps a debug build from being upgraded (downgraded) by a release
 * APK and vice versa.
 */
object UpdateVerifier {
    enum class ResultCode {
        OK,
        CHECKSUM_MISMATCH,
        PACKAGE_MISMATCH,
        VERSION_MISMATCH,
        SIGNATURE_MISMATCH,
        INVALID_APK,
        APKS_MISSING_SIGNING_INFO,
    }

    data class Result(
        val code: ResultCode,
        val details: String? = null,
        val versionCode: Long = -1,
        val versionName: String? = null,
    ) {
        val isVerified: Boolean get() = code == ResultCode.OK
    }

    fun verify(context: Context, apkFile: File, manifest: UpdateManifest): Result {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return Result(ResultCode.INVALID_APK, "APK file is missing")
        }

        // 1. Content integrity against the trusted manifest.
        val sha256 =
            runCatching { UpdateDownloader.sha256Of(apkFile) }.getOrElse {
                return Result(ResultCode.INVALID_APK, "Cannot hash the APK")
            }
        if (!sha256.equals(manifest.sha256, ignoreCase = true)) {
            return Result(ResultCode.CHECKSUM_MISMATCH, "SHA-256 $sha256 does not match the release manifest")
        }

        // 2/3. Package id and version, parsed from the APK archive itself.
        // GET_SIGNATURES is deprecated in favor of GET_SIGNING_CERTIFICATES
        // (API 28+) but is still needed as the pre-P fallback.
        @Suppress("DEPRECATION")
        val flags =
            PackageManager.GET_SIGNING_CERTIFICATES or
            PackageManager.GET_SIGNATURES
        val archiveInfo: PackageInfo? =
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        if (archiveInfo == null) {
            return Result(ResultCode.INVALID_APK, "APK could not be parsed")
        }

        if (archiveInfo.packageName != context.packageName) {
            return Result(
                ResultCode.PACKAGE_MISMATCH,
                "APK targets ${archiveInfo.packageName}, installed is ${context.packageName}",
            )
        }

        val archiveVersionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }
        if (archiveVersionCode != manifest.versionCode) {
            return Result(
                ResultCode.VERSION_MISMATCH,
                "APK versionCode $archiveVersionCode does not match manifest ${manifest.versionCode}",
            )
        }
        if (archiveVersionCode <= currentVersionCode(context)) {
            return Result(
                ResultCode.VERSION_MISMATCH,
                "APK versionCode $archiveVersionCode is not newer than the installed build",
            )
        }

        // 4. Signature continuity: the update must be signed by the same
        //    key as the running installation, otherwise Android could not
        //    install it in place and the file may be tampered with.
        val archiveSignatures = signaturesOf(archiveInfo)
        val installedSignatures = installedSignatureDigests(context)
        if (archiveSignatures.isEmpty() || installedSignatures.isEmpty()) {
            return Result(ResultCode.APKS_MISSING_SIGNING_INFO, "Signing information unavailable")
        }
        val archiveDigests = archiveSignatures.map { sha256(it) }
        if (archiveDigests.none { it in installedSignatures }) {
            return Result(
                ResultCode.SIGNATURE_MISMATCH,
                "APK signature ${archiveDigests.firstOrNull()} does not match the installed signature",
            )
        }

        return Result(
            ResultCode.OK,
            versionName = archiveInfo.versionName,
            versionCode = archiveVersionCode,
        )
    }

    private fun currentVersionCode(context: Context): Long {
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                0,
            )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun signaturesOf(info: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty().map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty().map { it.toByteArray() }
        }

    private fun installedSignatureDigests(context: Context): Set<String> {
        // GET_SIGNATURES keeps pre-Android-9 devices working; the modern
        // GET_SIGNING_CERTIFICATES path is preferred wherever available.
        @Suppress("DEPRECATION")
        val flags =
            PackageManager.GET_SIGNING_CERTIFICATES or
            PackageManager.GET_SIGNATURES
        val info: PackageInfo? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
        return signaturesOf(info ?: return emptySet()).map { sha256(it) }.toSet()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
