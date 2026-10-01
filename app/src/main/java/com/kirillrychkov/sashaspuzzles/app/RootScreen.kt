package com.kirillrychkov.sashaspuzzles.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.game.GameScreen
import com.kirillrychkov.sashaspuzzles.game.SetupScreen
import com.kirillrychkov.sashaspuzzles.library.HomeScreen
import com.kirillrychkov.sashaspuzzles.library.ProfileScreen
import com.kirillrychkov.sashaspuzzles.settings.AppSettings
import com.kirillrychkov.sashaspuzzles.settings.SettingsScreen
import com.kirillrychkov.sashaspuzzles.ui.PuzzleTheme
import com.kirillrychkov.sashaspuzzles.ui.Theme
import com.kirillrychkov.sashaspuzzles.ui.Blob
import com.kirillrychkov.sashaspuzzles.ui.markPath
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.delay

@Composable
fun RootScreen(model: AppModel, skipSplash: Boolean = false) {
    val dark = when (model.settings.appearance) {
        AppSettings.Appearance.SYSTEM -> isSystemInDarkTheme()
        AppSettings.Appearance.LIGHT -> false
        AppSettings.Appearance.DARK -> true
    }
    val view = LocalView.current
    val activity = androidx.activity.compose.LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    // The library tune while a picture is chosen, the board tune once the
    // game is on screen, silence in the background.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val route = model.route
    val music = when {
        !active -> null
        route is AppModel.Route.Game -> model.settings.boardMusic.track
        else -> com.kirillrychkov.sashaspuzzles.support.Feedback.Music.LIBRARY
    }
    LaunchedEffect(music, model.settings.musicEnabled) { model.feedback.setMusic(music, model.settings) }

    var showSplash by rememberSaveable { mutableStateOf(!skipSplash) }

    PuzzleTheme(dark) {
        val colors = Theme.colors
        CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxSize().background(colors.bg)) {
                BackHandler(enabled = route != AppModel.Route.Home || model.sheet == AppModel.Sheet.SETTINGS || model.sheet == AppModel.Sheet.PROFILE) { model.back() }

                AnimatedContent(
                    targetState = route,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    contentKey = { it::class },
                    label = "route",
                ) { target ->
                    when (target) {
                        AppModel.Route.Home -> HomeScreen(model)
                        is AppModel.Route.Setup -> model.library.item(target.itemId)?.let { SetupScreen(model, it) }
                        AppModel.Route.Game -> model.session?.let { GameScreen(model, it) }
                    }
                }

                AnimatedVisibility(model.sheet == AppModel.Sheet.PROFILE, enter = fadeIn(), exit = fadeOut()) {
                    ProfileScreen(model) { model.sheet = null }
                }
                AnimatedVisibility(model.sheet == AppModel.Sheet.SETTINGS, enter = fadeIn(), exit = fadeOut()) {
                    SettingsScreen(model) { model.sheet = null }
                }
                AnimatedVisibility(model.sheet == AppModel.Sheet.ONBOARDING, enter = fadeIn(), exit = fadeOut()) {
                    OnboardingScreen(model) {
                        model.settings.hasSeenOnboarding = true
                        model.sheet = null
                    }
                }

                AnimatedVisibility(showSplash, enter = fadeIn(), exit = fadeOut(tween(350))) {
                    SplashScreen {
                        showSplash = false
                        if (!model.settings.hasSeenOnboarding) model.sheet = AppModel.Sheet.ONBOARDING
                    }
                }
            }
        }
    }
}

/** First frame: the mark assembles from four pieces of the puzzle's own geometry, then the name rises. */
@Composable
private fun SplashScreen(onFinish: () -> Unit) {
    val colors = Theme.colors
    var assembled by remember { mutableStateOf(false) }
    var named by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        assembled = true
        delay(650)
        named = true
        delay(900)
        onFinish()
    }
    val flyIn = listOf(-90f to -70f, 96f to -58f, -78f to 88f, 104f to 76f)
    val spin = listOf(-28f, 24f, 20f, -22f)
    val fills = listOf(colors.accent, colors.accentSoft, colors.sage, colors.sageSoft)
    val progress = (0 until 4).map { index ->
        animateFloatAsState(
            if (assembled) 1f else 0f,
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
            label = "piece$index",
        ).value
    }
    val nameAlpha by animateFloatAsState(if (named) 1f else 0f, tween(450), label = "name")

    Box(Modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
        Blob(Modifier.offset((-140).dp, (-260).dp), size = 280.dp)
        Blob(Modifier.offset(160.dp, 200.dp).alpha(0.8f), color = colors.blob2, size = 230.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Canvas(Modifier.size(128.dp)) {
                for (index in 0 until 4) {
                    val t = progress[index]
                    val (dx, dy) = flyIn[index]
                    val path = markPath(index, size.width)
                    translate((1 - t) * dx.dp.toPx(), (1 - t) * dy.dp.toPx()) {
                        drawContext.transform.rotate(spin[index] * (1 - t), center)
                        translate(0f, 5.dp.toPx()) { drawPath(path, Color.Black.copy(alpha = 0.12f * t)) }
                        drawPath(path, fills[index], alpha = t.coerceIn(0f, 1f))
                        drawContext.transform.rotate(-spin[index] * (1 - t), center)
                    }
                }
            }
            Column(
                Modifier.graphicsLayer { alpha = nameAlpha; translationY = (1 - nameAlpha) * 14.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.sashas_puzzles), style = Theme.display(32), color = colors.text)
                Text(stringResource(R.string.real_locks_a_warm_table), style = Theme.body(14), color = colors.faint)
            }
        }
    }
}
