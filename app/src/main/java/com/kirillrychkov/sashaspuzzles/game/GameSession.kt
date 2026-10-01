package com.kirillrychkov.sashaspuzzles.game

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kirillrychkov.sashaspuzzles.engine.Pt
import com.kirillrychkov.sashaspuzzles.engine.PuzzleGeometry
import com.kirillrychkov.sashaspuzzles.engine.PuzzleState
import com.kirillrychkov.sashaspuzzles.engine.Rect
import com.kirillrychkov.sashaspuzzles.engine.SettleOutcome
import com.kirillrychkov.sashaspuzzles.engine.SplitMix64
import com.kirillrychkov.sashaspuzzles.engine.Sz
import com.kirillrychkov.sashaspuzzles.engine.Viewport
import com.kirillrychkov.sashaspuzzles.engine.clamp
import com.kirillrychkov.sashaspuzzles.library.ImageStore
import com.kirillrychkov.sashaspuzzles.model.LibraryItem
import com.kirillrychkov.sashaspuzzles.model.PuzzleAspect
import com.kirillrychkov.sashaspuzzles.model.SnapAssist
import com.kirillrychkov.sashaspuzzles.persistence.GameSnapshot
import com.kirillrychkov.sashaspuzzles.persistence.SaveStore
import com.kirillrychkov.sashaspuzzles.render.PieceTextures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * One game in progress: geometry, placement, textures, clock and autosave.
 *
 * The session owns the logical game. Screens observe it and draw; they never
 * hold placement state of their own, which is what makes folding the phone —
 * or any resize — a pure re-layout with nothing to lose.
 */
