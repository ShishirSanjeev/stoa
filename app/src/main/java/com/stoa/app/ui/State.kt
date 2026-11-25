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
    "home" to "Home", "today" to "Today", "almanac" to "Almanac", "notes" to "Notes", "gym" to "Gym",
    "reading" to "Reading", "still" to "Stillness", "writing" to "Writing", "tasks" to "Tasks",
    "ideas" to "Ideas", "canvas" to "Canvas", "shots" to "Screenshots"
)
val SECTION_TAB = mapOf("reading" to "today", "still" to "today", "writing" to "today", "tasks" to "notes", "ideas" to "notes", "canvas" to "notes", "shots" to "notes", "gym" to "notes")
val HOME_WIDGETS = linkedMapOf("level" to "Level & XP", "screen" to "Usage graph", "three" to "Today's three", "search" to "Search", "capture" to "Capture an idea", "seren" to "Serendipity", "due" to "Due soon")
val QUICK = linkedMapOf("idea" to "Idea", "task" to "Task", "daily" to "Daily note", "read" to "Read", "still" to "Breathe", "gym" to "Lift", "canvas" to "Canvas", "shots" to "Shots", "search" to "Search", "sync" to "Sync")
val ALMANAC_WIDGETS = linkedMapOf("balance" to "Weekly balance", "screen" to "Screen time", "bio" to "Bio-metrics", "routines" to "Routines", "clips" to "Clipped this week")
val TODAY_SECTIONS = linkedMapOf("three" to "Today's three", "routines" to "Routines", "habits" to "Habits", "projects" to "Projects")

/** What's shown and in which order. Device-local; edited from Settings → Layout. */
data class Layout(
    val tabs: List<Pair<String, Boolean>>, val home: List<Pair<String, Boolean>>, val today: List<Pair<String, Boolean>>,
    val todaySections: List<Pair<String, Boolean>>, val notes: List<Pair<String, Boolean>>, val dock: List<String>,
    val quick: List<Pair<String, Boolean>> = QUICK.keys.map { it to (it != "canvas" && it != "task") },
    val almanac: List<Pair<String, Boolean>> = ALMANAC_WIDGETS.keys.map { it to true }
) {
    fun on(list: List<Pair<String, Boolean>>) = list.filter { it.second }.map { it.first }
    fun toJson(): String {
        fun arr(l: List<Pair<String, Boolean>>) = JSONArray(l.map { JSONArray(listOf(it.first, it.second)) })
        return JSONObject().put("tabs", arr(tabs)).put("home", arr(home)).put("today", arr(today)).put("todaySections", arr(todaySections))
            .put("notes", arr(notes)).put("dock", JSONArray(dock)).put("quick", arr(quick)).put("almanac", arr(almanac)).put("v", 3).toString()
    }
    companion object {
        val DEFAULT = Layout(
            listOf("home", "today", "notes", "almanac").map { it to true },
            HOME_WIDGETS.keys.map { it to true },
            listOf("today", "reading", "still", "writing").map { it to true },
            TODAY_SECTIONS.keys.map { it to true },
            listOf("tasks", "ideas", "canvas", "shots", "gym").map { it to true },
            listOf("sec:today", "sec:writing", "sec:gym", "sec:shots"))
        /** Sub-pages that can live on either Today or Notes. */
        val SUBS = listOf("today", "reading", "still", "writing", "tasks", "ideas", "canvas", "shots", "gym")
        fun load(p: Prefs): Layout {
            val j = p.json("layout") ?: return DEFAULT
            if (j.optInt("v", 1) < 2) return DEFAULT.copy(dock = DEFAULT.dock)   // new page structure: start from the new order
            fun raw(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.optString(0) } } ?: emptyList()
            fun list(k: String, def: List<Pair<String, Boolean>>, allowed: List<String> = def.map { it.first }, elsewhere: List<String> = emptyList()): List<Pair<String, Boolean>> {
                val a = j.optJSONArray(k) ?: return def.filter { it.first !in elsewhere }
                val got = (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { it.optString(0) to it.optBoolean(1, true) } }.filter { g -> g.first in allowed }
                val out = got.toMutableList()
                def.forEachIndexed { i, d -> if (out.none { it.first == d.first } && d.first !in elsewhere) out.add(minOf(i, out.size), d) }   // new widgets appear where they belong
                return out
            }
            val v = j.optInt("v", 1)
            val dock = j.optJSONArray("dock")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: DEFAULT.dock
            var tabs = list("tabs", DEFAULT.tabs)
            if (v < 3) tabs = tabs.filter { it.first != "almanac" } + tabs.filter { it.first == "almanac" }   // Almanac moves to the end
            return Layout(tabs, list("home", DEFAULT.home), list("today", DEFAULT.today, SUBS, raw("notes")), list("todaySections", DEFAULT.todaySections), list("notes", DEFAULT.notes, SUBS, raw("today")), dock, list("quick", DEFAULT.quick), list("almanac", DEFAULT.almanac))
        }
    }
}

