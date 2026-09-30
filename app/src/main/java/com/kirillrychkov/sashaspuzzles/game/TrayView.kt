package com.kirillrychkov.sashaspuzzles.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.ui.PillButton
import com.kirillrychkov.sashaspuzzles.ui.PillStyle
import com.kirillrychkov.sashaspuzzles.ui.Theme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

enum class TrayPlacement { TRAILING, BOTTOM }

/** The piece on its way out of the tray, in root coordinates. Its own state so a move redraws only the ghost. */
class TrayDrag {
    var piece by mutableStateOf<Int?>(null)
    var location by mutableStateOf(Offset.Zero)
}

/**
 * The panel of pieces not yet on the table. A lazy grid is essential: a
 * nightmare puzzle puts 800 cells here and only the visible ones exist.
 */
@Composable
fun TrayView(
    session: GameSession,
    placement: TrayPlacement,
    showAction: Boolean,
    onChanged: (Int, Offset) -> Unit,
    onEnded: (Int, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Theme.colors
    val trailing = placement == TrayPlacement.TRAILING
    val cellSize = if (trailing) 74.dp else 63.dp
    val pieces = session.trayPieces
    session.textures.revision // re-read bitmaps as they land

    Column(modifier.background(colors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = if (trailing) 20.dp else 16.dp, end = if (trailing) 20.dp else 16.dp,
                top = if (trailing) 18.dp else 12.dp, bottom = if (trailing) 12.dp else 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.pieces), style = Theme.display(if (trailing) 19 else 17), color = colors.text, modifier = Modifier.weight(1f))
            Text("${pieces.size}", style = Theme.body(14, FontWeight.Bold), color = colors.muted,
                modifier = Modifier.background(colors.card, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Box(Modifier.weight(1f)) {
            if (pieces.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                    Box(Modifier.size(64.dp).background(colors.sageTint, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = colors.onSageTint, modifier = Modifier.size(30.dp))
                    }
                    Text(stringResource(R.string.all_pieces_are_on_the_table), style = Theme.body(15), color = colors.muted)
                }
            } else if (trailing) {
                LazyVerticalGrid(
                    GridCells.Adaptive(cellSize),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(pieces, key = { it }) { piece -> TrayCell(session, piece, cellSize, scrollsVertically = true, onChanged, onEnded) }
                }
            } else {
                LazyHorizontalGrid(
                    GridCells.Adaptive(cellSize),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(pieces, key = { it }) { piece -> TrayCell(session, piece, cellSize, scrollsVertically = false, onChanged, onEnded) }
                }
            }
        }
        if (showAction && trailing) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            val action = session.trayAction
            PillButton(
                stringResource(if (action == GameSession.TrayAction.GATHER) R.string.gather_from_the_table else R.string.scatter_on_the_table),
                Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
                style = PillStyle.SECONDARY, size = 16, expand = true, enabled = action != null,
            ) { session.performTrayAction() }
        }
    }
}

/**
 * One piece in the tray. Every piece is drawn at one scale, with its cell body —
 * not the texture — centred, so tabs stick out evenly around it.
 *
 * A touch reads its first few pixels: across the scroll axis lifts the piece,
 * along it leaves the grid to scroll. Holding still for a quarter second
 * first lifts it in any direction.
 */
@Composable
private fun TrayCell(
    session: GameSession,
    piece: Int,
    size: Dp,
    scrollsVertically: Boolean,
    onChanged: (Int, Offset) -> Unit,
    onEnded: (Int, Offset) -> Unit,
) {
    val colors = Theme.colors
    val slop = LocalViewConfiguration.current.touchSlop
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val description = stringResource(R.string.puzzle_piece)
    Box(
        Modifier
            .size(size)
            .semantics { contentDescription = description }
            .shadow(1.5.dp, RoundedCornerShape(18.dp))
            .background(colors.card, RoundedCornerShape(18.dp))
            .onGloballyPositioned { coordinates[0] = it }
            .pointerInput(piece, scrollsVertically) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var lifted = false
                    var position = start
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed || change.isConsumed) return@awaitEachGesture
                        val dx = abs(change.position.x - start.x); val dy = abs(change.position.y - start.y)
                        if (hypot(dx, dy) < slop) continue
                        val along = if (scrollsVertically) dy else dx
                        val across = if (scrollsVertically) dx else dy
                        val held = change.uptimeMillis - down.uptimeMillis > 250
                        if (!held && across <= along * 0.4f) return@awaitEachGesture
                        lifted = true
                        position = change.position
                        change.consume()
                        break
                    }
                    if (!lifted) return@awaitEachGesture
                    fun root(local: Offset) = coordinates[0]?.localToRoot(local) ?: local
                    onChanged(piece, root(position))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            onEnded(piece, Offset(-1f, -1f))
                            break
                        }
                        if (!change.pressed) {
                            onEnded(piece, root(change.position))
                            break
                        }
                        change.consume()
                        onChanged(piece, root(change.position))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        session.textures.revision // this cell redraws itself when its bitmap lands
        val bitmap = session.textures.bitmap(piece)
        val bounds = session.textures.localBounds.getOrNull(piece)
        if (bitmap != null && bounds != null) {
            val frame = remember(session.textures.localBounds) { pieceFrame(session) }
            Canvas(Modifier.fillMaxSize()) {
                val k = (this.size.width - 12.dp.toPx()) / frame.toFloat()
                val cell = session.geometry.cellSize
                val w = bounds.width.toFloat() * k; val h = bounds.height.toFloat() * k
                val cx = this.size.width / 2 + (bounds.midX - cell.width / 2).toFloat() * k
                val cy = this.size.height / 2 + (bounds.midY - cell.height / 2).toFloat() * k
                drawIntoCanvas {
                    val rect = android.graphics.RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
                    it.nativeCanvas.drawBitmap(bitmap, null, rect, cellPaint)
                }
            }
        } else {
            Icon(Icons.Rounded.Extension, null, tint = colors.track)
        }
    }
}

private val cellPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

/**
 * Side of the square, in board units around a piece's cell centre, that every
 * tray piece is fitted into: wide enough for the farthest tab of any piece.
 */
private fun pieceFrame(session: GameSession): Double {
    val cell = session.geometry.cellSize
    val reach = session.textures.localBounds.fold(0.0) { reach, b ->
        maxOf(reach, -b.minX, b.maxX - cell.width, -b.minY, b.maxY - cell.height)
    }
    return max(cell.width, cell.height) + 2 * reach
}
