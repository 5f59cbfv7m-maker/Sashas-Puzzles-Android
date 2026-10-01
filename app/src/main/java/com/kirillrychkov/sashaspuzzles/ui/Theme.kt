package com.kirillrychkov.sashaspuzzles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.engine.PuzzleGeometry
import com.kirillrychkov.sashaspuzzles.render.PieceTextures

/**
 * The "Organic" design tokens from the iOS app: a cream-and-sand ground, a
 * terracotta accent, a sage second voice, Caprasimo display type and
 * over-rounded shapes. The dark values are the light ramps' lighter steps, so
 * the accents do not dim at night.
 */
@Immutable
data class Palette(
    val bg: Color, val surface: Color, val card: Color, val text: Color, val muted: Color, val faint: Color,
    val track: Color, val chip: Color, val blob: Color, val blob2: Color,
    val accent: Color, val accentSoft: Color, val onAccent: Color, val accentDeep: Color,
    val sage: Color, val sageSoft: Color, val onSage: Color, val sageTint: Color, val onSageTint: Color,
    val isDark: Boolean,
) {
    val hairline: Color get() = text.copy(alpha = 0.08f)

    companion object {
        private fun hex(value: Long) = Color(0xFF000000 or value)

        val light = Palette(
            bg = hex(0xf5ead8), surface = hex(0xebddc5), card = hex(0xf9f4ed), text = hex(0x201e1d),
            muted = hex(0x645c50), faint = hex(0x82796a), track = hex(0xdcd3c4), chip = hex(0xebddc5),
            blob = hex(0xeee7db), blob2 = hex(0xf0fae1),
            accent = hex(0xc67139), accentSoft = hex(0xd67f48), onAccent = hex(0xf5ead8), accentDeep = hex(0x8c491a),
            sage = hex(0x7a8a5e), sageSoft = hex(0x8fa073), onSage = hex(0xf9f4ed), sageTint = hex(0xe1eecc),
            onSageTint = hex(0x56633f), isDark = false,
        )
        val dark = Palette(
            bg = hex(0x2e2b25), surface = hex(0x3d3a32), card = hex(0x474238), text = hex(0xf9f4ed),
            muted = hex(0xc0b6a5), faint = hex(0xa19786), track = hex(0x5b564b), chip = hex(0x524d43),
            blob = hex(0x3d3a32), blob2 = hex(0x3d472b),
            accent = hex(0xf6a06b), accentSoft = hex(0xd67f48), onAccent = hex(0x402310), accentDeep = hex(0xffc6a5),
            sage = hex(0xaebf92), sageSoft = hex(0x8fa073), onSage = hex(0x272e1b), sageTint = hex(0x3d472b),
            onSageTint = hex(0xf0fae1), isDark = true,
        )
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.light }
/** Phone-sized layouts: the Fold's cover screen and ordinary phones, held either way. */
val LocalCompact = staticCompositionLocalOf { true }

object Theme {
    val colors: Palette @Composable get() = LocalPalette.current
    const val RADIUS_CARD = 24
    const val RADIUS_PANEL = 28

    /**
     * Caprasimo and Figtree, as on iOS. Neither has Cyrillic or CJK; Android
     * falls back glyph by glyph to the system face, so Russian stays readable.
     */
    var displayFamily: FontFamily = FontFamily(Font(R.font.caprasimo, FontWeight.ExtraBold))
        private set

    /**
     * Android draws the glyphs a custom font lacks with the regular system face,
     * whatever weight was asked for. From Android 10 the fallback can be named
     * outright: Caprasimo first, then the system sans at weight 900, which is
     * what SF Rounded Heavy is to the iOS app.
     */
    fun install(context: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT < 29) return
        runCatching {
            val font = android.graphics.fonts.Font.Builder(context.resources, R.font.caprasimo).build()
            val typeface = android.graphics.Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(font).build())
                .setSystemFallback("sans-serif")
                .setStyle(android.graphics.fonts.FontStyle(900, android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT))
                .build()
            displayFamily = FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
        }
    }

    private val bodyFamilies = HashMap<Int, FontFamily>()

    fun bodyFamily(weight: FontWeight): FontFamily = bodyFamilies.getOrPut(weight.weight) {
        FontFamily(Font(R.font.figtree, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))))
    }

    /**
     * Caprasimo is registered at ExtraBold so the style can ask for that
     * weight without faking it: Caprasimo draws Latin as is, and the system
     * fallback that draws Cyrillic comes out heavy too, as SF Rounded Heavy does on iOS.
     */
    fun display(size: Int): TextStyle =
        TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, lineHeight = (size * 1.18).sp)

    fun body(size: Int, weight: FontWeight = FontWeight.Normal): TextStyle =
        TextStyle(fontFamily = bodyFamily(weight), fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.3).sp)
}

