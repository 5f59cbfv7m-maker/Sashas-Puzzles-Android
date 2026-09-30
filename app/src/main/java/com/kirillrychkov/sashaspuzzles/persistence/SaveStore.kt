package com.kirillrychkov.sashaspuzzles.persistence

import com.kirillrychkov.sashaspuzzles.engine.PuzzleState
import com.kirillrychkov.sashaspuzzles.model.ArtCategory
import com.kirillrychkov.sashaspuzzles.model.ImageSource
import com.kirillrychkov.sashaspuzzles.model.LibraryItem
import com.kirillrychkov.sashaspuzzles.model.PuzzleAspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Everything needed to resume a game, and nothing more. Geometry is not
 * stored: `seed`, `columns` and `rows` regenerate the same Bézier edges, so an
 * 800-piece save is a few tens of kilobytes.
 */
@Serializable
data class GameSnapshot(
    val version: Int = CURRENT_VERSION,
    val id: String,
    val itemId: String,
    val itemTitle: String,
    val category: ArtCategory,
    val source: ImageSource,
    val imageAspect: Double,
    val puzzleAspect: PuzzleAspect,
    val targetPieces: Int,
    val columns: Int,
    val rows: Int,
    val seed: ULong,
    val elapsedMillis: Long,
    val state: PuzzleState,
    val updatedAt: Long,
    val isComplete: Boolean,
) {
    val pieceCount: Int get() = columns * rows

    val libraryItem: LibraryItem
        get() = LibraryItem(itemId, itemTitle, category, source, imageAspect)

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Saved games as one small JSON file, rewritten atomically. Deliberately not a
 * database: a save is a single document, and an atomic write is faster and far
 * harder to corrupt than a partially migrated store.
 */
class SaveStore(directory: File) {
    private val file = File(directory, "savedGames.json")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Game by game: an entry this build cannot read drops only that game,
     * never every save on the phone.
     */
    fun load(): List<GameSnapshot> {
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        val games = runCatching { json.parseToJsonElement(text).jsonObject["games"]?.jsonArray }.getOrNull() ?: return emptyList()
        return games.mapNotNull { runCatching { json.decodeFromJsonElement(GameSnapshot.serializer(), it) }.getOrNull() }
            .filter { it.version <= GameSnapshot.CURRENT_VERSION }
            .sortedByDescending { it.updatedAt }
    }

    /** Inserts or replaces one game, keeping the most recent `limit`. */
    fun save(snapshot: GameSnapshot, limit: Int = 12) {
        val games = listOf(snapshot) + load().filter { it.id != snapshot.id }
        write(games.take(limit))
    }

    fun delete(id: String) = write(load().filter { it.id != id })

    fun deleteAll() = write(emptyList())

    private fun write(games: List<GameSnapshot>) {
        val array = JsonArray(games.map { json.encodeToJsonElement(GameSnapshot.serializer(), it) })
        val text = json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("version" to kotlinx.serialization.json.JsonPrimitive(1), "games" to array)))
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }
}
