package com.kirillrychkov.sashaspuzzles.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.app.AppModel
import com.kirillrychkov.sashaspuzzles.engine.Pt
import com.kirillrychkov.sashaspuzzles.engine.Sz
import com.kirillrychkov.sashaspuzzles.ui.ChipStyle
import com.kirillrychkov.sashaspuzzles.ui.LocalCompact
import com.kirillrychkov.sashaspuzzles.ui.FittedLine
import com.kirillrychkov.sashaspuzzles.ui.OneLine
import com.kirillrychkov.sashaspuzzles.ui.PillButton
import com.kirillrychkov.sashaspuzzles.ui.ProgressBar
import com.kirillrychkov.sashaspuzzles.ui.RoundIconButton
import com.kirillrychkov.sashaspuzzles.ui.Theme
import com.kirillrychkov.sashaspuzzles.ui.TimeFormatting
import com.kirillrychkov.sashaspuzzles.ui.pressable
import kotlin.math.roundToInt

/** The playing screen: header, board, tray and overlays. */
@Composable
fun GameScreen(model: AppModel, session: GameSession) {
    val colors = Theme.colors
    val drag = remember(session) { TrayDrag() }
    var showOriginal by remember { mutableStateOf(false) }
    val boardBounds = remember { arrayOf(Rect.Zero) }
    val gameOrigin = remember { arrayOf(Offset.Zero) }

    LaunchedEffect(session) {
        if (!session.isLoaded) session.load(model.images, model.settings.showPieceOutlines)
    }
    LaunchedEffect(model.settings.showPieceOutlines) { session.setOutlines(model.settings.showPieceOutlines) }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        Header(model, session) { showOriginal = true }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        BoxWithConstraints(
            Modifier.fillMaxSize().onGloballyPositioned { gameOrigin[0] = it.positionInRoot() }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        ) {
            val compact = LocalCompact.current
            val trailing = maxWidth > maxHeight && maxWidth >= 700.dp
            val placement = if (trailing) TrayPlacement.TRAILING else TrayPlacement.BOTTOM
            // The folded phone held sideways is wide but short: a slimmer tray
            // leaves the board more width, and its action lives in the menu.
            val thickness = when {
                trailing && compact -> 168.dp
                trailing -> (maxWidth * 0.24f).coerceIn(220.dp, 300.dp)
                else -> (maxHeight * 0.2f).coerceIn(130.dp, 220.dp)
            }
            val zoomRow = compact && maxWidth > maxHeight
            val board = @Composable { modifier: Modifier ->
                Box(modifier.onGloballyPositioned { boardBounds[0] = it.boundsInRoot() }) {
                    BoardView(model, session, Modifier.fillMaxSize())
                    ZoomControls(session, compact, zoomRow, Modifier.align(Alignment.BottomEnd).padding(if (compact) 10.dp else 18.dp)
                        .onGloballyPositioned { c ->
                            val parent = c.parentLayoutCoordinates?.size ?: return@onGloballyPositioned
                            val at = c.positionInParent()
                            session.controlsLaidOut(Sz((parent.width - at.x).toDouble(), (parent.height - at.y).toDouble()))
                        })
                }
            }
            val onChanged: (Int, Offset) -> Unit = { piece, location ->
                drag.piece = piece
                drag.location = location
            }
            val onEnded: (Int, Offset) -> Unit = { piece, location ->
                drag.piece = null
                val bounds = boardBounds[0]
                if (bounds.contains(location)) {
                    val local = location - bounds.topLeft
                    val outcome = session.placeFromTray(piece, session.viewport.board(Pt(local.x.toDouble(), local.y.toDouble())), model.settings.snapAssist)
                    model.feedback.report(outcome, model.settings)
                }
            }
            if (trailing) {
                Row(Modifier.fillMaxSize()) {
                    board(Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().background(colors.hairline))
                    TrayView(session, placement, showAction = !compact, onChanged, onEnded, Modifier.width(thickness).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    board(Modifier.weight(1f).fillMaxWidth())
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                    TrayView(session, placement, showAction = false, onChanged, onEnded, Modifier.height(thickness).fillMaxWidth())
                }
            }
            Overlays(model, session)
            TrayGhost(session, drag, gameOrigin)
        }
    }

    if (showOriginal) OriginalImageDialog(model, session) { showOriginal = false }
}

