package com.kirillrychkov.sashaspuzzles.game

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.app.AppModel
import com.kirillrychkov.sashaspuzzles.engine.SplitMix64
import com.kirillrychkov.sashaspuzzles.persistence.Achievement
import kotlinx.coroutines.launch
import com.kirillrychkov.sashaspuzzles.ui.Blob
import com.kirillrychkov.sashaspuzzles.ui.FittedLine
import com.kirillrychkov.sashaspuzzles.ui.PillButton
import com.kirillrychkov.sashaspuzzles.ui.PillStyle
import com.kirillrychkov.sashaspuzzles.ui.ProgressBar
import com.kirillrychkov.sashaspuzzles.ui.Theme
import com.kirillrychkov.sashaspuzzles.ui.TimeFormatting
import com.kirillrychkov.sashaspuzzles.ui.WashedMatrix
import com.kirillrychkov.sashaspuzzles.ui.substituted

/** Frosted backdrop shared by every overlay; it also swallows touches meant for the board. */
@Composable
private fun Backdrop() {
    Box(Modifier.fillMaxSize().background(Theme.colors.surface.copy(alpha = 0.86f))
        .clickable(remember { MutableInteractionSource() }, indication = null) {})
}

/**
 * Centres an overlay card and scrolls it when the screen is shorter than the
 * card — the Fold's cover screen in landscape is well under 400dp tall.
 */
@Composable
private fun FittedCard(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().heightIn(min = height), contentAlignment = Alignment.Center) { content() }
        }
    }
}

