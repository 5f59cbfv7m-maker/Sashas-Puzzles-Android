package com.kirillrychkov.sashaspuzzles.engine

import kotlin.math.max
import kotlin.math.min

/**
 * Maps board units to screen pixels.
 *
 * Keeping the transform in one small value is what makes folding and
 * unfolding the phone harmless: the puzzle's logical coordinates never
 * change, only this mapping does.
 */
data class Viewport(val scale: Double = 1.0, val offsetX: Double = 0.0, val offsetY: Double = 0.0) {

    fun screen(p: Pt) = Pt(p.x * scale + offsetX, p.y * scale + offsetY)
    fun board(p: Pt) = Pt((p.x - offsetX) / scale, (p.y - offsetY) / scale)
    fun screen(r: Rect) = Rect(r.x * scale + offsetX, r.y * scale + offsetY, r.width * scale, r.height * scale)

    /** Zooms around a fixed screen point, so the content under the fingers stays put. */
    fun zoomed(factor: Double, anchor: Pt, minimumScale: Double, maximumScale: Double): Viewport {
        val newScale = clamp(scale * factor, minimumScale, maximumScale)
        val effective = newScale / scale
        return Viewport(newScale,
            anchor.x - (anchor.x - offsetX) * effective,
            anchor.y - (anchor.y - offsetY) * effective)
    }

    fun panned(dx: Double, dy: Double) = Viewport(scale, offsetX + dx, offsetY + dy)

    /** Keeps the content from being dragged entirely off screen. */
    fun clamped(content: Rect, view: Sz): Viewport {
        val frame = screen(content)
        val slackX = max(view.width * 0.35, 80.0)
        val slackY = max(view.height * 0.35, 80.0)
        var x = offsetX; var y = offsetY
        if (frame.maxX < slackX) x += slackX - frame.maxX
        if (frame.minX > view.width - slackX) x -= frame.minX - (view.width - slackX)
        if (frame.maxY < slackY) y += slackY - frame.maxY
        if (frame.minY > view.height - slackY) y -= frame.minY - (view.height - slackY)
        return Viewport(scale, x, y)
    }

    companion object {
        /** Fits `content` inside `view` with a margin, centred. */
        fun fitting(content: Rect, view: Sz, padding: Double, minimumScale: Double, maximumScale: Double): Viewport =
            fitting(content, Rect(0.0, 0.0, view.width, view.height), padding, minimumScale, maximumScale)

        /** Fits `content` inside `area`, a part of the view, with a margin, centred in it. */
        fun fitting(content: Rect, area: Rect, padding: Double, minimumScale: Double, maximumScale: Double): Viewport {
            if (content.width <= 0 || content.height <= 0 || area.width <= 0 || area.height <= 0) return Viewport()
            val w = max(1.0, area.width - padding * 2)
            val h = max(1.0, area.height - padding * 2)
            val scale = clamp(min(w / content.width, h / content.height), minimumScale, maximumScale)
            return Viewport(scale, area.midX - content.midX * scale, area.midY - content.midY * scale)
        }
    }
}
