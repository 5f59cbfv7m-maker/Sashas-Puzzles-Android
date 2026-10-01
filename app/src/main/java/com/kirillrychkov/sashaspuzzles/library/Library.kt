package com.kirillrychkov.sashaspuzzles.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.model.ArtCategory
import com.kirillrychkov.sashaspuzzles.model.ImageSource
import com.kirillrychkov.sashaspuzzles.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The picture library: the photographs in `assets/pictures/` — the same 120 as
 * the iOS app — plus the player's own photos.
 *
 * A built-in file name carries the metadata — `sea_Sunset Beach.jpg` is
 * category `sea`, title "Sunset Beach" — and only the JPEG header is read, so
 * the library appears instantly and bitmaps are decoded lazily.
 *
 * Own photos are *copied* into `files/Photos/` as upright JPEGs no larger than
 * 4096 px, and listed in `library.json`: a puzzle keeps working after the
 * original is deleted from the gallery, and the list never holds a bitmap.
 */
class Library(private val context: Context) {

    val builtIn: List<LibraryItem> by lazy { scan() }

    // Declared before `userItems`, whose initial value reads them.
    private val photosDirectory = File(context.filesDir, PHOTOS).apply { mkdirs() }
    private val manifestFile = File(context.filesDir, "library.json")

    /** The player's photos, newest first. */
    var userItems: List<LibraryItem> by mutableStateOf(loadManifest())
        private set

    val all: List<LibraryItem> get() = userItems + builtIn

    fun items(category: ArtCategory?): List<LibraryItem> = when (category) {
        null -> all
        ArtCategory.MINE -> userItems
        else -> builtIn.filter { it.category == category }
    }

    fun item(id: String): LibraryItem? = userItems.firstOrNull { it.id == id } ?: builtIn.firstOrNull { it.id == id }

    // Own photos

    @Serializable
    private data class Manifest(val version: Int = 1, val items: List<LibraryItem>)

    private fun loadManifest(): List<LibraryItem> {
        val items = runCatching { Json.decodeFromString(Manifest.serializer(), manifestFile.readText()).items }.getOrDefault(emptyList())
        // Entries whose file vanished (a restore, a full disk) are dropped.
        return items.filter { (it.source as? ImageSource.Imported)?.let { source -> File(context.filesDir, "$PHOTOS/${source.fileName}").exists() } == true }
    }

    private fun saveManifest() {
        runCatching { manifestFile.writeText(Json.encodeToString(Manifest.serializer(), Manifest(items = userItems))) }
    }

    /**
     * Copies a picked image into the library. Photos from the gallery are called
     * "My Photo", like on iOS; files keep their name. Throws when the image cannot be read.
     */
    suspend fun importPhoto(uri: Uri, fromFiles: Boolean): LibraryItem {
        val fileName = "${UUID.randomUUID()}.jpg"
        val (title, aspect) = withContext(Dispatchers.IO) {
            val bitmap = decodeUpright(uri) ?: throw IOException("Unreadable image")
            try {
                File(photosDirectory, fileName).outputStream().use {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) throw IOException("Could not write the photo")
                }
            } catch (failure: Exception) {
                File(photosDirectory, fileName).delete()
                throw failure
            }
            val name = if (fromFiles) displayName(uri) else null
            (name ?: context.getString(R.string.my_photo)) to bitmap.width.toDouble() / bitmap.height
        }
        val item = LibraryItem("user.$fileName", title, ArtCategory.MINE, ImageSource.Imported(fileName), aspect)
        userItems = listOf(item) + userItems
        saveManifest()
        return item
    }

    fun delete(item: LibraryItem) {
        val source = item.source as? ImageSource.Imported ?: return
        File(photosDirectory, source.fileName).delete()
        userItems = userItems.filter { it.id != item.id }
        saveManifest()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }

    /** The image the right way up — phones store most photos sideways with an EXIF turn — and at most [IMPORT_MAX] px. */
    private fun decodeUpright(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder applies the EXIF orientation itself and reads HEIC too.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > IMPORT_MAX) {
                    val k = IMPORT_MAX.toDouble() / longest
                    decoder.setTargetSize(max(1, (info.size.width * k).roundToInt()), max(1, (info.size.height * k).roundToInt()))
                }
                // A hardware bitmap cannot be compressed back to JPEG.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bitmap = ImageStore.decode(IMPORT_MAX) { resolver.openInputStream(uri) ?: throw IOException("No stream") } ?: return null
        val degrees = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (degrees == 0f) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }

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
        /** Where own photos live inside the app's files; [ImageStore] reads them from here. */
        const val PHOTOS = "Photos"
        /** Longest edge kept for an own photo: enough for 800 pieces, small enough to keep many. */
        private const val IMPORT_MAX = 4096
        /** The daily puzzle is always played at this size. */
        const val DAILY_PIECES = 150
        private const val EPOCH_DAY_ORDINAL = 719_163L
    }
}
