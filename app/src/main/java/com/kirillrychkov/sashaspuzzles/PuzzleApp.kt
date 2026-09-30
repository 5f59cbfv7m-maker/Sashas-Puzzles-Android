package com.kirillrychkov.sashaspuzzles

import android.app.Application
import android.content.ComponentCallbacks2
import com.kirillrychkov.sashaspuzzles.app.AppModel

class PuzzleApp : Application() {
    /** Created on first use, after the application context is ready. */
    val model: AppModel by lazy { AppModel(this) }

    override fun onCreate() {
        super.onCreate()
        com.kirillrychkov.sashaspuzzles.ui.Theme.install(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Everything in the image cache can be decoded again; it is the cheapest thing to give back.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) model.images.purgeMemory()
    }
}
