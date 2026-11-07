/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.AppBarHeight
import java.time.LocalTime

/**
 * Spotify-inspired home header: Melora wordmark + time-based greeting on the
 * left, search and library/history actions on the right. Same height as the
 * app TopAppBar (status bars + 64dp) so LocalPlayerAwareWindowInsets keep
 * working unchanged.
 */
@Composable
fun HomeTopBar(
    accountName: String?,
    accountAvatarUrl: String?,
    showUpdateBadge: Boolean,
    showListenTogether: Boolean,
    containerColor: Color,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onListenTogetherClick: () -> Unit,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(containerColor),
    ) {
        Spacer(
            modifier =
                Modifier.windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Top),
                ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppBarHeight)
                    .padding(horizontal = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Official Melora logo mark (like Spotify's home header).
                // Image (not Icon) so the emblem's own colors are drawn without tint.
                Image(
                    painter = painterResource(R.drawable.splash_emblem),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                // The time-based greeting moved into the scroll content below
                // the bar to match the mock layout.
                Text(
                    text = "MELORA",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Search access (Spotify keeps search one tap away from home)
                AppTopBarAction(
                    icon = R.drawable.search,
                    label = stringResource(R.string.search),
                    onClick = onSearchClick,
                )

                // Update notifications (bell) — opens the changelog
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNotificationClick),
                ) {
                    BadgedBox(
                        badge = {
                            if (showUpdateBadge) {
                                Badge()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.notification),
                            contentDescription = stringResource(R.string.changelog),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                if (showListenTogether) {
                    AppTopBarAction(
                        icon = R.drawable.group_outlined,
                        label = stringResource(R.string.together),
                        onClick = onListenTogetherClick,
                    )
                }

                // Profile / account access
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onAccountClick),
                ) {
                    if (accountAvatarUrl != null) {
                        AsyncImage(
                            model = accountAvatarUrl,
                            contentDescription = stringResource(R.string.account),
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.account),
                            contentDescription = stringResource(R.string.account),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTopBarAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
