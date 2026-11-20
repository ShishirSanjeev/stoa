package com.stoa.app

import android.app.Application
import com.stoa.app.vault.Engine

class StoaApp : Application() {
    lateinit var engine: Engine
        private set
    override fun onCreate() {
        super.onCreate()
        // keep the last crash so it can be read in Settings (and sent to me) instead of vanishing
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { getSharedPreferences("crash", MODE_PRIVATE).edit().putString("last", java.time.LocalDateTime.now().toString() + "\n" + android.util.Log.getStackTraceString(e).take(6000)).commit() } catch (x: Throwable) { }
            prev?.uncaughtException(t, e)
        }
        engine = Engine(this)
        engine.scan()
    }
}
