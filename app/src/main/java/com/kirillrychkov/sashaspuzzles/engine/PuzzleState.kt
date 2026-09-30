package com.kirillrychkov.sashaspuzzles.engine

import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * A rigid cluster of connected pieces. Members are always in their exact solved
 * relationship, so the whole cluster is one `translation` — its offset from the
 * solved layout. Immutable: a change replaces the entry in the state's map,
 * which keeps an undo snapshot a cheap shallow copy.
 */
@Serializable
data class PieceGroup(val id: Int, val members: List<Int>, val translation: Pt, val z: Int) {
    val isHome: Boolean get() = translation.approx(Pt.ZERO, 0.001)
    /** A cluster at home is part of the finished picture and never moves again. */
    val isLocked: Boolean get() = isHome
}

/** What happened when a dragged group was released. */
data class SettleOutcome(
    val didSnap: Boolean = false,
    val absorbedGroups: List<Int> = emptyList(),
    val group: Int = -1,
    /** Pieces that gained a new connection — used for the green flash. */
    val connectedPieces: List<Int> = emptyList(),
    val didComplete: Boolean = false,
) {
    val didMerge: Boolean get() = absorbedGroups.isNotEmpty()
}

/**
 * Placement of every piece: the pure, testable core of the game.
 *
 * Two pieces are correctly joined **iff their groups share the same
 * translation**. That one invariant replaces per-edge bookkeeping and makes
 * snapping, merging and completion trivial and impossible to corrupt.
 */
