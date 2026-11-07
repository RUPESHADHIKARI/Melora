/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.onboarding

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.metrolist.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// MELORA brand palette (shared with the splash overlay).
private val BrandPink = Color(0xFFEC4899)
private val BrandPurple = Color(0xFF8B5CF6)
private val BrandCyan = Color(0xFF22D3EE)

private val BrandGradient =
    Brush.linearGradient(listOf(BrandPink, BrandPurple, BrandCyan))

private val EnterEasing = CubicBezierEasing(0.16f, 0.84f, 0.27f, 1f)

/** Upper bound for stored first names. */
private const val MAX_NAME_LENGTH = 32

/**
 * Melora first-launch onboarding: a "What should we call you?" card with a
 * glowing Continue, then an animated "Welcome to MELORA, [Name]!" moment that
 * auto-fades into the app.
 *
 * [armed] becomes true once the splash overlay is gone so the two flows
 * hand off seamlessly (the dark backdrops match). The app is composed
 * underneath the whole time, so dismissing the flow never delays usability.
 *
 * [onNameSubmitted] persists the trimmed first name the moment the user taps
 * Continue; [onFinished] fires only after the welcome choreography and its
 * fade-out complete.
 */
@Composable
fun MeloraOnboardingFlow(
    armed: Boolean,
    savedName: String,
    onNameSubmitted: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion =
        remember {
            Settings.Global
                .getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }

    val keyboard = LocalSoftwareKeyboardController.current

    var nameInput by rememberSaveable { mutableStateOf(savedName) }
    var showedWelcome by rememberSaveable { mutableStateOf(false) }
    var contentDone by remember { mutableStateOf(false) }
    // Continue stays disabled until the card entrance finishes so invisible
    // UI cannot be tapped mid-animation (alpha-0 layers still hit-test).
    var cardEntered by remember { mutableStateOf(false) }

    val step = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    val emblemBitmap =
        remember {
            BitmapFactory.decodeResource(context.resources, R.drawable.splash_emblem_alpha)
                .asImageBitmap()
        }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(armed) {
        if (!armed || showedWelcome) return@LaunchedEffect
        if (reducedMotion) {
            step.snapTo(1f)
            cardEntered = true
        } else {
            step.animateTo(1f, tween(640, delayMillis = 240, easing = EnterEasing))
            cardEntered = true
            delay(180)
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Welcome choreography lives in WelcomeStage; once its content is done,
    // fade the whole overlay away and hand control back to the app.
    LaunchedEffect(showedWelcome, armed, contentDone) {
        if (!armed || !showedWelcome || !contentDone) return@LaunchedEffect
        keyboard?.hide()
        overlayAlpha.animateTo(0f, tween(340, easing = LinearEasing))
        onFinished()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(900f)
                .background(Color(0xFF0B0614))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
    ) {
        // Soft brand lighting behind the whole composition.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            BrandPurple.copy(alpha = 0.16f),
                                            BrandPink.copy(alpha = 0.07f),
                                            BrandCyan.copy(alpha = 0.05f),
                                            Color.Transparent,
                                        ),
                                    center = Offset(size.width / 2f, size.height * 0.34f),
                                    radius = size.maxDimension * 0.9f,
                                ),
                        )
                    },
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .graphicsLayer { alpha = overlayAlpha.value },
            contentAlignment = Alignment.Center,
        ) {
            if (!showedWelcome) {
                NameCard(
                    step = { step.value },
                    nameInput = nameInput,
                    onNameChange = { nameInput = it.take(MAX_NAME_LENGTH) },
                    emblemBitmap = emblemBitmap,
                    focusRequester = focusRequester,
                    reducedMotion = reducedMotion,
                    enabled = cardEntered,
                    onContinue = {
                        val firstName =
                            nameInput
                                .trim()
                                .split(Regex("\\s+"))
                                .firstOrNull()
                                ?.take(MAX_NAME_LENGTH)
                                .orEmpty()
                        keyboard?.hide()
                        onNameSubmitted(firstName)
                        showedWelcome = true
                    },
                )
            } else {
                WelcomeStage(
                    name = nameInput.trim().split(Regex("\\s+")).firstOrNull().orEmpty(),
                    reducedMotion = reducedMotion,
                    emblemBitmap = emblemBitmap,
                    onContentDone = { contentDone = true },
                )
            }
        }
    }
}

