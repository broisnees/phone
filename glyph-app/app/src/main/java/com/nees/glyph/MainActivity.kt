package com.nees.glyph

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nees.glyph.ui.AppsScreen
import com.nees.glyph.ui.C
import com.nees.glyph.ui.ContactsScreen
import com.nees.glyph.ui.EaseOut
import com.nees.glyph.ui.HomeScreen
import com.nees.glyph.ui.LightScreen
import com.nees.glyph.ui.PatternsScreen
import com.nees.glyph.ui.Screen
import com.nees.glyph.ui.TalkTheme
import com.nees.glyph.ui.VisualizerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val missing = PERMISSIONS.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
        val settings = Settings(this)
        GlyphService.sync(this)
        setContent {
            TalkTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                val back = { screen = Screen.Home }
                BackHandler(screen != Screen.Home) { back() }
                AnimatedContent(
                    screen,
                    transitionSpec = {
                        if (targetState != Screen.Home) slideInHorizontally(tween(280, easing = EaseOut)) { it / 4 } + fadeIn(tween(200)) togetherWith fadeOut(tween(120))
                        else fadeIn(tween(200)) togetherWith slideOutHorizontally(tween(280, easing = EaseOut)) { it / 4 } + fadeOut(tween(120))
                    },
                    modifier = Modifier.fillMaxSize().background(C.bg),
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen(settings) { screen = it }
                        Screen.Apps -> AppsScreen(settings, back)
                        Screen.Contacts -> ContactsScreen(settings, back)
                        Screen.Visualizer -> VisualizerScreen(settings, back)
                        Screen.Light -> LightScreen(settings, back)
                        Screen.Patterns -> PatternsScreen(settings, back)
                    }
                }
            }
        }
    }

    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
        )
    }
}
