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
    fun icon(ctx: Context, a: LApp): ImageBitmap? {
        val pack = Prefs(ctx).str("icon_pack", "")
        val k = pack + "|" + a.pkg + "/" + a.cls
        synchronized(icons) { icons[k]?.let { return it } }
        val d = (if (pack.isNotEmpty()) IconPacks.drawable(ctx, pack, a.pkg, a.cls) else null)
            ?: try { ctx.packageManager.getActivityIcon(ComponentName(a.pkg, a.cls)) } catch (e: Exception) { null } ?: return null
        val b = try { d.toBitmap(144, 144).asImageBitmap() } catch (e: Exception) { return null }
        synchronized(icons) { icons[k] = b }
        return b
    }
    fun clearIcons() = synchronized(icons) { icons.clear() }
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
    private fun tryStart(ctx: Context, i: Intent): Boolean = try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    fun openClock(ctx: Context) {
        tryStart(ctx, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) ||
            listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage", "com.oneplus.deskclock", "com.coloros.alarmclock", "com.nothing.clock")
                .any { pk -> ctx.packageManager.getLaunchIntentForPackage(pk)?.let { tryStart(ctx, it) } == true }
    }
    fun openCalendar(ctx: Context) {
        val u = android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build()
        tryStart(ctx, Intent(Intent.ACTION_VIEW, u)) || tryStart(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
    }
    fun openWellbeing(ctx: Context) {
        val tries = listOf(
            Intent().setClassName("com.google.android.apps.wellbeing", "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"),
            Intent().setClassName("com.samsung.android.forest", "com.samsung.android.forest.launcher.LauncherActivity"),
            Intent("android.settings.DIGITAL_WELLBEING_SETTINGS"))
        if (tries.any { tryStart(ctx, it) }) return
        listOf("com.google.android.apps.wellbeing", "com.samsung.android.forest", "com.oplus.healthservice", "com.nothing.wellbeing")
            .any { pk -> ctx.packageManager.getLaunchIntentForPackage(pk)?.let { tryStart(ctx, it) } == true } || tryStart(ctx, Intent(Settings.ACTION_SETTINGS))
    }

    fun hourlyUsage(ctx: Context, day: LocalDate = LocalDate.now()): IntArray {
        val out = IntArray(24)
        if (!hasUsage(ctx)) return out
        val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = minOf(System.currentTimeMillis(), day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
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

/** ADW / Nova-style icon packs: read the pack's appfilter.xml and pull its drawables. */
object IconPacks {
    data class Pack(val pkg: String, val label: String)
    private var loaded = ""; private val map = HashMap<String, String>()
    /** Packs that announce themselves to launchers, plus any app that simply ships an appfilter. */
    fun installed(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        val acts = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme", "com.teslacoilsw.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK", "com.anddoes.launcher.THEME", "com.dlto.atom.launcher.THEME", "com.lge.launcher2.THEME", "net.oneplus.launcher.icons.ACTION_PICK_ICON")
        val found = LinkedHashSet<String>()
        for (a in acts) try { pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA).forEach { found.add(it.activityInfo.packageName) } } catch (e: Exception) { }
        for (c in listOf("com.anddoes.launcher.THEME", "com.fede.launcher.THEME_ICONPACK")) try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(c), 0).forEach { found.add(it.activityInfo.packageName) } } catch (e: Exception) { }
        // fallback: any installed app with an appfilter.xml
        try {
            pm.getInstalledApplications(0).forEach { ai ->
                if (ai.packageName in found || (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) return@forEach
                try {
                    val res = pm.getResourcesForApplication(ai)
                    if (res.getIdentifier("appfilter", "xml", ai.packageName) != 0 || res.assets.list("")?.contains("appfilter.xml") == true) found.add(ai.packageName)
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        return found.filter { it != ctx.packageName }.map { Pack(it, try { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() } catch (e: Exception) { it }) }.sortedBy { it.label.lowercase() }
    }
    @Synchronized private fun load(ctx: Context, pack: String) {
        if (loaded == pack) return
        map.clear(); loaded = pack
        try {
            val res = ctx.packageManager.getResourcesForApplication(pack)
            val id = res.getIdentifier("appfilter", "xml", pack)
            val parser: org.xmlpull.v1.XmlPullParser = if (id != 0) res.getXml(id) else
                org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser().also { it.setInput(res.assets.open("appfilter.xml"), "utf-8") }
            var ev = parser.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component") ?: ""; val dr = parser.getAttributeValue(null, "drawable")
                    val m = Regex("ComponentInfo\\{([^/]+)/([^}]+)\\}").find(comp)
                    if (m != null && dr != null) {
                        val pkg = m.groupValues[1]; var cls = m.groupValues[2]; if (cls.startsWith(".")) cls = pkg + cls
                        map["$pkg/$cls"] = dr; map.putIfAbsent("$pkg/*", dr)
                    }
                }
                ev = parser.next()
            }
        } catch (e: Exception) { }
    }
    fun drawable(ctx: Context, pack: String, pkg: String, cls: String): android.graphics.drawable.Drawable? {
        load(ctx, pack)
        val name = map["$pkg/$cls"] ?: map["$pkg/*"] ?: return null
        return try {
            val res = ctx.packageManager.getResourcesForApplication(pack)
            val id = res.getIdentifier(name, "drawable", pack).takeIf { it != 0 } ?: res.getIdentifier(name, "mipmap", pack)
            if (id == 0) null else androidx.core.content.res.ResourcesCompat.getDrawable(res, id, null)
        } catch (e: Exception) { null }
    }
}

/** Needed only so Android lets Stoa read what Spotify is playing (Notification access). */
class MediaListener : android.service.notification.NotificationListenerService()

/** Device admin used only to lock the screen (double-tap on Home). */
class LockAdmin : android.app.admin.DeviceAdminReceiver() {
    companion object {
        private fun comp(ctx: Context) = ComponentName(ctx, LockAdmin::class.java)
        private fun dpm(ctx: Context) = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        fun active(ctx: Context) = try { dpm(ctx).isAdminActive(comp(ctx)) } catch (e: Exception) { false }
        fun ask(ctx: Context) = try {
            ctx.startActivity(Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp(ctx))
                .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets you double-tap Home to lock the screen. Stoa uses nothing else.")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
        fun remove(ctx: Context) = try { dpm(ctx).removeActiveAdmin(comp(ctx)) } catch (e: Exception) { }
        fun lock(ctx: Context) { if (active(ctx)) try { dpm(ctx).lockNow() } catch (e: Exception) { } else ask(ctx) }
    }
}
