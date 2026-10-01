package com.kirillrychkov.sashaspuzzles.game

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.app.AppModel
import com.kirillrychkov.sashaspuzzles.engine.SplitMix64
import com.kirillrychkov.sashaspuzzles.library.LibraryThumbnail
import com.kirillrychkov.sashaspuzzles.persistence.Achievement
import com.kirillrychkov.sashaspuzzles.support.Feedback
import com.kirillrychkov.sashaspuzzles.ui.Kicker
import com.kirillrychkov.sashaspuzzles.ui.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** "Remove animations" in the system's accessibility settings. */
@Composable
fun reduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** "How it reads": the title, then what earns it. */
@Composable
fun Achievement.detailText(): String =
    category?.let { stringResource(detail, stringResource(it.title)) } ?: stringResource(detail)

private val Achievement.icon: ImageVector
    get() = when (this) {
        Achievement.TEN_PUZZLES -> Icons.Rounded.Star
        Achievement.FIFTY_PUZZLES -> Icons.Rounded.EmojiEvents
        Achievement.NIGHTMARE -> Icons.Rounded.NightsStay
        Achievement.WEEK_STREAK -> Icons.Rounded.LocalFireDepartment
        else -> Icons.Rounded.Favorite
    }

/** The night-sky disc of the hardest achievement, as in its Game Center art. */
private val Night = Color(0.29f, 0.23f, 0.2f)

/**
 * An achievement's medal, drawn like the iOS one: the number for a milestone,
 * a stopwatch or a motif, or the category's photograph.
 *
 * Raising [celebration] plays the medal's own flourish once — the number counts
 * up, the stopwatch hand sweeps, the photograph settles — with a shine across
 * the disc. [armed] shows the starting frame of that flourish (a zero, the hand
 * at twelve) for a medal that is about to celebrate.
 */
@Composable
fun AchievementBadge(model: AppModel, achievement: Achievement, size: Dp = 48.dp, celebration: Int = 0, armed: Boolean = false) {
    val colors = Theme.colors
    val reduce = reduceMotion()
    val isArmed = armed && !reduce
    val target = achievement.count
    var count by remember { mutableIntStateOf(if (isArmed) 0 else target ?: 0) }
    val hand = remember { Animatable(if (isArmed) 0f else 30f) }
    val zoom = remember { Animatable(1f) }
    val shine = remember { Animatable(-1f) }
    val bounce = remember { Animatable(0f) }

    LaunchedEffect(celebration) {
        if (celebration <= 0 || reduce) return@LaunchedEffect
        if (target != null) launch {
            val step = max(1, target / 25)
            val pause = if (target > 1) 600L / (target / step) else 200L
            for (value in 0..target step step) {
                count = value
                delay(pause)
            }
            count = target
        }
        if (achievement == Achievement.SPRINTER) launch {
            hand.snapTo(0f)
            hand.animateTo(390f, tween(1100, easing = FastOutSlowInEasing))
        }
        if (achievement.coverStem != null) launch {
            zoom.snapTo(1.4f)
            zoom.animateTo(1f, tween(1400, easing = LinearOutSlowInEasing))
        }
        if (target == null && achievement != Achievement.SPRINTER && achievement.coverStem == null) launch {
            repeat(if (achievement == Achievement.WEEK_STREAK) 3 else 2) {
                bounce.animateTo(1f, tween(140))
                bounce.animateTo(0f, spring(dampingRatio = 0.5f))
            }
        }
        launch {
            delay(200)
            shine.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
            shine.snapTo(-1f)
        }
    }

    val fill = when {
        achievement.category != null -> colors.sage
        achievement == Achievement.NIGHTMARE -> Night
        else -> colors.accent
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(fill)
            .drawWithContent {
                drawContent()
                if (shine.value > -1f) {
                    val band = this.size.width * 0.45f
                    val x = center.x + shine.value * this.size.width * 1.1f
                    rotate(20f) {
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f)),
                                startX = x - band / 2, endX = x + band / 2),
                            topLeft = Offset(x - band / 2, -this.size.height), size = Size(band, this.size.height * 3),
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val cover = achievement.coverStem?.let { model.library.item("bundled.$it") }
        when {
            target != null -> {
                // Sized in dp, not sp: large system text must not burst the disc.
                val fontSize = with(LocalDensity.current) { (size * if (target >= 10) 0.42f else 0.52f).toSp() }
                Text("$count", style = Theme.display(20).copy(fontSize = fontSize), color = colors.onAccent,
                    modifier = Modifier.graphicsLayer { translationY = size.toPx() * 0.02f })
            }
            achievement == Achievement.SPRINTER -> Stopwatch(hand.value, colors.onAccent, Modifier.fillMaxSize())
            cover != null -> {
                LibraryThumbnail(model, cover, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }, longSide = 240)
                Box(Modifier.fillMaxSize().border(max(1.5f, size.value * 0.035f).dp, Color.White.copy(alpha = 0.75f), CircleShape))
            }
            else -> Icon(achievement.icon, null, tint = colors.onAccent,
                modifier = Modifier.size(size * 0.46f).graphicsLayer { translationY = -bounce.value * size.toPx() * 0.14f })
        }
    }
}

