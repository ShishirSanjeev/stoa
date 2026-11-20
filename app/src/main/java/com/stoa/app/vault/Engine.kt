package com.stoa.app.vault

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stoa.app.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Toast(val title: String, val sub: String = "", val kind: String = "xp")

private data class FileInfo(val mtime: Long, val size: Long, val tasks: List<Task>, val dayKey: String?, val day: Day?, val created: String?)

/**
 * Everything Stoa knows comes from the vault's markdown files, and everything it changes is written back
 * in exactly the formats Obsidian and the Serendipity dashboard use.
 */
class Engine(private val ctx: Context) {
    val prefs = Prefs(ctx)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version
    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 16)
    val toasts: SharedFlow<Toast> = _toasts

    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    @Volatile private var files: Map<String, FileInfo> = emptyMap()
    var days by mutableStateOf<Map<String, Day>>(emptyMap())
        private set
    var tasks by mutableStateOf<Map<String, List<Task>>>(emptyMap())
        private set
    var noteDays by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var mdPaths by mutableStateOf<List<String>>(emptyList())
        private set
    var dash by mutableStateOf<Map<String, Any?>>(emptyMap())
        private set
    var stoa by mutableStateOf<Map<String, Any?>>(emptyMap())
        private set
    var rpg by mutableStateOf<Rpg?>(null)
        private set

    /* ── settings ── */
    val root: File? get() = prefs.vaultPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }
    val vaultName get() = root?.name ?: ""
    val habitHeading get() = prefs.str("habitHeading", "## Habits")
    val priorityHeading get() = prefs.str("priorityHeading", "## Today's three")
    val workoutHeading get() = prefs.str("workoutHeading", "## Workout")
    val dashState get() = prefs.str("dashboardState", "Dashboard State.md")
    val stoaState get() = prefs.str("stoaState", "Stoa State.md")
    val clippings get() = prefs.str("clippingsFolder", "9999 Web-Clippings")
    val capture get() = prefs.str("captureFolder", "9999 Quick Capture")
    val inbox get() = prefs.str("inboxPath", "Inbox.md")
    val bookNote get() = prefs.str("bookNote", "0001 Resources/Writing - Hope of the Alterian.md")
    val writingFolder get() = prefs.str("writingFolder", "0001 Resources")
    val writingPrefix get() = prefs.str("writingPrefix", "HOTA - ")
    val canvasFolder get() = prefs.str("canvasFolder", "0001 Resources/Canvas")
    val attachments get() = prefs.str("attachmentsFolder", "0001 Resources/Attachments")
    private val dashboardNote get() = prefs.str("dashboardNote", "0001 Resources/dashboards/Dashboard-Serendipity-v3.9.0.md")
    private val excludeFolders = listOf("Templates", "Weekly Notes")

    fun toast(t: String, s: String = "", k: String = "xp") { _toasts.tryEmit(Toast(t, s, k)) }
    private fun bump() { _version.value = _version.value + 1 }
    fun today(): String = LocalDate.now().toString()

    /* ── daily notes (settings from the Daily notes core plugin) ── */
    data class DailyCfg(val folder: String, val format: String, val template: String) {
        val fmt: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern(momentToJava(format)) }
    }
    @Volatile private var dailyCfgCache: DailyCfg? = null
    fun dailyCfg(): DailyCfg {
        dailyCfgCache?.let { return it }
        var c = DailyCfg("Periodic/Daily", "YYYY-MM-DD", "")
        try {
            val f = File(root ?: return c, ".obsidian/daily-notes.json")
            if (f.isFile) { val j = JSONObject(f.readText()); c = DailyCfg(j.optString("folder", "").trim('/'), j.optString("format", "").ifBlank { "YYYY-MM-DD" }, j.optString("template", "")) }
        } catch (e: Exception) { }
        dailyCfgCache = c; return c
    }
    fun dailyRel(date: LocalDate = LocalDate.now()): String { val c = dailyCfg(); return (if (c.folder.isNotEmpty()) c.folder + "/" else "") + date.format(c.fmt) + ".md" }
    fun dailyKeyOf(rel: String): String? {
        val c = dailyCfg(); var r = rel
        if (c.folder.isNotEmpty()) { if (!rel.startsWith(c.folder + "/")) return null; r = rel.substring(c.folder.length + 1) }
        if (!r.endsWith(".md")) return null
        return try { LocalDate.parse(r.removeSuffix(".md"), c.fmt).toString() } catch (e: Exception) { null }
    }
    fun dailyLink(key: String): String { val rel = dailyRel(LocalDate.parse(key)); return "[[" + rel.removeSuffix(".md") + "|" + rel.substringAfterLast('/').removeSuffix(".md") + "]]" }

    /* ── files ── */
    fun file(rel: String) = File(root, rel)
    fun exists(rel: String) = root != null && file(rel).isFile
    fun readText(rel: String): String? = try { file(rel).takeIf { it.isFile }?.readText() } catch (e: Exception) { null }
    fun relOf(f: File): String = f.relativeTo(root!!).invariantSeparatorsPath

    /** Serialised read-modify-write; the file is rescanned right after so the UI follows. */
    suspend fun modify(rel: String, fn: (String) -> String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val f = file(rel); f.parentFile?.mkdirs()
            val before = if (f.isFile) f.readText() else ""
            val after = fn(before)
            if (after != before || !f.isFile) f.writeText(after)
        }
        rescanOne(rel); bump()
    }
    suspend fun create(rel: String, text: String): String = withContext(Dispatchers.IO) {
        var r = rel; var n = 2
        val base = rel.substringBeforeLast('.'); val ext = rel.substringAfterLast('.', "md")
        while (file(r).exists()) r = "$base ${n++}.$ext"
        lock.withLock { file(r).parentFile?.mkdirs(); file(r).writeText(text) }
        if (r.endsWith(".md")) rescanOne(r)
        bump(); r
    }
    suspend fun ensureDaily(date: LocalDate = LocalDate.now()): String {
        val rel = dailyRel(date)
        if (!exists(rel)) {
            var body = ""
            val t = dailyCfg().template
            if (t.isNotBlank()) {
                val tr = if (t.endsWith(".md")) t else "$t.md"
                readText(tr)?.let { tpl ->
                    body = tpl.replace(Regex("\\{\\{\\s*(date|time|title)\\s*(?::([^}]+))?\\}\\}", RegexOption.IGNORE_CASE)) { m ->
                        when (m.groupValues[1].lowercase()) {
                            "title" -> date.format(dailyCfg().fmt)
                            "time" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern(momentToJava(m.groupValues[2].ifBlank { "HH:mm" }.trim())))
                            else -> date.format(DateTimeFormatter.ofPattern(momentToJava(m.groupValues[2].ifBlank { dailyCfg().format }.trim())))
                        }
                    }
                }
            }
            withContext(Dispatchers.IO) { lock.withLock { file(rel).parentFile?.mkdirs(); if (!file(rel).exists()) file(rel).writeText(body) } }
        }
        return rel
    }

    /* ── scanning ── */
    private fun isExcluded(rel: String) = rel == dashState || rel == stoaState || rel == dashboardNote || excludeFolders.any { rel.startsWith("$it/") }

    private fun parseFile(rel: String, f: File, habits: List<String>): FileInfo {
        val mtime = f.lastModified(); val size = f.length()
        val key = dailyKeyOf(rel)
        val text = try { f.readText() } catch (e: Exception) { "" }
        val tasks = tasksIn(rel, text, habitHeading.trimStart('#').trim())
        var day: Day? = null; var created: String? = null
        if (key != null) {
            val fm = Frontmatter.split(text).fm
            val (done, present) = scanHabits(text, habitHeading.trimStart('#').trim(), habits)
            day = Day(done, present, fm["morning_routine"].asList().toSet(), fm["evening_routine"].asList().toSet(),
                fm["routines_complete"].asList().map { it.lowercase() }.toSet(), fm["reading_minutes"].asInt() ?: 0, fm["meditation_minutes"].asInt() ?: 0,
                scanWorkouts(text, workoutHeading.trimStart('#').trim()))
        } else if (!rel.startsWith(clippings.trim('/') + "/") && size >= 200) {
            created = try {
                val a = Files.readAttributes(f.toPath(), BasicFileAttributes::class.java)
                Instant.ofEpochMilli(a.creationTime().toMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            } catch (e: Exception) { Instant.ofEpochMilli(mtime).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
        }
        return FileInfo(mtime, size, tasks, key, day, created)
    }

    private fun loadStates() {
        dash = readText(dashState)?.let { Frontmatter.split(it).fm } ?: emptyMap()
        stoa = readText(stoaState)?.let { Frontmatter.split(it).fm["stoa"].asMap() } ?: emptyMap()
    }

    /** Incremental: only files whose modification time changed are read again. */
    fun scan(force: Boolean = false) = scope.launch(Dispatchers.IO) { scanNow(force) }
    private suspend fun scanNow(force: Boolean) {
        val r = root
        if (r == null) { error = if (prefs.vaultPath.isBlank()) "Choose your vault folder in Settings." else "Can't open the vault folder — check Stoa has All-files access."; ready = false; bump(); return }
        try {
            dailyCfgCache = null
            loadStates()
            val habits = habitList()
            val old = if (force) emptyMap() else files
            val next = HashMap<String, FileInfo>()
            r.walkTopDown().onEnter { it == r || !it.name.startsWith(".") }.forEach { f ->
                if (!f.isFile || !f.name.endsWith(".md")) return@forEach
                val rel = relOf(f)
                val prev = old[rel]
                next[rel] = if (prev != null && prev.mtime == f.lastModified() && prev.size == f.length()) prev else parseFile(rel, f, habits)
            }
            files = next; rebuild(); error = null; ready = true; bump()
            checkLevel()
        } catch (e: Exception) { error = "Couldn't read the vault: ${e.message}"; bump() }
    }
    private fun rescanOne(rel: String) {
        if (!rel.endsWith(".md")) return
        if (rel == dashState || rel == stoaState) loadStates()
        val f = file(rel); val m = HashMap(files)
        if (f.isFile) m[rel] = parseFile(rel, f, habitList()) else m.remove(rel)
        files = m; rebuild()
    }
    private fun rebuild() {
        val d = HashMap<String, Day>(); val t = HashMap<String, List<Task>>(); val nd = HashMap<String, Int>(); val paths = ArrayList<String>()
        for ((rel, fi) in files) {
            if (isExcluded(rel)) continue
            paths.add(rel)
            if (fi.tasks.isNotEmpty()) t[rel] = fi.tasks
            if (fi.dayKey != null && fi.day != null) d[fi.dayKey] = fi.day
            fi.created?.let { nd[it] = (nd[it] ?: 0) + 1 }
        }
        days = d; tasks = t; noteDays = nd; mdPaths = paths; recompute()
    }

    /* ── XP ── */
    fun allTasks(): List<Task> = tasks.values.flatten()
    private fun recompute() {
        val tk = today(); val all = allTasks()
        val S = computeStats(all, days, noteDays, RpgOpts(tk, prefs.bool("weekStartsMonday", false), areas(), habitList(), dash["habit_added"].asMap().mapValues { it.value.toString() },
            habitList().size, mdPaths.size, all.count { it.status == "open" && it.due != null && it.due < tk }))
        rpg = evaluateRpg(S)
    }
    private fun reward(before: Rpg?, reason: String) {
        val cur = rpg ?: return
        if (before != null && reason.isNotEmpty()) { val d = cur.baseXP - before.baseXP; if (d > 0) toast("+$d XP", reason, "xp") }
        checkLevel(); bump()
    }
    private fun checkLevel() {
        val cur = rpg ?: return
        val book = dash["achievements"]
        if (book is Map<*, *>) {
            val m = LinkedHashMap(book.entries.associate { it.key.toString() to it.value })
            val fresh = cur.unlocked.filter { !m.containsKey(it) }
            if (fresh.isNotEmpty()) {
                fresh.forEach { m[it] = today() }; setDash("achievements", m)
                fresh.take(2).forEach { id -> ACHIEVEMENTS.find { it.id == id }?.let { a -> toast("${a.icon}  ${a.name}", "${TIER_NAME[a.tier]} achievement · +${TIER_XP[a.tier]} XP", "ach") } }
            }
        }
        val seen = dash["level_seen"].asInt()
        if (seen != null && cur.level > seen) toast("Level up!", "Level ${cur.level} · ${cur.rank}", "level")
        if (seen == null || cur.level > seen) setDash("level_seen", cur.level)
    }

    /* ── habits, areas, routines: shared with the dashboard through Dashboard State.md ── */
    fun habitLayout(): List<String> = (dash["habits"] as? List<*>)?.map { it.toString() } ?: listOf("Reading", "Coding", "Meditate", "Gym")
    fun habitList(): List<String> = habitLayout().filter { !it.trim().startsWith("---") }
    fun habitIcon(h: String): String = dash["habit_icons"].asMap()[h.lowercase()]?.toString() ?: ""
    fun habitNamed(n: String): String? = habitList().firstOrNull { it.equals(n, true) }
    fun areas(): List<Area> {
        val defs = listOf(
            Area("creativity", "Creativity", "🎨", mapOf("Reading" to 5, "Writing" to 3), notesTarget = 3, notesUnless = "writing"),
            Area("mental", "Mental", "🧠", mapOf("Meditate" to 5)),
            Area("physical", "Physical", "💪", mapOf("Gym" to 3, "Badminton" to 2)),
            Area("professional", "Professional", "💼", mapOf("Leetquiz" to 3), tag = "#metric", minTasks = 10))
        val ov = dash["area_targets"].asMap()
        return defs.map { a ->
            val o = ov[a.id] as? Map<*, *> ?: return@map a
            var out = a
            (o["habits"] as? Map<*, *>)?.let { hm -> out = out.copy(habits = hm.entries.mapNotNull { (k, v) -> v.asInt()?.takeIf { it > 0 }?.let { k.toString() to it } }.toMap()) }
            if (o.containsKey("tag")) { val t = o["tag"]?.toString()?.trim()?.trimStart('#')?.lowercase() ?: ""; out = out.copy(tag = if (t.isNotEmpty()) "#$t" else null) }
            o["minTasks"].asInt()?.let { out = out.copy(minTasks = maxOf(0, it)) }
            if (a.notesTarget > 0 && o["notes"] != null) out = out.copy(notesTarget = maxOf(0, o["notes"].asInt() ?: 0))
            out
        }
    }
    fun routines(): Map<String, List<RoutineItem>> {
        val r = dash["routines"] as? Map<*, *>
        val defs = mapOf("morning" to listOf("🪥 Brush teeth", "💦 Wash face"), "evening" to listOf("🫧 Mouth wash", "💊 Multivitamin"))
        return listOf("morning", "evening").associateWith { k ->
            val l = r?.get(k) as? List<*>
            (l?.map { x -> if (x is Map<*, *>) RoutineItem(x["emoji"]?.toString() ?: "", x["text"]?.toString()?.trim() ?: "") else parseRoutineItem(x.toString()) }
                ?: defs[k]!!.map { parseRoutineItem(it) }).filter { it.text.isNotEmpty() }
        }
    }
    fun saveRoutines(r: Map<String, List<RoutineItem>>) = setDash("routines", r.mapValues { (_, l) -> l.map { mapOf("emoji" to it.emoji, "text" to it.text) } })
    fun day(key: String = today()) = days[key] ?: Day()
    private fun putDay(key: String, d: Day) { days = HashMap(days).also { it[key] = d } }
    fun streak(h: String): Int {
        val l = h.lowercase(); var k = LocalDate.now(); var n = 0
        if (days[k.toString()]?.done?.contains(l) != true) k = k.minusDays(1)
        while (days[k.toString()]?.done?.contains(l) == true) { n++; k = k.minusDays(1) }
        return n
    }

    fun setHabit(habit: String, on: Boolean, key: String = today()) {
        val before = rpg; val d = day(key); val l = habit.lowercase()
        putDay(key, d.copy(done = if (on) d.done + l else d.done - l, present = d.present + l)); recompute(); reward(before, if (on) habit else "")
        scope.launch { val rel = ensureDaily(LocalDate.parse(key)); modify(rel) { writeHabitText(it, habitHeading, habit, on) } }
    }
    suspend fun ensureHabit(name: String) { val h = habitNamed(name) ?: return; if (!day().done.contains(h.lowercase())) setHabit(h, true) }
    fun addHabit(name: String) {
        if (habitNamed(name) != null || name.isBlank()) return
        setDash("habits", habitLayout() + name)
        setDash("habit_added", LinkedHashMap(dash["habit_added"].asMap()).also { it[name.lowercase()] = today() })
    }
    fun addHabitDivider(label: String) = setDash("habits", habitLayout() + ("--- " + label).trim())
    fun removeHabit(name: String) = setDash("habits", habitLayout().filter { it != name })

    fun routineFull(d: Day, kind: String): Boolean { val l = routines()[kind] ?: emptyList(); val s = if (kind == "morning") d.morning else d.evening; return l.isNotEmpty() && l.all { s.contains(it.text) } }
    fun toggleRoutine(kind: String, text: String, key: String = today()) {
        val before = rpg; var d = day(key); val set = if (kind == "morning") d.morning else d.evening
        val on = !set.contains(text); val ns = if (on) set + text else set - text
        d = if (kind == "morning") d.copy(morning = ns) else d.copy(evening = ns)
        val wasFull = d.complete.contains(kind)
        d = d.copy(complete = if (routineFull(d, kind)) d.complete + kind else d.complete - kind)
        putDay(key, d); recompute(); reward(before, if (on) text else "")
        if (!wasFull && d.complete.contains(kind)) toast(if (kind == "morning") "☀️ Morning routine done" else "🌙 Evening routine done", if (kind == "morning") "A good start. Go get the day." else "All wrapped up. Rest well.", "ach")
        val lists = routines()
        fun ord(k: String, s: Set<String>): List<String> { val c = (lists[k] ?: emptyList()).map { it.text }.filter { s.contains(it) }; return c + s.filter { !c.contains(it) } }
        val m = ord("morning", d.morning); val e = ord("evening", d.evening); val c = listOf("morning", "evening").filter { d.complete.contains(it) }
        scope.launch {
            val rel = ensureDaily(LocalDate.parse(key))
            modify(rel) { txt -> Frontmatter.update(txt) { fm ->
                if (m.isNotEmpty()) fm["morning_routine"] = m else fm.remove("morning_routine")
                if (e.isNotEmpty()) fm["evening_routine"] = e else fm.remove("evening_routine")
                if (c.isNotEmpty()) fm["routines_complete"] = c else fm.remove("routines_complete")
            } }
        }
    }
    suspend fun addToDailyProp(prop: String, add: Int) {
        val rel = ensureDaily()
        modify(rel) { txt -> Frontmatter.update(txt) { fm -> fm[prop] = (fm[prop].asInt() ?: 0) + add } }
    }

    /* ── tasks ── */
    fun setTask(t: Task, done: Boolean) {
        val before = rpg; val tk = today()
        val nt = t.copy(status = if (done) "done" else "open", completion = if (done) tk else null)
        tasks = HashMap(tasks).also { m -> m[t.path] = (m[t.path] ?: emptyList()).map { if (it.id == t.id) nt else it } }
        recompute(); reward(before, if (done) t.text else "")
        scope.launch {
            modify(t.path) { txt ->
                val (lines, nl) = splitLines(txt)
                fun ok(j: Int) = TASK_LINE.find(lines.getOrNull(j) ?: "")?.groupValues?.get(4) == t.raw
                var i = t.line; if (!ok(i)) i = lines.indices.firstOrNull { ok(it) } ?: return@modify txt
                val m = TASK_LINE.find(lines[i])!!
                var rest = m.groupValues[4].replace(Regex("\\s*✅\\s*\\d{4}-\\d{2}-\\d{2}"), "")
                if (done) rest = rest.trimEnd() + " ✅ " + tk
                lines[i] = m.groupValues[1] + (if (done) "x" else " ") + m.groupValues[3] + rest
                lines.joinToString(nl)
            }
        }
    }
    suspend fun addTask(rel: String, heading: String?, text: String) {
        val line = "- [ ] $text ➕ ${today()}"
        modify(rel) { txt ->
            if (heading.isNullOrBlank()) { val (l, nl) = splitLines(txt); while (l.isNotEmpty() && l.last().isBlank()) l.removeAt(l.size - 1); l.add(line); l.joinToString(nl) + nl }
            else appendToSection(txt, heading, listOf(line))
        }
    }
    fun dueTasks(): List<Task> { val tm = LocalDate.now().plusDays(1).toString(); return allTasks().filter { it.status == "open" && it.due != null && it.due <= tm }.sortedWith(compareBy<Task> { it.due }.thenByDescending { it.prio }) }

    /* ── today's three: tasks under a heading in today's note, so tomorrow starts clean ── */
    fun priorities(): List<Task>? {
        val want = priorityHeading.trimStart('#').trim()
        return tasks[dailyRel()]?.filter { it.section.equals(want, true) }?.takeIf { it.isNotEmpty() }
    }
    suspend fun setPriorities(items: List<String>) {
        val rel = ensureDaily(); val old = priorities() ?: emptyList(); val head = priorityHeading
        val newLines = items.map { s -> val o = old.find { it.text == s }; if (o?.status == "done") "- [x] $s ✅ ${o.completion ?: today()}" else "- [ ] $s" }
        modify(rel) { txt ->
            val (lines, nl) = splitLines(txt)
            val sec = section(lines, head.trimStart('#').trim())
            if (sec != null) {
                var end = sec.last + 1; while (end - 1 > sec.first && lines[end - 1].isBlank()) end--
                for (k in sec.first + 1 until end) lines.removeAt(sec.first + 1)
                lines.addAll(sec.first + 1, newLines); return@modify lines.joinToString(nl)
            }
            var at = 0
            if (lines.firstOrNull() == "---") { val j = lines.subList(1, lines.size).indexOf("---"); if (j >= 0) at = j + 2 }
            val block = (if (at > 0) listOf("") else emptyList()) + listOf(head) + newLines + listOf("")
            lines.addAll(at, block); lines.joinToString(nl)
        }
    }

    /* ── shared state notes ── */
    fun setDash(key: String, value: Any?) {
        dash = LinkedHashMap(dash).also { it[key] = value }; bump()
        scope.launch {
            if (!exists(dashState)) create(dashState, "# Dashboard state\n\nWritten by the Serendipity dashboard and Stoa so habits, routines and achievements follow you across devices.\n")
            modify(dashState) { Frontmatter.update(it) { fm -> fm[key] = value } }
        }
    }
    fun setStoa(key: String, value: Any?) {
        stoa = LinkedHashMap(stoa).also { it[key] = value }; bump()
        val snap = LinkedHashMap(stoa)
        scope.launch {
            if (!exists(stoaState)) create(stoaState, "# Stoa state\n\nBooks, gym templates and custom exercises from the Stoa phone app.\n")
            modify(stoaState) { Frontmatter.update(it) { fm -> fm["stoa"] = snap } }
        }
    }

    /* ── capture, writing, reading, workouts ── */
    suspend fun captureIdea(text: String) {
        create(capture.trim('/') + "/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH_mm_ss")) + ".md", text.trim() + "\n")
        toast("Captured", capture.substringAfterLast('/'), "ach")
    }
    val bookName get() = bookNote.substringAfterLast('/').removeSuffix(".md")
    suspend fun uploadWriting(title: String, text: String): String {
        val bn = bookName
        val rel = create(writingFolder.trim('/') + "/" + writingPrefix + safeName(title) + ".md",
            "---\nbook: \"[[$bn]]\"\ncreated: ${today()}\ntags:\n  - writing\n---\nPart of [[$bn]]\n\n${text.trim()}\n")
        val base = rel.substringAfterLast('/').removeSuffix(".md"); val link = "[[$base]]"
        if (exists(bookNote)) modify(bookNote) { txt ->
            val (lines, nl) = splitLines(txt)
            val firstH1 = lines.indexOfFirst { it.startsWith("# ") }.let { if (it < 0) lines.size else it }
            val pre = Regex("\\[\\[" + Regex.escape(writingPrefix), RegexOption.IGNORE_CASE)
            val last = (0 until firstH1).lastOrNull { pre.containsMatchIn(lines[it]) }
            if (last != null) { lines.add(last + 1, link); lines.joinToString(nl) } else appendToSection(txt, "## Writing", listOf("- $link"))
        }
        toast("Uploaded", base, "ach"); ensureHabit(prefs.str("writingHabit", "Writing"))
        return rel
    }
    suspend fun logReading(bookRel: String, min: Int) {
        putDay(today(), day().let { it.copy(readMin = it.readMin + min) }); bump()
        if (exists(bookRel)) modify(bookRel) { appendToSection(it, "## Reading log", listOf("- ${dailyLink(today())} · $min min")) }
        addToDailyProp("reading_minutes", min)
        toast("$min min read", bookRel.substringAfterLast('/').removeSuffix(".md"), "ach")
        ensureHabit(prefs.str("readingHabit", "Reading"))
    }
    suspend fun logStillness(min: Int) {
        putDay(today(), day().let { it.copy(medMin = it.medMin + min) }); bump()
        addToDailyProp("meditation_minutes", min)
        toast("Session complete", "$min min of stillness", "ach")
        ensureHabit(prefs.str("meditateHabit", "Meditate"))
    }
    suspend fun writeWorkout(name: String, start: Long, end: Long, ex: List<WEx>) {
        val mins = maxOf(1, ((end - start) / 60000).toInt())
        val vol = ex.sumOf { e -> e.sets.sumOf { (it.kg.toDoubleOrNull() ?: 0.0) * (it.reps.toDoubleOrNull() ?: 0.0) } }
        val time = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        val head = "**$name** · $time · $mins min" + (if (vol > 0) " · " + String.format(java.util.Locale.US, "%,d", vol.toLong()) + " kg" else "")
        val body = ex.map { e -> "- ${e.name}: " + e.sets.joinToString(", ") { (if (it.kg.isNotBlank()) it.kg + "×" else "") + it.reps } }
        val rel = ensureDaily()
        modify(rel) { appendToSection(it, workoutHeading, listOf(head) + body, blankBefore = true) }
        modify(rel) { txt -> Frontmatter.update(txt) { fm -> val prev = fm["Workout"]?.toString()?.takeIf { it.isNotBlank() }; fm["Workout"] = (if (prev != null) "$prev; " else "") + "$name $mins min" } }
        toast("Workout saved", "Written to today's daily note", "ach")
        ensureHabit(prefs.str("gymHabit", "Gym"))
    }
    fun workoutsDesc(): List<Pair<String, Workout>> = days.keys.sortedDescending().flatMap { k -> days[k]!!.workouts.reversed().map { k to it } }

    /* ── vault listings for the UI ── */
    fun projects(): List<Pair<String, Double?>> {
        val out = ArrayList<Pair<String, Double?>>()
        for (rel in mdPaths) {
            val t = readHead(rel) ?: continue
            val sp = Frontmatter.split(t)
            val tagList = (sp.fm["tags"].asList() + sp.fm["tag"].asList()).map { it.trimStart('#').lowercase() }
            val inline = Regex("(?:^|\\s)#project(?:/[\\w-]+)?\\b").containsMatchIn(sp.body)
            if (!(tagList.any { it == "project" || it.startsWith("project/") } || inline)) continue
            val a = sp.fm["archived"]; if (a == true || a?.toString() == "true") continue
            out.add(rel to sp.fm["priority_rank"].asDouble())
        }
        return out.sortedWith(compareBy<Pair<String, Double?>> { it.second == null }.thenBy { it.second ?: 0.0 }.thenBy { it.first })
    }
    private val headCache = HashMap<String, Pair<Long, String>>()
    private fun readHead(rel: String): String? {
        val f = file(rel); val m = f.lastModified()
        synchronized(headCache) { headCache[rel]?.let { if (it.first == m) return it.second } }
        return try { val s = f.bufferedReader().use { r -> val cb = CharArray(4096); val n = r.read(cb); if (n > 0) String(cb, 0, n) else "" }; synchronized(headCache) { headCache[rel] = m to s }; s } catch (e: Exception) { null }
    }
    suspend fun setRank(rel: String, rank: Double) = modify(rel) { Frontmatter.update(it) { fm -> fm["priority_rank"] = if (rank == Math.floor(rank)) rank.toLong() else rank } }
    fun mdIn(folder: String): List<File> { val d = file(folder.trim('/')); return d.listFiles()?.filter { it.isFile && it.name.endsWith(".md") }?.sortedByDescending { it.lastModified() } ?: emptyList() }
    fun filesUnder(folder: String, exts: Set<String>): List<File> { val d = file(folder.trim('/')); if (!d.isDirectory) return emptyList(); return d.walkTopDown().filter { it.isFile && exts.contains(it.extension.lowercase()) }.sortedByDescending { it.lastModified() }.toList() }
    fun clippingFiles(): List<String> = mdPaths.filter { it.startsWith(clippings.trim('/') + "/") }
    fun searchNotes(q: String): List<String> { val s = q.lowercase(); return mdPaths.filter { it.substringAfterLast('/').lowercase().contains(s) }.sortedBy { it.substringAfterLast('/').length }.take(12) }
}

/** moment.js tokens → java.time pattern (covers the formats daily notes use). */
fun momentToJava(f: String): String {
    val sb = StringBuilder(); var i = 0
    val toks = listOf("YYYY" to "yyyy", "YY" to "yy", "MMMM" to "MMMM", "MMM" to "MMM", "MM" to "MM", "M" to "M", "DD" to "dd", "Do" to "d", "D" to "d",
        "dddd" to "EEEE", "ddd" to "EEE", "HH" to "HH", "H" to "H", "hh" to "hh", "mm" to "mm", "ss" to "ss", "A" to "a", "ww" to "ww", "gggg" to "YYYY")
    while (i < f.length) {
        if (f[i] == '[') { val j = f.indexOf(']', i); if (j > i) { sb.append("'").append(f.substring(i + 1, j).replace("'", "''")).append("'"); i = j + 1; continue } }
        val t = toks.firstOrNull { f.startsWith(it.first, i) }
        if (t != null) { sb.append(t.second); i += t.first.length; continue }
        val c = f[i]; if (c.isLetter()) sb.append("'").append(c).append("'") else sb.append(c); i++
    }
    return sb.toString()
}
