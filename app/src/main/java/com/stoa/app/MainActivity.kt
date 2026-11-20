package com.stoa.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.stoa.app.ui.LocalEngine
import com.stoa.app.ui.LocalUi
import com.stoa.app.ui.Root
import com.stoa.app.ui.StoaTheme
import com.stoa.app.ui.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var ui: UiState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val engine = (application as StoaApp).engine
        ui = UiState(engine.prefs)
        loadApps()
        setContent {
            StoaTheme {
                CompositionLocalProvider(LocalEngine provides engine, LocalUi provides ui) { Root() }
            }
        }
    }

    /** Pressing Home while Stoa is already open brings you back to the home page. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("resisted", false)) (application as StoaApp).engine.toast("Good call.", "Urge resisted", "ach")
        ui.homeTick++
        ui.canvas = null; ui.viewer = null; ui.settings = false; ui.pauseFor = null
        ui.tab = "home"
    }

    override fun onResume() {
        super.onResume()
        (application as StoaApp).engine.scan()
        ui.resume++
        loadApps()
    }

    private fun loadApps() {
        CoroutineScope(Dispatchers.Default).launch {
            val list = Apps.list(this@MainActivity)
            withContext(Dispatchers.Main) { ui.apps = list }
        }
    }
}
