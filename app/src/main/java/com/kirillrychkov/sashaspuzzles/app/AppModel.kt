package com.kirillrychkov.sashaspuzzles.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kirillrychkov.sashaspuzzles.game.GameSession
import com.kirillrychkov.sashaspuzzles.library.ImageStore
import com.kirillrychkov.sashaspuzzles.library.Library
import com.kirillrychkov.sashaspuzzles.model.LibraryItem
import com.kirillrychkov.sashaspuzzles.model.PuzzleAspect
import com.kirillrychkov.sashaspuzzles.persistence.Achievement
import com.kirillrychkov.sashaspuzzles.persistence.CompletionSummary
import com.kirillrychkov.sashaspuzzles.persistence.GameSnapshot
import com.kirillrychkov.sashaspuzzles.persistence.PlayerStats
import com.kirillrychkov.sashaspuzzles.persistence.SaveStore
import com.kirillrychkov.sashaspuzzles.persistence.SolvedRecord
import com.kirillrychkov.sashaspuzzles.settings.AppSettings
import com.kirillrychkov.sashaspuzzles.support.Feedback
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Application-level state: preferences, library, navigation and the live game.
 *
 * It lives in the `Application`, not the activity, so nothing the player is
 * doing depends on the activity surviving — folding the phone, changing the
 * language or the system reclaiming the window never loses the game.
 */
class AppModel(context: Context) {

    sealed interface Route {
        data object Home : Route
        data class Setup(val itemId: String) : Route
        data object Game : Route
    }

    enum class Sheet { ONBOARDING, SETTINGS, PROFILE }

    val scope = MainScope()
    val settings = AppSettings(context)
    val library = Library(context)
    val images = ImageStore(context)
    val feedback = Feedback(context)
    private val saveStore = SaveStore(context.filesDir)
    val stats = PlayerStats(context.filesDir, library)

    var route: Route by mutableStateOf(Route.Home)
        private set
    /** The one modal over the screens. */
    var sheet: Sheet? by mutableStateOf(null)
    var session: GameSession? by mutableStateOf(null)
        private set
    /** Record and achievement news for the game that just finished. */
    var lastCompletion: CompletionSummary? by mutableStateOf(null)
        private set
    var savedGames: List<GameSnapshot> by mutableStateOf(emptyList())
        private set

    /** Debug stages only: the medals the next completion reveals, whatever was earned. */
    private var forcedAchievements: List<Achievement>? = null

    init {
        refreshSaves()
        if (settings.seenAchievements == null) markAchievementsSeen()
    }

    /** Unlocked achievements the player has not looked at in the profile yet. */
    val unseenAchievements: List<Achievement>
        get() {
            val seen = settings.seenAchievements.orEmpty()
            return stats.unlocked.filter { it.key !in seen }
        }

    fun markAchievementsSeen() {
        settings.seenAchievements = stats.unlocked.map { it.key }.toSet()
    }

    val resumable: List<GameSnapshot> get() = savedGames.filter { !it.isComplete }

    fun refreshSaves() {
        savedGames = saveStore.load()
    }

    // Navigation

    fun openSetup(item: LibraryItem) {
        route = Route.Setup(item.id)
    }

    fun showLibrary() {
        dropSession()
        route = Route.Home
        refreshSaves()
    }

    fun start(item: LibraryItem, aspect: PuzzleAspect, pieces: Int) {
        dropSession()
        attach(GameSession(item, aspect, pieces, saveStore, scope))
        route = Route.Game
    }

    fun startDaily() = start(library.dailyItem(), PuzzleAspect.ORIGINAL, Library.DAILY_PIECES)

    fun resume(snapshot: GameSnapshot) {
        dropSession()
        attach(GameSession(snapshot, saveStore, scope))
        route = Route.Game
    }

    fun restartCurrent() {
        val current = session ?: return
        start(current.item, current.puzzleAspect, current.targetPieces)
    }

    /** Back from wherever the player is: the game and the setup both lead home. */
    fun back(): Boolean {
        if (sheet == Sheet.SETTINGS || sheet == Sheet.PROFILE) { sheet = null; return true }
        if (route != Route.Home) { showLibrary(); return true }
        return false
    }

    private fun dropSession() {
        session?.let {
            it.saveNow()
            it.close()
        }
        session = null
    }

    private fun attach(new: GameSession) {
        lastCompletion = null
        new.onComplete = { finished ->
            val summary = stats.record(SolvedRecord(
                itemId = finished.item.id, category = finished.item.category, pieces = finished.pieceCount,
                targetPieces = finished.targetPieces, elapsedMillis = finished.elapsedMillis,
                date = System.currentTimeMillis(), isUserPhoto = finished.item.isUserPhoto,
            ))
            lastCompletion = forcedAchievements?.let { summary.copy(newAchievements = it) } ?: summary
        }
        session = new
    }

    // Saves

    fun delete(snapshot: GameSnapshot) {
        saveStore.delete(snapshot.id)
        refreshSaves()
    }

    fun deleteAllSaves() {
        saveStore.deleteAll()
        refreshSaves()
    }

    /**
     * Debug builds only: drives the app into a named state so a screen can be
     * checked without playing to it — `adb shell am start -n …/.MainActivity --es stage completed`.
     * Stages: `board` (48 pieces), `scattered`, `completed` (12 pieces, solved),
     * `profile` (every medal earned so far marked new). `--es achievements sprinter,nightmare`
     * makes the completion reveal those medals.
     */
    fun runStage(stage: String, achievements: String? = null) {
        val item = library.builtIn.getOrNull(3) ?: return
        settings.hasSeenOnboarding = true
        sheet = null
        forcedAchievements = achievements?.split(',')?.mapNotNull { Achievement.fromKey(it.trim()) }
        if (stage == "profile") {
            settings.seenAchievements = emptySet()
            sheet = Sheet.PROFILE
            return
        }
        start(item, PuzzleAspect.ORIGINAL, if (stage == "completed") 12 else 48)
        val live = session ?: return
        scope.launch {
            while (!live.textures.isReady) delay(100)
            when (stage) {
                "scattered" -> live.performTrayAction()
                "completed" -> live.solveImmediately()
            }
        }
    }

    /** The app went to the background: stop the clock and write the table down. */
    fun handleBackground() {
        session?.handleBackground()
        feedback.setMusic(null, settings)
    }
}
