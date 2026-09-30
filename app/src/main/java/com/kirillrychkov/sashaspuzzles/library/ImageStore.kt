package com.kirillrychkov.sashaspuzzles.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.kirillrychkov.sashaspuzzles.engine.Rect
import com.kirillrychkov.sashaspuzzles.model.ImageSource
import com.kirillrychkov.sashaspuzzles.model.LibraryItem
import com.kirillrychkov.sashaspuzzles.model.PuzzleAspect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Central supplier of pixels: a memory LRU over a disk cache.
 *
 * Decoding and cropping a full-size photo is expensive once and cheap after:
 * the master bitmap is written to the cache folder as JPEG at the size the
 * board needs, so reopening a picture costs one small decode. Nothing here
 * touches the network — the whole library works on a plane.
 */
class ImageStore(private val context: Context) {

    data class Request(val item: LibraryItem, val aspect: PuzzleAspect, val longSide: Int)

    private val memory = object : LruCache<Request, Bitmap>(maxMemoryKb()) {
        override fun sizeOf(key: Request, value: Bitmap) = value.allocationByteCount / 1024
    }
    private val inFlight = HashMap<Request, CompletableDeferred<Bitmap?>>()
    private val lock = Mutex()
    private val cacheDirectory = File(context.cacheDir, "PuzzleImages").apply { mkdirs() }

    /** Peek without decoding: lets a thumbnail show instantly when it is already warm. */
    fun cached(request: Request): Bitmap? = memory.get(request)

    /** The bitmap for a request. Concurrent callers of the same picture share one decode. */
    suspend fun image(request: Request): Bitmap? {
        memory.get(request)?.let { return it }
        val (deferred, owner) = lock.withLock {
            inFlight[request]?.let { it to false } ?: CompletableDeferred<Bitmap?>().also { inFlight[request] = it }.let { it to true }
        }
        if (!owner) return deferred.await()
        val result = runCatching { withContext(Dispatchers.IO) { produce(request) } }.getOrNull()
        if (result != null) memory.put(request, result)
        lock.withLock { inFlight.remove(request) }
        deferred.complete(result)
        return result
    }

    fun purgeMemory() = memory.evictAll()

    private fun produce(request: Request): Bitmap? {
        val cacheFile = File(cacheDirectory, cacheName(request))
        if (cacheFile.exists()) {
            decode(request.longSide) { cacheFile.inputStream() }?.let { return it }
        }
        val decoded = when (val source = request.item.source) {
            is ImageSource.Bundled -> decode(request.longSide) { context.assets.open("${Library.FOLDER}/${source.fileName}") }
            is ImageSource.Imported -> File(context.filesDir, "Photos/${source.fileName}").takeIf { it.exists() }
                ?.let { file -> decode(request.longSide) { file.inputStream() } }
        } ?: return null
        val produced = crop(decoded, request.aspect.ratio)
        if (request.longSide >= 700) {
            // Only masters are worth keeping on disk; thumbnails regenerate fast.
            runCatching { cacheFile.outputStream().use { produced.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        }
        return produced
    }

    private fun cacheName(request: Request) =
        "${request.item.id}-${request.aspect.name}-${request.longSide}".replace('/', '_') + ".jpg"

    companion object {
        private fun maxMemoryKb() = (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()

        /**
         * Decodes so the longest side is at most `longSide`: a power-of-two
         * subsample first (never materialising the full photo), then one exact
         * scale.
         */
        fun decode(longSide: Int, open: () -> InputStream): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open().use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = max(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return null
            var sample = 1
            while (longest / (sample * 2) >= longSide) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = open().use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            val current = max(bitmap.width, bitmap.height)
            if (current <= longSide) return bitmap
            val k = longSide.toDouble() / current
            val scaled = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * k).roundToInt()),
                max(1, (bitmap.height * k).roundToInt()), true)
            if (scaled !== bitmap) bitmap.recycle()
            return scaled
        }

        /** Centre-crop to an aspect ratio; `null` keeps the whole picture, never stretched. */
        fun crop(bitmap: Bitmap, aspect: Double?): Bitmap {
            if (aspect == null || aspect <= 0) return bitmap
            val target = Rect(0.0, 0.0, bitmap.width.toDouble(), bitmap.height.toDouble()).centeredCrop(aspect)
            val x = target.x.roundToInt().coerceIn(0, bitmap.width - 1)
            val y = target.y.roundToInt().coerceIn(0, bitmap.height - 1)
            val w = target.width.roundToInt().coerceIn(1, bitmap.width - x)
            val h = target.height.roundToInt().coerceIn(1, bitmap.height - y)
            if (w == bitmap.width && h == bitmap.height) return bitmap
            return Bitmap.createBitmap(bitmap, x, y, w, h)
        }
    }
}
