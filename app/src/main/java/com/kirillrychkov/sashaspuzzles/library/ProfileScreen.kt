package com.kirillrychkov.sashaspuzzles.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.app.AppModel
import com.kirillrychkov.sashaspuzzles.game.AchievementBadge
import com.kirillrychkov.sashaspuzzles.game.NewMedalGlow
import com.kirillrychkov.sashaspuzzles.game.detailText
import com.kirillrychkov.sashaspuzzles.persistence.Achievement
import com.kirillrychkov.sashaspuzzles.ui.LocalCompact
import com.kirillrychkov.sashaspuzzles.ui.FittedLine
import com.kirillrychkov.sashaspuzzles.ui.PillButton
import com.kirillrychkov.sashaspuzzles.ui.RoundIconButton
import com.kirillrychkov.sashaspuzzles.ui.Theme
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

/** Profile and statistics: the name, totals, the weekly chart and the achievement wall. */
@Composable
fun ProfileScreen(model: AppModel, onDone: () -> Unit) {
    val colors = Theme.colors
    // Looked at: the "New" marks stay for this visit and go after it.
    DisposableEffect(Unit) { onDispose { model.markAchievementsSeen() } }

    Box(
        Modifier.fillMaxSize().background(colors.bg).clickable(remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        val compact = LocalCompact.current
        val gutter = if (compact) 18.dp else 30.dp
        Column(
            Modifier.widthIn(max = 980.dp).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = gutter, end = gutter, top = if (compact) 16.dp else 26.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Header(model, compact, onDone)
            Tiles(model, compact)
            Chart(model)
            Achievements(model, compact)
        }
    }
}

@Composable
private fun Header(model: AppModel, compact: Boolean, onDone: () -> Unit) {
    val colors = Theme.colors
    val buttons = @Composable {
        RoundIconButton(Icons.Rounded.Settings, stringResource(R.string.settings)) { model.sheet = AppModel.Sheet.SETTINGS }
        PillButton(stringResource(R.string.done), size = 15, onClick = onDone)
    }
    val identity = @Composable { modifier: Modifier ->
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(model, if (compact) 56.dp else 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NameField(model, compact)
                Text(subtitle(model), style = Theme.body(14), color = colors.muted)
            }
        }
    }
    if (compact) {
        // The cover screen is too narrow for the name beside the buttons.
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) { buttons() }
            identity(Modifier.fillMaxWidth())
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            identity(Modifier.weight(1f))
            buttons()
        }
    }
}

@Composable
private fun displayName(model: AppModel): String =
    model.settings.playerName.trim().ifEmpty { stringResource(R.string.player) }

@Composable
private fun Avatar(model: AppModel, side: androidx.compose.ui.unit.Dp) {
    val name = displayName(model)
    val initial = String(Character.toChars(name.codePointAt(0))).uppercase()
    Box(Modifier.size(side).background(Theme.colors.accent, CircleShape), contentAlignment = Alignment.Center) {
        Text(initial, style = Theme.display(26), color = Theme.colors.onAccent, maxLines = 1)
    }
}

