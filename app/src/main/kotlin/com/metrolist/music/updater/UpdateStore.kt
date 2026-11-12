/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.updater

import android.content.Context
import com.metrolist.music.constants.DismissedStandaloneUpdateKey
import com.metrolist.music.constants.UpdateCachedManifestKey
import com.metrolist.music.constants.UpdateLastCheckedKey
import com.metrolist.music.constants.UpdateVerifiedApkKey
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed persistence of the updater state: last successful check,
 * the trusted manifest snapshot, "Remind me later" dismissals and the path of
 * an already-verified APK (so "Ready to install" survives a process death).
 */
internal suspend fun Context.readLastCheckedAt(): Long? = dataStore.data.first()[UpdateLastCheckedKey]

internal suspend fun Context.readCachedManifest(): UpdateManifest? =
    dataStore.data.first()[UpdateCachedManifestKey]?.let { saved ->
        runCatching { Updater.json.decodeFromString(UpdateManifest.serializer(), saved) }.getOrNull()
    }

/**
 * The persisted snapshot as a full [UpdateInfo]: the checkpointed manifest
 * keeps the release title/date/body inline, so "What's New" survives across
 * processes without any network call.
 */
internal suspend fun Context.readCachedUpdateInfo(): UpdateInfo? =
    readCachedManifest()?.let { manifest ->
        UpdateInfo(
            manifest = manifest,
            releaseTitle = manifest.releaseTitle ?: "v${manifest.versionName}",
            publishedAt = manifest.publishedAt,
            releaseBody = manifest.releaseBody,
            tag = manifest.tagName ?: "v${manifest.versionName}",
        )
    }

internal suspend fun Context.readVerifiedApkPath(): String? = dataStore.data.first()[UpdateVerifiedApkKey]

internal suspend fun Context.readDismissedStandaloneTag(): String? =
    dataStore.data.first()[DismissedStandaloneUpdateKey]

/** Persists a successful check: timestamp + trusted manifest snapshot. */
internal suspend fun Context.storeUpdateCheckpoint(info: UpdateInfo) {
    safeDataStoreEdit { prefs ->
        prefs[UpdateLastCheckedKey] = System.currentTimeMillis()
        prefs[UpdateCachedManifestKey] = Updater.json.encodeToString(UpdateManifest.serializer(), info.manifest)
    }
}

internal suspend fun Context.storeVerifiedApk(path: String) {
    safeDataStoreEdit { it[UpdateVerifiedApkKey] = path }
}

internal suspend fun Context.clearVerifiedApk() {
    safeDataStoreEdit { it.remove(UpdateVerifiedApkKey) }
}

internal suspend fun Context.storeDismissedTag(tag: String) {
    safeDataStoreEdit { it[DismissedStandaloneUpdateKey] = tag }
}
