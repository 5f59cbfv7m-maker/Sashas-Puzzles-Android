package com.kirillrychkov.sashaspuzzles.model

import androidx.annotation.StringRes
import com.kirillrychkov.sashaspuzzles.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where a puzzle picture comes from. */
@Serializable
sealed interface ImageSource {
    /** A photograph shipped in `assets/pictures/`. */
    @Serializable @SerialName("bundled")
    data class Bundled(val fileName: String) : ImageSource

    /** A photo the player imported; the file lives in the app's photo folder. */
    @Serializable @SerialName("imported")
    data class Imported(val fileName: String) : ImageSource

    val isUserPhoto: Boolean get() = this is Imported
}

/** One entry in the picture library. */
@Serializable
data class LibraryItem(
    val id: String,
    /** English title; [com.kirillrychkov.sashaspuzzles.library.Library.title] translates bundled ones. */
    val title: String,
    val category: ArtCategory,
    val source: ImageSource,
    /** Width / height of the photograph. */
    val aspect: Double,
) {
    val isUserPhoto: Boolean get() = source.isUserPhoto
}

/** Coarse grouping used by the library's category filter. */
@Serializable
enum class ArtCategory(@StringRes val title: Int) {
    @SerialName("space") SPACE(R.string.space),
    @SerialName("nature") NATURE(R.string.nature),
    @SerialName("mountains") MOUNTAINS(R.string.mountains),
    @SerialName("sea") SEA(R.string.sea),
    @SerialName("city") CITY(R.string.city),
    @SerialName("animals") ANIMALS(R.string.animals),
    @SerialName("abstract") ABSTRACT(R.string.abstract_label),
    @SerialName("mine") MINE(R.string.my_photos);

    val key: String get() = name.lowercase()

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

/**
 * Preset piece counts, from a coffee-break puzzle to a genuinely punishing
 * 800-piece board. The engine itself has no upper bound.
 */
@Serializable
enum class Difficulty(val targetPieces: Int, @StringRes val title: Int, @StringRes val estimate: Int) {
    EASY(12, R.string.easy, R.string.a_few_minutes),
    NORMAL(24, R.string.normal, R.string.n_10_20_minutes),
    HARD(48, R.string.hard, R.string.n_30_45_minutes),
    EXPERT(80, R.string.expert, R.string.about_an_hour),
    MASTER(150, R.string.master, R.string.a_couple_of_hours),
    INSANE(300, R.string.insane, R.string.an_evening),
    EXTREME(500, R.string.extreme, R.string.several_sittings),
    NIGHTMARE(800, R.string.nightmare, R.string.a_serious_project);

    companion object {
        val bounds = 12..1000

        /** The preset whose estimate describes a custom count best. */
        fun nearest(pieces: Int) = entries.minBy { kotlin.math.abs(it.targetPieces - pieces) }
    }
}

/** How eagerly pieces jump into place. */
enum class SnapAssist(val multiplier: Double, @StringRes val title: Int) {
    PRECISE(0.65, R.string.precise),
    STANDARD(1.0, R.string.standard),
    GENEROUS(1.5, R.string.generous),
}

/** Aspect the photo is cropped to before cutting; `null` ratio keeps the whole picture. */
@Serializable
enum class PuzzleAspect(val ratio: Double?, @StringRes val titleRes: Int?, val label: String) {
    ORIGINAL(null, R.string.original, ""),
    SQUARE(1.0, null, "1:1"),
    LANDSCAPE_3_2(3.0 / 2.0, null, "3:2"),
    LANDSCAPE_4_3(4.0 / 3.0, null, "4:3"),
    LANDSCAPE_16_9(16.0 / 9.0, null, "16:9"),
    PORTRAIT_2_3(2.0 / 3.0, null, "2:3"),
}