/** A five-minute dial: the hand rests on five. */
@Composable
private fun Stopwatch(hand: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.width
        val line = s * 0.06f
        drawCircle(color, radius = s * 0.3f - line / 2, style = Stroke(line))
        drawRoundRect(color, Offset(center.x - s * 0.07f, center.y - s * 0.36f - s * 0.04f), Size(s * 0.14f, s * 0.08f), CornerRadius(s * 0.02f))
        rotate(hand, pivot = center) {
            val w = s * 0.07f
            drawRoundRect(color, Offset(center.x - w / 2, center.y - s * 0.1f - s * 0.12f), Size(w, s * 0.24f), CornerRadius(w / 2))
        }
        drawCircle(color, radius = s * 0.055f)
    }
}

/** Soft pulsing ring around a medal the player has not looked at yet. */
@Composable
fun NewMedalGlow(content: @Composable () -> Unit) {
    val color = Theme.colors.accent
    val still = reduceMotion()
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        0f, if (still) 0f else 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    Box(Modifier.drawBehind {
        val radius = (size.minDimension / 2 + 6.dp.toPx()) * (0.92f + 0.2f * pulse)
        drawCircle(color.copy(alpha = 0.35f * (0.9f - 0.65f * pulse)), radius)
    }) { content() }
}

/**
 * Little paper jigsaw pieces flung out of a medal as it lands, then falling
 * away. Draws nothing once it is over.
 */
