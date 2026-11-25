/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.util.LruCache
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

private const val MAX_LYRICS_FETCH_MS = 25000L
private const val PER_PROVIDER_TIMEOUT_MS = 8000L
private const val PROVIDER_NONE = ""
private const val NEGATIVE_CACHE_TTL_MS = 86_400_000L

/**
 * Lifecycle of a lyrics fetch for the currently playing track. Drives the status
 * indicators and the retry affordances in the lyrics UI.
 */
sealed class LyricsFetchState {
    data object Idle : LyricsFetchState()
    data object Loading : LyricsFetchState()
    data object Synced : LyricsFetchState()
    data object Plain : LyricsFetchState()
    data object Available : LyricsFetchState()
    data object OfflineAvailable : LyricsFetchState()
    data object OfflineWithoutSaved : LyricsFetchState()
    data object CachingRestricted : LyricsFetchState()
    data object NotFound : LyricsFetchState()
    data class Error(val message: String? = null) : LyricsFetchState()
}

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
    private val database: MusicDatabase,
) {
    val preferred =
        context.dataStore.data
            .map { preferences ->
                resolveLyricsProviders(preferences)
            }.distinctUntilChanged()

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private val negativeCache = mutableMapOf<String, Long>()
    private var currentLyricsJob: Job? = null
    private var autoRetryArmed = false
    private var lastFetchRequest: LyricsFetchRequest? = null

    private val _fetchState = MutableStateFlow<LyricsFetchState>(LyricsFetchState.Idle)
    val fetchState: StateFlow<LyricsFetchState> = _fetchState.asStateFlow()

    init {
        // Retry a temporary lyrics failure automatically once connectivity returns.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            networkConnectivity.networkStatus.collect { isConnected ->
                if (isConnected) {
                    val shouldRetry = autoRetryArmed && _fetchState.value is LyricsFetchState.Error
                    if (shouldRetry) {
                        autoRetryArmed = false
                        lastFetchRequest?.let { request ->
                            getLyrics(
                                mediaId = request.mediaId,
                                title = request.title,
                                artist = request.artist,
                                duration = request.duration,
                                album = request.album,
                            )
                        }
                    }
                }
            }
        }
    }

    private data class LyricsFetchRequest(
        val mediaId: String,
        val title: String,
        val artist: String,
        val duration: Int,
        val album: String?,
    )

    fun cancelFetch() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    private fun isCurrentlyOffline(): Boolean =
        try {
            !networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider =
        getLyrics(
            mediaId = mediaMetadata.id,
            title = mediaMetadata.title,
            artist = mediaMetadata.artists.joinToString { it.name },
            duration = mediaMetadata.duration,
            album = mediaMetadata.album?.title,
        )

    suspend fun getLyrics(
        mediaId: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        forceRefresh: Boolean = false,
    ): LyricsWithProvider {
        currentLyricsJob?.cancel()
        lastFetchRequest = LyricsFetchRequest(mediaId, title, artist, duration, album)

        cache.get(mediaId)?.firstOrNull()?.let { cached ->
            if (!forceRefresh) {
                _fetchState.value = LyricsFetchState.Available
                return LyricsWithProvider(cached.lyrics, cached.providerName)
            }
        }

        val negativeExpiresAt = negativeCache[mediaId] ?: 0L
        if (!forceRefresh && negativeExpiresAt > System.currentTimeMillis()) {
            _fetchState.value = LyricsFetchState.NotFound
            return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        // Offline-first: serve previously saved lyrics before touching the network.
        database.lyrics(mediaId).first()
            ?.takeIf { it.lyrics != LYRICS_NOT_FOUND }
            ?.let { saved ->
                _fetchState.value = if (isCurrentlyOffline()) LyricsFetchState.OfflineAvailable else LyricsFetchState.Available
                return LyricsWithProvider(saved.lyrics, saved.provider)
            }

        if (isCurrentlyOffline()) {
            _fetchState.value = LyricsFetchState.OfflineWithoutSaved
            return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        _fetchState.value = LyricsFetchState.Loading

        var hadProviderError = false

        val result =
            withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
                val cleanedTitle = LyricsUtils.cleanTitleForSearch(title)
                val orderedProviders = context.dataStore.data
                    .map { preferences -> resolveLyricsProviders(preferences) }
                    .first()
                val enabledProviders = orderedProviders.filter { it.isEnabled(context) }

                Timber.tag("LyricsHelper").d("Starting sequential fetch for: $cleanedTitle by $artist")
                Timber.tag("LyricsHelper").d("Enabled providers in order: ${enabledProviders.joinToString { it.name }}")

                var firstMeaningful: LyricsWithProvider? = null
                var firstSynced: LyricsWithProvider? = null

                for (provider in enabledProviders) {
                    Timber.tag("LyricsHelper").d("Trying provider: ${provider.name}")
                    val providerResult =
                        try {
                            withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                                provider.getLyrics(
                                    context,
                                    mediaId,
                                    cleanedTitle,
                                    artist,
                                    duration,
                                    album,
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.tag("LyricsHelper").w("${provider.name} threw: ${e.message}")
                            null
                        }

                    if (providerResult != null && providerResult.isSuccess) {
                        val fetchedLyrics = providerResult.getOrNull()?.trim().orEmpty()
                        if (LyricsUtils.isMeaningfulLyrics(fetchedLyrics)) {
                            Timber.tag("LyricsHelper").i("Got lyrics from ${provider.name}")
                            val candidate = LyricsWithProvider(LyricsUtils.filterLyricsCreditLines(fetchedLyrics), provider.name)
                            if (firstMeaningful == null) {
                                firstMeaningful = candidate
                            }
                            // Prefer synced lyrics, plain text is only a fallback.
                            if (fetchedLyrics.contains("[") && firstSynced == null) {
                                firstSynced = candidate
                            }
                        }
                        if (firstMeaningful == null) {
                            Timber.tag("LyricsHelper").w("${provider.name} returned empty or invalid lyrics, trying next provider")
                        }
                    } else {
                        hadProviderError = true
                        val errorMsg = providerResult?.exceptionOrNull()?.message ?: "timeout or exception"
                        Timber.tag("LyricsHelper").w("${provider.name} failed: $errorMsg")
                    }
                }

                Timber.tag("LyricsHelper").w("No lyrics found after checking all providers")
                firstSynced ?: firstMeaningful ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
            }

        val finalResult =
            if (result == null) {
                // The provider chain timed out: transient failure, surface the error state
                // instead of treating it as a confirmed "lyrics not found".
                _fetchState.value = LyricsFetchState.Error()
                null
            } else {
                result
            }

        if (finalResult == null) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        if (finalResult.lyrics == LYRICS_NOT_FOUND) {
            if (hadProviderError) {
                // A provider errored before valid lyrics were found: transient error, do not negative-cache.
                _fetchState.value = LyricsFetchState.Error()
            } else {
                negativeCache[mediaId] = System.currentTimeMillis() + NEGATIVE_CACHE_TTL_MS
                _fetchState.value = LyricsFetchState.NotFound
            }
        } else {
            val resolvedProvider = finalResult.provider.takeIf { it.isNotBlank() }?.let { name ->
                LyricsProviderRegistry.getProviderByName(name)
            }
            if (resolvedProvider != null && resolvedProvider.supportsOfflineCaching) {
                cache.put(mediaId, listOf(LyricsResult(finalResult.provider, finalResult.lyrics)))
                database.query {
                    upsert(LyricsEntity(mediaId, finalResult.lyrics, finalResult.provider))
                }
                _fetchState.value = if (finalResult.lyrics.contains("[")) LyricsFetchState.Synced else LyricsFetchState.Plain
            } else {
                // Provider does not permit offline saving; keep the lyrics session-only.
                _fetchState.value = LyricsFetchState.CachingRestricted
            }
        }
        autoRetryArmed = _fetchState.value is LyricsFetchState.Error
        return finalResult
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }

        if (isCurrentlyOffline()) return

        val allResult = mutableListOf<LyricsResult>()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(songTitle)
            val allProviders = context.dataStore.data
                .map { preferences -> resolveLyricsProviders(preferences) }
                .first()
            val enabledProviders = allProviders.filter { it.isEnabled(context) }

            val otherProviders = enabledProviders.filter { it.name != "LyricsPlus" }
            val lyricsPlusProvider = enabledProviders.find { it.name == "LyricsPlus" }

            val callbackMutex = Any()

            val otherJobs = otherProviders.map { provider ->
                launch {
                    try {
                        provider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(provider.name, filteredLyrics)
                            synchronized(callbackMutex) {
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            otherJobs.forEach { it.join() }

            val otherLyricsCount = allResult.count { it.providerName != "LyricsPlus" }
            if (lyricsPlusProvider != null && otherLyricsCount <= 2) {
                launch {
                    try {
                        lyricsPlusProvider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(lyricsPlusProvider.name, filteredLyrics)
                            synchronized(callbackMutex) {
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }.join()
            }

            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    private fun resolveLyricsProviders(preferences: androidx.datastore.preferences.core.Preferences): List<LyricsProvider> {
        val providerOrder = preferences[LyricsProviderOrderKey].orEmpty()
        if (providerOrder.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(providerOrder)
        }

        return LyricsProviderRegistry.getDefaultProviderOrder()
            .mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
) {
    /**
     * Whether the provider permits its lyrics to be stored locally for offline playback.
     * Restricted providers keep lyrics session-only and never persist them.
     */
    val canSaveOffline: Boolean
        get() = provider.isBlank() || LyricsProviderRegistry.getProviderByName(provider)?.supportsOfflineCaching ?: true
}
