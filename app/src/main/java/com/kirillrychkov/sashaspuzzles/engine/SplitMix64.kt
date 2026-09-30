package com.kirillrychkov.sashaspuzzles.engine

/**
 * Deterministic 64-bit generator (SplitMix64), bit-for-bit the one in the iOS app.
 *
 * Every random aspect of a puzzle — edge shapes, shuffle order, scatter — must
 * be exactly reproducible from a stored seed. Saves persist one number instead
 * of thousands of Bézier control points, and the geometry regenerates
 * identically on any device.
 */
class SplitMix64(seed: ULong) {
    private var state = seed

    fun next(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }

    /** Uniform value in `0 until 1`. */
    fun unit(): Double = (next() shr 11).toDouble() * UNIT

    fun double(lower: Double, upper: Double) = lower + unit() * (upper - lower)

    fun int(lower: Int, upper: Int): Int {
        if (upper <= lower) return lower
        val span = (upper - lower + 1).toULong()
        return lower + (next() % span).toInt()
    }

    /** `+1` or `-1`, used for tab polarity. */
    fun polarity(): Int = if (unit() < 0.5) -1 else 1

    fun point(rect: Rect) = Pt(double(rect.minX, rect.maxX), double(rect.minY, rect.maxY))

    /** Fisher–Yates shuffle driven by this generator, so shuffles are reproducible. */
    fun <T> shuffled(items: List<T>): MutableList<T> {
        val out = items.toMutableList()
        for (i in out.size - 1 downTo 1) {
            val j = int(0, i)
            val tmp = out[i]; out[i] = out[j]; out[j] = tmp
        }
        return out
    }

    private companion object {
        const val UNIT = 1.0 / (1L shl 53).toDouble()
    }
}

/**
 * Mixes integers into a well-distributed seed, so each edge gets its own
 * generator from `(puzzleSeed, kind, row, column)` and can be produced in any
 * order with identical results.
 */
fun mixSeed(vararg values: ULong): ULong {
    var h = 0xCBF29CE484222325uL
    for (value in values) {
        h = (h xor value) * 0x100000001B3uL
        h = h xor (h shr 29)
        h *= 0xBF58476D1CE4E5B9uL
        h = h xor (h shr 32)
    }
    return if (h == 0uL) 0x9E3779B97F4A7C15uL else h
}
