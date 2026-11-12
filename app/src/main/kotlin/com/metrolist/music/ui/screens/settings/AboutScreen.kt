/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.theme.DefaultSeedColor
import com.metrolist.music.ui.theme.DefaultThemeColor
import com.metrolist.music.ui.theme.bbhBartle
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.updater.AppUpdateViewModel
import com.metrolist.music.updater.UpdateStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class Contributor(
    val name: String,
    val roleRes: Int,
    val githubHandle: String,
    val avatarUrl: String = "https://github.com/$githubHandle.png",
    val githubUrl: String = "https://github.com/$githubHandle",
    val sponsorUrl: String? = null,
    val polygon: RoundedPolygon? = null,
    val favoriteSongVideoId: String? = null
)

private data class CommunityLink(
    val labelRes: Int,
    val iconRes: Int,
    val url: String,
    val descRes: Int? = null
)

private const val MELORA_REPO_URL = "https://github.com/RUPESHADHIKARI/Melora"
private const val MELORA_ISSUES_URL = "$MELORA_REPO_URL/issues"
private const val MELORA_LICENSE_URL = "$MELORA_REPO_URL/blob/main/LICENSE"
private const val UPSTREAM_REPO_URL = "https://github.com/MetrolistGroup/Metrolist"
private const val DEV_GITHUB_URL = "https://github.com/RUPESHADHIKARI"
private const val DEV_WEBSITE_URL = "https://rupeshdevlopes.vercel.app/"
private const val DEV_INSTAGRAM_URL = "https://www.instagram.com/rupesh_adhikari101"
private const val DEV_AVATAR_URL = "https://github.com/RUPESHADHIKARI.png"

// MELORA brand accents: the app's pink/purple seeds plus a cyan edge.
private val BrandCyan = Color(0xFF22D3EE)
private val BrandGradient =
    Brush.linearGradient(listOf(DefaultThemeColor, DefaultSeedColor, BrandCyan))

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val upstreamLeadDeveloper = Contributor(
    name = "Mo Agamy",
    roleRes = R.string.credits_lead_developer,
    githubHandle = "mostafaalagamy",
    polygon = MaterialShapes.Cookie9Sided,
    favoriteSongVideoId = "Mh2JWGWvy_Y"
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val upstreamContributors = listOf(
    Contributor(name = "Adriel O'Connel", roleRes = R.string.credits_collaborator, githubHandle = "adrielGGmotion", sponsorUrl = "https://github.com/sponsors/adrielGGmotion", polygon = MaterialShapes.Cookie4Sided, favoriteSongVideoId = "m2zUrruKjDQ"),
    Contributor(name = "Nyx", roleRes = R.string.credits_collaborator, githubHandle = "nyxiereal", sponsorUrl = "https://github.com/sponsors/nyxiereal", polygon = MaterialShapes.Cookie12Sided, favoriteSongVideoId = "zselaN6zPXw"),
)

private val communityLinks = listOf(
    CommunityLink(R.string.credits_discord, R.drawable.discord, "https://discord.com/invite/zrdbeRG2Mt", R.string.community_discord_desc),
    CommunityLink(R.string.credits_telegram, R.drawable.telegram, "https://t.me/metrolistapp", R.string.community_telegram_desc),
    CommunityLink(R.string.credits_view_repo, R.drawable.github, MELORA_REPO_URL, R.string.community_repo_desc),
    CommunityLink(R.string.report_issue, R.drawable.bug_report, MELORA_ISSUES_URL, R.string.report_issue_desc),
)

private fun handleEasterEggClick(
    clickCount: Int,
    favoriteSongVideoId: String?,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    playerConnection: PlayerConnection?,
    wannaPlayStr: String,
    yeahStr: String,
    onCountUpdate: (Int) -> Unit
) {
    if (favoriteSongVideoId != null) {
        val newCount = clickCount + 1
        onCountUpdate(newCount)
        if (newCount >= 3) {
            onCountUpdate(0)
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = wannaPlayStr,
                    actionLabel = yeahStr,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = favoriteSongVideoId)))
                }
            }
        }
    }
}

/** One-shot entrance: fades and slides a section in, staggered by index. */
@Composable
private fun Modifier.entrance(index: Int): Modifier {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        appear.animateTo(1f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = appear.value
        translationY = (1f - appear.value) * 36f
    }
}