@Composable
private fun TrayGhost(session: GameSession, drag: TrayDrag, gameOrigin: Array<Offset>) {
    val piece = drag.piece ?: return
    val bitmap = session.textures.bitmap(piece) ?: return
    val density = LocalDensity.current
    val sizePx = maxOf(with(density) { 44.dp.toPx() }, (session.geometry.cellSize.minimumSide * session.viewport.scale * 1.6).toFloat())
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val location = drag.location - gameOrigin[0]
    Image(
        image, null,
        Modifier
            .offset { IntOffset((location.x - sizePx / 2).roundToInt(), (location.y - sizePx / 2).roundToInt()) }
            .size(with(density) { sizePx.toDp() })
            .graphicsLayer { shadowElevation = 10.dp.toPx(); alpha = 0.98f },
        contentScale = ContentScale.Fit,
    )
}

/**
 * Back, title, progress and clock, and the action chips. A phone-wide header
 * keeps hint and pause on the surface and folds the rest into one menu.
 */
@Composable
private fun Header(model: AppModel, session: GameSession, onShowOriginal: () -> Unit) {
    val colors = Theme.colors
    val compact = LocalCompact.current
    val playing = session.phase == GameSession.Phase.PLAYING
    val action = session.trayAction
    val actionIcon = if (action == GameSession.TrayAction.GATHER) Icons.Rounded.MoveToInbox else Icons.Rounded.Shuffle
    val actionTitle = stringResource(if (action == GameSession.TrayAction.GATHER) R.string.gather_pieces else R.string.scatter_pieces)

    BoxWithConstraints(
        Modifier.fillMaxWidth().background(colors.card)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
        // The unfolded Fold is tablet-sized but narrower than an iPad: below
        // ~780dp the secondary actions fold into the menu, below ~900dp the
        // progress bar leaves the chip, so the title always keeps its room.
        val width = maxWidth
        val menuActions = compact || width < 780.dp
        val showBar = !compact && width >= 900.dp
        Row(
            Modifier.fillMaxWidth().height(if (compact) 56.dp else 70.dp).padding(horizontal = if (compact) 12.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back_to_library), size = 42.dp) { model.showLibrary() }
            // The cover screen, or large system text, leaves no room for the name
            // next to the chips; the picture itself is on the board, so the
            // title gives way first.
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            if (width / fontScale >= (if (compact) 400.dp else 640.dp)) FittedLine(model.library.title(session.item), Theme.display(20), colors.text, Modifier.weight(1f))
            else Box(Modifier.weight(1f))
            StatusChip(session, showBar)
            RoundIconButton(Icons.Rounded.Lightbulb, stringResource(R.string.hint), style = ChipStyle.SAGE, size = 42.dp, enabled = playing) { session.requestHint() }
            if (menuActions) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    RoundIconButton(Icons.Rounded.MoreHoriz, stringResource(R.string.actions), size = 42.dp) { menu = true }
                    DropdownMenu(menu, { menu = false }, containerColor = colors.card) {
                        MenuItem(stringResource(R.string.show_original), Icons.Rounded.Image) { menu = false; onShowOriginal() }
                        MenuItem(actionTitle, actionIcon, enabled = action != null) { menu = false; session.performTrayAction() }
                        MenuItem(stringResource(R.string.undo), Icons.AutoMirrored.Rounded.Undo, enabled = session.canUndo) { menu = false; session.undo() }
                        MenuItem(stringResource(R.string.redo), Icons.AutoMirrored.Rounded.Redo, enabled = session.canRedo) { menu = false; session.redo() }
                    }
                }
            } else {
                RoundIconButton(Icons.Rounded.Image, stringResource(R.string.show_original), size = 42.dp, onClick = onShowOriginal)
                RoundIconButton(actionIcon, actionTitle, size = 42.dp, enabled = action != null) { session.performTrayAction() }
                RoundIconButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo), size = 42.dp, enabled = session.canUndo) { session.undo() }
                RoundIconButton(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.redo), size = 42.dp, enabled = session.canRedo) { session.redo() }
            }
            PauseButton(session, compact)
        }
    }
}

@Composable
private fun MenuItem(title: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = Theme.colors
    DropdownMenuItem(
        text = { Text(title, style = Theme.body(15), color = if (enabled) colors.text else colors.faint) },
        leadingIcon = { Icon(icon, null, tint = if (enabled) colors.muted else colors.faint) },
        enabled = enabled, onClick = onClick,
    )
}

@Composable
private fun PauseButton(session: GameSession, compact: Boolean) {
    val colors = Theme.colors
    val paused = session.phase == GameSession.Phase.PAUSED
    val enabled = session.phase != GameSession.Phase.COMPLETED
    val title = stringResource(if (paused) R.string.resume else R.string.pause)
    Row(
        Modifier.heightIn(min = 42.dp).alpha(if (enabled) 1f else 0.45f).clip(CircleShape).background(colors.accent)
            .pressable(enabled) { if (paused) session.resume() else session.pause() }
            .padding(horizontal = if (compact) 11.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, title, tint = colors.onAccent, modifier = Modifier.size(20.dp))
        if (!compact) Text(title, style = Theme.body(15, FontWeight.Bold), color = colors.onAccent, maxLines = 1)
    }
}