/** Picks the palette and the compact flag for everything below it. */
@Composable
fun PuzzleTheme(dark: Boolean, content: @Composable () -> Unit) {
    BoxWithConstraints {
        CompositionLocalProvider(
            LocalPalette provides if (dark) Palette.dark else Palette.light,
            LocalCompact provides (maxWidth < 600.dp || maxHeight < 480.dp),
        ) { content() }
    }
}

/** Pressed state from the ramp: a touch smaller, a step darker. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .scale(if (pressed) 0.96f else 1f)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** A circular icon button: the design's chip. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.NEUTRAL,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Theme.colors
    val (background, foreground) = when (style) {
        ChipStyle.NEUTRAL -> c.chip to c.muted
        ChipStyle.CARD -> c.card to c.muted
        ChipStyle.SAGE -> c.sageTint to c.onSageTint
        ChipStyle.ACCENT -> c.accent to c.onAccent
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .pressable(enabled, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.background(c.bg.copy(alpha = 0.45f))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = foreground, modifier = Modifier.size(size * 0.46f))
    }
}

enum class ChipStyle { NEUTRAL, CARD, SAGE, ACCENT }
enum class PillStyle { PRIMARY, SECONDARY, GHOST, SAGE }

/** Capsule button. `PRIMARY` is solid terracotta with display type. */
@Composable
fun PillButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: PillStyle = PillStyle.PRIMARY,
    size: Int = 17,
    expand: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Theme.colors
    val background = when (style) {
        PillStyle.PRIMARY -> c.accent
        PillStyle.SECONDARY -> c.card
        PillStyle.GHOST -> c.bg
        PillStyle.SAGE -> c.sage
    }
    val foreground = when (style) {
        PillStyle.PRIMARY -> c.onAccent
        PillStyle.SECONDARY, PillStyle.GHOST -> c.text
        PillStyle.SAGE -> c.onSage
    }
    val textStyle = if (style == PillStyle.PRIMARY || style == PillStyle.SAGE) Theme.display(size) else Theme.body(size - 2, FontWeight.Bold)
    Row(
        modifier
            .then(if (expand) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = (size * 2.7).dp)
            .clip(CircleShape)
            .background(background)
            .then(if (style == PillStyle.GHOST) Modifier.border(1.dp, c.text.copy(alpha = 0.16f), CircleShape) else Modifier)
            .pressable(enabled, onClick = onClick)
            .padding(horizontal = (size * 1.4).dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = foreground, modifier = Modifier.size((size * 1.05).dp))
            Box(Modifier.width(8.dp))
        }
        // Large system text in a narrow pill shrinks a little before it truncates.
        FittedLine(title, textStyle, foreground.copy(alpha = if (enabled) 1f else 0.45f), Modifier.weight(1f, fill = false), minScale = 0.65f)
    }
}

