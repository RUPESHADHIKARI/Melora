/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.splash

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.metrolist.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Brand palette used for the subtle lighting and highlight sweep
private val SplashPink = Color(0xFFEC4899)
private val SplashPurple = Color(0xFF8B5CF6)
private val SplashCyan = Color(0xFF22D3EE)

private val EmblemEnterEasing = CubicBezierEasing(0.16f, 0.84f, 0.27f, 1f)
private val SweepEasing = CubicBezierEasing(0.5f, 0f, 0.5f, 1f)

/**
 * Cinematic Melora welcome overlay, shown right after the system splash screen.
 *
 * The real app content is composed underneath the whole time, so genuine
 * initialization keeps happening while the choreography plays; dismissing the
 * overlay never delays usability. The overlay also swallows touches so users
 * cannot trigger invisible content behind it.
 */
@Composable
fun MeloraSplashOverlay(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion =
        remember {
            Settings.Global
                .getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }

    // Start near the state the system splash icon left off in for a seamless hand-off
    val emblemAlpha = remember { Animatable(0.4f) }
    val emblemScale = remember { Animatable(0.93f) }
    val emblemDrift = remember { Animatable(0f) }
    val sweep = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val markAlpha = remember { Animatable(0f) }
    val markRise = remember { Animatable(1f) }
    val tagAlpha = remember { Animatable(0f) }
    val creditAlpha = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    // Decoded once per overlay lifetime; loaded via BitmapFactory for a reliable
    // ImageBitmap source across Compose versions.
    val emblemBitmap =
        remember {
            requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.splash_emblem_alpha))
                .asImageBitmap()
        }
    val markBitmap =
        remember {
            requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.splash_wordmark))
                .asImageBitmap()
        }
    val arcBitmap =
        remember {
            requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.splash_arc))
                .asImageBitmap()
        }

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            // Show the full final composition statically, then fade away quickly
            emblemAlpha.snapTo(1f)
            emblemScale.snapTo(1f)
            glow.snapTo(1f)
            markAlpha.snapTo(1f)
            markRise.snapTo(0f)
            tagAlpha.snapTo(1f)
            creditAlpha.snapTo(1f)
            delay(1100)
            overlayAlpha.animateTo(0f, tween(260, easing = LinearEasing))
            onFinished()
            return@LaunchedEffect
        }

        // Stage 2 — emblem entrance (scale + fade + controlled settle motion)
        launch { emblemAlpha.animateTo(1f, tween(420, easing = LinearOutSlowInEasing)) }
        launch { emblemScale.animateTo(1f, tween(680, easing = EmblemEnterEasing)) }
        launch {
            emblemDrift.animateTo(1f, tween(760, delayMillis = 80, easing = FastOutSlowInEasing))
        }
        // Stage 3 — subtle brand lighting + light sweep across the emblem
        launch { glow.animateTo(1f, tween(620, delayMillis = 160)) }
        launch { sweep.animateTo(1f, tween(640, delayMillis = 380, easing = SweepEasing)) }
        // Stage 4 — wordmark rise
        launch {
            markRise.animateTo(0f, tween(680, delayMillis = 520, easing = FastOutSlowInEasing))
        }
        launch { markAlpha.animateTo(1f, tween(560, delayMillis = 520, easing = LinearOutSlowInEasing)) }
        // Stage 5 — tagline
        launch { tagAlpha.animateTo(1f, tween(520, delayMillis = 800)) }
        // Stage 6 — developer credit
        launch { creditAlpha.animateTo(1f, tween(540, delayMillis = 960)) }
        // Stage 7 — hold the full composition briefly, then crossfade into the app
        delay(2140)
        overlayAlpha.animateTo(0f, tween(340, easing = LinearEasing))
        onFinished()
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(1000f)
                .background(Color.Black)
                .graphicsLayer { alpha = overlayAlpha.value }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
    ) {
        val emblemWidth = minOf(maxWidth * 0.52f, 236.dp)
        val emblemHeight = emblemWidth * (emblemBitmap.height.toFloat() / emblemBitmap.width)
        val markWidth = minOf(maxWidth * 0.66f, 302.dp)
        val markHeight = markWidth * (markBitmap.height.toFloat() / markBitmap.width)
        val arcWidth = markWidth * 0.9f
        val arcHeight = arcWidth * (arcBitmap.height.toFloat() / arcBitmap.width)

        Column(
            modifier = Modifier.align(Alignment.Center).padding(bottom = (maxHeight * 0.11f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(emblemWidth)
                        .height(emblemHeight)
            ) {
                // Soft brand-lit halo behind the emblem
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = glow.value
                                val grow = 1f + 0.3f * (1f - glow.value)
                                scaleX = grow
                                scaleY = grow
                            }
                            .drawBehind {
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    SplashPink.copy(alpha = 0.16f),
                                                    SplashPurple.copy(alpha = 0.10f),
                                                    SplashCyan.copy(alpha = 0.07f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(size.width / 2f, size.height / 2f),
                                            radius = size.maxDimension * 0.72f,
                                        ),
                                )
                            },
                )

                Image(
                    bitmap = emblemBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = emblemAlpha.value
                                scaleX = emblemScale.value
                                scaleY = emblemScale.value
                                translationY = -emblemDrift.value * 10.dp.toPx()
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                val p = sweep.value
                                if (p > 0f && p < 1f) {
                                    // Band travels fully off-stage left → right, 3 widths of travel
                                    val band = size.width * 0.6f
                                    val cx = (p - 0.4f) * size.width * 3.0f
                                    drawRect(
                                        brush =
                                            Brush.linearGradient(
                                                colors =
                                                    listOf(
                                                        Color.Transparent,
                                                        SplashPink.copy(alpha = 0.22f),
                                                        Color.White.copy(alpha = 0.42f),
                                                        SplashCyan.copy(alpha = 0.22f),
                                                        Color.Transparent,
                                                    ),
                                                start = Offset(cx - band, 0f),
                                                end = Offset(cx + band * 0.4f, size.height),
                                            ),
                                        blendMode = BlendMode.SrcAtop,
                                    )
                                }
                            },
                )
            }

            Spacer(Modifier.height(20.dp))

            // Stage 4 — official wordmark (play-button "O"), fades in while rising
            Image(
                bitmap = markBitmap,
                contentDescription = stringResource(R.string.melora_wordmark_content_description),
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .width(markWidth)
                        .height(markHeight)
                        .graphicsLayer {
                            alpha = markAlpha.value
                            translationY = markRise.value * 14.dp.toPx()
                        },
            )

            Spacer(Modifier.height(6.dp))

            // Official gradient arc under the wordmark (part of the logo lockup)
            Image(
                bitmap = arcBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .width(arcWidth)
                        .height(arcHeight)
                        .graphicsLayer {
                            alpha = markAlpha.value * 0.9f
                            translationY = markRise.value * 18.dp.toPx()
                        },
            )

            Spacer(Modifier.height(12.dp))

            // Stage 5 — brand tagline
            Text(
                text = stringResource(R.string.melora_splash_tagline),
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = tagAlpha.value },
            )
        }

        // Stage 6 — developer credit near the lower section, above system navigation
        Text(
            text = stringResource(R.string.melora_splash_credit),
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 12.sp,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .graphicsLayer { alpha = creditAlpha.value },
        )
    }
}
