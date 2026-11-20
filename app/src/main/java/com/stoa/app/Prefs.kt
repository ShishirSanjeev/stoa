package com.stoa.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-local settings and working state (timers, drafts, the workout in progress, launcher choices). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("stoa", Context.MODE_PRIVATE)
    fun str(k: String, def: String): String = sp.getString(k, null)?.takeIf { it.isNotBlank() } ?: def
    fun bool(k: String, def: Boolean) = sp.getBoolean(k, def)
    fun long(k: String, def: Long = 0L) = sp.getLong(k, def)
    fun int(k: String, def: Int = 0) = sp.getInt(k, def)
    fun put(k: String, v: Any?) {
        val e = sp.edit()
        when (v) {
            null -> e.remove(k)
            is String -> e.putString(k, v)
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toSet())
            else -> e.putString(k, v.toString())
        }
        e.apply()
    }
    fun set(k: String): Set<String> = sp.getStringSet(k, emptySet())?.toSet() ?: emptySet()
    fun json(k: String): JSONObject? = sp.getString(k, null)?.let { try { JSONObject(it) } catch (e: Exception) { null } }
    fun jsonArr(k: String): JSONArray? = sp.getString(k, null)?.let { try { JSONArray(it) } catch (e: Exception) { null } }

    var vaultPath: String
        get() = sp.getString("vaultPath", "") ?: ""
        set(v) { put("vaultPath", v) }

    /* mindful apps & passes are read by the accessibility service too */
    var mindful: Set<String>
        get() = sp.getStringSet("mindful", null)?.toSet() ?: DEFAULT_MINDFUL
        set(v) = put("mindful", v)
    var hidden: Set<String>
        get() = set("hidden")
        set(v) = put("hidden", v)
    fun passUntil(pkg: String) = long("pass_$pkg")
    fun grantPass(pkg: String, minutes: Int) = put("pass_$pkg", System.currentTimeMillis() + minutes * 60_000L)
    fun resisted(day: String) = int("resisted_$day")
    fun addResisted(day: String) = put("resisted_$day", resisted(day) + 1)

    companion object {
        val DEFAULT_MINDFUL = setOf("com.instagram.android", "com.google.android.youtube", "com.twitter.android", "com.zhiliaoapp.musically", "com.reddit.frontpage", "com.facebook.katana", "com.snapchat.android")
    }
}
