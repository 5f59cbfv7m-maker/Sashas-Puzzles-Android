package com.kirillrychkov.sashaspuzzles.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kirillrychkov.sashaspuzzles.app.AppModel
import com.kirillrychkov.sashaspuzzles.model.LibraryItem
import com.kirillrychkov.sashaspuzzles.model.PuzzleAspect
import com.kirillrychkov.sashaspuzzles.ui.Theme
import com.kirillrychkov.sashaspuzzles.ui.WashedMatrix

/**
 * A lazily decoded preview of a library picture. The size comes from the
 * caller; the picture fills it and is cropped, so a square photo can never
 * stretch a 3:2 card and break the grid around it.
 */
@Composable
fun LibraryThumbnail(model: AppModel, item: LibraryItem, modifier: Modifier = Modifier, longSide: Int = 420, washed: Boolean = false) {
    val request = remember(item.id, longSide) { ImageStore.Request(item, PuzzleAspect.ORIGINAL, longSide) }
    var image by remember(request) { mutableStateOf<ImageBitmap?>(model.images.cached(request)?.asImageBitmap()) }
    LaunchedEffect(request) {
        if (image == null) image = model.images.image(request)?.asImageBitmap()
    }
    val alpha by animateFloatAsState(if (image != null) 1f else 0f, tween(200), label = "thumbnail")
    Box(modifier.background(Theme.colors.track.copy(alpha = 0.5f)).clipToBounds()) {
        image?.let {
            Image(it, null, Modifier.fillMaxSize().alpha(alpha), contentScale = ContentScale.Crop,
                colorFilter = if (washed) ColorFilter.colorMatrix(WashedMatrix) else null)
        }
    }
}
