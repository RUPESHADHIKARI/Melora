/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context

interface LyricsProvider {
    val name: String

    /**
     * Whether the provider permits its lyrics to be persisted locally for offline use.
     * Providers that do not allow offline saving must not be cached; the app should surface
     * a "caching restricted" state instead of silently storing their results.
     */
    val supportsOfflineCaching: Boolean get() = true

    fun isEnabled(context: Context): Boolean

    suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String>

    suspend fun getAllLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        getLyrics(context, id, title, artist, duration, album).onSuccess(callback)
    }
}