/** Tap the name to change it; empty shows "Player" as the placeholder. */
@Composable
private fun NameField(model: AppModel, compact: Boolean) {
    val colors = Theme.colors
    val focus = LocalFocusManager.current
    val style = Theme.display(if (compact) 24 else 26)
    val label = stringResource(R.string.your_name)
    val placeholder = stringResource(R.string.player)
    // A single-line field takes all the room it is offered; sized to its text,
    // the pencil stays right after the name.
    val measurer = rememberTextMeasurer()
    val textWidth = with(LocalDensity.current) {
        measurer.measure(model.settings.playerName.ifEmpty { placeholder }, style, maxLines = 1).size.width.toDp()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            model.settings.playerName, { model.settings.playerName = it.take(24) },
            Modifier.weight(1f, fill = false).width(textWidth + 4.dp).semantics { contentDescription = label },
            textStyle = style.copy(color = colors.text),
            singleLine = true,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            decorationBox = { field ->
                Box {
                    if (model.settings.playerName.isEmpty()) Text(placeholder, style = style, color = colors.faint, maxLines = 1)
                    field()
                }
            },
        )
        Icon(Icons.Rounded.Edit, null, tint = colors.faint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun subtitle(model: AppModel): String {
    val stats = model.stats
    val parts = mutableListOf<String>()
    stats.firstPlayed?.let { first ->
        // "MMMM" is the month as it reads inside a sentence — "с сентября" in Russian.
        val locale = LocalConfiguration.current.locales[0]
        parts += stringResource(R.string.at_the_table_since_x, first.format(DateTimeFormatter.ofPattern("MMMM", locale)))
    }
    val streak = stats.streak
    if (streak > 0) parts += pluralStringResource(R.plurals.x_days_in_a_row, streak, streak)
    return if (parts.isEmpty()) stringResource(R.string.the_first_puzzle_is_waiting) else parts.joinToString(" · ")
}

@Composable
private fun Tiles(model: AppModel, compact: Boolean) {
    val stats = model.stats
    val locale = LocalConfiguration.current.locales[0]
    val minutes = (stats.timePlayedMillis / 60_000).toInt()
    val time = if (minutes < 60) stringResource(R.string.x_min, minutes) else stringResource(R.string.x_h, minutes / 60)
    val tiles = listOf(
        Triple(stats.puzzlesSolved.toString(), stringResource(R.string.puzzles_solved_2), false),
        Triple(NumberFormat.getIntegerInstance(locale).format(stats.piecesPlaced), stringResource(R.string.pieces_placed_2), false),
        Triple(time, stringResource(R.string.at_the_table), false),
        Triple(stats.streak.toString(), stringResource(R.string.days_in_a_row), true),
    )
    Grid(tiles, if (compact) 2 else 4, 12.dp) { (value, title, tinted) -> Tile(value, title, tinted) }
}

@Composable
private fun Tile(value: String, title: String, tinted: Boolean) {
    val colors = Theme.colors
    val color = if (tinted) colors.onSageTint else colors.text
    Column(
        Modifier.fillMaxSize().background(if (tinted) colors.sageTint else colors.card, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FittedLine(value, Theme.display(30), color, minScale = 0.6f)
        Text(title, style = Theme.body(12), color = color)
    }
}

/** Twelve bars, one per week; the busiest week takes the accent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chart(model: AppModel) {
    val colors = Theme.colors
    val weeks = model.stats.weeklyPieces
    val peak = maxOf(1, weeks.max())
    Column(
        Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(26.dp)).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SectionHeading(stringResource(R.string.last_12_weeks), stringResource(R.string.pieces_per_week))
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
            for (pieces in weeks) {
                val share = pieces.toFloat() / peak
                val color = when {
                    pieces == peak && pieces > 0 -> colors.accent
                    share > 0.75f -> colors.sage
                    share > 0.5f -> colors.sageSoft
                    share > 0.25f -> colors.muted.copy(alpha = 0.5f)
                    else -> colors.track
                }
                val label = pluralStringResource(R.plurals.x_pieces, pieces, pieces)
                Box(
                    Modifier.weight(1f).height(maxOf(6.dp, 140.dp * share))
                        .background(color, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                        .semantics { contentDescription = label },
                )
            }
        }
    }
}

@Composable
private fun Achievements(model: AppModel, compact: Boolean) {
    val unlocked = model.stats.unlocked
    val unseen = model.unseenAchievements
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(stringResource(R.string.achievements),
            stringResource(R.string.x_of_x, unlocked.size, Achievement.entries.size))
        Grid(Achievement.entries, if (compact) 1 else 2, 12.dp) { achievement ->
            AchievementRow(model, achievement, achievement in unlocked, achievement in unseen)
        }
    }
}

@Composable
private fun AchievementRow(model: AppModel, achievement: Achievement, unlocked: Boolean, isNew: Boolean) {
    val colors = Theme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxSize()
            .background(if (unlocked) colors.card else colors.surface, shape)
            .then(if (isNew) Modifier.border(1.5.dp, colors.accent.copy(alpha = 0.55f), shape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (unlocked) {
            if (isNew) NewMedalGlow { AchievementBadge(model, achievement) } else AchievementBadge(model, achievement)
        } else {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(colors.track, radius = size.minDimension / 2 - 1.dp.toPx(),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
                }
                Icon(Icons.Rounded.Lock, null, tint = colors.faint, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f).alpha(if (unlocked) 1f else 0.6f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(achievement.title), style = Theme.body(16, FontWeight.Bold), color = colors.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isNew) {
                    Text(stringResource(R.string.new_label).uppercase(), style = Theme.body(11, FontWeight.Bold), color = colors.onAccent,
                        modifier = Modifier.background(colors.accent, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Text(achievement.detailText(), style = Theme.body(13), color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionHeading(title: String, detail: String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = Theme.display(19), color = Theme.colors.text, modifier = Modifier.alignByBaseline())
        Text(detail, style = Theme.body(13), color = Theme.colors.faint, modifier = Modifier.alignByBaseline())
    }
}

/**
 * A fixed number of columns, cells in a row as tall as the tallest. Fixed,
 * because the screen scrolls as a whole and a lazy grid cannot sit inside it.
 */
@Composable
private fun <T> Grid(items: List<T>, columns: Int, spacing: androidx.compose.ui.unit.Dp, cell: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        for (row in items.chunked(columns)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                for (item in row) Box(Modifier.weight(1f).fillMaxHeight()) { cell(item) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
