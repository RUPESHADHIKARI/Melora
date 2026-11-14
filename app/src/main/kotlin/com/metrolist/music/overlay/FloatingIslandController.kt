/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.constants.DynamicIslandAnimateKey
import com.metrolist.music.constants.DynamicIslandAutoShowKey
import com.metrolist.music.constants.DynamicIslandEnabledKey
import com.metrolist.music.constants.DynamicIslandShowArtworkKey
import com.metrolist.music.constants.DynamicIslandShowWaveformKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.currentMetadata
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import timber.log.Timber

/**
 * Playback commands routed from the island to the running MusicService, so
 * skip/like semantics (Cast, "restart after 3s", DB updates) match the media
 * session instead of being duplicated here.
 */
interface FloatingIslandHost {
    fun togglePlayPause()

    fun seekToNext()

    fun seekToPrevious()

    fun toggleShuffle()

    fun toggleLike()
}

/**
 * App-wide owner of the Floating Music Island. MusicService attaches the
 * shared ExoPlayer (no second player is ever created); this class tracks
 * playback state, computes visibility with [FloatingIslandPolicy] and shows
 * one TYPE_APPLICATION_OVERLAY window holding a compact pill that expands
 * into a player card.
 */
@Singleton
class FloatingIslandController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @com.metrolist.music.di.ApplicationScope private val scope: CoroutineScope,
        private val database: MusicDatabase,
    ) {
        /** Command surface of the attached playback service. */
        private var host: FloatingIslandHost? = null

        private var attachedPlayer: Player? = null

        private val appInForeground = MutableStateFlow(false)
        private val playbackActive = MutableStateFlow(false)
        private val expanded = MutableStateFlow(false)
        private val metadata = MutableStateFlow<MediaMetadata?>(null)
        private val shuffleEnabled = MutableStateFlow(false)
        private val liked = MutableStateFlow(false)

        private val prefs =
            context.dataStore.data
                .map { p ->
                    IslandPrefs(
                        enabled = p[DynamicIslandEnabledKey] ?: false,
                        showArtwork = p[DynamicIslandShowArtworkKey] ?: true,
                        showWaveform = p[DynamicIslandShowWaveformKey] ?: true,
                        autoShow = p[DynamicIslandAutoShowKey] ?: true,
                        animate = p[DynamicIslandAnimateKey] ?: true,
                    )
                }

        init {
            observeState()
            observeLiked()
        }

        // ── Wiring API ────────────────────────────────────────────────────

        /** Attaches the shared player of [com.metrolist.music.playback.MusicService]. */
        fun attachPlayer(
            player: Player,
            host: FloatingIslandHost,
        ) {
            attachedPlayer?.removeListener(playerListener)
            attachedPlayer = player
            player.addListener(playerListener)
            this.host = host
            metadata.value = player.currentMetadata
            shuffleEnabled.value = player.shuffleModeEnabled
            refreshPlaybackState()
        }

        fun detachPlayer() {
            attachedPlayer?.removeListener(playerListener)
            attachedPlayer = null
            host = null
            playbackActive.value = false
            metadata.value = null
            shuffleEnabled.value = false
            expanded.value = false
        }

        /** Called from App's ActivityLifecycleCallbacks; true while an activity is resumed. */
        fun setAppInForeground(foreground: Boolean) {
            appInForeground.value = foreground
        }

        fun expand() {
            expanded.value = true
        }

        fun collapse() {
            expanded.value = false
        }

        // ── Player state ──────────────────────────────────────────────────

        private val playerListener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    refreshPlaybackState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    refreshPlaybackState()
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    metadata.value = mediaItem?.metadata
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    shuffleEnabled.value = shuffleModeEnabled
                }
            }

        // Buffered gaps between tracks must not make the island flicker.
        private fun refreshPlaybackState() {
            val player = attachedPlayer
            playbackActive.value =
                player != null &&
                    player.playWhenReady &&
                    (player.playbackState == Player.STATE_READY ||
                        player.playbackState == Player.STATE_BUFFERING)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun observeLiked() {
            metadata
                .flatMapLatest { database.song(it?.id) }
                .onEach { liked.value = it?.song?.liked == true }
                .launchIn(scope)
        }

        // ── Visibility + window management ────────────────────────────────

        private val wm by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

        private var windowView: ViewGroup? = null
        private var windowParams: WindowManager.LayoutParams? = null
        private var windowAdded = false
        private var lastState: IslandUiState? = null
        private var lastAppliedExpanded = false
        private var sizeAnimator: ValueAnimator? = null

        /** Front-camera cutout detected from DisplayCutout; null when unavailable. */
        private var camera: FloatingIslandGeometry.Camera? = null

        private fun observeState() {
            scope.launch(Dispatchers.Main) {
                combine(
                    prefs,
                    combine(playbackActive, appInForeground, expanded) { active, fg, exp ->
                        Triple(active, fg, exp)
                    },
                    combine(metadata, shuffleEnabled, liked) { md, sh, lk ->
                        Triple(md, sh, lk)
                    },
                ) { p, (active, fg, exp), (md, sh, lk) ->
                    IslandUiState(
                        visible =
                            FloatingIslandPolicy.isVisible(
                                enabled = p.enabled,
                                canDrawOverlays = Settings.canDrawOverlays(context),
                                playbackActive = active,
                                appInForeground = fg,
                                autoShow = p.autoShow,
                            ),
                        prefs = p,
                        runtime =
                            IslandRuntime(
                                playbackActive = active,
                                appInForeground = fg,
                                expanded = exp,
                                metadata = md,
                                shuffle = sh,
                                liked = lk,
                            ),
                    )
                }
                    .distinctUntilChanged()
                    .collect { state ->
                        lastState = state
                        if (state.visible) {
                            if (!windowAdded) ensureWindow(state)
                            applyState(state)
                        } else {
                            removeWindow()
                        }
                    }
            }
        }

        private fun ensureWindow(state: IslandUiState) {
            if (windowAdded || !Settings.canDrawOverlays(context)) return
            try {
                val view =
                    LayoutInflater
                        .from(context)
                        .inflate(R.layout.overlay_floating_island, null) as ViewGroup
                bindView(view)
                // Pre-read the cutout (API 30+); the insets listener refines it
                // for older versions and after rotation.
                if (Build.VERSION.SDK_INT >= 30) {
                    captureInsets(view, wm.maximumWindowMetrics.windowInsets)
                }
                val bounds = currentBounds(expanded = false)
                val params =
                    WindowManager.LayoutParams(
                        bounds.width,
                        bounds.height,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT,
                    ).apply {
                        // Absolute display coordinates so the pill can sit on
                        // the camera cutout inside the status-bar strip.
                        gravity = Gravity.TOP or Gravity.START
                        x = bounds.x
                        y = bounds.y
                        if (Build.VERSION.SDK_INT >= 28) {
                            layoutInDisplayCutoutMode =
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        }
                    }
                windowView = view
                windowParams = params
                wm.addView(view, params)
                windowAdded = true
                // A freshly shown island is always compact.
                lastAppliedExpanded = false
                animateIn(state.prefs)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to show floating island")
                windowView = null
                windowParams = null
                windowAdded = false
            }
        }

        /**
         * Reads the front-camera cutout and status-bar inset from [insets],
         * then re-places the window. Called at attach time and whenever the
         * system re-dispatches insets (rotation, foldables).
         *
         * Cutout rects delivered through window insets are in window-local
         * coordinates, so [offsetX]/[offsetY] convert them to display space;
         * the pre-read from maximumWindowMetrics passes 0 (full-display frame).
         */
        private fun captureInsets(
            view: ViewGroup,
            insets: WindowInsets?,
            offsetX: Int = 0,
            offsetY: Int = 0,
        ) {
            statusBarH = statusBarHeight()
            camera =
                if (Build.VERSION.SDK_INT >= 28) {
                    insets
                        ?.displayCutout
                        ?.boundingRects
                        ?.minByOrNull { it.top }
                        ?.let { rect ->
                            FloatingIslandGeometry.Camera(
                                centerX = offsetX + rect.centerX(),
                                top = offsetY + rect.top,
                                width = rect.width(),
                                height = rect.height(),
                            )
                        }
                } else {
                    null
                }
            applyCameraGap()
            // Don't re-place while a morph is animating: the insets listener
            // re-fires on every window resize the morph itself performs, and
            // re-placing would cancel the animation mid-flight.
            if (sizeAnimator?.isRunning != true) reapplyLayout()
        }

        private fun removeWindow() {
            sizeAnimator?.cancel()
            sizeAnimator = null
            val view = windowView ?: return
            windowView = null
            windowParams = null
            windowAdded = false
            expanded.value = false
            lastAppliedExpanded = false
            if (lastState?.prefs?.animate == true) {
                animateOut(view)
            } else {
                try {
                    wm.removeView(view)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Island view already removed")
                }
            }
        }

        // ── View binding ──────────────────────────────────────────────────

        private lateinit var pill: LinearLayout
        private lateinit var pillArtwork: ImageView
        private lateinit var pillGap: View
        private lateinit var pillWaveform: WaveformView
        private lateinit var card: LinearLayout
        private lateinit var cardContent: LinearLayout
        private lateinit var cardArtwork: ImageView
        private lateinit var cardTitle: TextView
        private lateinit var cardArtist: TextView
        private lateinit var btnShuffle: ImageView
        private lateinit var btnPrev: ImageView
        private lateinit var btnPlay: ImageView
        private lateinit var btnNext: ImageView
        private lateinit var btnLike: ImageView

        private var pillW = 0
        private var pillH = 0
        private var pillPadTopDefault = 0
        private var pillPadBottomDefault = 0
        private var cardW = 0
        private var cardH = 0
        private var statusBarH = 0

        /** Display width the card was last measured for (rotation guard). */
        private var lastCardDisplayWidth = 0

        /**
         * Sizes the card to its final, fixed expanded shape. During the morph
         * the window grows/shrinks around it and the surface clips the
         * overflow, so content never squeezes or re-wraps mid-animation.
         */
        private fun remeasureCard() {
            val displayWidth = context.resources.displayMetrics.widthPixels
            if (displayWidth == lastCardDisplayWidth) return
            lastCardDisplayWidth = displayWidth
            cardW = minOf(dp(320), displayWidth - dp(24))
            card.measure(
                View.MeasureSpec.makeMeasureSpec(cardW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            cardH = card.measuredHeight
            card.updateLayoutParams<ViewGroup.LayoutParams> {
                width = cardW
                height = cardH
            }
            cardContent.pivotX = cardContent.measuredWidth / 2f
            cardContent.pivotY = 0f
        }

        /** Display width when the window was last re-placed; guards against
         *  re-running placement on layouts triggered by the morph itself. */
        private var lastLayoutWidth = 0

        private fun bindView(root: ViewGroup) {
            pill = root.findViewById(R.id.island_pill)
            pillArtwork = root.findViewById(R.id.island_pill_artwork)
            pillGap = root.findViewById(R.id.island_pill_gap)
            pillWaveform = root.findViewById(R.id.island_pill_waveform)
            card = root.findViewById(R.id.island_card)
            cardContent = root.findViewById(R.id.island_card_content)
            cardArtwork = root.findViewById(R.id.island_card_artwork)
            cardTitle = root.findViewById(R.id.island_card_title)
            cardArtist = root.findViewById(R.id.island_card_artist)
            btnShuffle = root.findViewById(R.id.island_btn_shuffle)
            btnPrev = root.findViewById(R.id.island_btn_prev)
            btnPlay = root.findViewById(R.id.island_btn_play)
            btnNext = root.findViewById(R.id.island_btn_next)
            btnLike = root.findViewById(R.id.island_btn_like)

            val pillBg =
                GradientDrawable().apply {
                    setColor(BG_COLOR)
                    cornerRadius = dp(19f)
                }
            val cardBg =
                GradientDrawable().apply {
                    setColor(BG_COLOR)
                    cornerRadius = dp(22f)
                }
            pill.background = pillBg
            card.background = cardBg

            val artworkCorner =
                object : ViewOutlineProvider() {
                    override fun getOutline(
                        view: View,
                        outline: Outline,
                    ) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(9f))
                    }
                }
            listOf(pillArtwork, cardArtwork).forEach {
                it.clipToOutline = true
                it.outlineProvider = artworkCorner
            }

            listOf(btnShuffle, btnPrev, btnPlay, btnNext, btnLike).forEach {
                it.setColorFilter(Color.WHITE)
            }

            // Top-align the pill inside the resizing window root; on screens
            // with a status-bar strip deeper than the capsule, reapplyLayout
            // bottom-anchors its content so taps reach it below the strip.
            (pill.layoutParams as? FrameLayout.LayoutParams)?.gravity =
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            (card.layoutParams as? FrameLayout.LayoutParams)?.gravity =
                Gravity.TOP or Gravity.CENTER_HORIZONTAL

            // Measure both shapes so window morphing can lerp between them.
            pill.visibility = View.VISIBLE
            card.visibility = View.INVISIBLE
            remeasureCard()
            pill.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            pillW = pill.measuredWidth
            pillH = pill.measuredHeight
            pillPadTopDefault = pill.paddingTop
            pillPadBottomDefault = pill.paddingBottom

            // Keep soft island corners on the morphing shape at every size.
            root.clipToOutline = true
            root.outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(
                        view: View,
                        outline: Outline,
                    ) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(22f))
                    }
                }

            // Camera cutout and rotation: insets re-dispatch covers both.
            root.setOnApplyWindowInsetsListener { v, insets ->
                captureInsets(v as ViewGroup, insets, windowParams?.x ?: 0, windowParams?.y ?: 0)
                insets
            }
            // Rotation can re-frame the window without new insets. Only react
            // to a real display-width change, never to the layouts triggered
            // by the morph animation itself (those would cancel it mid-flight).
            root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (!windowAdded) return@addOnLayoutChangeListener
                val width = context.resources.displayMetrics.widthPixels
                if (width != lastLayoutWidth) {
                    lastLayoutWidth = width
                    captureInsets(
                        v as ViewGroup,
                        v.rootWindowInsets,
                        windowParams?.x ?: 0,
                        windowParams?.y ?: 0,
                    )
                }
            }

            pill.setOnClickListener { expand() }
            // Buttons consume their own taps; taps on the card's empty area collapse.
            card.setOnClickListener { collapse() }
            root.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) collapse()
                false
            }

            btnPlay.setOnClickListener { host?.togglePlayPause() }
            btnNext.setOnClickListener { host?.seekToNext() }
            btnPrev.setOnClickListener { host?.seekToPrevious() }
            btnShuffle.setOnClickListener { host?.toggleShuffle() }
            btnLike.setOnClickListener { host?.toggleLike() }
        }

        private fun applyState(state: IslandUiState) {
            val view = windowView ?: return
            val md = state.runtime.metadata

            cardTitle.text = md?.title.orEmpty()
            cardArtist.text = md?.artists?.joinToString { it.name }.orEmpty()
            val artworkUrl = md?.thumbnailUrl
            if (state.prefs.showArtwork) {
                pillArtwork.visibility = View.VISIBLE
                cardArtwork.visibility = View.VISIBLE
                loadArtwork(pillArtwork, artworkUrl)
                loadArtwork(cardArtwork, artworkUrl)
            } else {
                pillArtwork.visibility = View.GONE
                cardArtwork.visibility = View.GONE
            }
            pillWaveform.visibility =
                if (state.prefs.showWaveform) View.VISIBLE else View.GONE
            pillWaveform.playing = state.runtime.playbackActive && state.prefs.showWaveform

            btnPlay.setImageResource(
                if (state.runtime.playbackActive) R.drawable.pause else R.drawable.play,
            )
            btnShuffle.setImageResource(
                if (state.runtime.shuffle) R.drawable.shuffle_on else R.drawable.shuffle,
            )
            btnLike.setImageResource(
                if (state.runtime.liked) R.drawable.favorite else R.drawable.favorite_border,
            )

            if (lastAppliedExpanded != state.runtime.expanded) {
                lastAppliedExpanded = state.runtime.expanded
                applySize(state.runtime.expanded, animate = state.prefs.animate)
            }
        }

        private fun applySize(
            expanded: Boolean,
            animate: Boolean,
        ) {
            val view = windowView ?: return
            val params = windowParams ?: return
            sizeAnimator?.cancel()
            sizeAnimator = null
            val to = currentBounds(expanded)
            if (!animate) {
                setWindowShape(view, params, to)
                settleShape(expanded)
                return
            }
            // Start from the window's live shape so a morph always animates
            // smoothly, even if it was interrupted or re-placed meanwhile.
            val from = FloatingIslandGeometry.Bounds(params.x, params.y, params.width, params.height)
            card.visibility = View.VISIBLE
            pill.visibility = View.VISIBLE
            sizeAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration =
                        if (expanded) MORPH_EXPAND_MS else MORPH_COLLAPSE_MS
                    // Expand settles gently, collapse gets out of the way
                    // quickly — the iOS island motion pair.
                    interpolator =
                        if (expanded) DecelerateInterpolator(1.6f) else AccelerateInterpolator(1.4f)
                    addUpdateListener { anim ->
                        val t = anim.animatedValue as Float
                        setWindowShape(view, params, lerpBounds(from, to, t))
                        if (expanded) {
                            // The shape grows around the pill: the pill
                            // dissolves early, content blooms in later with a
                            // subtle scale-up from the camera anchor.
                            pill.alpha = 1f - clamp01(t / 0.35f)
                            cardContent.alpha = clamp01((t - 0.3f) / 0.55f)
                            val scale = 0.94f + 0.06f * clamp01((t - 0.25f) / 0.6f)
                            cardContent.scaleX = scale
                            cardContent.scaleY = scale
                        } else {
                            // Content folds away first, then the pill
                            // re-emerges as the shape shrinks onto the camera.
                            cardContent.alpha = 1f - clamp01(t / 0.45f)
                            val scale = 1f - 0.06f * clamp01(t / 0.6f)
                            cardContent.scaleX = scale
                            cardContent.scaleY = scale
                            pill.alpha = clamp01((t - 0.45f) / 0.45f)
                        }
                        if (t >= 1f) settleShape(expanded)
                    }
                    start()
                }
        }

        /** Final, non-animating state after a morph completes. */
        private fun settleShape(expanded: Boolean) {
            pill.visibility = if (expanded) View.GONE else View.VISIBLE
            card.visibility = if (expanded) View.VISIBLE else View.GONE
            pill.alpha = 1f
            card.alpha = 1f
            cardContent.alpha = 1f
            cardContent.scaleX = 1f
            cardContent.scaleY = 1f
        }

        /**
         * Window bounds for the requested shape, computed from the live camera
         * cutout so rotation and foldables stay aligned without caches.
         */
        private fun currentBounds(expanded: Boolean): FloatingIslandGeometry.Bounds {
            val displayWidth = context.resources.displayMetrics.widthPixels
            val compact =
                FloatingIslandGeometry.compact(
                    displayWidth = displayWidth,
                    camera = camera,
                    centerTolerance = dp(32),
                    statusBarHeight = statusBarH,
                    fallbackWidth = pillW,
                    fallbackHeight = pillH,
                    sidePad = dp(38),
                    minHeight = pillH,
                    minTop = dp(4),
                    tapClearance = dp(6),
                    touchInset = dp(3),
                )
            return if (expanded) {
                FloatingIslandGeometry.expanded(
                    displayWidth = displayWidth,
                    compact = compact,
                    width = cardW,
                    height = cardH,
                    margin = dp(16),
                )
            } else {
                compact
            }
        }
        private fun setWindowShape(
            view: ViewGroup,
            params: WindowManager.LayoutParams,
            bounds: FloatingIslandGeometry.Bounds,
        ) {
            params.x = bounds.x
            params.y = bounds.y
            params.width = bounds.width
            params.height = bounds.height
            try {
                wm.updateViewLayout(view, params)
                // Refresh the rounded outline for the new window size so the
                // morphing shape keeps its soft island corners.
                view.invalidateOutline()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Island relayout failed")
            }
        }

        /** Re-places the window at the current shape without animating. */
        private fun reapplyLayout() {
            val view = windowView ?: return
            val params = windowParams ?: return
            remeasureCard()
            // On screens whose status-bar strip is taller than the capsule
            // (low densities), the capsule touches the cutout from below and
            // its content is bottom-anchored with a slim bottom pad so the
            // artwork and waveform stay in the tappable zone below the
            // strip; elsewhere the XML padding applies unchanged.
            val bottomPad = if (pillH in 1 until statusBarH) dp(2) else pillPadBottomDefault
            val topPad = (pillPadTopDefault + pillPadBottomDefault - bottomPad).coerceAtLeast(0)
            if (pill.paddingTop != topPad || pill.paddingBottom != bottomPad) {
                pill.setPadding(pill.paddingStart, topPad, pill.paddingEnd, bottomPad)
            }
            val target = currentBounds(lastAppliedExpanded)
            if (params.x != target.x || params.y != target.y ||
                params.width != target.width || params.height != target.height
            ) {
                sizeAnimator?.cancel()
                sizeAnimator = null
                setWindowShape(view, params, target)
            }
        }

        /** Gap between artwork and waveform equals the camera hole it wraps. */
        private fun applyCameraGap() {
            val overlap = isOverlappingCamera()
            val target = if (overlap) (camera?.width ?: dp(18)) else dp(18)
            if (pillGap.layoutParams.width != target) {
                pillGap.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = target
                }
            }
        }

        private fun isOverlappingCamera(): Boolean {
            val cam = camera ?: return false
            return abs(cam.centerX - context.resources.displayMetrics.widthPixels / 2) <= dp(32)
        }

        private fun lerpBounds(
            from: FloatingIslandGeometry.Bounds,
            to: FloatingIslandGeometry.Bounds,
            t: Float,
        ): FloatingIslandGeometry.Bounds =
            FloatingIslandGeometry.Bounds(
                x = lerp(from.x, to.x, t),
                y = lerp(from.y, to.y, t),
                width = lerp(from.width, to.width, t),
                height = lerp(from.height, to.height, t),
            )

        // ── Animations ────────────────────────────────────────────────────

        private fun animateIn(prefs: IslandPrefs) {
            val view = windowView ?: return
            if (!prefs.animate) {
                view.alpha = 1f
                return
            }
            // Bloom out of the camera cutout, like the iPhone island.
            view.pivotX = (windowParams?.width ?: pillW) / 2f
            view.pivotY = (windowParams?.height ?: pillH) / 2f
            view.scaleX = 0.4f
            view.scaleY = 0.8f
            view.alpha = 0f
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        private fun animateOut(view: ViewGroup) {
            view.animate()
                .scaleX(0.4f)
                .scaleY(0.8f)
                .alpha(0f)
                .setDuration(160)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    try {
                        wm.removeView(view)
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Island view already removed")
                    }
                }
                .start()
        }

        // ── Artwork ───────────────────────────────────────────────────────

        private fun loadArtwork(
            imageView: ImageView,
            url: String?,
        ) {
            if (imageView.tag == url) return
            imageView.tag = url
            if (url == null) {
                imageView.setImageResource(R.mipmap.ic_launcher)
                return
            }
            scope.launch {
                val bitmap =
                    withContext(Dispatchers.IO) {
                        try {
                            SingletonImageLoader
                                .get(context)
                                .execute(
                                    ImageRequest
                                        .Builder(context)
                                        .data(url)
                                        .size(128, 128)
                                        .allowHardware(false)
                                        .build(),
                                ).image
                                ?.toBitmap()
                        } catch (e: Exception) {
                            Timber.tag(TAG).w(e, "Island artwork load failed")
                            null
                        }
                    }
                if (bitmap != null && imageView.tag == url) {
                    withContext(Dispatchers.Main) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }

        // ── Helpers ───────────────────────────────────────────────────────

        private fun statusBarHeight(): Int =
            if (Build.VERSION.SDK_INT >= 30) {
                wm.currentWindowMetrics.windowInsets
                    .getInsets(WindowInsets.Type.statusBars())
                    .top
            } else {
                @Suppress("DEPRECATION")
                val id = Resources.getSystem().getIdentifier("status_bar_height", "dimen", "android")
                if (id > 0) Resources.getSystem().getDimensionPixelSize(id) else dp(24)
            }

        private fun dp(value: Int): Int =
            TypedValue
                .applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value.toFloat(),
                    context.resources.displayMetrics,
                ).toInt()

        private fun dp(value: Float): Float =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.resources.displayMetrics,
            )

        private fun lerp(
            from: Int,
            to: Int,
            t: Float,
        ): Int = (from + (to - from) * t).toInt()

        private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

        private companion object {
            const val TAG = "FloatingIsland"
            const val BG_COLOR = 0xE6000000.toInt()
            const val MORPH_EXPAND_MS = 360L
            const val MORPH_COLLAPSE_MS = 260L
        }
    }

private data class IslandPrefs(
    val enabled: Boolean,
    val showArtwork: Boolean,
    val showWaveform: Boolean,
    val autoShow: Boolean,
    val animate: Boolean,
)

private data class IslandRuntime(
    val playbackActive: Boolean,
    val appInForeground: Boolean,
    val expanded: Boolean,
    val metadata: MediaMetadata?,
    val shuffle: Boolean,
    val liked: Boolean,
)

private data class IslandUiState(
    val visible: Boolean,
    val prefs: IslandPrefs,
    val runtime: IslandRuntime,
)
