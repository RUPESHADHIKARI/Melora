/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import com.metrolist.music.LocalNavController
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalIsPlayerExpanded
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.PauseSearchHistoryKey
import com.metrolist.music.constants.SearchSource
import com.metrolist.music.constants.SearchSourceKey
import com.metrolist.music.db.entities.SearchHistory
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    pureBlack: Boolean,
    savedStateHandle: SavedStateHandle,
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current
    val isPlayerExpanded = LocalIsPlayerExpanded.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = LocalContext.current

    // Voice search requires a microphone; hide the entry otherwise.
    val voiceSearchSupported =
        remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) }

    val scrollToTopCount by savedStateHandle.getStateFlow("scrollToTopCount", 0).collectAsStateWithLifecycle(initialValue = 0)

    var lastHandledCount by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (!isPlayerExpanded) {
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (e: Exception) {
            }
        }
    }
    LaunchedEffect(scrollToTopCount) {
        if (scrollToTopCount > lastHandledCount) {
            lastHandledCount = scrollToTopCount
            val isHandlingScrollToTop = true

            kotlinx.coroutines.delay(100)

            if (!isPlayerExpanded) {
                focusManager.clearFocus(force = true)
                kotlinx.coroutines.delay(50)
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                } catch (e: Exception) {
                }
            }

            kotlinx.coroutines.delay(500)
        }
    }

    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

    fun handleSearch(searchQuery: String) {
        if (searchQuery.isEmpty()) {
            return
        }

        focusManager.clearFocus()

        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
            is YouTubeUrlParser.ParsedUrl.Video -> {
                playerConnection?.playQueue(
                    YouTubeQueue(
                        WatchEndpoint(videoId = parsedUrl.id),
                    ),
                )
            }

            is YouTubeUrlParser.ParsedUrl.Playlist -> {
                navController.navigate("online_playlist/${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Album -> {
                navController.navigate("album/MPREb_${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Artist -> {
                navController.navigate("artist/${parsedUrl.id}")
            }

            null -> {
                navController.navigate(SearchRoutes.resultRoute(searchQuery))
            }
        }

        if (!pauseSearchHistory) {
            coroutineScope.launch(Dispatchers.IO) {
                database.query {
                    insert(SearchHistory(query = searchQuery))
                }
            }
        }
    }

    val onSearch: (String) -> Unit = { searchQuery -> handleSearch(searchQuery) }

    val onSearchFromSuggestion: (String) -> Unit = { searchQuery -> handleSearch(searchQuery) }

    val barColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(barColor),
            ) {
                Spacer(
                    modifier =
                        Modifier.windowInsetsPadding(
                            WindowInsets.systemBars.only(WindowInsetsSides.Top),
                        ),
                )

                // Brand header on the landing state; hidden while typing so
                // results keep the space.
                AnimatedVisibility(
                    visible = query.text.isEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.splash_emblem),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MELORA",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { navController.navigate("account") },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.account),
                                contentDescription = stringResource(R.string.account),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // Hero title mirrors the brand landing look.
                AnimatedVisibility(
                    visible = query.text.isEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, bottom = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.search),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.search_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Rounded search field: [search] [input] [clear] [mic]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (pureBlack) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .padding(start = 14.dp)
                                .size(20.dp),
                    )

                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 14.dp)
                                .focusRequester(focusRequester),
                        textStyle =
                            TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (query.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.what_to_listen),
                                        style =
                                            TextStyle(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 16.sp,
                                            ),
                                        maxLines = 1,
                                    )
                                }
                                innerTextField()
                            }
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Search,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onSearch = { onSearch(query.text) },
                            ),
                    )

                    if (query.text.isNotEmpty()) {
                        IconButton(onClick = { query = TextFieldValue("") }) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (voiceSearchSupported) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .padding(end = 8.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable {
                                        focusManager.clearFocus()
                                        navController.navigate("recognition")
                                    },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.mic),
                                contentDescription = stringResource(R.string.voice_search),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Online / local scope chips (same switching as the old globe icon).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, bottom = 8.dp),
                ) {
                    SearchScopeChip(
                        label = stringResource(R.string.remote_history),
                        selected = searchSource == SearchSource.ONLINE,
                        onClick = { searchSource = SearchSource.ONLINE },
                    )
                    SearchScopeChip(
                        label = stringResource(R.string.local_history),
                        selected = searchSource == SearchSource.LOCAL,
                        onClick = { searchSource = SearchSource.LOCAL },
                    )
                }
            }
        },
        containerColor = barColor,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .padding(top = paddingValues.calculateTopPadding())
                    .fillMaxSize(),
        ) {
            when (searchSource) {
                SearchSource.LOCAL -> {
                    LocalSearchScreen(
                        query = query.text,
                        onDismiss = { navController.navigateUp() },
                        pureBlack = pureBlack,
                    )
                }

                SearchSource.ONLINE -> {
                    OnlineSearchScreen(
                        query = query.text,
                        onQueryChange = { query = it },
                        onSearch = onSearchFromSuggestion,
                        onDismiss = { /* Don't dismiss when searching from suggestions */ },
                        pureBlack = pureBlack,
                    )
                }
            }
        }
    }

    // Handle lifecycle events to manage keyboard visibility
    DisposableEffect(lifecycleOwner, isPlayerExpanded) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        // Always hide keyboard when resuming if player is expanded
                        if (isPlayerExpanded) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        // Clear focus when pausing to prevent keyboard from showing on resume
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Initial check - hide keyboard if player is expanded
        if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun SearchScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