/** Phase 1 — the "What should we call you?" card with the glowing Continue. */
@Composable
private fun NameCard(
    step: () -> Float,
    nameInput: String,
    onNameChange: (String) -> Unit,
    emblemBitmap: ImageBitmap,
    focusRequester: FocusRequester,
    reducedMotion: Boolean,
    enabled: Boolean,
    onContinue: () -> Unit,
) {
    val cardWidth = 340.dp
    val glowTransition = rememberInfiniteTransition(label = "continueGlow")

    // Gentle breathing glow for the Continue button; static for reduced motion.
    val glowAlpha: State<Float> =
        if (reducedMotion) {
            remember { mutableFloatStateOf(0.55f) }
        } else {
            glowTransition.animateFloat(
                0.35f,
                0.7f,
                infiniteRepeatable<Float>(tween(1700), RepeatMode.Reverse),
                label = "glowAlpha",
            )
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .padding(horizontal = 28.dp)
                .graphicsLayer { alpha = step() },
    ) {
        // Emblem with its brand halo, consistent with the splash silhouette.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(112.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                BrandPink.copy(alpha = 0.20f),
                                                BrandPurple.copy(alpha = 0.12f),
                                                BrandCyan.copy(alpha = 0.08f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = size.maxDimension * 0.55f,
                                    ),
                            )
                        },
            )
            Image(
                bitmap = emblemBitmap,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(84.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.onboarding_question),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_hint),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        Surface(
            shape = CircleShape,
            color = Color(0xFF181024),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier =
                Modifier
                    .width(cardWidth)
                    .height(54.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    tint = BrandPurple.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = nameInput,
                    onValueChange = onNameChange,
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        ),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { onContinue() }),
                    cursorBrush = Brush.verticalGradient(listOf(BrandPink, BrandCyan)),
                    decorationBox = { inner ->
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (nameInput.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.onboarding_first_name),
                                    color = Color.White.copy(alpha = 0.38f),
                                    fontSize = 15.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Glowing gradient Continue.
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .width(cardWidth)
                    .height(56.dp)
                    .drawBehind {
                        drawRect(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            BrandPurple.copy(alpha = 0.55f * glowAlpha.value),
                                            BrandPink.copy(alpha = 0.35f * glowAlpha.value),
                                            Color.Transparent,
                                        ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.maxDimension * 0.42f,
                                ),
                        )
                    }
                    .background(BrandGradient, RoundedCornerShape(50))
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onContinue,
                    ),
        ) {
            Text(
                text = stringResource(R.string.continue_action),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/**
 * Phase 2 — animated "Welcome to MELORA, [Name]!" that holds for a couple of
 * seconds, then reports completion via [onContentDone] so the overlay can
 * fade into the app.
 */
@Composable
private fun WelcomeStage(
    name: String,
    reducedMotion: Boolean,
    emblemBitmap: ImageBitmap,
    onContentDone: () -> Unit,
) {
    val titleAlpha = remember { Animatable(0f) }
    val titleRise = remember { Animatable(1f) }
    val taglineAlpha = remember { Animatable(0f) }
    val emblemAlpha = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            titleAlpha.snapTo(1f)
            titleRise.snapTo(0f)
            taglineAlpha.snapTo(1f)
            emblemAlpha.snapTo(1f)
            glow.snapTo(1f)
            delay(1500)
        } else {
            launch { emblemAlpha.animateTo(1f, tween(420)) }
            launch { glow.animateTo(1f, tween(600, delayMillis = 120)) }
            launch { titleRise.animateTo(0f, tween(640, delayMillis = 200, easing = EnterEasing)) }
            launch { titleAlpha.animateTo(1f, tween(520, delayMillis = 200)) }
            launch { taglineAlpha.animateTo(1f, tween(520, delayMillis = 560)) }
            delay(2450)
        }
        onContentDone()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = glow.value }
                        .drawBehind {
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                BrandPink.copy(alpha = 0.22f),
                                                BrandPurple.copy(alpha = 0.14f),
                                                BrandCyan.copy(alpha = 0.08f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = size.maxDimension * 0.55f,
                                    ),
                            )
                        },
            )
            Image(
                bitmap = emblemBitmap,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            alpha = emblemAlpha.value
                            compositingStrategy = CompositingStrategy.Offscreen
                        },
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text =
                if (name.isNotBlank()) {
                    stringResource(R.string.welcome_title_format, name)
                } else {
                    stringResource(R.string.welcome_title_plain)
                },
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .widthIn(max = 340.dp)
                    .graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleRise.value * 14.dp.toPx()
                    },
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.welcome_tagline),
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = taglineAlpha.value },
        )
    }
}
