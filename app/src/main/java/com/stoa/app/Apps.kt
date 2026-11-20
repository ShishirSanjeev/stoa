package com.stoa.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.time.LocalDate
import java.time.ZoneId

data class LApp(val pkg: String, val cls: String, val label: String)

/** Installed apps, launching, icons and the permission checks the launcher needs. */
object Apps {
    private val icons = HashMap<String, ImageBitmap>()

    fun list(ctx: Context): List<LApp> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0).map { LApp(it.activityInfo.packageName, it.activityInfo.name, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg + "/" + it.cls }.sortedBy { it.label.lowercase() }
    }
    fun icon(ctx: Context, a: LApp): ImageBitmap? = synchronized(icons) {
        icons[a.pkg + a.cls] ?: try {
            ctx.packageManager.getActivityIcon(ComponentName(a.pkg, a.cls)).toBitmap(112, 112).asImageBitmap().also { icons[a.pkg + a.cls] = it }
        } catch (e: Exception) { null }
    }
    fun launch(ctx: Context, a: LApp) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(a.pkg, a.cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (e: Exception) { ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
    fun openUrl(ctx: Context, url: String) = try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    fun appInfo(ctx: Context, pkg: String) = try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }

    /** Opens a vault note in Obsidian. */
    fun openInObsidian(ctx: Context, vault: String, rel: String) {
        val u = "obsidian://open?vault=" + Uri.encode(vault) + "&file=" + Uri.encode(rel.removeSuffix(".md"))
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { android.widget.Toast.makeText(ctx, "Install Obsidian to open notes", android.widget.Toast.LENGTH_SHORT).show() }
    }

    /** Pulls down the notification shade (needs EXPAND_STATUS_BAR, a normal permission). */
    @android.annotation.SuppressLint("WrongConstant")
    fun expandNotifications(ctx: Context) {
        try { val sb = ctx.getSystemService("statusbar"); Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb) } catch (e: Exception) { }
    }
    fun hasOverlay(ctx: Context) = Settings.canDrawOverlays(ctx)
    fun askOverlay(ctx: Context) = try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    fun hasNotifications(ctx: Context) = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun webSearch(ctx: Context, q: String) = openUrl(ctx, "https://www.google.com/search?q=" + Uri.encode(q))

    /** Minutes of screen time in each hour of today (index 0 = midnight), from app-in-front events. */
    fun hourlyUsage(ctx: Context): IntArray {
        val out = IntArray(24)
        if (!hasUsage(ctx)) return out
        val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); val now = System.currentTimeMillis()
        val ev = um.queryEvents(start, now); val e = android.app.usage.UsageEvents.Event()
        val ms = LongArray(24); var openPkg: String? = null; var openAt = 0L
        fun add(a: Long, b: Long) { var t = a; while (t < b) { val h = ((t - start) / 3_600_000L).toInt().coerceIn(0, 23); val end = minOf(b, start + (h + 1) * 3_600_000L); ms[h] += end - t; t = end } }
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> { if (openPkg != null) add(openAt, e.timeStamp); openPkg = e.packageName; openAt = e.timeStamp }
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> if (openPkg == e.packageName) { add(openAt, e.timeStamp); openPkg = null }
                android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE -> if (openPkg != null) { add(openAt, e.timeStamp); openPkg = null }
            }
        }
        if (openPkg != null) add(openAt, now)
        for (i in 0 until 24) out[i] = (ms[i] / 60_000L).toInt().coerceAtMost(60)
        return out
    }

    /* ── permissions ── */
    fun hasAllFiles(ctx: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    fun askAllFiles(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 30) try {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun hasUsage(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
    fun askUsage(ctx: Context) = try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    fun isDefaultHome(ctx: Context): Boolean {
        val r = ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == ctx.packageName
    }
    fun askHome(ctx: Context) = try { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }

    /** Today's screen time per app (needs Usage access). */
    fun screenTime(ctx: Context): List<Pair<String, Long>> {
        if (!hasUsage(ctx)) return emptyList()
        val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        // Walk foreground/background events so only today's time is counted.
        val ev = um.queryEvents(start, now); val e = android.app.usage.UsageEvents.Event()
        val open = HashMap<String, Long>(); val total = HashMap<String, Long>()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> open[e.packageName] = e.timeStamp
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> open.remove(e.packageName)?.let { total[e.packageName] = (total[e.packageName] ?: 0L) + (e.timeStamp - it) }
            }
        }
        open.forEach { (p, t) -> total[p] = (total[p] ?: 0L) + (now - t) }
        total.remove(ctx.packageName)
        return total.entries.filter { it.value > 30_000 }.sortedByDescending { it.value }.map { it.key to it.value }
    }
    fun labelOf(ctx: Context, pkg: String): String = try { val pm = ctx.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg.substringAfterLast('.') }
}