@Composable
private fun ContributorAvatar(
    avatarUrl: String,
    sizeDp: Int,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null
) {
    val fallback = painterResource(R.drawable.small_icon)
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.size(sizeDp.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder = fallback,
            fallback = fallback,
            error = fallback,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    navController: NavController,
    appUpdateViewModel: AppUpdateViewModel,
) {
    val uriHandler = LocalUriHandler.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val wannaPlayStr = stringResource(R.string.wanna_play_favorite_song)
    val yeahStr = stringResource(R.string.yeah)

    val windowInsets = LocalPlayerAwareWindowInsets.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                windowInsets.only(WindowInsetsSides.Top)
            )
        )

        Spacer(Modifier.height(16.dp))

        // ── MELORA brand card ────────────────────────────────────────────
        ElevatedCard(
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .entrance(0),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.melora_logo),
                    contentDescription = stringResource(R.string.melora),
                    modifier = Modifier.size(88.dp),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.melora),
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = bbhBartle),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.melora_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(4.dp)
                        .background(BrandGradient, RoundedCornerShape(2.dp)),
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = BuildConfig.ARCHITECTURE.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (BuildConfig.DEBUG) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "DEBUG",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Update Center ────────────────────────────────────────────────
        if (BuildConfig.UPDATER_AVAILABLE) {
            Spacer(Modifier.height(24.dp))

            Box(Modifier.fillMaxWidth().entrance(1)) {
                UpdateCenterCard(
                    appUpdateViewModel = appUpdateViewModel,
                    onOpenCenter = { navController.navigate("settings/updater") },
                )
            }
        }

        // ── Developer ───────────────────────────────────────────────────
        Spacer(Modifier.height(24.dp))

        ElevatedCard(
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .entrance(2),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.designed_developed_by),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContributorAvatar(
                        avatarUrl = DEV_AVATAR_URL,
                        sizeDp = 96,
                        contentDescription = "Rupesh",
                    )

                    Spacer(Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Rupesh",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.5).sp,
                        )
                        Text(
                            text = stringResource(R.string.credits_creator),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = { uriHandler.openUri(DEV_WEBSITE_URL) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.language),
                            contentDescription = stringResource(R.string.cd_website)
                        )
                    }
                    FilledTonalButton(
                        onClick = { uriHandler.openUri(DEV_GITHUB_URL) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.github),
                            contentDescription = stringResource(R.string.cd_github_profile)
                        )
                    }
                    FilledTonalButton(
                        onClick = { uriHandler.openUri(DEV_INSTAGRAM_URL) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.instagram),
                            contentDescription = stringResource(R.string.cd_instagram_profile)
                        )
                    }
                }
            }
        }

        // ── Community & Support ─────────────────────────────────────────
        Spacer(Modifier.height(24.dp))

        Box(Modifier.fillMaxWidth().entrance(3)) {
            Material3SettingsGroup(
                title = stringResource(R.string.community_and_support),
                items = communityLinks.map { link ->
                    val descRes = link.descRes
                    Material3SettingsItem(
                        icon = painterResource(link.iconRes),
                        title = { Text(stringResource(link.labelRes), fontWeight = FontWeight.SemiBold) },
                        description = if (descRes != null) {
                            { Text(stringResource(descRes)) }
                        } else null,
                        onClick = { uriHandler.openUri(link.url) }
                    )
                }
            )
        }

        // ── Open Source & Licenses ──────────────────────────────────────
        Spacer(Modifier.height(24.dp))

        Box(Modifier.fillMaxWidth().entrance(4)) {
            Column {
                Material3SettingsGroup(
                    title = stringResource(R.string.open_source_licenses),
                    items = listOf(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.info),
                            title = { Text(stringResource(R.string.credits_license_name), fontWeight = FontWeight.SemiBold) },
                            description = { Text(stringResource(R.string.credits_license_desc)) },
                            onClick = { uriHandler.openUri(MELORA_LICENSE_URL) }
                        ),
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.github),
                            title = { Text(stringResource(R.string.based_on_metrolist), fontWeight = FontWeight.SemiBold) },
                            description = { Text(stringResource(R.string.based_on_metrolist_desc)) },
                            onClick = { uriHandler.openUri(UPSTREAM_REPO_URL) }
                        ),
                    ),
                )

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.gpl_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.gpl_copyright),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.third_party_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Metrolist contributors (upstream credits) ───────────────────
        Spacer(Modifier.height(24.dp))

        Box(Modifier.fillMaxWidth().entrance(5)) {
            Material3SettingsGroup(
                title = stringResource(R.string.metrolist_contributors),
                items = (listOf(upstreamLeadDeveloper) + upstreamContributors).map { contributor ->
                    var clickCount by remember(contributor.name) { mutableIntStateOf(0) }
                    Material3SettingsItem(
                        leadingContent = {
                            ContributorAvatar(
                                avatarUrl = contributor.avatarUrl,
                                sizeDp = 48,
                                shape = contributor.polygon?.toShape() ?: CircleShape,
                                contentDescription = contributor.name,
                                onClick = {
                                    handleEasterEggClick(
                                        clickCount = clickCount,
                                        favoriteSongVideoId = contributor.favoriteSongVideoId,
                                        coroutineScope = coroutineScope,
                                        snackbarHostState = snackbarHostState,
                                        playerConnection = playerConnection,
                                        wannaPlayStr = wannaPlayStr,
                                        yeahStr = yeahStr,
                                        onCountUpdate = { clickCount = it }
                                    )
                                }
                            )
                        },
                        title = { Text(text = contributor.name, fontWeight = FontWeight.SemiBold) },
                        description = { Text(stringResource(contributor.roleRes)) },
                        trailingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (contributor.sponsorUrl != null) {
                                    Surface(
                                        onClick = { uriHandler.openUri(contributor.sponsorUrl) },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(R.drawable.buymeacoffee),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    painter = painterResource(R.drawable.github),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { uriHandler.openUri(contributor.githubUrl) }
                    )
                }
            )
        }

        // ── Footer ──────────────────────────────────────────────────────
        Spacer(Modifier.height(32.dp))

        Box(Modifier.fillMaxWidth().entrance(6)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.footer_thanks),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.stands_with_nepal),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.about)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        }
    )

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        )
    }
}