/** Progress and clock, in the header so the board keeps its whole area. */
@Composable
private fun StatusChip(session: GameSession, showBar: Boolean) {
    val colors = Theme.colors
    val compact = LocalCompact.current
    val clock = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
            Icon(Icons.Rounded.Schedule, null, tint = colors.accent, modifier = Modifier.size(if (compact) 11.dp else 14.dp))
            Text(TimeFormatting.clock(session.elapsedMillis), style = Theme.body(if (compact) 12 else 15, FontWeight.Bold), color = colors.text, maxLines = 1)
        }
    }
    val progress = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
            Icon(Icons.Rounded.Extension, null, tint = colors.sage, modifier = Modifier.size(if (compact) 11.dp else 14.dp))
            Text(stringResource(R.string.x_x_5, session.placedCount, session.pieceCount),
                style = Theme.body(if (compact) 12 else 15, FontWeight.Bold), color = colors.text, maxLines = 1)
        }
    }
    Box(Modifier.clip(CircleShape).background(colors.chip).padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 4.dp else 7.dp)) {
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) { clock(); progress() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                progress()
                if (showBar) ProgressBar(session.completion, Modifier.width(80.dp))
                Box(Modifier.width(1.dp).height(16.dp).background(colors.track))
                clock()
            }
        }
    }
}

/**
 * Zoom and fit buttons. On a phone they shrink, and when the board is short
 * they lie in a row so they never stand taller than the picture.
 */
@Composable
private fun ZoomControls(session: GameSession, compact: Boolean, row: Boolean, modifier: Modifier) {
    val colors = Theme.colors
    val radius = if (compact) 19.dp else 22.dp
    val button = if (compact) 34.dp else 38.dp
    val buttons = @Composable {
        ZoomButton(Icons.Rounded.Add, stringResource(R.string.zoom_in_2), button) { session.zoomStep(1.25) }
        ZoomButton(Icons.Rounded.Remove, stringResource(R.string.zoom_out_2), button) { session.zoomStep(0.8) }
        if (row) Box(Modifier.width(1.dp).height(20.dp).background(colors.track))
        else Box(Modifier.width(if (compact) 20.dp else 24.dp).height(1.dp).background(colors.track))
        ZoomButton(Icons.Rounded.CenterFocusStrong, stringResource(R.string.fit_board_2), button) { session.fitBoard() }
        ZoomButton(Icons.Rounded.ZoomOutMap, stringResource(R.string.fit_table_2), button) { session.fitTable() }
    }
    val panel = modifier.shadow(6.dp, RoundedCornerShape(radius)).background(colors.card, RoundedCornerShape(radius))
        .padding(if (compact) 4.dp else 10.dp)
    val gap = if (compact) 2.dp else 6.dp
    if (row) {
        Row(panel, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) { buttons() }
    } else {
        Column(panel, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) { buttons() }
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Theme.colors.text, modifier = Modifier.size(if (size < 38.dp) 18.dp else 20.dp))
    }
}

@Composable
private fun Overlays(model: AppModel, session: GameSession) {
    val loading = !session.isLoaded || session.textures.progress < 1f
    AnimatedVisibility(loading && !session.loadFailed, enter = fadeIn(), exit = fadeOut()) { LoadingOverlay(session) }
    if (session.loadFailed) ErrorOverlay { model.showLibrary() }
    AnimatedVisibility(session.phase == GameSession.Phase.PAUSED && !session.loadFailed, enter = fadeIn(), exit = fadeOut()) {
        PauseOverlay(model, session)
    }
    if (session.phase == GameSession.Phase.COMPLETED && !loading) CompletionOverlay(model, session)
}

/** Full-size reference view of the picture being assembled. */
@Composable
private fun OriginalImageDialog(model: AppModel, session: GameSession, onDismiss: () -> Unit) {
    val colors = Theme.colors
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 22.dp, top = 22.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                OneLine(model.library.title(session.item), Theme.display(22), colors.text, Modifier.weight(1f))
                PillButton(stringResource(R.string.done), size = 15, onClick = onDismiss)
            }
            val source = session.source
            if (source != null) {
                val image = remember(source) { source.asImageBitmap() }
                Box(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, bottom = 26.dp, top = 4.dp), contentAlignment = Alignment.Center) {
                    Image(image, null, Modifier.clip(RoundedCornerShape(Theme.RADIUS_PANEL.dp)), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}
