/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.R
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.joinToArtistString

/**
 * Spotify-style shortcut cell: artwork flush to the left, title/subtitle and a
 * quick play action on the right. Click opens/plays the item, long-press shows
 * the context menu, the round button always starts playback (for songs it
 * toggles when the track is active — behavior is supplied by the caller).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialGridItem(
    item: YTItem,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onPlayClick: (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val cellColor =
        if (isDark) {
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    val cellShape = RoundedCornerShape(8.dp)
    val thumbnailShape =
        if (item is ArtistItem) {
            CircleShape
        } else {
            RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
        }

    val subtitle =
        remember(item.id, item::class) {
            when (item) {
                is SongItem -> item.artists.orEmpty().joinToArtistString(", ") { it.name }
                is AlbumItem -> item.artists.orEmpty().joinToArtistString(", ") { it.name }
                is ArtistItem -> null
                is PlaylistItem -> item.author?.name
                is PodcastItem -> item.author?.name
                is EpisodeItem -> item.author?.name
            }
        }

    Box(modifier = modifier.height(56.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(cellShape)
                    .background(cellColor)
                    .combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = { onLongClick?.invoke() },
                    ),
        ) {
            Box(modifier = Modifier.size(56.dp)) {
                ItemThumbnail(
                    thumbnailUrl = item.thumbnail?.resize(200, 200),
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = thumbnailShape,
                    modifier = Modifier.size(56.dp),
                )

                if (isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_push_pin),
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(14.dp),
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            onPlayClick?.let { play ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .combinedClickable(onClick = { play() }),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isActive && isPlaying) R.drawable.pause else R.drawable.play,
                            ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