/** A short screen: the cards switch to their denser layout. */
@Composable
private fun isShort(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480

/** Shown while the picture is decoded and the pieces are cut. */
@Composable
fun LoadingOverlay(session: GameSession) {
    val colors = Theme.colors
    val progress = maxOf(0.02f, session.textures.progress)
    val shown by animateFloatAsState(progress, tween(250), label = "progress")
    Box(Modifier.fillMaxSize().background(colors.bg).clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center) {
        Blob(Modifier.offset((-160).dp, (-240).dp), size = 300.dp)
        Blob(Modifier.offset(160.dp, 240.dp), color = colors.blob2, size = 260.dp)
        Column(Modifier.widthIn(max = 440.dp).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(colors.surface, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(colors.accent, -90f, 360f * shown, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("${(progress * 100).toInt()}%", style = Theme.display(26), color = colors.text)
            }
            Text(pluralStringResource(R.plurals.cutting_x_pieces, session.pieceCount, session.pieceCount),
                style = Theme.display(28), color = colors.text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
            Text(stringResource(R.string.real_lock_geometry_every_cut_exists_once_so_the), style = Theme.body(15), color = colors.muted,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            ProgressBar(shown, Modifier.fillMaxWidth().padding(top = 22.dp), height = 10.dp)
            Text(stringResource(R.string.x_of_x, (progress * session.pieceCount).toInt(), session.pieceCount),
                style = Theme.body(13), color = colors.faint, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
}

@Composable
fun ErrorOverlay(onDismiss: () -> Unit) {
    val colors = Theme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Backdrop()
        Column(
            Modifier.padding(20.dp).shadow(16.dp, RoundedCornerShape(Theme.RADIUS_PANEL.dp))
                .background(colors.card, RoundedCornerShape(Theme.RADIUS_PANEL.dp)).padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.Warning, null, tint = colors.accent, modifier = Modifier.size(36.dp))
            Text(stringResource(R.string.something_went_wrong), style = Theme.display(26), color = colors.text, textAlign = TextAlign.Center)
            Text(stringResource(R.string.this_picture_could_not_be_loaded), style = Theme.body(15), color = colors.muted,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
            PillButton(stringResource(R.string.back_to_library), onClick = onDismiss)
        }
    }
}

@Composable
fun PauseOverlay(model: AppModel, session: GameSession) {
    val colors = Theme.colors
    val short = isShort()
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400
    Box(Modifier.fillMaxSize()) {
        Backdrop()
        FittedCard {
            Column(
                Modifier.padding(20.dp).widthIn(max = 420.dp).shadow(16.dp, RoundedCornerShape(Theme.RADIUS_PANEL.dp))
                    .background(colors.card, RoundedCornerShape(Theme.RADIUS_PANEL.dp)).padding(if (short) 24.dp else 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(if (short) 60.dp else 84.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Pause, null, tint = colors.onAccent, modifier = Modifier.size(34.dp))
                }
                Text(stringResource(R.string.break_label), style = Theme.display(30), color = colors.text, modifier = Modifier.padding(top = if (short) 10.dp else 16.dp))
                Text(stringResource(R.string.the_table_is_saved_come_back_whenever_you_like), style = Theme.body(15), color = colors.muted, textAlign = TextAlign.Center)
                Text(TimeFormatting.clock(session.elapsedMillis), style = Theme.display(if (short) 34 else 44), color = colors.text,
                    modifier = Modifier.padding(top = if (short) 8.dp else 14.dp))
                // A narrow screen with large text cannot fit both side by side: "Resume" shrank to a dot.
                if (narrow && !short) {
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(stringResource(R.string.resume), icon = Icons.Rounded.PlayArrow, expand = true) { session.resume() }
                        PillButton(stringResource(R.string.library), style = PillStyle.SECONDARY, expand = true) { model.showLibrary() }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = if (short) 16.dp else 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(stringResource(R.string.resume), Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, expand = true) { session.resume() }
                        PillButton(stringResource(R.string.library), style = PillStyle.SECONDARY) { model.showLibrary() }
                    }
                }
            }
        }
    }
}

@Composable
fun CompletionOverlay(model: AppModel, session: GameSession) {
    val colors = Theme.colors
    val short = isShort()
    // The Fold's cover screen and small phones: the card's insets and buttons tighten.
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val pop by animateFloatAsState(if (appeared) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "pop")

    // The nightmare medal falls in the dark and lands with a flash.
    val night = remember { androidx.compose.animation.core.Animatable(0f) }
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()

    val pieces = session.pieceCount
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val pace = String.format(locale, "%.1f", pieces / (maxOf(session.elapsedMillis, 60_000L) / 60_000.0))

    Box(Modifier.fillMaxSize()) {
        session.source?.let { source ->
            val image = remember(source) { source.asImageBitmap() }
            Image(image, null, Modifier.fillMaxSize().alpha(0.3f), contentScale = ContentScale.Crop, colorFilter = ColorFilter.colorMatrix(WashedMatrix))
        }
        Backdrop()
        Confetti()
        FittedCard {
            Column(
                Modifier.padding(if (short) 12.dp else 20.dp).widthIn(max = 560.dp)
                    .scale(0.9f + 0.1f * pop).alpha(pop.coerceIn(0f, 1f))
                    .shadow(16.dp, RoundedCornerShape(Theme.RADIUS_PANEL.dp))
                    .background(colors.card, RoundedCornerShape(Theme.RADIUS_PANEL.dp))
                    .padding(horizontal = if (narrow) 20.dp else if (short) 28.dp else 40.dp, vertical = if (short) 24.dp else if (narrow) 28.dp else 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val checkmark = @Composable {
                    val side = if (short) 56.dp else 104.dp
                    Box(Modifier.size(side).scale(0.4f + 0.6f * pop).rotate(-24f * (1 - pop)).shadow(10.dp, CircleShape).background(colors.accent, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = colors.onAccent, modifier = Modifier.size(side * 0.5f))
                    }
                }
                val title = @Composable { Text(stringResource(R.string.puzzle_solved), style = Theme.display(if (short) 28 else 38), color = colors.text, textAlign = TextAlign.Center) }
                val subtitle = @Composable {
                    Text(substituted(R.string.x_x_pieces, model.library.title(session.item), pieces, plural = mapOf(2 to R.plurals.x_x_pieces_arg2)),
                        style = Theme.body(if (short) 15 else 17), color = colors.muted, textAlign = TextAlign.Center)
                }
                if (short) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        checkmark()
                        Column { title(); subtitle() }
                    }
                } else {
                    checkmark()
                    Box(Modifier.padding(top = 22.dp)) { title() }
                    Box(Modifier.padding(top = 6.dp)) { subtitle() }
                }

                Row(Modifier.fillMaxWidth().padding(top = if (short) 16.dp else 26.dp), horizontalArrangement = Arrangement.spacedBy(if (narrow) 8.dp else 12.dp)) {
                    Stat(TimeFormatting.clock(session.elapsedMillis), stringResource(R.string.time_2), short)
                    val best = model.lastCompletion?.previousBestMillis
                    if (best != null && best - session.elapsedMillis >= 1000) {
                        Stat("−" + TimeFormatting.short(best - session.elapsedMillis), stringResource(R.string.faster_than_record), short, tinted = true)
                    } else {
                        Stat("$pieces", stringResource(R.string.pieces_2), short)
                    }
                    Stat(pace, stringResource(R.string.per_minute), short)
                }

                val news = model.lastCompletion?.newAchievements.orEmpty()
                if (news.isNotEmpty()) {
                    Box(Modifier.padding(top = if (short) 12.dp else 18.dp)) {
                        AchievementReveal(model, news, short,
                            onDrop = { if (it == Achievement.NIGHTMARE) scope.launch { night.animateTo(0.6f, tween(400)) } },
                            onLand = {
                                if (it == Achievement.NIGHTMARE) scope.launch {
                                    night.snapTo(0f)
                                    flash.snapTo(0.75f)
                                    flash.animateTo(0f, tween(800))
                                }
                            })
                    }
                }

                if (narrow && !short) {
                    Column(Modifier.fillMaxWidth().padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(stringResource(R.string.play_again), expand = true) { model.restartCurrent() }
                        PillButton(stringResource(R.string.library), style = PillStyle.GHOST, expand = true) { model.showLibrary() }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = if (short) 18.dp else 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(stringResource(R.string.play_again), Modifier.weight(1f), expand = true) { model.restartCurrent() }
                        PillButton(stringResource(R.string.library), Modifier.weight(1f), style = PillStyle.GHOST, expand = true) { model.showLibrary() }
                    }
                }
            }
        }
        if (night.value > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = night.value)))
        if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(value: String, title: String, short: Boolean, tinted: Boolean = false) {
    val colors = Theme.colors
    Column(
        Modifier.weight(1f).background(if (tinted) colors.sageTint else colors.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = if (short) 10.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = if (tinted) colors.onSageTint else colors.text
        // Large system text in a third of a narrow card shrinks rather than cuts.
        FittedLine(value, Theme.display(24), color, minScale = 0.4f)
        FittedLine(title, Theme.body(12), color, minScale = 0.6f)
    }
}

/** A few dozen paper pieces drifting down, in the palette. */
@Composable
private fun Confetti() {
    val colors = Theme.colors
    val palette = listOf(colors.accent, colors.sage, colors.accentSoft, colors.sageSoft)
    val pieces = remember {
        val rng = SplitMix64(7uL)
        List(28) {
            ConfettiPiece(rng.unit().toFloat(), 10f + rng.unit().toFloat() * 8, rng.unit() * 3, 3 + rng.unit() * 1.4,
                rng.unit() > 0.5, (rng.unit() * 4).toInt())
        }
    }
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { now = SystemClock.uptimeMillis() } }
    Canvas(Modifier.fillMaxSize()) {
        val seconds = now / 1000.0
        for (piece in pieces) {
            val t = (((seconds + piece.delay) % piece.duration) / piece.duration).toFloat()
            val y = -40.dp.toPx() + t * (size.height + 80.dp.toPx())
            val w = piece.size.dp.toPx()
            val h = if (piece.round) w else w * 1.5f
            val x = piece.x * size.width
            val alpha = minOf(1f, t / 0.1f, (1 - t) / 0.3f).coerceIn(0f, 1f)
            translate(x, y) {
                rotate(t * 520f, pivot = Offset.Zero) {
                    if (piece.round) drawOval(palette[piece.color], Offset(-w / 2, -h / 2), Size(w, h), alpha = alpha)
                    else drawRoundRect(palette[piece.color], Offset(-w / 2, -h / 2), Size(w, h), CornerRadius(4.dp.toPx()), alpha = alpha)
                }
            }
        }
    }
}

private class ConfettiPiece(val x: Float, val size: Float, val delay: Double, val duration: Double, val round: Boolean, val color: Int)
