package com.kirillrychkov.sashaspuzzles.library

import androidx.compose.foundation.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.app.AppModel
import com.kirillrychkov.sashaspuzzles.model.ArtCategory
import com.kirillrychkov.sashaspuzzles.model.LibraryItem
import com.kirillrychkov.sashaspuzzles.persistence.GameSnapshot
import com.kirillrychkov.sashaspuzzles.ui.LatticeOverlay
import com.kirillrychkov.sashaspuzzles.ui.LocalCompact
import com.kirillrychkov.sashaspuzzles.ui.FittedLine
import com.kirillrychkov.sashaspuzzles.ui.OneLine
import com.kirillrychkov.sashaspuzzles.ui.PillButton
import com.kirillrychkov.sashaspuzzles.ui.PillStyle
import com.kirillrychkov.sashaspuzzles.ui.ProgressBar
import com.kirillrychkov.sashaspuzzles.ui.RoundIconButton
import com.kirillrychkov.sashaspuzzles.ui.Tag
import com.kirillrychkov.sashaspuzzles.ui.TagStyle
import com.kirillrychkov.sashaspuzzles.ui.Theme
import com.kirillrychkov.sashaspuzzles.ui.TimeFormatting
import com.kirillrychkov.sashaspuzzles.ui.card
import com.kirillrychkov.sashaspuzzles.ui.pressable
import com.kirillrychkov.sashaspuzzles.ui.substituted

/** The library screen — the app's home. */
@Composable
fun HomeScreen(model: AppModel) {
    val compact = LocalCompact.current
    val gutter = if (compact) 16.dp else 26.dp
    var categoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    val category = categoryKey?.let { ArtCategory.fromKey(it) }
    val items = model.library.items(category)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LaunchedEffect(Unit) { model.refreshSaves() }

    // The system photo picker needs no permission; the document picker reaches Drive and downloads.
    val showMine = { categoryKey = ArtCategory.MINE.key }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        model.importPhotos(uris, fromFiles = false, onImported = showMine)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        model.importPhotos(uris, fromFiles = true, onImported = showMine)
    }
    val addPhoto = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val addFiles = { pickFiles.launch(arrayOf("image/*")) }

    // Large system text needs wider cards, so the grid takes fewer columns.
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(200.dp * fontScale),
        modifier = Modifier.fillMaxSize().background(Theme.colors.bg),
        contentPadding = PaddingValues(
            start = gutter + insets.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            end = gutter + insets.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            top = insets.calculateTopPadding() + if (compact) 4.dp else 10.dp,
            bottom = insets.calculateBottomPadding() + 30.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        full { Header(model, addPhoto, addFiles) }
        full { DailyCard(model) }
        if (model.resumable.isNotEmpty()) full { ContinueSection(model) }
        full { CategoryBar(model, category) { categoryKey = it?.key } }
        full {
            val solved = items.count { model.stats.bestTime(it.id) != null }
            SectionTitle(category?.let { stringResource(it.title) } ?: stringResource(R.string.all_pictures),
                stringResource(R.string.x_of_x_solved, solved, items.size))
        }
        if (category == ArtCategory.MINE && items.isEmpty()) full { NoPhotos(addPhoto) }
        items(items, key = { it.id }) { item ->
            PictureCard(model, item,
                solved = model.stats.bestTime(item.id),
                inProgress = model.savedGames.firstOrNull { !it.isComplete && it.itemId == item.id }?.pieceCount,
            ) { model.openSetup(item) }
        }
    }

    // A solid status bar: pictures scrolling under the clock made it unreadable.
    Box(Modifier.fillMaxWidth().height(insets.calculateTopPadding()).background(Theme.colors.bg))

    // Over the grid: the copy in progress, and a picture that could not be read.
    Box(Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding() + 20.dp), contentAlignment = Alignment.BottomCenter) {
        if (model.importing) {
            Row(Modifier.shadow(8.dp, CircleShape).background(Theme.colors.card, CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Downloading, null, tint = Theme.colors.text, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.importing), style = Theme.body(14, FontWeight.Bold), color = Theme.colors.text)
            }
        }
    }
    if (model.importFailed) {
        AlertDialog(
            onDismissRequest = { model.importFailed = false },
            confirmButton = { TextButton({ model.importFailed = false }) { Text(stringResource(R.string.ok), color = Theme.colors.accentDeep) } },
            title = { Text(stringResource(R.string.import_failed), style = Theme.display(22)) },
            text = { Text(stringResource(R.string.this_picture_could_not_be_loaded), style = Theme.body(15)) },
            containerColor = Theme.colors.card, titleContentColor = Theme.colors.text, textContentColor = Theme.colors.muted,
        )
    }
}

