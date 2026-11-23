/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.ui.screens.Screens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCollectionSection(
    navController: NavController,
    likedPlaylist: Playlist,
    downloadedPlaylist: Playlist,
    cachedPlaylist: Playlist,
    topPlaylist: Playlist,
    uploadedPlaylist: Playlist,
    topSize: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.your_collection),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = stringResource(R.string.see_all),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                Modifier
                    .clickable { navController.navigate(Screens.Library.route) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CollectionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.liked),
                    subtitle = stringResource(R.string.collection_liked_subtitle),
                    iconRes = R.drawable.favorite,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { navController.navigate("auto_playlist/liked") },
                )

                CollectionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.offline),
                    subtitle = stringResource(R.string.collection_downloads_subtitle),
                    iconRes = R.drawable.offline,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { navController.navigate("auto_playlist/downloaded") },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CollectionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.cached_playlist),
                    subtitle = stringResource(R.string.collection_cached_subtitle),
                    iconRes = R.drawable.cached,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { navController.navigate("cache_playlist/cached") },
                )

                CollectionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.my_top) + " $topSize",
                    subtitle = stringResource(R.string.collection_top_subtitle),
                    iconRes = R.drawable.crown,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { navController.navigate("top_playlist/$topSize") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        CollectionCard(
            isFullWidth = true,
            title = stringResource(R.string.collection_uploaded_title),
            subtitle = stringResource(R.string.collection_uploaded_subtitle),
            iconRes = R.drawable.upload,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = { navController.navigate("auto_playlist/uploaded") },
        )
    }
}

@Composable
private fun CollectionCard(
    title: String,
    subtitle: String,
    iconRes: Int,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFullWidth: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        if (isFullWidth) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp),
                )

                Spacer(Modifier.height(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        maxLines = 1,
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(40.dp),
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}