class GameSession(
    val item: LibraryItem,
    val puzzleAspect: PuzzleAspect,
    val targetPieces: Int,
    private val saveStore: SaveStore,
    private val scope: CoroutineScope,
    seed: ULong = Random.nextLong().toULong().let { if (it == 0uL) 1uL else it },
    val id: String = UUID.randomUUID().toString(),
) {
    enum class Phase { PREPARING, PLAYING, PAUSED, COMPLETED }

    data class DragState(val group: Int, val grab: Pt, val startTranslation: Pt, val moved: Boolean = false)
    data class Hint(val piece: Int, val expires: Long)

    val geometry: PuzzleGeometry
    /** Where pieces may be scattered, in board units. */
    val tableRect: Rect
    val boardRect: Rect

    var state: PuzzleState
        private set
    private val shuffleSeed = seed

    var phase by mutableStateOf(Phase.PREPARING)
        private set
    var source: Bitmap? by mutableStateOf(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    val textures = PieceTextures(scope)

    var viewport by mutableStateOf(Viewport())
    var drag: DragState? = null
        private set
    /** Pieces flashing sage after a connection, by start time (uptime ms). */
    var flashes: Map<Int, Long> by mutableStateOf(emptyMap())
        private set
    var hint: Hint? by mutableStateOf(null)
        private set
    var selectedPiece: Int? = null

    /** Bumped on every change to placement, so the board redraws. */
    var version by mutableIntStateOf(0)
        private set
    /** The tray's pieces, replaced only when the tray itself changes. */
    var trayPieces: List<Int> by mutableStateOf(emptyList())
        private set
    /** Fraction of pieces joined to at least one neighbour. */
    var completion by mutableFloatStateOf(0f)
        private set
    var elapsedMillis by mutableLongStateOf(0L)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Fired once when the last group locks; the app records stats here. */
    var onComplete: ((GameSession) -> Unit)? = null

    private var accumulated = 0L
    private var runningSince: Long? = null
    private var ticker: Job? = null
    private var autosave: Job? = null
    private var effects: Job? = null
    private val undoStack = ArrayDeque<PuzzleState>()
    private val redoStack = ArrayDeque<PuzzleState>()
    private var drawOrderCache: Pair<Int, List<Int>>? = null
    private val polygons = HashMap<Int, DoubleArray>()

    // Board view facts, set by the board as it lays out.
    var viewSize = Sz(0.0, 0.0)
        private set
    var density = 2.0
        private set
    private var fittedSize = Sz(0.0, 0.0)
    /** The zoom buttons' corner, measured from the board's bottom-right edges, in pixels. */
    private var controls = Sz(0.0, 0.0)
    private var fittedViewport: Viewport? = null
    private var textureRefresh: Job? = null
    private var refit: Job? = null
    private var showOutlines = true

    init {
        val boardAspect = puzzleAspect.ratio ?: item.aspect
        val (columns, rows) = PuzzleGeometry.grid(targetPieces, boardAspect)
        geometry = PuzzleGeometry(columns, rows, boardAspect, seed)
        state = PuzzleState(columns, rows, geometry.cellSize)
        boardRect = Rect(0.0, 0.0, geometry.boardSize.width, geometry.boardSize.height)
        tableRect = boardRect.inset(-boardRect.width * 0.62, -boardRect.height * 0.62)
        changed()
    }

    /** Rebuilds a session from a save; geometry regenerates bit for bit from the seed. */
    constructor(snapshot: GameSnapshot, saveStore: SaveStore, scope: CoroutineScope) : this(
        snapshot.libraryItem, snapshot.puzzleAspect, snapshot.targetPieces, saveStore, scope, snapshot.seed, snapshot.id,
    ) {
        state = snapshot.state
        accumulated = snapshot.elapsedMillis
        elapsedMillis = snapshot.elapsedMillis
        if (snapshot.isComplete) phase = Phase.COMPLETED
        changed()
    }

    val pieceCount: Int get() = geometry.pieceCount
    val placedCount: Int get() = pieceCount - trayPieces.size
    val isLoaded: Boolean get() = source != null

    private fun changed() {
        version++
        if (trayPieces != state.trayOrder) trayPieces = state.trayOrder.toList()
        completion = if (pieceCount > 0) state.connectedCount.toFloat() / pieceCount else 0f
    }

    // Loading

    suspend fun load(images: ImageStore, outlines: Boolean) {
        showOutlines = outlines
        val longSide = clamp(maxOf(geometry.boardSize.width, geometry.boardSize.height) * 2.2, 1400.0, 2800.0).toInt()
        val image = images.image(ImageStore.Request(item, puzzleAspect, longSide))
        if (image == null) {
            loadFailed = true
            phase = Phase.PAUSED
            return
        }
        source = image
        if (placedCount == 0 && elapsedMillis == 0L) {
            state.shuffleTray(SplitMix64(shuffleSeed))
            changed()
        }
        textures.rebuild(geometry, image, maxOf(1.0, viewport.scale), outlines)
        if (phase != Phase.COMPLETED) {
            phase = Phase.PLAYING
            startClock()
        }
    }

    fun setOutlines(outlines: Boolean) {
        if (outlines == showOutlines) return
        showOutlines = outlines
        refreshTextures()
    }

    private fun refreshTextures() {
        val image = source ?: return
        textures.rebuild(geometry, image, maxOf(0.6, viewport.scale), showOutlines)
    }

    // Clock

    private fun startClock() {
        if (runningSince != null) return
        runningSince = SystemClock.elapsedRealtime()
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(250)
                tick()
            }
        }
    }

    private fun tick() {
        val since = runningSince ?: return
        elapsedMillis = accumulated + (SystemClock.elapsedRealtime() - since)
    }

    private fun stopClock() {
        runningSince?.let {
            accumulated += SystemClock.elapsedRealtime() - it
            elapsedMillis = accumulated
        }
        runningSince = null
        ticker?.cancel()
        ticker = null
    }

    fun pause() {
        if (phase != Phase.PLAYING) return
        phase = Phase.PAUSED
        drag = null
        stopClock()
        saveNow()
    }

    fun resume() {
        if (phase != Phase.PAUSED || loadFailed) return
        phase = Phase.PLAYING
        startClock()
    }

    /** The app left the foreground: the clock must not run while the player is elsewhere. */
    fun handleBackground() {
        if (phase == Phase.PLAYING) pause()
        saveNow()
    }

    // Draw order

    /** Groups back to front; clusters at home sink so loose pieces are never hidden. */
    val drawOrder: List<Int>
        get() {
            drawOrderCache?.let { if (it.first == state.structureRevision) return it.second }
            val order = state.groups.values
                .sortedWith(compareBy<com.kirillrychkov.sashaspuzzles.engine.PieceGroup> { !it.isHome }.thenBy { it.z })
                .map { it.id }
            drawOrderCache = state.structureRevision to order
            return order
        }

    // Hit testing

    /** Sampled outline of a piece relative to its cell origin, for point-in-polygon tests. */
    private fun polygon(piece: Int): DoubleArray = polygons.getOrPut(piece) {
        val points = geometry.localOutline(piece).flatMap { it.sampled(6).drop(1) }
        DoubleArray(points.size * 2).also { for ((i, p) in points.withIndex()) { it[2 * i] = p.x; it[2 * i + 1] = p.y } }
    }

    private fun inside(polygon: DoubleArray, x: Double, y: Double): Boolean {
        var inside = false
        val n = polygon.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = polygon[2 * i]; val yi = polygon[2 * i + 1]
            val xj = polygon[2 * j]; val yj = polygon[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * Piece under a board point. The exact outline wins for a loose piece;
     * otherwise a loose piece is also caught by its cell plus a finger's slack —
     * a piece with four blanks is mostly holes, and a tap into one used to miss.
     */
    fun piece(point: Pt): Int? {
        val order = drawOrder.asReversed()
        val bounds = textures.localBounds
        var exact: Int? = null
        outer@ for (groupId in order) {
            val group = state.groups[groupId] ?: continue
            for (piece in group.members) {
                val local = point - (state.solvedOrigin(piece) + group.translation)
                val box = bounds.getOrNull(piece) ?: continue
                if (!box.contains(local) || !inside(polygon(piece), local.x, local.y)) continue
                exact = piece
                break@outer
            }
        }
        if (exact != null && state.group(exact)?.isLocked == false) return exact

        val slack = state.cellSize.minimumSide * 0.12
        val cell = Rect(-slack, -slack, state.cellWidth + 2 * slack, state.cellHeight + 2 * slack)
        for (groupId in order) {
            val group = state.groups[groupId] ?: continue
            if (group.isLocked) continue
            for (piece in group.members) {
                if (cell.contains(point - (state.solvedOrigin(piece) + group.translation))) return piece
            }
        }
        return exact
    }

    // Dragging

    /** Starts dragging the cluster under `point`; false lets the gesture pan the board. */
    fun beginDrag(point: Pt): Boolean {
        if (phase != Phase.PLAYING) return false
        val piece = piece(point) ?: return false
        val group = state.group(piece) ?: return false
        if (group.isLocked) return false
        pushUndo()
        state.bringToFront(group.id)
        selectedPiece = piece
        drag = DragState(group.id, point, group.translation)
        changed()
        return true
    }

    fun updateDrag(point: Pt) {
        val current = drag ?: return
        if (phase != Phase.PLAYING) return
        val delta = point - current.grab
        if (abs(delta.x) + abs(delta.y) > 0.5 && !current.moved) drag = current.copy(moved = true)
        state.setTranslation(current.startTranslation + delta, current.group)
        changed()
    }

    fun endDrag(assist: SnapAssist): SettleOutcome? {
        val current = drag ?: return null
        drag = null
        if (!current.moved) {
            undoStack.removeLastOrNull()
            refreshUndoFlags()
            changed()
            return null
        }
        val outcome = state.settle(current.group, state.snapTolerance(viewport.scale / density, assist.multiplier))
        afterSettle(outcome, outcome.connectedPieces.ifEmpty { state.groups[outcome.group]?.members ?: emptyList() })
        return outcome
    }

    fun cancelDrag() {
        val current = drag ?: return
        state.setTranslation(current.startTranslation, current.group)
        drag = null
        undoStack.removeLastOrNull()
        refreshUndoFlags()
        changed()
    }

    /** Drops a tray piece onto the table at a board point. */
    fun placeFromTray(piece: Int, point: Pt, assist: SnapAssist): SettleOutcome? {
        if (phase != Phase.PLAYING) return null
        pushUndo()
        val centre = state.solvedOrigin(piece) + Pt(geometry.cellSize.width / 2, geometry.cellSize.height / 2)
        val group = state.placeFromTray(piece, point - centre)
        selectedPiece = piece
        val outcome = state.settle(group, state.snapTolerance(viewport.scale / density, assist.multiplier))
        afterSettle(outcome, outcome.connectedPieces + piece)
        return outcome
    }

    private fun afterSettle(outcome: SettleOutcome, flashed: List<Int>) {
        if (outcome.didSnap) {
            val now = SystemClock.uptimeMillis()
            flashes = flashes + flashed.associateWith { now }
            scheduleEffectsExpiry()
        }
        changed()
        if (outcome.didComplete) finish()
        scheduleAutosave()
    }

    /** Sends a placed piece back to the tray (long press). Locked pieces ignore it. */
    fun returnPieceToTray(piece: Int) {
        if (phase != Phase.PLAYING) return
        val group = state.group(piece) ?: return
        if (group.isLocked) return
        pushUndo()
        state.returnToTray(piece)
        if (selectedPiece == piece) selectedPiece = null
        changed()
        scheduleAutosave()
    }

    enum class TrayAction { SCATTER, GATHER }

    /** Empty the tray while it holds pieces, then pull loose ones back: one button, two presses. */
    val trayAction: TrayAction?
        get() {
            if (phase != Phase.PLAYING) return null
            if (trayPieces.isNotEmpty()) return TrayAction.SCATTER
            version // observed: loose singles change with placement
            return if (state.hasLooseSingles) TrayAction.GATHER else null
        }

    fun performTrayAction() {
        when (trayAction) {
            TrayAction.SCATTER -> {
                pushUndo()
                state.scatterTray(tableRect, boardRect, SplitMix64(shuffleSeed + state.trayOrder.size.toULong()))
            }
            TrayAction.GATHER -> {
                pushUndo()
                state.gatherLooseSingles()
                selectedPiece?.let { if (state.group(it) == null) selectedPiece = null }
            }
            null -> return
        }
        changed()
        scheduleAutosave()
    }

    // Assistance

    /** Highlights where the selected (or a waiting) piece belongs. It never moves a piece. */
    fun requestHint() {
        if (phase != Phase.PLAYING) return
        val selected = selectedPiece
        val candidate = when {
            selected != null && state.group(selected)?.isHome != true -> selected
            state.trayOrder.isNotEmpty() -> state.trayOrder.first()
            else -> state.groups.values.filter { !it.isHome }.minByOrNull { it.members.size }?.members?.firstOrNull()
        } ?: return
        selectedPiece = candidate
        hint = Hint(candidate, SystemClock.uptimeMillis() + 3000)
        scheduleEffectsExpiry()
    }

    private fun scheduleEffectsExpiry() {
        effects?.cancel()
        val deadlines = flashes.values.map { it + FLASH_MILLIS } + listOfNotNull(hint?.expires)
        val next = deadlines.minOrNull() ?: return
        effects = scope.launch {
            delay(maxOf(0L, next - SystemClock.uptimeMillis()) + 50)
            val now = SystemClock.uptimeMillis()
            hint?.let { if (it.expires < now) hint = null }
            flashes = flashes.filterValues { now - it < FLASH_MILLIS }
            scheduleEffectsExpiry()
        }
    }

    /** True while something on the board animates on its own. */
    val needsAnimation: Boolean get() = flashes.isNotEmpty() || hint != null

    // Undo / redo

    private fun pushUndo() {
        undoStack.addLast(state.copy())
        if (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        refreshUndoFlags()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(state)
        state = previous
        drag = null
        refreshUndoFlags()
        changed()
        scheduleAutosave()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(state)
        state = next
        drag = null
        refreshUndoFlags()
        changed()
        scheduleAutosave()
    }

    private fun refreshUndoFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // Completion

    private fun finish() {
        stopClock()
        phase = Phase.COMPLETED
        drag = null
        hint = null
        for (id in state.groups.keys.toList()) state.setTranslation(Pt.ZERO, id)
        changed()
        saveNow()
        onComplete?.invoke(this)
    }

    // Viewport policy

    private val minScale get() = MIN_SCALE * density
    private val maxScale get() = MAX_SCALE * density

    /** The board reports its size; the first report fits the board, later ones only clamp. */
    fun boardLaidOut(size: Sz, density: Double) {
        this.density = density
        val first = viewSize.width < 1
        viewSize = size
        if (first || viewport.scale == 1.0) fitBoard() else handleResize()
    }

    /**
     * Pulls the table back into view after the board changes size — folding,
     * unfolding, rotating, split screen. Only the offset is nudged; when the
     * shape flips between portrait and landscape the board is re-fitted once
     * the sizes settle.
     */
    private fun handleResize() {
        if (viewSize.width <= 1 || viewSize.height <= 1) return
        val adjusted = viewport.clamped(tableRect, viewSize)
        if (adjusted != viewport) viewport = adjusted
        val flipped = (fittedSize.width > fittedSize.height) != (viewSize.width > viewSize.height)
        val grew = abs(viewSize.width - fittedSize.width) > fittedSize.width * 0.3
        if (flipped || grew) {
            refit?.cancel()
            refit = scope.launch {
                delay(150)
                fitBoard()
            }
        }
    }

    /** The zoom buttons report their corner; a board still sitting where it was fitted is re-fitted around them. */
    fun controlsLaidOut(size: Sz) {
        if (size == controls) return
        controls = size
        if (fittedViewport != null && viewport == fittedViewport) fitBoard()
    }

    /**
     * Fits the picture to the board, clear of the zoom buttons: when they would
     * cover a corner, the picture moves out of their column or above their
     * row, whichever leaves it larger.
     */
    fun fitBoard() {
        if (viewSize.width <= 1) return
        // A phone-sized board can't spare 40dp on each side; the picture gets it instead.
        val roomy = min(viewSize.width, viewSize.height) / density >= 480
        val padding = (if (roomy) 40 else 12) * density
        val whole = Rect(0.0, 0.0, viewSize.width, viewSize.height)
        var fit = Viewport.fitting(boardRect, whole, padding, minScale, maxScale)
        val frame = fit.screen(boardRect)
        val gap = 6 * density
        if (controls.width > 0 && frame.maxX > viewSize.width - controls.width - gap && frame.maxY > viewSize.height - controls.height - gap) {
            val beside = Viewport.fitting(boardRect, Rect(0.0, 0.0, viewSize.width - controls.width, viewSize.height), padding, minScale, maxScale)
            val above = Viewport.fitting(boardRect, Rect(0.0, 0.0, viewSize.width, viewSize.height - controls.height), padding, minScale, maxScale)
            fit = if (beside.scale >= above.scale) beside else above
        }
        viewport = fit
        fittedViewport = fit
        fittedSize = viewSize
        scheduleTextureRefresh()
    }

    fun fitTable() {
        if (viewSize.width <= 1) return
        viewport = Viewport.fitting(tableRect, viewSize, 16 * density, minScale, maxScale)
        fittedSize = viewSize
        scheduleTextureRefresh()
    }

    fun pan(dx: Double, dy: Double) {
        viewport = viewport.panned(dx, dy).clamped(tableRect, viewSize)
    }

    fun zoom(factor: Double, anchor: Pt) {
        if (!factor.isFinite() || factor <= 0) return
        viewport = viewport.zoomed(factor, anchor, minScale, maxScale).clamped(tableRect, viewSize)
        scheduleTextureRefresh()
    }

    fun zoomStep(factor: Double) = zoom(factor, Pt(viewSize.width / 2, viewSize.height / 2))

    /** Re-cuts the bitmaps once zooming stops, so a magnified board stays crisp. */
    private fun scheduleTextureRefresh() {
        textureRefresh?.cancel()
        textureRefresh = scope.launch {
            delay(350)
            refreshTextures()
        }
    }

    // Persistence

    fun snapshot() = GameSnapshot(
        id = id, itemId = item.id, itemTitle = item.title, category = item.category, source = item.source,
        imageAspect = item.aspect, puzzleAspect = puzzleAspect, targetPieces = targetPieces,
        columns = geometry.columns, rows = geometry.rows, seed = geometry.seed, elapsedMillis = elapsedMillis,
        state = state, updatedAt = System.currentTimeMillis(), isComplete = phase == Phase.COMPLETED,
    )

    private fun scheduleAutosave() {
        autosave?.cancel()
        autosave = scope.launch {
            delay(2000)
            saveNow()
        }
    }

    fun saveNow() {
        autosave?.cancel()
        autosave = null
        if (phase == Phase.PREPARING && placedCount == 0) return
        runCatching { saveStore.save(snapshot()) }
    }

    /** Stops every background job; the session is being dropped. */
    fun close() {
        ticker?.cancel(); autosave?.cancel(); effects?.cancel(); textureRefresh?.cancel(); refit?.cancel()
        textures.cancel()
    }

    /** Debug shortcut: solves the board at once. */
    fun solveImmediately() {
        pushUndo()
        state.solveAll()
        changed()
        finish()
    }

    companion object {
        const val FLASH_MILLIS = 850L
        private const val UNDO_LIMIT = 40
        /** Board-unit scale limits in dp, as on iOS (points). */
        private const val MIN_SCALE = 0.08
        private const val MAX_SCALE = 8.0
    }
}
