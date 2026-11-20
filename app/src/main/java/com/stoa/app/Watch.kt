package com.stoa.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.stoa.app.mindful.PauseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The mindful pause without Accessibility (so banking apps like GPay stay happy): a quiet foreground service reads
 * which app is in front from Usage access, and shows the breathing screen over your chosen apps using
 * "Display over other apps". It checks about once a second while the screen is on and sleeps when it's off.
 */
class Watch : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var last = ""; private var lastShown = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Mindful pause", NotificationManager.IMPORTANCE_MIN).apply { description = "Keeps the breathing pause ready. You can hide this notification."; setShowBadge(false) })
        val tap = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH).setSmallIcon(R.drawable.ic_fg).setContentTitle("Mindful pause is on").setContentText("Stoa pauses the apps you chose before they open.")
            .setOngoing(true).setContentIntent(tap).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        running = true
        val prefs = Prefs(this); val um = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager; val pm = getSystemService(PowerManager::class.java)
        scope.launch {
            val e = UsageEvents.Event()
            while (isActive) {
                if (!pm.isInteractive) { last = ""; delay(3000); continue }
                try {
                    val now = System.currentTimeMillis(); val ev = um.queryEvents(now - 4000, now); var top: String? = null
                    while (ev.hasNextEvent()) { ev.getNextEvent(e); if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) top = e.packageName }
                    if (top != null && top != last) {
                        last = top
                        if (top != packageName && prefs.mindful.contains(top) && prefs.passUntil(top) < now && now - lastShown > 1500) {
                            lastShown = now
                            startActivity(Intent(this@Watch, PauseActivity::class.java).putExtra("pkg", top)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
                        }
                    }
                } catch (x: Exception) { }
                delay(700)
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { running = false; scope.cancel(); super.onDestroy() }

    companion object {
        const val CH = "watch"
        @Volatile var running = false
        fun canRun(ctx: Context) = Apps.hasUsage(ctx) && Apps.hasOverlay(ctx)
        fun sync(ctx: Context) {
            val want = Prefs(ctx).bool("watch_on", true) && canRun(ctx)
            val i = Intent(ctx, Watch::class.java)
            try { if (want && !running) ctx.startForegroundService(i) else if (!want && running) ctx.stopService(i) } catch (e: Exception) { }
        }
    }
}

/** Starts the watcher again after a reboot and re-arms reminders. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { Watch.sync(ctx); Reminders.rearm(ctx) }
}
