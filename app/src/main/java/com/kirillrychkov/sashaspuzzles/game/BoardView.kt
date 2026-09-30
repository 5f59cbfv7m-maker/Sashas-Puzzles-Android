package com.kirillrychkov.sashaspuzzles.game

import android.graphics.Bitmap
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.withFrameMillis
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.app.AppModel
import com.kirillrychkov.sashaspuzzles.engine.PieceGroup
import com.kirillrychkov.sashaspuzzles.engine.Pt
import com.kirillrychkov.sashaspuzzles.engine.Rect
import com.kirillrychkov.sashaspuzzles.engine.Sz
import com.kirillrychkov.sashaspuzzles.render.PieceTextures
import com.kirillrychkov.sashaspuzzles.ui.Palette
import com.kirillrychkov.sashaspuzzles.ui.Theme
import kotlin.math.min
import kotlin.math.sin

/**
 * The puzzle surface: one draw pass over the cached piece bitmaps, and one
 * pointer handler. One finger moves a piece, or pans when it starts on the
 * table; two fingers always move and zoom the board.
 */
@Composable
fun BoardView(model: AppModel, session: GameSession, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    val density = LocalDensity.current.density.toDouble()
    val painter = remember(session) { BoardPainter(session) }
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val animating = session.needsAnimation
    LaunchedEffect(animating) {
        while (animating) withFrameMillis { now = SystemClock.uptimeMillis() }
    }
    val description = stringResource(R.string.puzzle_board)

    Box(
        modifier
            .semantics { contentDescription = description }
            .onSizeChanged { session.boardLaidOut(Sz(it.width.toDouble(), it.height.toDouble()), density) }
            .pointerInput(session) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragging = session.beginDrag(session.viewport.board(Pt(down.position.x.toDouble(), down.position.y.toDouble())))
                    var multitouch = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            if (dragging) {
                                val last = event.changes.first()
                                session.updateDrag(session.viewport.board(Pt(last.position.x.toDouble(), last.position.y.toDouble())))
                                model.feedback.report(session.endDrag(model.settings.snapAssist), model.settings)
                            }
                            break
                        }
                        if (pressed.size >= 2) {
                            if (dragging) {
                                session.cancelDrag()
                                dragging = false
                            }
                            multitouch = true
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            session.pan(pan.x.toDouble(), pan.y.toDouble())
                            if (zoom != 1f && centroid != Offset.Unspecified) {
                                session.zoom(zoom.toDouble(), Pt(centroid.x.toDouble(), centroid.y.toDouble()))
                            }
                        } else {
                            val change = pressed[0]
                            if (dragging) {
                                session.updateDrag(session.viewport.board(Pt(change.position.x.toDouble(), change.position.y.toDouble())))
                            } else {
                                val delta = change.position - change.previousPosition
                                // After a pinch the lifted finger's partner keeps panning.
                                session.pan(delta.x.toDouble(), delta.y.toDouble())
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (multitouch && dragging) session.cancelDrag()
                }
            }
            .drawBehind {
                // Read so any placement change or texture landing redraws.
                session.version
                session.textures.revision
                session.viewport
                painter.draw(this, colors, model.settings.showGhostImage, now)
            },
    )
}