@Serializable
class PuzzleState private constructor(
    val columns: Int,
    val rows: Int,
    val cellWidth: Double,
    val cellHeight: Double,
    /** Group id per piece; `-1` while the piece is still in the tray. */
    private val pieceGroup: IntArray,
    private val groupMap: HashMap<Int, PieceGroup>,
    /** Pieces waiting in the tray, in shuffled display order. */
    private val tray: ArrayList<Int>,
    private var nextGroupId: Int = 0,
    private var zCounter: Int = 0,
) {
    /** Bumped whenever membership, z-order or the tray changes — not on pure movement. */
    @kotlinx.serialization.Transient
    var structureRevision: Int = 0
        private set

    constructor(columns: Int, rows: Int, cellSize: Sz) : this(
        columns, rows, cellSize.width, cellSize.height,
        IntArray(rows * columns) { -1 }, HashMap(), ArrayList((0 until rows * columns).toList()),
    )

    val cellSize: Sz get() = Sz(cellWidth, cellHeight)
    val groups: Map<Int, PieceGroup> get() = groupMap
    val trayOrder: List<Int> get() = tray

    val pieceCount: Int get() = rows * columns
    val placedCount: Int get() = pieceCount - tray.size
    /** Pieces that sit in a cluster of two or more. */
    val connectedCount: Int get() = groupMap.values.sumOf { if (it.members.size > 1) it.members.size else 0 }
    val isComplete: Boolean get() = tray.isEmpty() && groupMap.size == 1

    /** An independent copy for the undo stack. Groups are immutable, so this is shallow. */
    fun copy(): PuzzleState = PuzzleState(columns, rows, cellWidth, cellHeight, pieceGroup.copyOf(),
        HashMap(groupMap), ArrayList(tray), nextGroupId, zCounter).also { it.structureRevision = structureRevision + 1 }

    // Geometry helpers

    fun row(piece: Int) = piece / columns
    fun column(piece: Int) = piece % columns
    fun solvedOrigin(piece: Int) = Pt(column(piece) * cellWidth, row(piece) * cellHeight)

    /** Current top-left of a piece's cell, or null while it is in the tray. */
    fun origin(piece: Int): Pt? = group(piece)?.let { solvedOrigin(piece) + it.translation }

    fun group(piece: Int): PieceGroup? {
        val id = pieceGroup[piece]
        return if (id >= 0) groupMap[id] else null
    }

    fun isLocked(piece: Int) = group(piece)?.isLocked ?: false

    fun neighbors(piece: Int): List<Int> {
        val r = row(piece); val c = column(piece)
        val result = ArrayList<Int>(4)
        if (r > 0) result.add(piece - columns)
        if (r < rows - 1) result.add(piece + columns)
        if (c > 0) result.add(piece - 1)
        if (c < columns - 1) result.add(piece + 1)
        return result
    }

    /**
     * Snap radius in board units. Scales with piece size so it feels the same on
     * 12 and 800 pieces, and with zoom so it feels the same on screen.
     * `viewScale` is screen dp per board unit.
     */
    fun snapTolerance(viewScale: Double, assist: Double = 1.0): Double {
        val side = cellSize.minimumSide
        val screenBased = if (viewScale > 0) 12 / viewScale else side * 0.2
        return clamp(max(side * 0.20, screenBased) * assist, side * 0.12, side * 0.55)
    }

    // Tray

    fun shuffleTray(rng: SplitMix64) {
        val shuffled = rng.shuffled(tray)
        tray.clear(); tray.addAll(shuffled)
        structureRevision++
    }

    /** Moves a piece out of the tray onto the table at the given group translation. */
    fun placeFromTray(piece: Int, translation: Pt): Int {
        if (pieceGroup[piece] >= 0) return pieceGroup[piece]
        tray.remove(piece)
        zCounter++
        val id = nextGroupId++
        groupMap[id] = PieceGroup(id, listOf(piece), translation, zCounter)
        pieceGroup[piece] = id
        structureRevision++
        return id
    }

    /** Sends one piece back to the tray, splitting it off its group. Locked pieces stay. */
    fun returnToTray(piece: Int) {
        val id = pieceGroup[piece]
        val group = if (id >= 0) groupMap[id] else null
        if (group == null || group.isLocked) return
        val members = group.members - piece
        if (members.isEmpty()) groupMap.remove(id) else groupMap[id] = group.copy(members = members)
        pieceGroup[piece] = -1
        if (piece !in tray) tray.add(piece)
        structureRevision++
    }

    /** Single pieces on the table, neither joined nor locked, bottom first. */
    val looseSingles: List<Int>
        get() = groupMap.values.filter { it.members.size == 1 && !it.isLocked }.sortedBy { it.z }.map { it.members[0] }

    val hasLooseSingles: Boolean get() = groupMap.values.any { it.members.size == 1 && !it.isLocked }

    /** The undo of a scatter. Joined clusters stay — a join is always correct. */
    fun gatherLooseSingles() {
        for (piece in looseSingles) returnToTray(piece)
    }

    /** Empties the tray onto the table inside `area`, avoiding the board where possible. */
    fun scatterTray(area: Rect, board: Rect, rng: SplitMix64) {
        val avoid = board.inset(-cellWidth * 0.2, -cellHeight * 0.2)
        for (piece in tray.toList()) {
            var target = Pt.ZERO
            for (attempt in 0 until 12) {
                val candidate = rng.point(area.inset(cellWidth, cellHeight))
                target = candidate
                if (!avoid.contains(candidate)) break
            }
            placeFromTray(piece, target - solvedOrigin(piece))
        }
    }

    // Movement

    fun bringToFront(id: Int) {
        val group = groupMap[id] ?: return
        zCounter++
        groupMap[id] = group.copy(z = zCounter)
        structureRevision++
    }

    fun setTranslation(translation: Pt, id: Int) {
        val group = groupMap[id] ?: return
        groupMap[id] = group.copy(translation = translation)
    }

    fun move(id: Int, delta: Pt) {
        val group = groupMap[id] ?: return
        setTranslation(group.translation + delta, id)
    }

    // Snapping & merging

    /** Home plus the translation of every group this one touches. */
    fun snapCandidates(id: Int): List<Pt> {
        val group = groupMap[id] ?: return emptyList()
        val candidates = arrayListOf(Pt.ZERO)
        val seen = hashSetOf(id)
        for (piece in group.members) {
            for (neighbor in neighbors(piece)) {
                val other = pieceGroup[neighbor]
                val target = if (other >= 0) groupMap[other] else null
                if (target == null || !seen.add(other)) continue
                candidates.add(target.translation)
            }
        }
        return candidates
    }

    /**
     * Releases a group: snaps it if a valid position is within `tolerance`,
     * then absorbs every neighbouring group that now lines up — transitively,
     * so a piece dropped into a gap can join four clusters at once.
     */
    fun settle(id: Int, tolerance: Double): SettleOutcome {
        val group = groupMap[id] ?: return SettleOutcome(group = id)
        var best: Pt? = null
        var bestDistance = Double.MAX_VALUE
        for (candidate in snapCandidates(id)) {
            val distance = candidate.distance(group.translation)
            if (distance <= tolerance && distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        val target = best ?: return SettleOutcome(group = id)
        setTranslation(target, id)
        val absorbed = ArrayList<Int>()
        val connected = mergeNeighbours(id, target, absorbed)
        return SettleOutcome(didSnap = true, absorbedGroups = absorbed, group = id,
            connectedPieces = connected, didComplete = isComplete)
    }

    /** Breadth-first absorption of every touching group sharing our translation. */
    private fun mergeNeighbours(id: Int, translation: Pt, absorbed: MutableList<Int>): List<Int> {
        val frontier = ArrayList(groupMap[id]?.members ?: emptyList())
        val visited = HashSet(frontier)
        val connected = LinkedHashSet<Int>()
        while (frontier.isNotEmpty()) {
            val piece = frontier.removeAt(frontier.size - 1)
            for (neighbor in neighbors(piece)) {
                val otherId = pieceGroup[neighbor]
                if (otherId < 0 || otherId == id) continue
                val other = groupMap[otherId] ?: continue
                if (!other.translation.approx(translation, 0.01)) continue
                connected.add(piece); connected.add(neighbor)
                absorbed.add(otherId)
                for (member in other.members) {
                    pieceGroup[member] = id
                    if (visited.add(member)) frontier.add(member)
                }
                val self = groupMap.getValue(id)
                groupMap[id] = self.copy(members = self.members + other.members)
                groupMap.remove(otherId)
                structureRevision++
            }
        }
        return connected.toList()
    }

    /** Instantly solves the puzzle. Tests and the debug shortcut only. */
    fun solveAll() {
        for (piece in tray.toList()) placeFromTray(piece, Pt.ZERO)
        val ids = groupMap.keys.toList()
        for (id in ids) setTranslation(Pt.ZERO, id)
        val anchor = ids.firstOrNull() ?: return
        mergeNeighbours(anchor, Pt.ZERO, ArrayList())
    }
}