@Composable
private fun UpdateCenterCard(
    appUpdateViewModel: AppUpdateViewModel,
    onOpenCenter: () -> Unit,
) {
    val status by appUpdateViewModel.status.collectAsStateWithLifecycle()
    val lastChecked by appUpdateViewModel.lastCheckedAt.collectAsStateWithLifecycle()

    // A check only makes sense while no download/install cycle is running.
    val canCheck = when (status) {
        is UpdateStatus.Idle, is UpdateStatus.UpToDate, is UpdateStatus.Available, is UpdateStatus.CheckFailed -> true
        else -> false
    }

    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.update),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_center),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            UpdateStatusContent(status)

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { appUpdateViewModel.manualCheck() },
                    enabled = canCheck,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.uc_check_now))
                }
                Button(
                    onClick = onOpenCenter,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.open_update_center))
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = lastChecked?.let {
                    stringResource(R.string.uc_last_checked, formatDate(it))
                } ?: stringResource(R.string.uc_never_checked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateStatusContent(status: UpdateStatus) {
    val busy =
        status is UpdateStatus.Checking ||
            status is UpdateStatus.Verifying ||
            status is UpdateStatus.Installing

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = status.statusText(),
            style = MaterialTheme.typography.titleSmall,
            color = status.statusColor(),
        )
    }

    when (val s = status) {
        is UpdateStatus.Downloading -> {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { s.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.uc_downloaded_of,
                    formatBytes(s.downloadedBytes),
                    formatBytes(s.totalBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is UpdateStatus.Available -> {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.uc_latest_version) + " " + s.info.manifest.versionName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is UpdateStatus.ReadyToInstall -> {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.uc_latest_version) + " " + s.info.manifest.versionName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.uc_data_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> {}
    }
}

@Composable
private fun UpdateStatus.statusText(): String =
    when (this) {
        UpdateStatus.Idle -> stringResource(R.string.uc_status_idle)
        is UpdateStatus.Checking -> stringResource(R.string.uc_status_checking)
        is UpdateStatus.UpToDate -> stringResource(R.string.uc_status_up_to_date)
        is UpdateStatus.Available -> stringResource(R.string.uc_status_available)
        is UpdateStatus.Downloading -> stringResource(R.string.uc_status_downloading)
        is UpdateStatus.Verifying -> stringResource(R.string.uc_status_verifying)
        is UpdateStatus.ReadyToInstall -> stringResource(R.string.uc_status_ready)
        UpdateStatus.Installing -> stringResource(R.string.uc_status_installing)
        UpdateStatus.InstallPermissionRequired -> stringResource(R.string.uc_status_install_permission)
        is UpdateStatus.CheckFailed -> failureMessage(reason)
    }

@Composable
private fun UpdateStatus.statusColor(): Color =
    when (this) {
        is UpdateStatus.CheckFailed -> MaterialTheme.colorScheme.error
        UpdateStatus.InstallPermissionRequired -> MaterialTheme.colorScheme.tertiary
        UpdateStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

private fun formatDate(timestamp: Long): String =
    DateTimeFormatter
        .ofPattern("MMM d, yyyy • HH:mm")
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
