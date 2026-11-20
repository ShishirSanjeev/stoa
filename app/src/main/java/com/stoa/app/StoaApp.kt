package com.stoa.app

import android.app.Application
import com.stoa.app.vault.Engine

class StoaApp : Application() {
    lateinit var engine: Engine
        private set
    override fun onCreate() {
        super.onCreate()
        engine = Engine(this)
        engine.scan()
    }
}
