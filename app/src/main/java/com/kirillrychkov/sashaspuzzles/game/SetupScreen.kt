package com.kirillrychkov.sashaspuzzles.game

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.kirillrychkov.sashaspuzzles.ui.FittedLine
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.app.AppModel
import com.kirillrychkov.sashaspuzzles.engine.PuzzleGeometry
import com.kirillrychkov.sashaspuzzles.library.LibraryThumbnail
import com.kirillrychkov.sashaspuzzles.model.Difficulty
import com.kirillrychkov.sashaspuzzles.model.LibraryItem
import com.kirillrychkov.sashaspuzzles.model.PuzzleAspect
import com.kirillrychkov.sashaspuzzles.ui.Kicker
import com.kirillrychkov.sashaspuzzles.ui.LatticeOverlay
import com.kirillrychkov.sashaspuzzles.ui.LocalCompact
import com.kirillrychkov.sashaspuzzles.ui.OneLine
import com.kirillrychkov.sashaspuzzles.ui.PillButton
import com.kirillrychkov.sashaspuzzles.ui.RoundIconButton
import com.kirillrychkov.sashaspuzzles.ui.Tag
import com.kirillrychkov.sashaspuzzles.ui.Theme
import com.kirillrychkov.sashaspuzzles.ui.pressable
import kotlin.math.roundToInt

