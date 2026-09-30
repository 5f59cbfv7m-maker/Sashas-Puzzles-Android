package com.kirillrychkov.sashaspuzzles.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

/**
 * Minimal double-precision geometry for the engine.
 *
 * The engine is pure Kotlin — no Android types — so it runs in plain JVM unit
 * tests and off the main thread. Board space runs y-down, like the screen.
 */
@Serializable
data class Pt(val x: Double, val y: Double) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Double) = Pt(x * k, y * k)
    val magnitude: Double get() = sqrt(x * x + y * y)
    fun distance(o: Pt) = (this - o).magnitude
    fun approx(o: Pt, tolerance: Double = 1e-6) = abs(x - o.x) <= tolerance && abs(y - o.y) <= tolerance

    companion object {
        val ZERO = Pt(0.0, 0.0)
    }
}

data class Sz(val width: Double, val height: Double) {
    val minimumSide: Double get() = min(width, height)
    val aspect: Double get() = if (height > 0) width / height else 1.0
}

data class Rect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val minX get() = x
    val minY get() = y
    val maxX get() = x + width
    val maxY get() = y + height
    val midX get() = x + width / 2
    val midY get() = y + height / 2
    val center get() = Pt(midX, midY)
    val origin get() = Pt(x, y)
    val size get() = Sz(width, height)

    fun offset(p: Pt) = Rect(x + p.x, y + p.y, width, height)
    fun inset(dx: Double, dy: Double) = Rect(x + dx, y + dy, width - 2 * dx, height - 2 * dy)
    fun contains(p: Pt) = p.x >= x && p.x <= maxX && p.y >= y && p.y <= maxY
    fun intersects(o: Rect) = x < o.maxX && o.x < maxX && y < o.maxY && o.y < maxY

    /** Rounds outward by a hair so anti-aliased strokes are never clipped. */
    fun integralOutset(margin: Double = 1.0) = Rect(
        floor(x - margin), floor(y - margin),
        ceil(width + margin * 2), ceil(height + margin * 2),
    )

    /** Largest sub-rect with the given aspect ratio, centred. */
    fun centeredCrop(aspect: Double): Rect {
        if (aspect <= 0 || width <= 0 || height <= 0) return this
        return if (width / height > aspect) {
            val w = height * aspect
            Rect(midX - w / 2, y, w, height)
        } else {
            val h = width / aspect
            Rect(x, midY - h / 2, width, h)
        }
    }

    companion object {
        fun union(points: List<Pt>): Rect {
            var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
            for (p in points) {
                minX = min(minX, p.x); minY = min(minY, p.y)
                maxX = max(maxX, p.x); maxY = max(maxY, p.y)
            }
            return Rect(minX, minY, maxX - minX, maxY - minY)
        }
    }
}

fun clamp(value: Double, lower: Double, upper: Double) = min(max(value, lower), upper)
fun clamp(value: Int, lower: Int, upper: Int) = min(max(value, lower), upper)
