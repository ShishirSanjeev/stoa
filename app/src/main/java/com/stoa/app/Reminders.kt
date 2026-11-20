package com.stoa.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** A task with an optional time, parsed from plain words: "call mum tomorrow 6pm", "pay rent fri", "stretch in 20 min". */
data class Parsed(val text: String, val at: LocalDateTime?, val dayOnly: LocalDate?)

object Reminders {
    private const val CH = "reminders"

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): Parsed {
        var s = " " + input.trim() + " "
        var day: LocalDate? = null; var time: LocalTime? = null; var rel: LocalDateTime? = null
        fun cut(r: Regex): MatchResult? { val m = r.find(s) ?: return null; s = s.removeRange(m.range).let { " " + it.trim() + " " }; return m }
        cut(Regex("(?i)\\s(?:in)\\s+(\\d+)\\s*(minutes|minute|mins|min|m|hours|hour|hrs|hr|h)\\b"))?.let { m ->
            val n = m.groupValues[1].toLong(); rel = if (m.groupValues[2].lowercase().startsWith("h")) now.plusHours(n) else now.plusMinutes(n)
        }
        cut(Regex("(?i)\\s(\\d{4}-\\d{2}-\\d{2})\\b"))?.let { day = LocalDate.parse(it.groupValues[1]) }
        cut(Regex("(?i)\\s(?:on\\s+)?(today|tonight|tomorrow|tmrw|tmr)\\b"))?.let { m ->
            when (m.groupValues[1].lowercase()) { "today" -> { day = now.toLocalDate() }; "tonight" -> { day = now.toLocalDate(); if (time == null) time = LocalTime.of(20, 0) }; else -> { day = now.toLocalDate().plusDays(1) } }
        }
        cut(Regex("(?i)\\s(?:on\\s+|next\\s+)?(mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)(?:day|nesday|sday|urday)?\\b"))?.let { m ->
            val d = mapOf("mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
                "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY)[m.groupValues[1].lowercase()]!!
            day = now.toLocalDate().with(TemporalAdjusters.next(d))
        }
        cut(Regex("(?i)\\s(?:at\\s+|@)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b"))?.let { m ->
            var h = m.groupValues[1].toInt() % 12; if (m.groupValues[3].lowercase() == "pm") h += 12
            time = LocalTime.of(h, m.groupValues[2].ifEmpty { "0" }.toInt().coerceIn(0, 59))
        } ?: cut(Regex("(?i)\\s(?:at\\s+|@)(\\d{1,2})(?::(\\d{2}))?\\b|\\s(\\d{1,2}):(\\d{2})\\b"))?.let { m ->
            val h = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toInt().coerceIn(0, 23); val mi = (m.groupValues[2].ifEmpty { m.groupValues[4] }).ifEmpty { "0" }.toInt().coerceIn(0, 59)
            time = LocalTime.of(h, mi)
        }
        cut(Regex("(?i)\\s(morning)\\b"))?.let { if (time == null) time = LocalTime.of(9, 0) }
        cut(Regex("(?i)\\s(evening)\\b"))?.let { if (time == null) time = LocalTime.of(18, 0) }
        val text = s.trim().replace(Regex("\\s+(at|on|by)$", RegexOption.IGNORE_CASE), "").trim()
        rel?.let { return Parsed(text, it, null) }
        val t = time
        if (t != null) {
            var d = day ?: now.toLocalDate()
            if (day == null && LocalDateTime.of(d, t).isBefore(now)) d = d.plusDays(1)
            return Parsed(text, LocalDateTime.of(d, t), null)
        }
        return Parsed(text, null, day)
    }

    fun describe(p: Parsed): String {
        val at = p.at; val d = p.dayOnly
        val today = LocalDate.now()
        fun dayWord(x: LocalDate) = when (x) { today -> "today"; today.plusDays(1) -> "tomorrow"; else -> x.format(DateTimeFormatter.ofPattern("EEE d MMM")) }
        return when { at != null -> "remind " + dayWord(at.toLocalDate()) + " " + at.format(DateTimeFormatter.ofPattern("h:mm a")); d != null -> "due " + dayWord(d); else -> "no reminder" }
    }

    /** "- [ ] text (@2026-11-21 18:00) 📅 2026-11-21" — the Reminder plugin's format plus a Tasks due date. */
    fun taskLine(p: Parsed): String {
        val at = p.at; val d = at?.toLocalDate() ?: p.dayOnly
        return p.text + (if (at != null) " (@" + at.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ")" else "") + (if (d != null) " 📅 $d" else "")
    }

    fun schedule(ctx: Context, text: String, at: LocalDateTime) {
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); if (ms <= System.currentTimeMillis()) return
        val p = Prefs(ctx); val arr = p.jsonArr("reminders") ?: JSONArray()
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        arr.put(JSONObject().put("id", id).put("text", text).put("at", ms)); p.put("reminders", arr.toString())
        arm(ctx, id, text, ms)
    }
    private fun arm(ctx: Context, id: Int, text: String, ms: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("text", text), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi) }
    }
    fun rearm(ctx: Context) {
        val p = Prefs(ctx); val arr = p.jsonArr("reminders") ?: return; val keep = JSONArray(); val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) { val o = arr.optJSONObject(i) ?: continue; if (o.optLong("at") > now) { keep.put(o); arm(ctx, o.optInt("id"), o.optString("text"), o.optLong("at")) } }
        p.put("reminders", keep.toString())
    }
    fun show(ctx: Context, id: Int, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        val tap = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(id, Notification.Builder(ctx, CH).setSmallIcon(R.drawable.ic_fg).setContentTitle(text).setContentText("Reminder from Stoa · it's in your Inbox").setAutoCancel(true).setContentIntent(tap).build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { Reminders.show(ctx, intent.getIntExtra("id", 0), intent.getStringExtra("text") ?: "Reminder") }
}
