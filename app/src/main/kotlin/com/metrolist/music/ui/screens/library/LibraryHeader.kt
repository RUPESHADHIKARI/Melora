/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.ui.component.LibrarySearchHeader

/**
 * Redesigned library header: "Your Library" title + tagline, search and create
 * actions, with a subtle violet radial artwork background. While search is
 * active the title row swaps out for the in-place search field; the sort row
 * (passed in via [sortRow]) stays available underneath in both states.
 */
@Composable
fun LibraryHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    keyboardController: SoftwareKeyboardController?,
    onCreateClick: () -> Unit,
    onSearchClick: () -> Unit,
    sortRow: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LibraryHeaderLogo(
            modifier = Modifier.fillMaxWidth(),
        )

        if (!isSearchActive) {
            LibraryHeaderTitle(
                onSearchClick = onSearchClick,
                onCreateClick = onCreateClick,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0x26C77DFF), Color.Transparent),
                            center = Offset.Zero,
                            radius = 1f,
                        ),
                    ).padding(bottom = 4.dp),
            )
        }

        LibrarySearchHeader(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onBack = onBack,
            keyboardController = keyboardController,
            modifier = Modifier.fillMaxWidth(),
            inactiveContent = sortRow,
        )
    }
}

@Composable
private fun LibraryHeaderTitle(
    onSearchClick: () -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        modifier
            .fillMaxWidth()
            .background(
                Brush.radialGradient(
                    listOf(Color(0x26C77DFF), Color.Transparent),
                    center = Offset.Zero,
                    radius = 1f,
                ),
            ).padding(bottom = 4.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.your_library),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = stringResource(R.string.your_music_memories),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        IconButton(
            onClick = onCreateClick,
            modifier = Modifier.padding(end = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = stringResource(R.string.create_playlist),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(
            onClick = onSearchClick,
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = stringResource(R.string.search),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LibraryHeaderLogo(
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Official Melora logo mark (same as the homepage top bar).
        // Image (not Icon) so the emblem's own colors are drawn without tint.
        Image(
            painter = painterResource(R.drawable.splash_emblem),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "MELORA",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
