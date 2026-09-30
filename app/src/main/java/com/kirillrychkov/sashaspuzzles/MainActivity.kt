package com.kirillrychkov.sashaspuzzles

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kirillrychkov.sashaspuzzles.app.RootScreen

class MainActivity : ComponentActivity() {
    private val model get() = (application as PuzzleApp).model

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val stage = if (debuggable && savedInstanceState == null) intent.getStringExtra("stage") else null
        setContent { RootScreen(model, skipSplash = stage != null) }
        stage?.let { model.runStage(it) }
    }

    override fun onStop() {
        super.onStop()
        model.handleBackground()
    }
}