/** Preview a picture, pick a framing and a piece count, then start. */
@Composable
fun SetupScreen(model: AppModel, item: LibraryItem) {
    val colors = Theme.colors
    val compact = LocalCompact.current
    var aspect by rememberSaveable { mutableStateOf(model.settings.defaultAspect) }
    var difficulty by rememberSaveable { mutableStateOf(model.settings.defaultDifficulty) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var customCount by rememberSaveable { mutableFloatStateOf(200f) }

    val targetPieces = if (custom) customCount.roundToInt() else difficulty.targetPieces
    val boardAspect = aspect.ratio ?: item.aspect
    val (columns, rows) = PuzzleGeometry.grid(targetPieces, boardAspect)

    val preview = @Composable { modifier: Modifier, wide: Boolean ->
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                (if (wide) Modifier.weight(1f, fill = false) else Modifier.heightIn(max = if (compact) 300.dp else 520.dp))
                    .aspectRatio(boardAspect.toFloat(), matchHeightConstraintsFirst = wide)
                    .shadow(12.dp, RoundedCornerShape(Theme.RADIUS_PANEL.dp))
                    .clip(RoundedCornerShape(Theme.RADIUS_PANEL.dp))
                    .animateContentSize(),
            ) {
                LibraryThumbnail(model, item, Modifier.fillMaxSize(), longSide = 1200)
                LatticeOverlay(columns, rows, Modifier.fillMaxSize())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.x_x_4, columns, rows), style = Theme.display(24), color = colors.text)
                Box(Modifier.size(6.dp).clip(CircleShape).background(colors.track))
                OneLine("${pluralStringResource(R.plurals.x_pieces, columns * rows, columns * rows)} · ${stringResource(Difficulty.nearest(targetPieces).estimate)}",
                    Theme.body(17), colors.muted)
            }
        }
    }

    val panel = @Composable { modifier: Modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker(stringResource(R.string.framing))
                AspectChips(aspect) { aspect = it }
                Text(stringResource(R.string.original_keeps_the_whole_picture_the_other_optio), style = Theme.body(13), color = colors.muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Shrinks rather than breaking mid-word beside the switch on a narrow screen.
                    FittedLine(stringResource(R.string.difficulty).uppercase(), Theme.body(12, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
                        colors.muted, Modifier.weight(1f), minScale = 0.6f)
                    Text(stringResource(R.string.custom), style = Theme.body(13), color = colors.muted)
                    Switch(custom, { custom = it }, colors = switchColors())
                }
                if (custom) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.card).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Slider(customCount, { customCount = it }, valueRange = Difficulty.bounds.first.toFloat()..Difficulty.bounds.last.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.track))
                        Text(pluralStringResource(R.plurals.x_pieces, customCount.roundToInt(), customCount.roundToInt()),
                            style = Theme.body(15), color = colors.muted)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (option in Difficulty.entries) {
                            DifficultyRow(option, boardAspect, option == difficulty) { difficulty = option }
                        }
                    }
                }
                if (targetPieces >= Difficulty.INSANE.targetPieces) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = colors.muted, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Text(stringResource(R.string.large_puzzles_take_a_moment_to_cut_and_are_best), style = Theme.body(13), color = colors.muted)
                    }
                }
            }
            PillButton(stringResource(R.string.start_puzzle_2), icon = Icons.Rounded.PlayArrow, size = 20, expand = true) {
                model.start(item, aspect, targetPieces)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().height(70.dp).padding(horizontal = if (compact) 16.dp else 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) { model.showLibrary() }
            OneLine(model.library.title(item), Theme.display(22), colors.text, Modifier.weight(1f, fill = false))
            Tag(stringResource(item.category.title))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 860.dp) {
                Row(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1f).fillMaxHeight().padding(30.dp), true)
                    Box(Modifier.width(400.dp).fillMaxHeight().background(colors.surface)) {
                        panel(Modifier.verticalScroll(rememberScrollState()).padding(26.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    preview(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp), false)
                    panel(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp).widthIn(max = 640.dp).align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(Theme.RADIUS_PANEL.dp)).background(colors.surface).padding(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AspectChips(selected: PuzzleAspect, onSelect: (PuzzleAspect) -> Unit) {
    val colors = Theme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        for (option in PuzzleAspect.entries) {
            val isSelected = option == selected
            Box(
                Modifier.weight(1f).heightIn(min = 38.dp).clip(CircleShape).background(if (isSelected) colors.accent else colors.card)
                    .pressable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(option.titleRes?.let { stringResource(it) } ?: option.label,
                    style = Theme.body(15, if (isSelected) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (isSelected) colors.onAccent else colors.text, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DifficultyRow(difficulty: Difficulty, aspect: Double, selected: Boolean, onClick: () -> Unit) {
    val colors = Theme.colors
    val (columns, rows) = PuzzleGeometry.grid(difficulty.targetPieces, aspect)
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(if (selected) colors.accent else colors.card).pressable(onClick = onClick)
            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // Sized for the widest count at the current text size: large system text
        // cut "300" to "30" in a fixed circle. Every row gets the same badge, so the names line up.
        val badgeStyle = Theme.body(12, FontWeight.Bold)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val widest = Difficulty.entries.maxOf { it.targetPieces }.toString()
        val (badgeWidth, badgeHeight) = with(density) {
            val size = measurer.measure(widest, badgeStyle, maxLines = 1).size
            maxOf(30.dp, size.width.toDp() + 10.dp) to maxOf(30.dp, size.height.toDp() + 8.dp)
        }
        Box(Modifier.size(badgeWidth, badgeHeight).clip(CircleShape).background(if (selected) colors.onAccent else colors.sageTint), contentAlignment = Alignment.Center) {
            Text("${difficulty.targetPieces}", style = badgeStyle, color = if (selected) colors.accent else colors.onSageTint, maxLines = 1, softWrap = false)
        }
        val name = @Composable { modifier: Modifier ->
            Text(stringResource(difficulty.title), style = Theme.body(15, FontWeight.Bold), color = if (selected) colors.onAccent else colors.text,
                modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val details = stringResource(R.string.x_x_x, columns, rows, stringResource(difficulty.estimate))
        val detailsColor = if (selected) colors.onAccent.copy(alpha = 0.85f) else colors.faint
        if (LocalCompact.current) {
            // A phone-wide row cannot hold the name beside the grid and the time: they go under it.
            Column(Modifier.weight(1f)) {
                name(Modifier)
                OneLine(details, Theme.body(12), detailsColor)
            }
        } else {
            name(Modifier.weight(1f))
            OneLine(details, Theme.body(12), detailsColor)
        }
    }
}

@Composable
fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Theme.colors.onAccent, checkedTrackColor = Theme.colors.accent,
    uncheckedThumbColor = Theme.colors.card, uncheckedTrackColor = Theme.colors.track, uncheckedBorderColor = Theme.colors.track,
)
