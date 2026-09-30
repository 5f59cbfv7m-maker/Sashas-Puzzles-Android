package com.kirillrychkov.sashaspuzzles

import com.kirillrychkov.sashaspuzzles.engine.PuzzleGeometry
import com.kirillrychkov.sashaspuzzles.engine.PuzzleState
import com.kirillrychkov.sashaspuzzles.engine.Pt
import com.kirillrychkov.sashaspuzzles.engine.SplitMix64
import com.kirillrychkov.sashaspuzzles.engine.Viewport
import com.kirillrychkov.sashaspuzzles.engine.Sz
import com.kirillrychkov.sashaspuzzles.engine.Rect
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineTest {

    /** The invariant everything rests on: neighbours share one identical curve. */
    @Test
    fun neighbouringPiecesShareIdenticalEdges() {
        val geometry = PuzzleGeometry(7, 5, 1.4, 0xC0FFEEuL)
        for (piece in 0 until geometry.pieceCount) {
            val r = geometry.row(piece); val c = geometry.column(piece)
            val edges = geometry.outline(piece)
            if (c < geometry.columns - 1) {
                val right = edges[1].sampled()
                val neighbourLeft = geometry.outline(geometry.index(r, c + 1))[3].reversed.sampled()
                assertCurvesEqual(right, neighbourLeft)
            }
            if (r < geometry.rows - 1) {
                val bottom = edges[2].sampled()
                val neighbourTop = geometry.outline(geometry.index(r + 1, c))[0].reversed.sampled()
                assertCurvesEqual(bottom, neighbourTop)
            }
        }
    }

    @Test
    fun outlinesAreClosed() {
        val geometry = PuzzleGeometry(4, 4, 1.0, 42uL)
        for (piece in 0 until geometry.pieceCount) {
            val edges = geometry.outline(piece)
            for (i in edges.indices) {
                assertTrue(edges[i].end.approx(edges[(i + 1) % 4].start, 1e-9))
            }
        }
    }

    @Test
    fun geometryIsReproducibleFromSeed() {
        val a = PuzzleGeometry(6, 4, 1.5, 12345uL).outline(9)
        val b = PuzzleGeometry(6, 4, 1.5, 12345uL).outline(9)
        assertEquals(a, b)
    }

    /** Same numbers as the iOS generator, so the two apps cut identical puzzles. */
    @Test
    fun splitMixMatchesReference() {
        val rng = SplitMix64(0uL)
        assertEquals(0xE220A8397B1DCDAFuL, rng.next())
        assertEquals(0x6E789E6AA1B965F4uL, rng.next())
    }

    @Test
    fun gridStaysNearTarget() {
        for (target in listOf(12, 24, 48, 80, 150, 300, 500, 800)) {
            for (aspect in listOf(0.66, 1.0, 1.5, 1.78)) {
                val (columns, rows) = PuzzleGeometry.grid(target, aspect)
                assertTrue("$target @ $aspect → $columns×$rows", abs(columns * rows - target) <= target * 0.2)
            }
        }
    }

    @Test
    fun droppingAPieceAtHomeSnapsAndLocks() {
        val geometry = PuzzleGeometry(3, 3, 1.0, 7uL)
        val state = PuzzleState(3, 3, geometry.cellSize)
        val group = state.placeFromTray(4, Pt(3.0, -2.0))
        val outcome = state.settle(group, state.snapTolerance(1.0))
        assertTrue(outcome.didSnap)
        assertTrue(state.isLocked(4))
        state.returnToTray(4)
        assertTrue("a locked piece never goes back", state.isLocked(4))
    }

    @Test
    fun neighboursMergeWhenTheyLineUp() {
        val geometry = PuzzleGeometry(3, 3, 1.0, 7uL)
        val state = PuzzleState(3, 3, geometry.cellSize)
        val shift = Pt(900.0, 900.0)
        val a = state.placeFromTray(0, shift)
        val b = state.placeFromTray(1, shift + Pt(4.0, 3.0))
        val outcome = state.settle(b, state.snapTolerance(1.0))
        assertTrue(outcome.didMerge)
        assertEquals(state.group(0)?.id, state.group(1)?.id)
        assertFalse(state.isLocked(0))
        assertEquals(listOf(a), outcome.absorbedGroups)
    }

    @Test
    fun solvingEverythingCompletes() {
        val geometry = PuzzleGeometry(5, 4, 1.2, 99uL)
        val state = PuzzleState(5, 4, geometry.cellSize)
        state.solveAll()
        assertTrue(state.isComplete)
    }

    @Test
    fun stateSurvivesJsonRoundTrip() {
        val geometry = PuzzleGeometry(4, 3, 1.3, 5uL)
        val state = PuzzleState(4, 3, geometry.cellSize)
        state.shuffleTray(SplitMix64(5uL))
        state.placeFromTray(2, Pt(10.0, 20.0))
        val json = Json.encodeToString(PuzzleState.serializer(), state)
        val back = Json.decodeFromString(PuzzleState.serializer(), json)
        assertEquals(state.trayOrder, back.trayOrder)
        assertEquals(state.groups, back.groups)
        assertEquals(state.origin(2), back.origin(2))
    }

    @Test
    fun viewportRoundTripsAndFits() {
        val viewport = Viewport.fitting(Rect(0.0, 0.0, 1000.0, 1000.0), Sz(800.0, 600.0), 20.0, 0.01, 50.0)
        val point = Pt(123.0, 456.0)
        assertTrue(viewport.board(viewport.screen(point)).approx(point, 1e-9))
        val screen = viewport.screen(Rect(0.0, 0.0, 1000.0, 1000.0))
        assertTrue(screen.minY >= 19.9 && screen.maxY <= 580.1)
    }

    private fun assertCurvesEqual(a: List<Pt>, b: List<Pt>) {
        assertEquals(a.size, b.size)
        for (i in a.indices) assertTrue("${a[i]} vs ${b[i]}", a[i].approx(b[i], 1e-9))
    }
}
