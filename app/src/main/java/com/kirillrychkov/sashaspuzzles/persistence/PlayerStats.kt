package com.kirillrychkov.sashaspuzzles.persistence

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.library.Library
import com.kirillrychkov.sashaspuzzles.model.ArtCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

/**
 * One finished game. Everything the profile shows — totals, best times, the
 * daily streak, achievements, the weekly chart — is derived from this list,
 * so there is exactly one thing to persist.
 */
@Serializable
data class SolvedRecord(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val category: ArtCategory,
    val pieces: Int,
    val targetPieces: Int,
    val elapsedMillis: Long,
    val date: Long,
    val isUserPhoto: Boolean,
) {
    val day: LocalDate get() = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()
}

/** What the completion screen reports about the game just finished. */
data class CompletionSummary(val previousBestMillis: Long?, val newAchievements: List<Achievement> = emptyList())

/** The player's history, one JSON file in the app's folder. */
class PlayerStats(directory: File, private val library: Library) {
    private val file = File(directory, "stats.json")
    private val serializer = ListSerializer(SolvedRecord.serializer())

    var records: List<SolvedRecord> by mutableStateOf(
        runCatching { Json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    )
        private set

    fun record(solved: SolvedRecord): CompletionSummary {
        val before = unlocked
        val previousBest = bestTime(solved.itemId)
        records = records + solved
        persist()
        return CompletionSummary(previousBest, unlocked - before.toSet())
    }

    fun reset() {
        records = emptyList()
        persist()
    }

    private fun persist() {
        runCatching { file.writeText(Json.encodeToString(serializer, records)) }
    }

    val puzzlesSolved: Int get() = records.size
    val piecesPlaced: Int get() = records.sumOf { it.pieces }
    val timePlayedMillis: Long get() = records.sumOf { it.elapsedMillis }
    val firstPlayed: LocalDate? get() = records.minByOrNull { it.date }?.day

    fun isSolved(itemId: String): Boolean = records.any { it.itemId == itemId }

    fun bestTime(itemId: String): Long? = records.filter { it.itemId == itemId }.minOfOrNull { it.elapsedMillis }

    /**
     * A game counts as that day's daily puzzle when it is the day's picture at
     * the daily piece count — no flag to persist, nothing to migrate.
     */
    private fun isDaily(record: SolvedRecord) =
        record.targetPieces == Library.DAILY_PIECES && record.itemId == library.dailyItem(record.day).id

    private val dailyDays: Set<LocalDate> get() = records.filter(::isDaily).map { it.day }.toSet()

    val dailySolvedToday: Boolean get() = LocalDate.now() in dailyDays

    /** The longest run of consecutive daily puzzles ever, not just the current one. */
    val longestStreak: Int
        get() {
            var best = 0
            var run = 0
            var previous: LocalDate? = null
            for (day in dailyDays.sorted()) {
                run = if (previous?.plusDays(1) == day) run + 1 else 1
                best = maxOf(best, run)
                previous = day
            }
            return best
        }

    /** Consecutive daily puzzles ending today or, if today's is still open, yesterday. */
    val streak: Int
        get() {
            val days = dailyDays
            var day = LocalDate.now()
            if (day !in days) {
                day = day.minusDays(1)
                if (day !in days) return 0
            }
            var count = 0
            while (day in days) {
                count++
                day = day.minusDays(1)
            }
            return count
        }

    /** Pieces solved per week over the last twelve weeks, oldest first; weeks start as the locale's do. */
    val weeklyPieces: List<Int>
        get() {
            val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            val thisWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(firstDay))
            val byDay = records.groupBy { it.day }
            return (11 downTo 0).map { back ->
                val start = thisWeek.minusWeeks(back.toLong())
                (0L until 7L).sumOf { offset -> byDay[start.plusDays(offset)].orEmpty().sumOf { it.pieces } }
            }
        }

    // Achievements

    val unlocked: List<Achievement> get() = Achievement.entries.filter(::isUnlocked)

    fun isUnlocked(achievement: Achievement): Boolean = when (achievement) {
        Achievement.FIRST_PUZZLE -> puzzlesSolved >= 1
        Achievement.TEN_PUZZLES -> puzzlesSolved >= 10
        Achievement.FIFTY_PUZZLES -> puzzlesSolved >= 50
        Achievement.SPRINTER -> records.any { it.pieces >= 48 && it.elapsedMillis <= 300_000 }
        Achievement.NIGHTMARE -> records.any { it.pieces >= 800 }
        // The longest run, so a medal once earned is never taken back when a streak breaks.
        Achievement.WEEK_STREAK -> longestStreak >= 7
        Achievement.OWN_PHOTO -> records.any { it.isUserPhoto }
        else -> {
            val solved = records.mapTo(HashSet()) { it.itemId }
            val pictures = library.builtIn.filter { it.category == achievement.category }
            pictures.isNotEmpty() && pictures.all { it.id in solved }
        }
    }
}

/**
 * Milestones derived from the history. Order here is display order; [key] is
 * the iOS raw value, so both apps name them alike.
 */
enum class Achievement(
    val key: String,
    @StringRes val title: Int,
    @StringRes val detail: Int,
    val category: ArtCategory? = null,
    /** The photograph in a category medal — the one its iOS Game Center art uses too. */
    val coverStem: String? = null,
) {
    FIRST_PUZZLE("firstPuzzle", R.string.first_piece, R.string.solve_your_first_puzzle),
    TEN_PUZZLES("tenPuzzles", R.string.regular, R.string.solve_10_puzzles),
    FIFTY_PUZZLES("fiftyPuzzles", R.string.collector, R.string.solve_50_puzzles),
    SPRINTER("sprinter", R.string.sprinter, R.string.n_48_pieces_in_5_minutes),
    NIGHTMARE("nightmare", R.string.nightmare_survived, R.string.solve_800_pieces),
    WEEK_STREAK("weekStreak", R.string.a_week_in_a_row, R.string.n_7_daily_puzzles_in_a_row),
    OWN_PHOTO("ownPhoto", R.string.family_album, R.string.solve_one_of_your_own_photos),
    SPACE("space", R.string.stargazer, R.string.all_x_puzzles, ArtCategory.SPACE, "space_Aurora over the Pines"),
    NATURE("nature", R.string.naturalist, R.string.all_x_puzzles, ArtCategory.NATURE, "nature_Autumn Forest from Above"),
    MOUNTAINS("mountains", R.string.mountaineer, R.string.all_x_puzzles, ArtCategory.MOUNTAINS, "mountains_Alpine Village"),
    SEA("sea", R.string.master_of_water, R.string.all_x_puzzles, ArtCategory.SEA, "sea_Cliff Lighthouse"),
    CITY("city", R.string.city_lights_2, R.string.all_x_puzzles, ArtCategory.CITY, "city_Boats of Vernazza"),
    ANIMALS("animals", R.string.zookeeper, R.string.all_x_puzzles, ArtCategory.ANIMALS, "animals_Bengal Tiger"),
    ABSTRACT("abstract", R.string.abstract_mind, R.string.all_x_puzzles, ArtCategory.ABSTRACT, "abstract_Balloons over Cappadocia");

    /** The number a milestone medal shows. */
    val count: Int?
        get() = when (this) {
            FIRST_PUZZLE -> 1
            TEN_PUZZLES -> 10
            FIFTY_PUZZLES -> 50
            else -> null
        }

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
    }
}