/** "My Photos" before the first one: what the category is for, and the way in. */
@Composable
private fun NoPhotos(addPhoto: () -> Unit) {
    val colors = Theme.colors
    Column(
        Modifier.fillMaxWidth().card(colors.surface, Theme.RADIUS_PANEL.dp).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(64.dp).background(colors.sageTint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = colors.onSageTint, modifier = Modifier.size(30.dp))
        }
        Text(stringResource(R.string.your_photos_are_puzzles_too), style = Theme.display(24), color = colors.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        PillButton(stringResource(R.string.add_photo), icon = Icons.Rounded.AddPhotoAlternate, size = 16, onClick = addPhoto)
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

/**
 * The title and the buttons. On a phone they cannot share one row — the name
 * came out as "Sasha's Puzz…" — so the buttons get a bar of their own above it.
 */
@Composable
private fun Header(model: AppModel, addPhoto: () -> Unit, addFiles: () -> Unit) {
    val compact = LocalCompact.current
    val chip = if (compact) 40.dp else 44.dp
    val buttons = @Composable {
        RoundIconButton(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.add_photo), size = chip, onClick = addPhoto)
        RoundIconButton(Icons.Rounded.CreateNewFolder, stringResource(R.string.import_from_files), size = chip, onClick = addFiles)
        Box {
            RoundIconButton(Icons.Rounded.Person, stringResource(R.string.profile), size = chip) {
                model.sheet = AppModel.Sheet.PROFILE
            }
            // An achievement not yet looked at in the profile.
            if (model.unseenAchievements.isNotEmpty()) {
                Box(Modifier.align(Alignment.TopEnd).offset(1.dp, (-1).dp).size(11.dp)
                    .background(Theme.colors.bg, CircleShape).padding(2.dp).background(Theme.colors.accent, CircleShape))
            }
        }
        RoundIconButton(Icons.Rounded.Settings, stringResource(R.string.settings), size = chip) {
            model.sheet = AppModel.Sheet.SETTINGS
        }
    }
    val title = @Composable { modifier: Modifier ->
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Theme.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Extension, null, tint = Theme.colors.onAccent, modifier = Modifier.size(24.dp))
            }
            FittedLine(stringResource(R.string.sashas_puzzles), Theme.display(if (compact) 30 else 34), Theme.colors.text, Modifier.weight(1f))
        }
    }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) { buttons() }
            title(Modifier.fillMaxWidth())
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            title(Modifier.weight(1f))
            buttons()
        }
    }
}

@Composable
private fun DailyCard(model: AppModel) {
    val compact = LocalCompact.current
    val colors = Theme.colors
    val item = model.library.dailyItem()
    val solvedToday = model.stats.dailySolvedToday
    val streak = model.stats.streak

    val thumbnail = @Composable { modifier: Modifier ->
        Box(modifier.height(144.dp).clip(RoundedCornerShape(20.dp))) {
            LibraryThumbnail(model, item, Modifier.fillMaxSize(), longSide = 640, washed = true)
            LatticeOverlay(6, 4, Modifier.fillMaxSize(), opacity = 0.5f)
        }
    }
    val copy = @Composable { modifier: Modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(stringResource(R.string.daily_puzzle), icon = Icons.Rounded.AutoAwesome, style = TagStyle.SAGE)
            FittedLine(model.library.title(item), Theme.display(28), colors.text, Modifier.padding(top = 4.dp))
            val pieces = if (streak > 0) {
                substituted(R.string.x_pieces_x_day_streak, Library.DAILY_PIECES, streak,
                    plural = mapOf(1 to R.plurals.x_pieces_x_day_streak_arg1, 2 to R.plurals.x_pieces_x_day_streak_arg2))
            } else {
                pluralStringResource(R.plurals.x_pieces, Library.DAILY_PIECES, Library.DAILY_PIECES)
            }
            Text(pieces, style = Theme.body(15), color = colors.muted)
            // The week: one dot per day of the streak, the next ones dashed.
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (day in 0 until 7) {
                    if (day < minOf(streak, 7)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
                        }
                    } else {
                        Canvas(Modifier.size(22.dp)) {
                            drawCircle(colors.track, radius = size.minDimension / 2 - 1.dp.toPx(),
                                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
                        }
                    }
                }
            }
        }
    }
    val button = @Composable { modifier: Modifier ->
        PillButton(
            stringResource(if (solvedToday) R.string.play_again else R.string.play), modifier,
            icon = if (solvedToday) Icons.Rounded.Check else Icons.Rounded.PlayArrow,
            style = if (solvedToday) PillStyle.SAGE else PillStyle.PRIMARY, expand = compact,
        ) { model.startDaily() }
    }

    Box(
        Modifier.fillMaxWidth().card(colors.surface, Theme.RADIUS_PANEL.dp)
            // The soft circle is painted, not laid out, so it never stretches the card.
            .drawBehind {
                drawCircle(colors.blob2.copy(alpha = 0.7f), radius = 110.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(size.width - 40.dp.toPx(), 40.dp.toPx()))
            },
    ) {
        if (compact) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                thumbnail(Modifier.fillMaxWidth())
                copy(Modifier)
                button(Modifier)
            }
        } else {
            Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                thumbnail(Modifier.width(216.dp))
                // With large system text the button moves under the title,
                // leaving the name the card's whole width.
                if (LocalDensity.current.fontScale > 1.15f) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        copy(Modifier)
                        button(Modifier)
                    }
                } else {
                    copy(Modifier.weight(1f))
                    button(Modifier)
                }
            }
        }
    }
}

