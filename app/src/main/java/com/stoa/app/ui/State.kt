package com.stoa.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.stoa.app.LApp
import com.stoa.app.Prefs
import com.stoa.app.vault.Engine
import org.json.JSONArray
import org.json.JSONObject

val SECTIONS = linkedMapOf(
    "home" to "Home", "today" to "Today", "notes" to "Notes", "gym" to "Gym",
    "reading" to "Reading", "still" to "Stillness", "writing" to "Writing", "tasks" to "Book tasks",
    "ideas" to "Ideas", "canvas" to "Canvas", "shots" to "Screenshots"
)
val SECTION_TAB = mapOf("reading" to "today", "still" to "today", "writing" to "notes", "tasks" to "notes", "ideas" to "notes", "canvas" to "notes", "shots" to "notes")
val HOME_WIDGETS = linkedMapOf("level" to "Level & XP", "quick" to "Quick shortcuts", "pins" to "Pinned apps & folders", "three" to "Today's three", "screen" to "Screen time", "due" to "Due soon", "seren" to "Serendipity", "capture" to "Capture an idea")
val QUICK = linkedMapOf("idea" to "Idea", "task" to "Task", "daily" to "Daily note", "read" to "Read", "still" to "Breathe", "gym" to "Lift", "canvas" to "Canvas", "shots" to "Shots", "search" to "Search", "sync" to "Sync")
val TODAY_SECTIONS = linkedMapOf("three" to "Today's three", "routines" to "Routines", "habits" to "Habits", "projects" to "Projects")

/** What's shown and in which order. Device-local; edited from Settings → Layout. */
data class Layout(
    val tabs: List<Pair<String, Boolean>>, val home: List<Pair<String, Boolean>>, val today: List<Pair<String, Boolean>>,
    val todaySections: List<Pair<String, Boolean>>, val notes: List<Pair<String, Boolean>>, val dock: List<String>,
    val quick: List<Pair<String, Boolean>> = QUICK.keys.map { it to (it != "canvas" && it != "task") }
) {
    fun on(list: List<Pair<String, Boolean>>) = list.filter { it.second }.map { it.first }
    fun toJson(): String {
        fun arr(l: List<Pair<String, Boolean>>) = JSONArray(l.map { JSONArray(listOf(it.first, it.second)) })
        return JSONObject().put("tabs", arr(tabs)).put("home", arr(home)).put("today", arr(today)).put("todaySections", arr(todaySections))
            .put("notes", arr(notes)).put("dock", JSONArray(dock)).put("quick", arr(quick)).toString()
    }
    companion object {
        val DEFAULT = Layout(
            listOf("home", "today", "notes", "gym").map { it to true },
            HOME_WIDGETS.keys.map { it to true },
            listOf("today", "reading", "still").map { it to true },
            TODAY_SECTIONS.keys.map { it to true },
            listOf("writing", "tasks", "ideas", "canvas", "shots").map { it to true },
            listOf("sec:today", "sec:writing", "sec:gym", "sec:shots"))
        fun load(p: Prefs): Layout {
            val j = p.json("layout") ?: return DEFAULT
            fun list(k: String, def: List<Pair<String, Boolean>>): List<Pair<String, Boolean>> {
                val a = j.optJSONArray(k) ?: return def
                val got = (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { it.optString(0) to it.optBoolean(1, true) } }.filter { g -> def.any { it.first == g.first } }
                val out = got.toMutableList()
                def.forEachIndexed { i, d -> if (out.none { it.first == d.first }) out.add(minOf(i, out.size), d) }   // new widgets appear where they belong
                return out
            }
            val dock = j.optJSONArray("dock")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: DEFAULT.dock
            return Layout(list("tabs", DEFAULT.tabs), list("home", DEFAULT.home), list("today", DEFAULT.today), list("todaySections", DEFAULT.todaySections), list("notes", DEFAULT.notes), dock, list("quick", DEFAULT.quick))
        }
    }
}

class UiState(val prefs: Prefs) {
    var tab by mutableStateOf("home")
    var todaySub by mutableStateOf("today")
    var notesSub by mutableStateOf("writing")
    var canvas by mutableStateOf<String?>(null)
    var viewer by mutableStateOf<Int?>(null)
    var settings by mutableStateOf(false)
    var pauseFor by mutableStateOf<LApp?>(null)
    var drawerReq by mutableIntStateOf(0)
    var homeTick by mutableIntStateOf(0)
    var shots by mutableStateOf<List<android.net.Uri>>(emptyList())
    var reader by mutableStateOf<String?>(null)
    var folder by mutableStateOf<String?>(null)
    var pins by mutableStateOf(prefs.jsonArr("pins")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList())
    fun savePins(l: List<String>) { pins = l; prefs.put("pins", JSONArray(l).toString()) }
    fun togglePin(k: String) = savePins(if (pins.contains(k)) pins - k else pins + k)
    var wpOn by mutableStateOf(prefs.bool("wp_on", false))
    var wpBlur by mutableStateOf(prefs.int("wp_blur", 24))
    var wpDim by mutableStateOf(prefs.int("wp_dim", 55))
    fun setWallpaper(on: Boolean = wpOn, blur: Int = wpBlur, dim: Int = wpDim) { wpOn = on; wpBlur = blur; wpDim = dim; prefs.put("wp_on", on); prefs.put("wp_blur", blur); prefs.put("wp_dim", dim) }
    var capture by mutableStateOf<String?>(null)
    var layout by mutableStateOf(Layout.load(prefs))
    var resume by mutableIntStateOf(0)
    var apps by mutableStateOf<List<LApp>>(emptyList())
    var hidden by mutableStateOf(prefs.hidden)
    var mindful by mutableStateOf(prefs.mindful)
    fun saveLayout(l: Layout) { layout = l; prefs.put("layout", l.toJson()) }
    fun updateHidden(s: Set<String>) { hidden = s; prefs.hidden = s }
    fun updateMindful(s: Set<String>) { mindful = s; prefs.mindful = s }
    fun go(key: String) {
        val t = SECTION_TAB[key]
        if (t == null) { tab = key; return }
        if (t == "today") todaySub = key else notesSub = key
        tab = t
    }
}

val LocalEngine = staticCompositionLocalOf<Engine> { error("no engine") }
val LocalUi = staticCompositionLocalOf<UiState> { error("no ui") }