/** A dock slot: an app / Stoa section (key) or a folder of them (apps). */
data class DockItem(val id: String, val key: String = "", val name: String = "", val apps: List<String> = emptyList()) {
    val isFolder get() = key.isEmpty()
    fun json(): JSONObject = JSONObject().put("id", id).put("key", key).put("name", name).put("apps", JSONArray(apps))
}

class UiState(val prefs: Prefs) {
    var tab by mutableStateOf("home")
    var todaySub by mutableStateOf("today")
    var notesSub by mutableStateOf("tasks")
    var canvas by mutableStateOf<String?>(null)
    var viewer by mutableStateOf<Int?>(null)
    var settings by mutableStateOf(false)
    var pauseFor by mutableStateOf<LApp?>(null)
    var drawerReq by mutableIntStateOf(0)
    var homeTick by mutableIntStateOf(0)
    val subPagers = mutableMapOf<String, androidx.compose.foundation.pager.PagerState>()
    var pendingSub by mutableStateOf<String?>(null)
    var homeName by mutableStateOf(prefs.str("home_name", "Atrium"))
    var shots by mutableStateOf<List<android.net.Uri>>(emptyList())
    var reader by mutableStateOf<String?>(null)
    var folder by mutableStateOf<String?>(null)
    var pins by mutableStateOf(prefs.jsonArr("pins")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList())
    fun savePins(l: List<String>) { pins = l; prefs.put("pins", JSONArray(l).toString()) }
    fun togglePin(k: String) = savePins(if (pins.contains(k)) pins - k else pins + k)
    var wpOn by mutableStateOf(prefs.bool("wp_on", false))
    var wpBmp by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    var dockCollapsed by mutableStateOf(prefs.bool("dock_collapsed", false))
    var dock by mutableStateOf(loadDock())
    var openFolder by mutableStateOf<String?>(null)
    var gSwipeDown by mutableStateOf(prefs.bool("g_swipe_down", true))
    var gDoubleLock by mutableStateOf(prefs.bool("g_double_lock", false))
    /** Quick shortcuts (drawer row + dock strip): order, with "!" marking hidden ones. */
    var shortcutPrefs by mutableStateOf(prefs.jsonArr("shortcuts")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList())
    fun saveShortcuts(l: List<String>) { shortcutPrefs = l; prefs.put("shortcuts", JSONArray(l).toString()) }
    var editShortcuts by mutableStateOf(false)
    var renames by mutableStateOf(prefs.json("renames")?.let { j -> j.keys().asSequence().associateWith { j.optString(it) } } ?: emptyMap())
    fun rename(key: String, name: String) { renames = if (name.isBlank()) renames - key else renames + (key to name.trim()); prefs.put("renames", JSONObject(renames as Map<*, *>).toString()) }
    fun applyPack(p: String) { prefs.put("icon_pack", p); com.stoa.app.Apps.clearIcons(); iconPack = p; if (p.isNotEmpty() && greyIcons) { greyIcons = false; prefs.put("grey_icons", false) } }
    /** Last-opened apps and how often each was opened from a search, for the top of the drawer. */
    var recent by mutableStateOf(prefs.jsonArr("recent_apps")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList())
    var searched by mutableStateOf(prefs.json("searched_apps")?.let { j -> j.keys().asSequence().associateWith { j.optInt(it) } } ?: emptyMap())
    fun noteLaunch(key: String) { recent = (listOf(key) + recent.filter { it != key }).take(8); prefs.put("recent_apps", JSONArray(recent).toString()) }
    fun noteSearched(key: String) { searched = searched + (key to (searched[key] ?: 0) + 1); prefs.put("searched_apps", JSONObject(searched as Map<*, *>).toString()) }
    var iconPack by mutableStateOf(prefs.str("icon_pack", ""))
    var greyIcons by mutableStateOf(prefs.bool("grey_icons", true))
    private fun loadDock(): List<DockItem> {
        val a = prefs.jsonArr("dock") ?: return (Layout.load(prefs).dock + pins.filter { it.startsWith("app:") }).distinct().take(6).map { DockItem(newId(), it) }
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o -> DockItem(o.optString("id").ifEmpty { newId() }, o.optString("key"), o.optString("name"),
            o.optJSONArray("apps")?.let { x -> (0 until x.length()).map { x.optString(it) } } ?: emptyList()) } }
    }
    private fun newId() = java.util.UUID.randomUUID().toString().take(8)
    fun saveDock(l: List<DockItem>) { dock = l; prefs.put("dock", JSONArray(l.map { it.json() }).toString()) }
    fun dockHas(k: String) = dock.any { it.key == k || it.apps.contains(k) }
    fun dockAdd(k: String) { if (!dockHas(k)) saveDock(dock + DockItem(newId(), k)) }
    fun dockRemove(k: String) = saveDock(dock.mapNotNull { d -> when { d.key == k -> null; d.apps.contains(k) -> d.copy(apps = d.apps - k).let { f -> if (f.apps.size == 1) DockItem(f.id, f.apps[0]) else if (f.apps.isEmpty()) null else f }; else -> d } })
    fun dockRemoveAt(i: Int) = saveDock(dock.filterIndexed { j, _ -> j != i })
    fun dockAddToFolder(id: String, k: String) = saveDock(dock.filter { it.key != k }.map { if (it.id == id && !it.apps.contains(k)) it.copy(apps = it.apps + k) else it })
    fun dockMove(from: Int, to: Int) { if (from == to) return; val l = dock.toMutableList(); val x = l.removeAt(from); l.add(to.coerceIn(0, l.size), x); saveDock(l) }
    /** Drop one slot onto another: into the folder, or make a new folder from the two. */
    fun dockMerge(from: Int, onto: Int) {
        val l = dock.toMutableList(); val a = l[from]; val b = l[onto]
        val incoming = if (a.isFolder) a.apps else listOf(a.key)
        l[onto] = if (b.isFolder) b.copy(apps = (b.apps + incoming).distinct()) else DockItem(newId(), "", "Folder", (listOf(b.key) + incoming).distinct())
        l.removeAt(from); saveDock(l)
    }
    fun dockRename(id: String, name: String) = saveDock(dock.map { if (it.id == id) it.copy(name = name) else it })
    fun setCollapsed(v: Boolean) { dockCollapsed = v; prefs.put("dock_collapsed", v) }
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
    fun goSub(page: String, sub: String) { if (page == "today") todaySub = sub else if (page == "notes") notesSub = sub; tab = page }
    fun go(key: String) {
        val t = when { layout.today.any { it.first == key } && key != "today" -> "today"; layout.notes.any { it.first == key } -> "notes"; else -> SECTION_TAB[key] }
        if (t == null) { tab = key; return }
        if (t == "today") todaySub = key else notesSub = key
        subPagers[t]?.let { pendingSub = key }
        tab = t
    }
}

val LocalEngine = staticCompositionLocalOf<Engine> { error("no engine") }
val LocalUi = staticCompositionLocalOf<UiState> { error("no ui") }
