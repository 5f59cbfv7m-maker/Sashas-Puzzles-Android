package com.kirillrychkov.sashaspuzzles.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import kotlin.reflect.KProperty
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.model.Difficulty
import com.kirillrychkov.sashaspuzzles.model.PuzzleAspect
import com.kirillrychkov.sashaspuzzles.model.SnapAssist
import com.kirillrychkov.sashaspuzzles.support.Feedback

/** Player preferences, observable by Compose and written through on every change. */
class AppSettings(context: Context) {

    enum class Appearance(@StringRes val title: Int) {
        SYSTEM(R.string.system), LIGHT(R.string.light), DARK(R.string.dark)
    }

    /** The tune under the board; the library always plays its own. */
    enum class BoardMusic(@StringRes val title: Int, val track: Feedback.Music) {
        PIANO(R.string.piano, Feedback.Music.BOARD_PIANO),
        VIBRAPHONE(R.string.vibraphone, Feedback.Music.BOARD_VIBRAPHONE),
    }

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun <T> pref(initial: T, write: (T) -> Unit) = Preference(mutableStateOf(initial), write)

    var appearance by pref(enumOf(prefs.getString("appearance", null), Appearance.SYSTEM)) { v -> prefs.edit { putString("appearance", v.name) } }
    var soundEnabled by pref(prefs.getBoolean("sound", true)) { v -> prefs.edit { putBoolean("sound", v) } }
    var musicEnabled by pref(prefs.getBoolean("music", true)) { v -> prefs.edit { putBoolean("music", v) } }
    var boardMusic by pref(enumOf(prefs.getString("boardMusic", null), BoardMusic.PIANO)) { v -> prefs.edit { putString("boardMusic", v.name) } }
    var hapticsEnabled by pref(prefs.getBoolean("haptics", true)) { v -> prefs.edit { putBoolean("haptics", v) } }
    /** Faint copy of the picture under the board — a guide, not a solution. */
    var showGhostImage by pref(prefs.getBoolean("ghost", true)) { v -> prefs.edit { putBoolean("ghost", v) } }
    var snapAssist by pref(enumOf(prefs.getString("snapAssist", null), SnapAssist.STANDARD)) { v -> prefs.edit { putString("snapAssist", v.name) } }
    var defaultDifficulty by pref(enumOf(prefs.getString("difficulty", null), Difficulty.NORMAL)) { v -> prefs.edit { putString("difficulty", v.name) } }
    var defaultAspect by pref(enumOf(prefs.getString("aspect", null), PuzzleAspect.ORIGINAL)) { v -> prefs.edit { putString("aspect", v.name) } }
    /** A thin outline around every piece; helps on busy pictures. */
    var showPieceOutlines by pref(prefs.getBoolean("outlines", true)) { v -> prefs.edit { putBoolean("outlines", v) } }
    var hasSeenOnboarding by pref(prefs.getBoolean("onboarding", false)) { v -> prefs.edit { putBoolean("onboarding", v) } }
    /** Asked on the last onboarding step; capped so a pasted paragraph cannot break the header. */
    var playerName by pref(prefs.getString("playerName", "") ?: "") { v -> prefs.edit { putString("playerName", v.take(24)) } }
    /**
     * Achievements already looked at in the profile. `null` until the first
     * launch that knows about them, so an update does not flag old ones as new.
     */
    var seenAchievements by pref(prefs.getStringSet("seenAchievements", null)?.toSet()) { v ->
        prefs.edit { if (v == null) remove("seenAchievements") else putStringSet("seenAchievements", v) }
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    /** A Compose state that also writes itself to the preferences file. */
    class Preference<T>(private val state: MutableState<T>, private val write: (T) -> Unit) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            write(value)
        }
    }
}
