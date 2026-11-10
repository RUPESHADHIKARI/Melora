/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.metrolist.music.LocalNavController
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.SuggestionItemHeight
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.ChartsViewModel
import com.metrolist.music.viewmodels.OnlineSearchSuggestionViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val coroutineScope = rememberCoroutineScope()

    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    // Powers the landing page shown while the field is empty: trending
    // searches (top songs) and popular artists reuse the real charts data.
    val chartsPage by chartsViewModel.chartsPage.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            chartsViewModel.loadCharts()
        }
    }

    // Recent-search banners: the media each stored query matched.
    val historyArtwork by viewModel.historyArtwork.collectAsStateWithLifecycle()
    LaunchedEffect(viewState.history) {
        if (query.isEmpty()) {
            viewModel.resolveHistoryArtwork(viewState.history)
        }
    }

    val lazyListState = rememberLazyListState()

    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        snapshotFlow { query }.debounce(300L).collectLatest {
            viewModel.query.value = it
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues(),
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        // Show parsed URL item at the top if present
        if (viewState.isUrlQuery && viewState.parsedUrlItem != null) {
            item(key = "parsed_url_header") {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.parsed_from_link),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            item(key = "parsed_url_item") {
                val item = viewState.parsedUrlItem!!
                YouTubeListItem(
                    item = item,
                    isActive =
                        when (item) {
                            is SongItem -> mediaMetadata?.id == item.id
                            is AlbumItem -> mediaMetadata?.album?.id == item.id
                            is EpisodeItem -> mediaMetadata?.id == item.id
                            else -> false
                        },
                    isPlaying = isPlaying,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    when (item) {
                                        is SongItem -> {
                                            YouTubeSongMenu(
                                                song = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is AlbumItem -> {
                                            YouTubeAlbumMenu(
                                                albumItem = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is ArtistItem -> {
                                            YouTubeArtistMenu(
                                                artist = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is PlaylistItem -> {
                                            YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = coroutineScope,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is PodcastItem -> {
                                            YouTubePlaylistMenu(
                                                playlist = item.asPlaylistItem(),
                                                coroutineScope = coroutineScope,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is EpisodeItem -> {
                                            YouTubeSongMenu(
                                                song = item.asSongItem(),
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .combinedClickable(
                                onClick = {
                                    when (item) {
                                        is SongItem -> {
                                            if (item.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    if (autoRadioQueue) {
                                                        YouTubeQueue.radio(item.toMediaMetadata())
                                                    } else {
                                                        ListQueue(
                                                            title = item.title,
                                                            items = listOf(item.toMediaItem())
                                                        )
                                                    }
                                                )
                                                onDismiss()
                                            }
                                        }

                                        is AlbumItem -> {
                                            navController.navigate("album/${item.id}")
                                            onDismiss()
                                        }

                                        is ArtistItem -> {
                                            navController.navigate("artist/${item.id}")
                                            onDismiss()
                                        }

                                        is PlaylistItem -> {
                                            navController.navigate("online_playlist/${item.id}")
                                            onDismiss()
                                        }

                                        is PodcastItem -> {
                                            navController.navigate("online_podcast/${item.id}")
                                            onDismiss()
                                        }

                                        is EpisodeItem -> {
                                            if (item.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue.radio(item.toMediaMetadata()),
                                                )
                                                onDismiss()
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        when (item) {
                                            is SongItem -> {
                                                YouTubeSongMenu(
                                                    song = item,
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }

                                            is AlbumItem -> {
                                                YouTubeAlbumMenu(
                                                    albumItem = item,
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }

                                            is ArtistItem -> {
                                                YouTubeArtistMenu(
                                                    artist = item,
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }

                                            is PlaylistItem -> {
                                                YouTubePlaylistMenu(
                                                    playlist = item,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }

                                            is PodcastItem -> {
                                                YouTubePlaylistMenu(
                                                    playlist = item.asPlaylistItem(),
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }

                                            is EpisodeItem -> {
                                                YouTubeSongMenu(
                                                    song = item.asSongItem(),
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                },
                            ).background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                            .animateItem(),
                )
            }

            item(key = "parsed_url_divider") {
                HorizontalDivider(
                    modifier =
                        Modifier
                            .padding(vertical = 8.dp)
                            .animateItem(),
                )
            }
        }

        if (query.isEmpty()) {
            item(key = "landing_categories_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.browse_categories),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.see_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .clickable { navController.navigate("mood_and_genres") }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            item(key = "landing_categories") {
                BrowseCategoryCards()
            }

            if (viewState.history.isNotEmpty()) {
                item(key = "landing_recent_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 2.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.history),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.recent_searches),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                        )
                        Icon(
                            painter = painterResource(R.drawable.delete_history),
                            contentDescription = stringResource(R.string.clear_all),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        database.query {
                                            clearSearchHistory()
                                        }
                                    }
                                    .padding(8.dp)
                                    .size(20.dp),
                        )
                    }
                }

                items(
                    viewState.history,
                    key = { "landing_history_${it.query}" },
                ) { history ->
                    RecentSearchRow(
                        query = history.query,
                        artwork = historyArtwork[history.query],
                        onClick = {
                            onSearch(history.query)
                            onDismiss()
                        },
                        onDelete = {
                            database.query {
                                delete(history)
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            val trendingItems =
                chartsPage?.sections
                    ?.flatMap { section -> section.items.filter { it is SongItem || it is PlaylistItem } }
                    ?.distinctBy { it.id }
                    ?.take(10)

            val popularArtists =
                chartsPage?.sections
                    ?.firstOrNull { section -> section.items.any { it is ArtistItem } }
                    ?.items
                    ?.filterIsInstance<ArtistItem>()
                    ?.distinctBy { it.id }
                    ?.take(12)

            if (!trendingItems.isNullOrEmpty()) {
                item(key = "landing_trending_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 2.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.trending_up),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.trending_searches),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                        )
                        Text(
                            text = stringResource(R.string.see_all),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .clickable { navController.navigate("charts_screen") }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
                item(key = "landing_trending_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                    ) {
                        items(trendingItems, key = { "trending_${it.id}" }) { item ->
                            TrendingSearchCard(
                                title = item.title,
                                subtitle =
                                    when (item) {
                                        is SongItem -> item.artists.joinToString { it.name }
                                        is PlaylistItem -> item.author?.name
                                        else -> null
                                    },
                                thumbnail = item.thumbnail,
                                onClick = {
                                    when (item) {
                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                        else -> onSearch(item.title)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (!popularArtists.isNullOrEmpty()) {
                item(key = "landing_artists_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.group),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.popular_artists),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                        )
                    }
                }
                item(key = "landing_artists_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    ) {
                        items(popularArtists, key = { "landing_artist_${it.id}" }) { artist ->
                            PopularArtistCard(
                                artist = artist,
                                onClick = { navController.navigate("artist/${artist.id}") },
                            )
                        }
                    }
                }
            }
        }

        if (query.isNotEmpty()) {
            items(viewState.history, key = { "history_${it.query}" }) { history ->
                SuggestionItem(
                    query = history.query,
                    online = false,
                    onClick = {
                        onSearch(history.query)
                        onDismiss()
                    },
                    onDelete = {
                        database.query {
                            delete(history)
                        }
                    },
                    onFillTextField = {
                        onQueryChange(TextFieldValue(history.query, TextRange(history.query.length)))
                    },
                    modifier = Modifier.animateItem(),
                    pureBlack = pureBlack,
                )
            }
        }

        items(viewState.suggestions, key = { "suggestion_$it" }) { query ->
            SuggestionItem(
                query = query,
                online = true,
                onClick = {
                    onSearch(query)
                    onDismiss()
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(query, TextRange(query.length)))
                },
                modifier = Modifier.animateItem(),
                pureBlack = pureBlack,
            )
        }

        if (viewState.items.isNotEmpty() && viewState.history.size + viewState.suggestions.size > 0) {
            item(key = "search_divider") {
                HorizontalDivider(
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "search_divider_spacer") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        items(viewState.items, key = { "item_${it.id}" }) { item ->
            YouTubeListItem(
                item = item,
                isActive =
                    when (item) {
                        is SongItem -> mediaMetadata?.id == item.id
                        is AlbumItem -> mediaMetadata?.album?.id == item.id
                        is EpisodeItem -> mediaMetadata?.id == item.id
                        else -> false
                    },
                isPlaying = isPlaying,
                trailingContent = {
                    IconButton(
                        onClick = {
                            menuState.show {
                                when (item) {
                                    is SongItem -> {
                                        YouTubeSongMenu(
                                            song = item,
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }

                                    is AlbumItem -> {
                                        YouTubeAlbumMenu(
                                            albumItem = item,
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }

                                    is ArtistItem -> {
                                        YouTubeArtistMenu(
                                            artist = item,
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }

                                    is PlaylistItem -> {
                                        YouTubePlaylistMenu(
                                            playlist = item,
                                            coroutineScope = coroutineScope,
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }

                                    is PodcastItem -> {
                                        YouTubePlaylistMenu(
                                            playlist = item.asPlaylistItem(),
                                            coroutineScope = coroutineScope,
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }

                                    is EpisodeItem -> {
                                        YouTubeSongMenu(
                                            song = item.asSongItem(),
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                },
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = {
                                when (item) {
                                    is SongItem -> {
                                        if (item.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                if (autoRadioQueue) {
                                                    YouTubeQueue.radio(item.toMediaMetadata())
                                                } else {
                                                    ListQueue(
                                                        title = item.title,
                                                        items = listOf(item.toMediaItem())
                                                    )
                                                }
                                            )
                                            onDismiss()
                                        }
                                    }

                                    is AlbumItem -> {
                                        navController.navigate("album/${item.id}")
                                        onDismiss()
                                    }

                                    is ArtistItem -> {
                                        navController.navigate("artist/${item.id}")
                                        onDismiss()
                                    }

                                    is PlaylistItem -> {
                                        navController.navigate("online_playlist/${item.id}")
                                        onDismiss()
                                    }

                                    is PodcastItem -> {
                                        navController.navigate("online_podcast/${item.id}")
                                        onDismiss()
                                    }

                                    is EpisodeItem -> {
                                        if (item.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue.radio(item.toMediaMetadata()),
                                            )
                                            onDismiss()
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    when (item) {
                                        is SongItem -> {
                                            YouTubeSongMenu(
                                                song = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is AlbumItem -> {
                                            YouTubeAlbumMenu(
                                                albumItem = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is ArtistItem -> {
                                            YouTubeArtistMenu(
                                                artist = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is PlaylistItem -> {
                                            YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = coroutineScope,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is PodcastItem -> {
                                            YouTubePlaylistMenu(
                                                playlist = item.asPlaylistItem(),
                                                coroutineScope = coroutineScope,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is EpisodeItem -> {
                                            YouTubeSongMenu(
                                                song = item.asSongItem(),
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        ).background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                        .animateItem(),
            )
        }
    }
}

@Composable
private fun BrowseCategoryCards() {
    val navController = LocalNavController.current
    val categories =
        listOf(
            BrowseCategory(
                label = stringResource(R.string.moods),
                subtitle = stringResource(R.string.feel_the_vibe),
                iconRes = R.drawable.music_note,
                gradient = Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF9B8CFF))),
                route = "mood_and_genres",
            ),
            BrowseCategory(
                label = stringResource(R.string.genres),
                subtitle = stringResource(R.string.explore_more),
                iconRes = R.drawable.album,
                gradient = Brush.linearGradient(listOf(Color(0xFFD34FA6), Color(0xFFF28BC4))),
                route = "mood_and_genres",
            ),
            BrowseCategory(
                label = stringResource(R.string.charts),
                subtitle = stringResource(R.string.top_hits),
                iconRes = R.drawable.trending_up,
                gradient = Brush.linearGradient(listOf(Color(0xFF1789A8), Color(0xFF35C9DB))),
                route = "charts_screen",
            ),
            BrowseCategory(
                label = stringResource(R.string.new_releases),
                subtitle = stringResource(R.string.fresh_music),
                iconRes = R.drawable.explore_outlined,
                gradient = Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFE86FB2))),
                route = "new_release",
            ),
        )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        categories.chunked(2).forEach { rowCategories ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowCategories.forEach { category ->
                    Box(
                        contentAlignment = Alignment.BottomStart,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(category.gradient)
                                .clickable { navController.navigate(category.route) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                            )
                            Text(
                                text = category.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                            )
                        }
                        Icon(
                            painter = painterResource(category.iconRes),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class BrowseCategory(
    val label: String,
    val subtitle: String,
    val iconRes: Int,
    val gradient: Brush,
    val route: String,
)

@Composable
private fun TrendingSearchCard(
    title: String,
    subtitle: String?,
    thumbnail: String?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.width(150.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = thumbnail,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(14.dp)),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PopularArtistCard(
    artist: ArtistItem,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp).clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = artist.thumbnail,
            contentDescription = artist.title,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(CircleShape),
        )
        Text(
            text = artist.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun RecentSearchRow(
    query: String,
    artwork: YTItem?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (artwork?.thumbnail != null) {
            AsyncImage(
                model = artwork.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        ) {
            Text(
                text = artwork?.title ?: query,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    when (val item = artwork) {
                        is SongItem ->
                            listOfNotNull(
                                stringResource(if (item.isVideoSong) R.string.type_video else R.string.type_song),
                                item.artists.firstOrNull()?.name,
                            ).joinToString(separator = "  ·  ")
                        is AlbumItem ->
                            listOfNotNull(
                                stringResource(R.string.type_album),
                                item.artists?.firstOrNull()?.name,
                            ).joinToString(separator = "  ·  ")
                        is ArtistItem -> stringResource(R.string.type_artist)
                        is PlaylistItem -> stringResource(R.string.type_playlist)
                        else -> query
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
    pureBlack: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(SuggestionItemHeight)
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        Icon(
            painterResource(if (online) R.drawable.search else R.drawable.history),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 16.dp).alpha(0.5f),
        )

        Text(
            text = query,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (!online) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.alpha(0.5f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                )
            }
        }

        IconButton(
            onClick = onFillTextField,
            modifier = Modifier.alpha(0.5f),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_top_left),
                contentDescription = null,
            )
        }
    }
}