/** Small uppercase section label. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = Theme.body(12, FontWeight.Bold).copy(letterSpacing = 1.2.sp), color = Theme.colors.muted)
}

enum class TagStyle { SAGE, SAGE_TINT, CARD }

/** Tinted capsule tag ("Daily puzzle", a category). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, style: TagStyle = TagStyle.SAGE_TINT) {
    val c = Theme.colors
    val (background, foreground) = when (style) {
        TagStyle.SAGE -> c.sage to c.onSage
        TagStyle.CARD -> c.card to c.accentDeep
        TagStyle.SAGE_TINT -> c.sageTint to c.onSageTint
    }
    Row(
        modifier.clip(CircleShape).background(background).padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = foreground, modifier = Modifier.size(13.dp))
            Box(Modifier.width(5.dp))
        }
        Text(text, style = Theme.body(12, FontWeight.Bold), color = foreground, maxLines = 1)
    }
}

/** A row of capsules, one selected — the design's segmented control. */
@Composable
fun <T> PillSegments(options: List<T>, selection: T, title: @Composable (T) -> String, expand: Boolean = false, onSelect: (T) -> Unit) {
    val c = Theme.colors
    Row(
        Modifier
            .then(if (expand) Modifier.fillMaxWidth() else Modifier)
            .clip(CircleShape).background(c.surface).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (option in options) {
            val selected = option == selection
            Box(
                Modifier
                    .then(if (expand) Modifier.weight(1f) else Modifier)
                    .clip(CircleShape)
                    .background(if (selected) c.accent else Color.Transparent)
                    .selectable(selected, onClick = { onSelect(option) }, role = Role.RadioButton)
                    .padding(horizontal = if (expand) 6.dp else 13.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                FittedLine(title(option), Theme.body(13, if (selected) FontWeight.Bold else FontWeight.SemiBold),
                    if (selected) c.onAccent else c.muted, minScale = 0.7f)
            }
        }
    }
}

/** Sage progress bar on a track. */
@Composable
fun ProgressBar(value: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val c = Theme.colors
    Canvas(modifier.height(height)) {
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        drawRoundRect(c.track, cornerRadius = radius)
        drawRoundRect(c.sage, size = size.copy(width = size.width * value.coerceIn(0f, 1f)), cornerRadius = radius)
    }
}

/** Faint lattice showing how a picture will be divided. */
@Composable
fun LatticeOverlay(columns: Int, rows: Int, modifier: Modifier = Modifier, opacity: Float = 0.42f) {
    val color = Theme.colors.card.copy(alpha = opacity)
    Canvas(modifier) {
        for (column in 1 until columns) {
            val x = size.width * column / columns
            drawLine(color, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }
        for (row in 1 until rows) {
            val y = size.height * row / rows
            drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
    }
}

/** Soft circular decoration behind a page. */
@Composable
fun Blob(modifier: Modifier = Modifier, color: Color = Theme.colors.blob, size: Dp = 280.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** The app mark: a 2×2 of the engine's own lock geometry, so the logo is literally four pieces. */
@Composable
fun PuzzleMark(modifier: Modifier = Modifier, size: Dp = 128.dp, colors: List<Color>? = null, shadow: Boolean = true) {
    val c = Theme.colors
    val fills = colors ?: listOf(c.accent, c.accentSoft, c.sage, c.sageSoft)
    Canvas(modifier.size(size)) {
        val paths = (0 until 4).map { markPath(it, this.size.width) }
        // All shadows first: a shadow drawn after a neighbour would darken its tab.
        if (shadow) for (path in paths) translate(0f, 5.dp.toPx()) { drawPath(path, Color.Black.copy(alpha = 0.10f)) }
        for ((index, path) in paths.withIndex()) drawPath(path, fills[index])
    }
}

private val markGeometry = PuzzleGeometry(2, 2, 1.0, 0x5A5AuL)

/** One of the four mark pieces, scaled into a square of side `side`. */
fun markPath(index: Int, side: Float): Path {
    val scale = side / (markGeometry.boardSize.width * 1.28).toFloat()
    val inset = (side - markGeometry.boardSize.width.toFloat() * scale) / 2
    val path = PieceTextures.path(markGeometry.outline(index))
    path.transform(android.graphics.Matrix().apply { setScale(scale, scale); postTranslate(inset, inset) })
    return path.asComposePath()
}

/** The design's `.washed` treatment: desaturated, lower contrast, lifted. */
val WashedMatrix: ColorMatrix = ColorMatrix().apply {
    setToSaturation(0.72f)
    // contrast 0.9 around mid-grey, then +0.04 brightness
    val contrast = 0.9f
    val shift = (1 - contrast) * 0.5f * 255 + 0.04f * 255
    val m = ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, shift,
        0f, contrast, 0f, 0f, shift,
        0f, 0f, contrast, 0f, shift,
        0f, 0f, 0f, 1f, 0f,
    ))
    timesAssign(m)
}

/** Surface-filled content card with the design's soft shadow. */
fun Modifier.card(color: Color, radius: Dp = Theme.RADIUS_CARD.dp, shape: Shape = RoundedCornerShape(radius)): Modifier =
    this.shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.18f))
        .clip(shape)
        .background(color)

/** One line that shrinks down to `minScale` of its size before it truncates, like iOS's minimumScaleFactor. */
@Composable
fun FittedLine(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, minScale: Float = 0.7f) {
    androidx.compose.foundation.text.BasicText(
        text, modifier, style = style.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
            minFontSize = style.fontSize * minScale, maxFontSize = style.fontSize, stepSize = 1.sp),
    )
}

/** Keeps the text from pushing a fixed-width row wider than the screen. */
@Composable
fun OneLine(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