/** Holds the paints and scratch objects the draw pass reuses every frame. */
private class BoardPainter(private val session: GameSession) {
    private val piecePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(android.graphics.Color.BLACK, PorterDuff.Mode.SRC_IN)
        alpha = 80
    }
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val ghostPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 51 }
    private val rect = RectF()
    private val hintPaths = HashMap<Int, Path>()
    private val scratchPath = Path()
    private val matrix = Matrix()

    fun draw(scope: DrawScope, colors: Palette, showGhost: Boolean, now: Long) = scope.drawIntoCanvas { composeCanvas ->
        val canvas = composeCanvas.nativeCanvas
        val viewport = session.viewport
        val width = scope.size.width.toDouble(); val height = scope.size.height.toDouble()
        val margin = 60.0 * scope.density
        val visible = Rect(-margin, -margin, width + 2 * margin, height + 2 * margin)
        val textures = session.textures
        val dp = scope.density

        // Board plate: where the finished picture belongs.
        val plate = viewport.screen(session.boardRect)
        val corner = min(14.0, maxOf(2.0, 10 * viewport.scale / dp)).toFloat() * dp
        rect.set(plate.minX.toFloat(), plate.minY.toFloat(), plate.maxX.toFloat(), plate.maxY.toFloat())
        fill.color = colors.text.copy(alpha = 0.06f).toArgb()
        canvas.drawRoundRect(rect, corner, corner, fill)
        val source = session.source
        if (showGhost && source != null) {
            // Strong enough to guide, faint enough that a placed piece reads as on top.
            canvas.save()
            scratchPath.reset()
            scratchPath.addRoundRect(rect, corner, corner, Path.Direction.CW)
            canvas.clipPath(scratchPath)
            canvas.drawBitmap(source, null, rect, ghostPaint)
            canvas.restore()
        }
        stroke.color = colors.text.copy(alpha = 0.12f).toArgb()
        stroke.strokeWidth = 2 * dp
        canvas.drawRoundRect(rect, corner, corner, stroke)

        if (textures.bitmaps.isEmpty()) return@drawIntoCanvas
        val dragged = session.drag?.group
        for (groupId in session.drawOrder) {
            val group = session.state.groups[groupId] ?: continue
            if (groupId == dragged) {
                // One offset shadow for the whole cluster reads as a single lifted object.
                drawGroup(canvas, group, visible, shadowPaint, dy = 7 * dp)
            }
            drawGroup(canvas, group, visible, piecePaint, dy = 0f)
        }
        drawFlashes(canvas, colors, visible, now, dp)
        drawHint(canvas, colors, now, dp)
    }

    private fun pieceRect(piece: Int, group: PieceGroup): Rect? {
        val bounds = session.textures.localBounds.getOrNull(piece) ?: return null
        return session.viewport.screen(bounds.offset(session.state.solvedOrigin(piece) + group.translation))
    }

    private fun drawGroup(canvas: android.graphics.Canvas, group: PieceGroup, visible: Rect, paint: Paint, dy: Float) {
        for (piece in group.members) {
            val bitmap: Bitmap = session.textures.bitmap(piece) ?: continue
            val r = pieceRect(piece, group) ?: continue
            if (!r.intersects(visible)) continue
            rect.set(r.minX.toFloat(), r.minY.toFloat() + dy, r.maxX.toFloat(), r.maxY.toFloat() + dy)
            canvas.drawBitmap(bitmap, null, rect, paint)
        }
    }

    private fun drawFlashes(canvas: android.graphics.Canvas, colors: Palette, visible: Rect, now: Long, dp: Float) {
        val flashes = session.flashes
        if (flashes.isEmpty()) return
        glowPaint.colorFilter = LightingColorFilter(colors.sage.toArgb(), 0)
        for ((piece, start) in flashes) {
            val progress = (now - start).toFloat() / GameSession.FLASH_MILLIS
            if (progress < 0 || progress >= 1) continue
            val bitmap = session.textures.bitmap(piece) ?: continue
            val group = session.state.group(piece) ?: continue
            val r = pieceRect(piece, group) ?: continue
            if (!r.intersects(visible)) continue
            rect.set(r.minX.toFloat(), r.minY.toFloat(), r.maxX.toFloat(), r.maxY.toFloat())
            glowPaint.alpha = ((1 - progress) * 0.7f * 255).toInt()
            canvas.drawBitmap(bitmap, null, rect, glowPaint)
            // The snap ring: a sage stroke that swells and fades.
            val grow = (4 + 10 * progress) * dp
            rect.inset(-grow, -grow)
            stroke.color = colors.sage.copy(alpha = (1 - progress) * 0.9f).toArgb()
            stroke.strokeWidth = 5 * dp
            canvas.drawRoundRect(rect, 16 * dp, 16 * dp, stroke)
        }
    }

    private fun drawHint(canvas: android.graphics.Canvas, colors: Palette, now: Long, dp: Float) {
        val hint = session.hint ?: return
        if (now >= hint.expires) return
        val piece = hint.piece
        val viewport = session.viewport
        val origin = session.state.solvedOrigin(piece)
        val local = hintPaths.getOrPut(piece) { PieceTextures.path(session.geometry.localOutline(piece)) }
        matrix.setTranslate(origin.x.toFloat(), origin.y.toFloat())
        matrix.postScale(viewport.scale.toFloat(), viewport.scale.toFloat())
        matrix.postTranslate(viewport.offsetX.toFloat(), viewport.offsetY.toFloat())
        scratchPath.reset()
        local.transform(matrix, scratchPath)
        val pulse = (0.55 + 0.45 * sin(now / 1000.0 * 6)).toFloat()

        fill.color = colors.accent.copy(alpha = 0.18f * pulse).toArgb()
        canvas.drawPath(scratchPath, fill)
        stroke.color = colors.accent.copy(alpha = 0.18f * pulse).toArgb()
        stroke.strokeWidth = 12 * dp
        canvas.drawPath(scratchPath, stroke)
        stroke.color = colors.accent.copy(alpha = 0.55f + 0.45f * pulse).toArgb()
        stroke.strokeWidth = 4 * dp
        canvas.drawPath(scratchPath, stroke)

        // Trace from the piece's current spot to where it belongs.
        val group = session.state.group(piece) ?: return
        val half = Pt(session.geometry.cellSize.width / 2, session.geometry.cellSize.height / 2)
        val from = viewport.screen(origin + group.translation + half)
        val to = viewport.screen(origin + half)
        stroke.color = colors.accent.copy(alpha = 0.5f).toArgb()
        stroke.strokeWidth = 2 * dp
        stroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4 * dp, 6 * dp), 0f)
        canvas.drawLine(from.x.toFloat(), from.y.toFloat(), to.x.toFloat(), to.y.toFloat(), stroke)
        stroke.pathEffect = null
    }
}
