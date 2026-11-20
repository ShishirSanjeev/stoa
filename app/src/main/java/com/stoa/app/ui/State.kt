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
val HOME_WIDGETS = linkedMapOf("level" to "Level & XP", "three" to "Today's three", "screen" to "Screen time", "due" to "Due soon", "seren" to "Serendipity", "capture" to "Capture an idea")
val TODAY_SECTIONS = linkedMapOf("three" to "Today's three", "routines" to "Routines", "habits" to "Habits", "projects" to "Projects")

/** What's shown and in which order. Device-local; edited from Settings → Layout. */
data class Layout(
    val tabs: List<Pair<String, Boolean>>, val home: List<Pair<String, Boolean>>, val today: List<Pair<String, Boolean>>,
    val todaySections: List<Pair<String, Boolean>>, val notes: List<Pair<String, Boolean>>, val dock: List<String>
) {
    fun on(list: List<Pair<String, Boolean>>) = list.filter { it.second }.map { it.first }
    fun toJson(): String {
        fun arr(l: List<Pair<String, Boolean>>) = JSONArray(l.map { JSONArray(listOf(it.first, it.second)) })
        return JSONObject().put("tabs", arr(tabs)).put("home", arr(home)).put("today", arr(today)).put("todaySections", arr(todaySections))
            .put("notes", arr(notes)).put("dock", JSONArray(dock)).toString()
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
                return got + def.filter { d -> got.none { it.first == d.first } }
            }
            val dock = j.optJSONArray("dock")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: DEFAULT.dock
            return Layout(list("tabs", DEFAULT.tabs), list("home", DEFAULT.home), list("today", DEFAULT.today), list("todaySections", DEFAULT.todaySections), list("notes", DEFAULT.notes), dock)
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
