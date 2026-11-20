package com.stoa.app.mindful

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stoa.app.Apps
import com.stoa.app.MainActivity
import com.stoa.app.Prefs
import com.stoa.app.ui.PauseScreen
import com.stoa.app.ui.StoaTheme
import java.time.LocalDate

/** Shown on top of a mindful app: breathe for a few seconds, then choose. */
class PauseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkg = intent.getStringExtra("pkg") ?: run { finish(); return }
        val prefs = Prefs(this)
        val label = Apps.labelOf(this, pkg)
        setContent {
            StoaTheme {
                BackHandler { goBack(prefs) }
                PauseScreen(label, prefs.resisted(LocalDate.now().toString()),
                    onBack = { goBack(prefs) },
                    onOpen = { minutes -> prefs.grantPass(pkg, minutes); finish(); overridePendingTransition(0, android.R.anim.fade_out) })
            }
        }
    }
    private fun goBack(prefs: Prefs) {
        prefs.addResisted(LocalDate.now().toString())
        startActivity(Intent(this, MainActivity::class.java).putExtra("resisted", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
