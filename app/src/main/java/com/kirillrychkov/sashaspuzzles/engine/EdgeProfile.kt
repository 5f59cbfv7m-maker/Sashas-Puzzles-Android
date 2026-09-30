package com.kirillrychkov.sashaspuzzles.engine

/** One cubic Bézier segment. The start point is implied by the previous segment. */
data class CubicSegment(val control1: Pt, val control2: Pt, val end: Pt)

/** A chain of cubic segments with an explicit start point. */
data class EdgeCurve(val start: Pt, val segments: List<CubicSegment>) {
    val end: Pt get() = segments.lastOrNull()?.end ?: start

    /**
     * The same curve traversed the other way. Exact — controls mirrored within
     * each segment, chain order flipped — which is what guarantees that two
     * neighbouring pieces share one identical boundary with no gap or overlap.
     */
    val reversed: EdgeCurve
        get() {
            if (segments.isEmpty()) return this
            val points = ArrayList<Pt>(segments.size + 1)
            points.add(start)
            for (segment in segments) points.add(segment.end)
            val flipped = ArrayList<CubicSegment>(segments.size)
            for (index in segments.indices.reversed()) {
                val segment = segments[index]
                flipped.add(CubicSegment(segment.control2, segment.control1, points[index]))
            }
            return EdgeCurve(end, flipped)
        }

    /** Samples the curve, including the start point, for tests and bounds. */
    fun sampled(perSegment: Int = 16): List<Pt> {
        val points = ArrayList<Pt>(1 + segments.size * perSegment)
        points.add(start)
        var current = start
        for (segment in segments) {
            for (step in 1..perSegment) {
                val t = step.toDouble() / perSegment
                points.add(evaluate(current, segment.control1, segment.control2, segment.end, t))
            }
            current = segment.end
        }
        return points
    }

    companion object {
        fun evaluate(p0: Pt, p1: Pt, p2: Pt, p3: Pt, t: Double): Pt {
            val u = 1 - t
            val a = u * u * u; val b = 3 * u * u * t; val c = 3 * u * t * t; val d = t * t * t
            return Pt(a * p0.x + b * p1.x + c * p2.x + d * p3.x, a * p0.y + b * p1.y + c * p2.y + d * p3.y)
        }
    }
}

/**
 * The randomised shape of one interior cut between two pieces, defined once in
 * a normalised frame from `(0, 0)` to `(1, 0)` with the tab bulging toward
 * `+y` in *tab units*. Both pieces sharing the cut derive their boundary from
 * the very same numbers. `headHalfWidth > neckHalfWidth` gives the undercut
 * that makes a real jigsaw piece lock into its neighbour.
 */
data class EdgeProfile(
    val polarity: Int,
    val center: Double,
    val neckHalfWidth: Double,
    val headHalfWidth: Double,
    val height: Double,
    val skew: Double,
    val bow1: Double, val bow2: Double, val bow3: Double, val bow4: Double,
) {
    /** The five cubic segments in normalised `(u, v)` space, `v` unsigned. */
    fun normalizedSegments(): List<CubicSegment> {
        val t = center
        val w = neckHalfWidth
        val b = headHalfWidth
        val h = height

        val neckLeft = w * (1 + skew); val neckRight = w * (1 - skew)
        val headLeft = b * (1 + skew * 0.5); val headRight = b * (1 - skew * 0.5)

        val a = Pt(t - neckLeft, 0.0)
        val c = Pt(t + neckRight, 0.0)
        val h1 = Pt(t - headLeft, 0.60 * h)
        val h2 = Pt(t + headRight, 0.60 * h)

        val s1 = CubicSegment(Pt(a.x * 0.32, bow1), Pt(a.x * 0.74, bow2), a)
        val s2 = CubicSegment(Pt(a.x + neckLeft * 0.55, 0.06 * h), Pt(h1.x - headLeft * 0.18, 0.30 * h), h1)
        val s3 = CubicSegment(Pt(h1.x + headLeft * 0.22, 1.18 * h), Pt(h2.x - headRight * 0.22, 1.18 * h), h2)
        val s4 = CubicSegment(Pt(h2.x + headRight * 0.18, 0.30 * h), Pt(c.x - neckRight * 0.55, 0.06 * h), c)
        val s5 = CubicSegment(Pt(c.x + (1 - c.x) * 0.26, bow3), Pt(c.x + (1 - c.x) * 0.68, bow4), Pt(1.0, 0.0))
        return listOf(s1, s2, s3, s4, s5)
    }

    /** Maps the profile onto a board-space edge; `normal` points to the "positive" neighbour. */
    fun curve(start: Pt, end: Pt, normal: Pt, amplitude: Double): EdgeCurve {
        val along = end - start
        val sign = polarity.toDouble()
        fun map(p: Pt) = Pt(
            start.x + along.x * p.x + normal.x * p.y * amplitude * sign,
            start.y + along.y * p.x + normal.y * p.y * amplitude * sign,
        )
        return EdgeCurve(start, normalizedSegments().map { CubicSegment(map(it.control1), map(it.control2), map(it.end)) })
    }

    companion object {
        fun random(rng: SplitMix64) = EdgeProfile(
            polarity = rng.polarity(),
            center = rng.double(0.455, 0.545),
            neckHalfWidth = rng.double(0.086, 0.104),
            headHalfWidth = rng.double(0.146, 0.174),
            height = rng.double(0.92, 1.10),
            skew = rng.double(-0.26, 0.26),
            bow1 = rng.double(-0.055, 0.055),
            bow2 = rng.double(-0.055, 0.055),
            bow3 = rng.double(-0.055, 0.055),
            bow4 = rng.double(-0.055, 0.055),
        )
    }
}