@Composable
private fun PieceBurst(start: Long, seed: Int, reach: Dp, modifier: Modifier) {
    val colors = Theme.colors
    val palette = listOf(colors.accent, colors.sage, colors.accentSoft, colors.sageSoft, colors.accentDeep)
    val bits = remember(seed) {
        val rng = SplitMix64(seed.toULong() * 0x9E3779B9uL + 11uL)
        // Evenly around the circle with a little jitter, so no side is left empty.
        List(22) { index ->
            BurstBit((index + rng.unit() * 0.7) / 22 * 2 * PI, 0.55 + rng.unit() * 0.6, (rng.unit() - 0.5) * 14,
                8 + rng.unit() * 7, (rng.unit() * 5).toInt())
        }
    }
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(start) {
        while (SystemClock.uptimeMillis() - start < BURST_MILLIS) withFrameMillis { now = SystemClock.uptimeMillis() }
        now = SystemClock.uptimeMillis()
    }
    Canvas(modifier) {
        val t = (now - start) / 1000.0
        if (t < 0 || t * 1000 >= BURST_MILLIS) return@Canvas
        val progress = t * 1000 / BURST_MILLIS
        val flight = 1 - Math.pow(1 - min(1.0, t / 0.6), 3.0)
        val reachPx = reach.toPx()
        for (bit in bits) {
            val travel = reachPx * bit.speed * flight
            val x = center.x + cos(bit.angle) * travel
            val y = center.y + sin(bit.angle) * travel + 70.dp.toPx() * t * t
            val scale = (bit.size.dp.toPx() * (1 - 0.35 * progress)).toFloat()
            withTransform({
                translate(x.toFloat(), y.toFloat())
                rotate(Math.toDegrees(bit.angle + bit.spin * t).toFloat(), pivot = Offset.Zero)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawPath(PiecePath, palette[bit.color], alpha = (1 - progress * progress).toFloat().coerceIn(0f, 1f))
            }
        }
    }
}

private class BurstBit(val angle: Double, val speed: Double, val spin: Double, val size: Double, val color: Int)

private const val BURST_MILLIS = 1300L

/** A unit jigsaw piece with tabs on the top and right. */
private val PiecePath = Path().apply {
    addRoundRect(RoundRect(-0.5f, -0.5f, 0.5f, 0.5f, CornerRadius(0.12f)))
    addOval(Rect(-0.18f, -0.84f, 0.18f, -0.48f))
    addOval(Rect(0.48f, -0.18f, 0.84f, 0.18f))
}

/**
 * New achievements on the completion card. The medals drop in one after
 * another; each lands with a burst of pieces, a chime and its own flourish,
 * and the caption follows the medal that landed last. [onDrop] and [onLand]
 * are for effects that belong to the whole screen rather than the card.
 */
@Composable
fun AchievementReveal(
    model: AppModel,
    achievements: List<Achievement>,
    compact: Boolean,
    onDrop: (Achievement) -> Unit = {},
    onLand: (Achievement) -> Unit = {},
) {
    val colors = Theme.colors
    val reduce = reduceMotion()
    val badge = if (compact) 36.dp else 48.dp
    var visible by remember { mutableStateOf(false) }
    // Only medals that have arrived take room, so the caption moves aside as each lands.
    var arrived by remember { mutableIntStateOf(0) }
    var landed by remember { mutableIntStateOf(0) }
    val bursts = remember { mutableStateMapOf<Int, Long>() }
    val appear by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "reveal")

    LaunchedEffect(Unit) {
        // After the card's own entrance, so the two do not compete.
        delay(900)
        for ((index, achievement) in achievements.withIndex()) {
            if (!reduce) {
                onDrop(achievement)
                if (achievement == Achievement.NIGHTMARE) delay(450)
            }
            visible = true
            arrived = index + 1
            delay(280)
            landed = index + 1
            model.feedback.play(Feedback.Tone.ACHIEVEMENT, model.settings)
            model.feedback.impact(Feedback.Strength.STRONG, model.settings)
            if (!reduce) {
                bursts[index] = SystemClock.uptimeMillis()
                onLand(achievement)
            }
            delay(1400)
            bursts.remove(index)
        }
    }

    val description = achievements.map { stringResource(R.string.x_x_2, stringResource(it.title), it.detailText()) }.joinToString(", ")
    val medals = @Composable {
        Row(Modifier.heightIn(min = badge), horizontalArrangement = Arrangement.spacedBy(-badge * 0.2f), verticalAlignment = Alignment.CenterVertically) {
            for (index in 0 until arrived) {
                key(achievements[index]) {
                    FallingMedal(badge, reduce, Modifier.zIndex(index.toFloat())) {
                        Box(Modifier.shadow(4.dp, CircleShape)) {
                            AchievementBadge(model, achievements[index], badge, celebration = if (index < landed) 1 else 0, armed = true)
                        }
                        bursts[index]?.let { start ->
                            PieceBurst(start, index + 1, badge * 2.2f, Modifier.align(Alignment.Center).requiredSize(badge * 5))
                        }
                    }
                }
            }
        }
    }
    val caption = @Composable { modifier: Modifier ->
        val current = achievements[max(0, landed - 1)]
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Kicker(stringResource(R.string.new_achievement), color = colors.accentDeep)
            AnimatedContent(current, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith fadeOut() }, label = "caption") { shown ->
                Text(stringResource(R.string.x_x_2, stringResource(shown.title), shown.detailText()),
                    style = Theme.body(17, FontWeight.Bold), color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().graphicsLayer { alpha = appear }
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 8.dp else 14.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        // Beside three medals a narrow card leaves the caption a sliver; it goes underneath.
        if (maxWidth < 300.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                medals()
                caption(Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                medals()
                caption(Modifier.weight(1f))
            }
        }
    }
}

/** A medal on its way down: it starts above its place, larger and invisible, and springs in. */
@Composable
private fun FallingMedal(badge: Dp, reduce: Boolean, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val fall = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        fall.animateTo(0f, if (reduce) tween(300) else spring(dampingRatio = 0.6f, stiffness = 224f))
    }
    Box(
        modifier.size(badge).graphicsLayer {
            val f = fall.value
            if (reduce) {
                alpha = 1 - f
            } else {
                scaleX = 1 + 0.9f * f
                scaleY = 1 + 0.9f * f
                translationY = -badge.toPx() * 2.4f * f
                alpha = (1 - f).coerceIn(0f, 1f)
            }
        },
        content = content,
    )
}