@Composable
private fun ContinueSection(model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.continue_label),
            pluralStringResource(R.plurals.x_saved_games, model.resumable.size, model.resumable.size))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // On a phone the card takes the screen's width (less a peek at the
            // next one), so large text wraps instead of being cut off.
            val width = when {
                !LocalCompact.current -> null
                model.resumable.size == 1 -> maxWidth
                else -> maxWidth - 36.dp
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (snapshot in model.resumable) ResumeCard(model, snapshot, width)
            }
        }
    }
}

/** A section's name with its count beside it, or under it when large text leaves no room. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionTitle(title: String, detail: String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, Modifier.alignByBaseline(), style = Theme.display(22), color = Theme.colors.text)
        Text(detail, Modifier.alignByBaseline(), style = Theme.body(14), color = Theme.colors.faint)
    }
}

@Composable
private fun ResumeCard(model: AppModel, snapshot: GameSnapshot, width: Dp?) {
    val colors = Theme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            (if (width != null) Modifier.width(width) else Modifier)
                .card(colors.card)
                .combinedClickable(onLongClick = { menu = true }) { model.resume(snapshot) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LibraryThumbnail(model, snapshot.libraryItem, Modifier.size(96.dp, 68.dp).clip(RoundedCornerShape(16.dp)), longSide = 220, washed = true)
            Column(if (width != null) Modifier.weight(1f) else Modifier.width(150.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(model.library.title(snapshot.libraryItem), style = Theme.body(17, FontWeight.Bold), color = colors.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(substituted(R.string.x_pieces_x, snapshot.pieceCount, TimeFormatting.clock(snapshot.elapsedMillis),
                    plural = mapOf(1 to R.plurals.x_pieces_x_arg1)), style = Theme.body(13), color = colors.muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                ProgressBar(snapshot.state.placedCount.toFloat() / maxOf(1, snapshot.pieceCount), Modifier.fillMaxWidth().padding(top = 7.dp))
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = colors.onAccent)
            }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = colors.card) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_saved_game), style = Theme.body(15), color = colors.accentDeep) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.accentDeep) },
                onClick = { menu = false; model.delete(snapshot) },
            )
        }
    }
}

@Composable
private fun CategoryBar(model: AppModel, selected: ArtCategory?, onSelect: (ArtCategory?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        CategoryChip("${stringResource(R.string.all)} ${model.library.all.size}", selected == null, icon = true) { onSelect(null) }
        // Own photos come first: an import switches to them, and the chip must be in view.
        for (candidate in listOf(ArtCategory.MINE) + ArtCategory.entries.filter { it != ArtCategory.MINE }) {
            CategoryChip(stringResource(candidate.title), selected == candidate) { onSelect(candidate) }
        }
    }
}

@Composable
private fun CategoryChip(title: String, selected: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    val colors = Theme.colors
    Row(
        Modifier.clip(CircleShape).background(if (selected) colors.accent else colors.chip).pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(Icons.Rounded.GridView, null, tint = if (selected) colors.onAccent else colors.text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(title, style = Theme.body(15, if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) colors.onAccent else colors.text, maxLines = 1)
    }
}

@Composable
private fun PictureCard(model: AppModel, item: LibraryItem, solved: Long?, inProgress: Int?, onClick: () -> Unit) {
    val colors = Theme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.card(colors.card).then(
            if (item.isUserPhoto) Modifier.combinedClickable(onLongClick = { menu = true }, onClick = onClick) else Modifier.pressable(onClick = onClick),
        )) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.5f)) {
                LibraryThumbnail(model, item, Modifier.fillMaxSize(), washed = true)
                Box(Modifier.padding(10.dp)) {
                    if (solved != null) Tag(stringResource(R.string.solved), style = TagStyle.SAGE)
                    else if (inProgress != null) Tag(pluralStringResource(R.plurals.x_pieces, inProgress, inProgress), style = TagStyle.CARD)
                }
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OneLine(model.library.title(item), Theme.body(17, FontWeight.Bold), colors.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The category gives way first, so the solved status is always readable.
                    Text(stringResource(item.category.title), Modifier.weight(1f, fill = false), style = Theme.body(13), color = colors.muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.size(4.dp).clip(CircleShape).background(colors.track))
                    Text(solved?.let { TimeFormatting.clock(it) } ?: stringResource(R.string.not_solved),
                        style = Theme.body(13), color = colors.muted, maxLines = 1)
                }
            }
        }
        if (item.isUserPhoto) {
            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = colors.card) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.start_puzzle), style = Theme.body(15), color = colors.text) },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, null, tint = colors.text) },
                    onClick = { menu = false; onClick() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_photo), style = Theme.body(15), color = colors.accentDeep) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.accentDeep) },
                    onClick = { menu = false; model.deletePhoto(item) },
                )
            }
        }
    }
}
