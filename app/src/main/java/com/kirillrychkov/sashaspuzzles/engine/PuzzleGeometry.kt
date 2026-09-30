package com.kirillrychkov.sashaspuzzles.engine

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Immutable description of how a board is cut into pieces.
 *
 * The engine works in **board units**, a resolution-independent space whose
 * area is always [REFERENCE_AREA]. Screen size, zoom and folding the phone
 * never touch these numbers, which is what lets a game survive any resize
 * without losing a single piece.
 *
 * Every interior cut is stored **once**:
 * * `horizontalCuts[row * columns + column]` — between `(row, column)` and
 *   `(row + 1, column)`, canonically left → right.
 * * `verticalCuts[row * (columns - 1) + column]` — between `(row, column)` and
 *   `(row, column + 1)`, canonically top → bottom.
 *
 * A piece's outline reuses those exact curves (reversing two of them), so
 * `A.right` is by construction the inverse of `B.left`.
 */
class PuzzleGeometry(val columns: Int, val rows: Int, aspect: Double, val seed: ULong) {
    val boardSize: Sz
    val cellSize: Sz
    /** Length of one tab unit in board units. */
    val tabAmplitude: Double

    private val horizontalCuts: List<EdgeCurve>
    private val verticalCuts: List<EdgeCurve>

    val pieceCount: Int get() = rows * columns

    /** Worst-case distance a tab reaches beyond its cell. */
    val maximumOverhang: Double get() = tabAmplitude * 1.25

    init {
        require(columns >= 2 && rows >= 2) { "A puzzle needs at least a 2×2 grid" }
        val safeAspect = clamp(aspect, 0.2, 5.0)
        val width = sqrt(REFERENCE_AREA * safeAspect)
        val height = REFERENCE_AREA / width
        boardSize = Sz(width, height)
        cellSize = Sz(width / columns, height / rows)
        // Tabs scale with the smaller cell side so they stay in proportion.
        tabAmplitude = cellSize.minimumSide * 0.215

        val cell = cellSize
        val horizontal = ArrayList<EdgeCurve>(max(0, rows - 1) * columns)
        for (row in 0 until rows - 1) {
            for (column in 0 until columns) {
                val rng = SplitMix64(mixSeed(seed, 0xA1uL, row.toULong(), column.toULong()))
                val profile = EdgeProfile.random(rng)
                val y = (row + 1) * cell.height
                horizontal.add(profile.curve(
                    Pt(column * cell.width, y), Pt((column + 1) * cell.width, y),
                    Pt(0.0, 1.0), tabAmplitude))
            }
        }
        val vertical = ArrayList<EdgeCurve>(rows * max(0, columns - 1))
        for (row in 0 until rows) {
            for (column in 0 until columns - 1) {
                val rng = SplitMix64(mixSeed(seed, 0xB2uL, row.toULong(), column.toULong()))
                val profile = EdgeProfile.random(rng)
                val x = (column + 1) * cell.width
                vertical.add(profile.curve(
                    Pt(x, row * cell.height), Pt(x, (row + 1) * cell.height),
                    Pt(1.0, 0.0), tabAmplitude))
            }
        }
        horizontalCuts = horizontal
        verticalCuts = vertical
    }

    fun index(row: Int, column: Int) = row * columns + column
    fun row(index: Int) = index / columns
    fun column(index: Int) = index % columns

    /** The cell a piece occupies when solved (tabs excluded). */
    fun cellFrame(index: Int) = Rect(column(index) * cellSize.width, row(index) * cellSize.height,
        cellSize.width, cellSize.height)

    fun solvedOrigin(index: Int) = cellFrame(index).origin

    fun horizontalCut(row: Int, column: Int): EdgeCurve? {
        if (row < 0 || row >= rows - 1 || column < 0 || column >= columns) return null
        return horizontalCuts[row * columns + column]
    }

    fun verticalCut(row: Int, column: Int): EdgeCurve? {
        if (row < 0 || row >= rows || column < 0 || column >= columns - 1) return null
        return verticalCuts[row * (columns - 1) + column]
    }

    /** The four boundary curves of a piece, clockwise from the top-left corner. */
    fun outline(index: Int): List<EdgeCurve> {
        val r = row(index); val c = column(index)
        val frame = cellFrame(index)
        val topLeft = Pt(frame.minX, frame.minY)
        val topRight = Pt(frame.maxX, frame.minY)
        val bottomRight = Pt(frame.maxX, frame.maxY)
        val bottomLeft = Pt(frame.minX, frame.maxY)

        fun straight(from: Pt, to: Pt): EdgeCurve {
            // A cubic with evenly spaced controls is exactly the straight segment.
            val c1 = Pt(from.x + (to.x - from.x) / 3, from.y + (to.y - from.y) / 3)
            val c2 = Pt(from.x + (to.x - from.x) * 2 / 3, from.y + (to.y - from.y) * 2 / 3)
            return EdgeCurve(from, listOf(CubicSegment(c1, c2, to)))
        }

        val top = horizontalCut(r - 1, c) ?: straight(topLeft, topRight)
        val right = verticalCut(r, c) ?: straight(topRight, bottomRight)
        val bottom = horizontalCut(r, c)?.reversed ?: straight(bottomRight, bottomLeft)
        val left = verticalCut(r, c - 1)?.reversed ?: straight(bottomLeft, topLeft)
        return listOf(top, right, bottom, left)
    }

    /** The outline translated so the piece's cell origin lands at `(0, 0)`. */
    fun localOutline(index: Int): List<EdgeCurve> {
        val o = solvedOrigin(index)
        fun shift(p: Pt) = Pt(p.x - o.x, p.y - o.y)
        return outline(index).map { edge ->
            EdgeCurve(shift(edge.start), edge.segments.map { CubicSegment(shift(it.control1), shift(it.control2), shift(it.end)) })
        }
    }

    /** Bounding box of a piece's outline (tabs included), relative to its cell origin. */
    fun localBounds(index: Int): Rect =
        Rect.union(localOutline(index).flatMap { it.sampled(24) }).integralOutset()

    companion object {
        /** Board area in board units². 1000×1000 for a square image. */
        const val REFERENCE_AREA = 1_000_000.0

        /** A grid whose piece count is close to `target`, with cells as square as possible. */
        fun grid(targetPieces: Int, aspect: Double): Pair<Int, Int> {
            val target = max(4, targetPieces)
            val safeAspect = clamp(aspect, 0.2, 5.0)
            val idealColumns = sqrt(target * safeAspect).roundToInt()
            var best = 2 to 2
            var bestScore = Double.MAX_VALUE
            for (columns in max(2, idealColumns - 4)..(idealColumns + 4)) {
                if (columns < 2) continue
                val rows = max(2, (target.toDouble() / columns).roundHalfAway())
                val count = columns * rows
                val cellAspect = (safeAspect / columns) * rows
                // Count accuracy dominates; squareness breaks ties.
                val score = abs(count - target).toDouble() / target * 4 + abs(ln(cellAspect)) * 1.6
                if (score < bestScore) {
                    bestScore = score
                    best = columns to rows
                }
            }
            return best
        }

        /** Swift's `.rounded()` rounds halves away from zero; Kotlin's `roundToInt` rounds them up. */
        private fun Double.roundHalfAway(): Int = if (this >= 0) Math.floor(this + 0.5).toInt() else -Math.floor(-this + 0.5).toInt()
    }
}
