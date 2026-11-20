package com.stoa.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Obsidian Sync only runs inside Obsidian, so "Sync now" opens your vault in Obsidian, watches the vault folder
 * until files stop changing, then brings you back to Stoa.
 */
object SyncNow {
    const val OBSIDIAN = "md.obsidian"
    var syncing by mutableStateOf(false)
        private set
    var lastDone by mutableLongStateOf(0L)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** When Obsidian last ran (it syncs whenever it's open) or our last Sync now — whichever is newer. */
    fun lastSynced(ctx: Context): Long {
        val p = Prefs(ctx)
        var t = maxOf(p.long("last_sync"), lastDone)
        if (Apps.hasUsage(ctx)) try {
            val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            um.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 7 * 86_400_000L, now)?.filter { it.packageName == OBSIDIAN }?.forEach { t = maxOf(t, it.lastTimeUsed) }
        } catch (e: Exception) { }
        return t
    }

    private fun newest(root: File): Long {
        var m = 0L
        root.walkTopDown().onEnter { it == root || !it.name.startsWith(".") }.forEach { if (it.isFile) { val l = it.lastModified(); if (l > m) m = l } }
        return m
    }
    private fun changedSince(root: File, t: Long): Int =
        root.walkTopDown().onEnter { it == root || !it.name.startsWith(".") }.count { it.isFile && it.name.endsWith(".md") && it.lastModified() > t }

    fun start(ctx: Context) {
        if (syncing) return
        val app = ctx.applicationContext as StoaApp; val E = app.engine
        val root = E.root
        if (root == null) { E.toast("Choose your vault first", "Settings → Your vault"); return }
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("obsidian://open?vault=" + Uri.encode(root.name))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(open) } catch (e: Exception) {
            val l = ctx.packageManager.getLaunchIntentForPackage(OBSIDIAN)
            if (l == null) { E.toast("Obsidian isn't installed", ""); return }
            ctx.startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        syncing = true
        val started = System.currentTimeMillis()
        scope.launch {
            try {
                var last = newest(root); var quietSince = System.currentTimeMillis()
                delay(6_000)                                     // give Obsidian time to start and connect
                while (System.currentTimeMillis() - started < 60_000) {
                    delay(2_000)
                    val n = newest(root)
                    if (n != last) { last = n; quietSince = System.currentTimeMillis() }
                    else if (System.currentTimeMillis() - quietSince >= 6_000 && System.currentTimeMillis() - started >= 10_000) break
                }
                val changed = changedSince(root, started - 1000)
                val now = System.currentTimeMillis(); lastDone = now; Prefs(ctx).put("last_sync", now)
                E.toast("Synced", if (changed == 0) "Everything was up to date" else "$changed note${if (changed == 1) "" else "s"} updated", "ach")
                E.scan()
                // come back to Stoa (allowed because the mindful-pause accessibility service is bound; otherwise Home does it)
                try { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } catch (e: Exception) { }
            } finally { syncing = false }
        }
    }

    fun ago(t: Long): String {
        if (t <= 0L) return "never"
        val m = (System.currentTimeMillis() - t) / 60_000
        return when { m < 1 -> "just now"; m < 60 -> "${m}m ago"; m < 1440 -> "${m / 60}h ago"; else -> "${m / 1440}d ago" }
    }
}
