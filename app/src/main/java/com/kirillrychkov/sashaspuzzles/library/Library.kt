package com.kirillrychkov.sashaspuzzles.library

import android.content.Context
import android.graphics.BitmapFactory
import com.kirillrychkov.sashaspuzzles.model.ArtCategory
import com.kirillrychkov.sashaspuzzles.model.ImageSource
import com.kirillrychkov.sashaspuzzles.model.LibraryItem
import java.time.LocalDate

/**
 * The built-in pictures: the photographs in `assets/pictures/`, the same 120 as
 * the iOS app. The file name carries the metadata — `sea_Sunset Beach.jpg` is
 * category `sea`, title "Sunset Beach" — and only the JPEG header is read, so
 * the library appears instantly and bitmaps are decoded lazily.
 */
class Library(private val context: Context) {

    val builtIn: List<LibraryItem> by lazy { scan() }

    fun items(category: ArtCategory?): List<LibraryItem> =
        if (category == null) builtIn else builtIn.filter { it.category == category }

    fun item(id: String): LibraryItem? = builtIn.firstOrNull { it.id == id }

    /** Translated title; imported photos keep the name they were given. */
    fun title(item: LibraryItem): String {
        val source = item.source
        if (source is ImageSource.Bundled) {
            PictureTitles.byStem[source.fileName.substringBeforeLast('.')]?.let { return context.getString(it) }
        }
        return item.title
    }

    /** One built-in picture per calendar day — the same one the iOS app picks. */
    fun dailyItem(date: LocalDate = LocalDate.now()): LibraryItem {
        val items = builtIn
        // Days since 1 January of year 1, counted from one, like Foundation's
        // `ordinality(of: .day, in: .era)`.
        val ordinal = date.toEpochDay() + EPOCH_DAY_ORDINAL
        return items[(ordinal % items.size).toInt()]
    }

    private fun scan(): List<LibraryItem> {
        val assets = context.assets
        val files = assets.list(FOLDER).orEmpty()
        return files.mapNotNull { file ->
            val stem = file.substringBeforeLast('.')
            val split = stem.indexOf('_')
            if (split <= 0) return@mapNotNull null
            val category = ArtCategory.fromKey(stem.substring(0, split)) ?: return@mapNotNull null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open("$FOLDER/$file").use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) return@mapNotNull null
            LibraryItem(
                id = "bundled.$stem",
                title = stem.substring(split + 1),
                category = category,
                source = ImageSource.Bundled(file),
                aspect = options.outWidth.toDouble() / options.outHeight,
            )
        }.sortedBy { it.id }
    }

    companion object {
        const val FOLDER = "pictures"
        /** The daily puzzle is always played at this size. */
        const val DAILY_PIECES = 150
        private const val EPOCH_DAY_ORDINAL = 719_163L
    }
}
