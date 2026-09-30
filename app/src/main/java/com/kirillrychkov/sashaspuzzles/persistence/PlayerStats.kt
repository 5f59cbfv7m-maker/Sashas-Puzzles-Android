package com.kirillrychkov.sashaspuzzles.persistence

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kirillrychkov.sashaspuzzles.library.Library
import com.kirillrychkov.sashaspuzzles.model.ArtCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * One finished game. Totals, best times and the daily streak are all derived
 * from this list, so there is exactly one thing to persist.
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
data class CompletionSummary(val previousBestMillis: Long?)

/** The player's history, one JSON file in the app's folder. */
class PlayerStats(directory: File, private val library: Library) {
    private val file = File(directory, "stats.json")
    private val serializer = ListSerializer(SolvedRecord.serializer())

    var records: List<SolvedRecord> by mutableStateOf(
        runCatching { Json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    )
        private set

    fun record(solved: SolvedRecord): CompletionSummary {
        val previousBest = bestTime(solved.itemId)
        records = records + solved
        persist()
        return CompletionSummary(previousBest)
    }

    fun reset() {
        records = emptyList()
        persist()
    }

    private fun persist() {
        runCatching { file.writeText(Json.encodeToString(serializer, records)) }
    }

    val puzzlesSolved: Int get() = records.size

    fun bestTime(itemId: String): Long? = records.filter { it.itemId == itemId }.minOfOrNull { it.elapsedMillis }

    /**
     * A game counts as that day's daily puzzle when it is the day's picture at
     * the daily piece count — no flag to persist, nothing to migrate.
     */
    private fun isDaily(record: SolvedRecord) =
        record.targetPieces == Library.DAILY_PIECES && record.itemId == library.dailyItem(record.day).id

    private val dailyDays: Set<LocalDate> get() = records.filter(::isDaily).map { it.day }.toSet()

    val dailySolvedToday: Boolean get() = LocalDate.now() in dailyDays

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
}
