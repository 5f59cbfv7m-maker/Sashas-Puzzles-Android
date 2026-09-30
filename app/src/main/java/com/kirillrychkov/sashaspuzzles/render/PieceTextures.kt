package com.kirillrychkov.sashaspuzzles.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kirillrychkov.sashaspuzzles.engine.EdgeCurve
import com.kirillrychkov.sashaspuzzles.engine.PuzzleGeometry
import com.kirillrychkov.sashaspuzzles.engine.Rect
import com.kirillrychkov.sashaspuzzles.engine.clamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A pre-rendered bitmap for every piece, bevel included.
 *
 * The single most important performance decision in the app, carried over
 * from iOS: clipping 800 Bézier outlines against a photograph every frame is
 * hopeless; doing it once per piece and then blitting bitmaps turns the draw
 * loop into a few hundred textured rectangles.
 */
class PieceTextures(private val scope: CoroutineScope) {

    var bitmaps: Array<Bitmap?> = emptyArray()
        private set
    /** Outline bounds of each piece relative to its own cell origin, in board units. */
    var localBounds: List<Rect> = emptyList()
        private set
    var pixelScale = 1.0
        private set
    private var outlines = false

    var progress by mutableFloatStateOf(0f)
        private set
    var isReady by mutableStateOf(false)
        private set
    /** Bumped whenever bitmaps land, so drawing code re-reads them. */
    var revision by mutableIntStateOf(0)
        private set

    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun bitmap(piece: Int): Bitmap? = bitmaps.getOrNull(piece)

    fun rebuild(geometry: PuzzleGeometry, source: Bitmap, desiredScale: Double, outlines: Boolean) {
        val scale = affordableScale(geometry, desiredScale)
        val count = geometry.pieceCount
        // Zooming out draws the existing, sharper textures downsampled; only a
        // markedly larger scale is worth re-cutting.
        if (isReady && bitmaps.size == count && outlines == this.outlines &&
            scale < pixelScale * 1.25 && scale > pixelScale * 0.5) return

        job?.cancel()
        // A re-cut of a live board must not blank it: the old textures keep
        // drawing until the new set lands in one swap.
        val silent = isReady && bitmaps.size == count
        if (localBounds.size != count) localBounds = (0 until count).map { geometry.localBounds(it) }
        if (!silent) {
            bitmaps = arrayOfNulls(count)
            progress = 0f
            isReady = false
            pixelScale = scale
            this.outlines = outlines
        }
        val bounds = localBounds
        job = scope.launch {
            val staged = if (silent) arrayOfNulls<Bitmap>(count) else bitmaps
            val chunk = max(16, count / 24)
            var index = 0
            while (index < count) {
                ensureActive()
                val range = index until min(index + chunk, count)
                val rendered = withContext(Dispatchers.Default) {
                    range.map { piece -> async { renderPiece(piece, geometry, bounds[piece], source, scale, outlines) } }.awaitAll()
                }
                ensureActive()
                for ((offset, bitmap) in rendered.withIndex()) staged[range.first + offset] = bitmap
                index = range.last + 1
                if (!silent) {
                    progress = index.toFloat() / count
                    revision++
                }
            }
            if (silent) {
                bitmaps = staged
                pixelScale = scale
                this@PieceTextures.outlines = outlines
            }
            isReady = true
            revision++
        }
    }

    companion object {
        /**
         * Texture memory ceiling in pixels. Beyond it the pixel scale is reduced
         * rather than risking the system killing the game on a huge puzzle.
         */
        const val PIXEL_BUDGET = 32_000_000.0

        fun affordableScale(geometry: PuzzleGeometry, desired: Double): Double {
            val cell = geometry.cellSize
            val overhang = geometry.maximumOverhang * 2
            val area = (cell.width + overhang) * (cell.height + overhang) * geometry.pieceCount
            if (area <= 0) return desired
            return clamp(min(desired, sqrt(PIXEL_BUDGET / area)), 0.3, 3.5)
        }

        /** Android path of an outline, in whatever frame the curves are in. */
        fun path(edges: List<EdgeCurve>): Path {
            val path = Path()
            path.moveTo(edges[0].start.x.toFloat(), edges[0].start.y.toFloat())
            for (edge in edges) for (s in edge.segments) {
                path.cubicTo(s.control1.x.toFloat(), s.control1.y.toFloat(), s.control2.x.toFloat(),
                    s.control2.y.toFloat(), s.end.x.toFloat(), s.end.y.toFloat())
            }
            path.close()
            return path
        }

        fun renderPiece(piece: Int, geometry: PuzzleGeometry, bounds: Rect, source: Bitmap,
                        pixelScale: Double, outlines: Boolean): Bitmap? {
            val width = ceil(bounds.width * pixelScale).toInt()
            val height = ceil(bounds.height * pixelScale).toInt()
            if (width < 2 || height < 2) return null
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(pixelScale.toFloat(), pixelScale.toFloat())
            canvas.translate(-bounds.minX.toFloat(), -bounds.minY.toFloat())

            val origin = geometry.solvedOrigin(piece)
            val path = path(geometry.localOutline(piece))

            // The photo fragment is painted through the outline with a shader
            // rather than under a clip: software clips are aliased, an
            // anti-aliased fill is not.
            val pixelsPerUnit = source.width / geometry.boardSize.width
            val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    setScale((1 / pixelsPerUnit).toFloat(), (1 / pixelsPerUnit).toFloat())
                    postTranslate(-origin.x.toFloat(), -origin.y.toFloat())
                })
            }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })

            // Bevel: light from the top-left, shadow to the bottom-right, both
            // kept inside the piece (SRC_ATOP) so it reads as cut cardboard.
            val depth = max(0.45, geometry.cellSize.minimumSide * 0.034).toFloat()
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            for ((d, color) in listOf(-depth * 0.5f to Color.argb(92, 255, 255, 255), depth * 0.5f to Color.argb(82, 0, 0, 0))) {
                canvas.save()
                canvas.translate(d, d)
                stroke.strokeWidth = depth * 1.7f
                stroke.color = color
                canvas.drawPath(path, stroke)
                canvas.restore()
            }
            // Inner rim: keeps two adjacent solved pieces visually apart.
            stroke.strokeWidth = depth * 0.7f
            stroke.color = Color.argb(56, 0, 0, 0)
            canvas.drawPath(path, stroke)

            if (outlines) {
                stroke.xfermode = null
                stroke.strokeWidth = max(0.35f, depth * 0.32f)
                stroke.color = Color.argb(97, 20, 20, 20)
                canvas.drawPath(path, stroke)
            }
            return bitmap
        }
    }
}
