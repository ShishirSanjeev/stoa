package com.stoa.app.vault

/* Pure text helpers shared by the engine: tasks, sections, habits, workouts. */

val TASK_LINE = Regex("^(\\s*(?:[-*+]|\\d+[.)])\\s+\\[)(.)(\\]\\s?)(.*)$")
private val DUE_RE = Regex("(?:📅|⏳|\\[?due::)\\s*(\\d{4}-\\d{2}-\\d{2})")
private val DONE_RE = Regex("✅\\s*(\\d{4}-\\d{2}-\\d{2})")
private val DONE_FIELD = Regex("(?:completion|completed)::\\s*(\\d{4}-\\d{2}-\\d{2})", RegexOption.IGNORE_CASE)
private val PRIO_MAP = listOf("🔺" to 5, "⏫" to 4, "🔼" to 3, "🔽" to 1, "⏬" to 0)
private val PRIO_FIELD = Regex("(?:^|\\s)priority::\\s+(\\w+)", RegexOption.IGNORE_CASE)
private val TAG_RE = Regex("#[\\p{L}\\p{N}_/-]+")
private val HEADING = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$")
val EMOJI_LEAD = Regex("^((?:[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{2300}-\\x{23FF}])(?:\\x{FE0F}|\\x{20E3}|[\\x{1F3FB}-\\x{1F3FF}]|\\x{200D}[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}])*)\\s*")

fun stripMd(s: String): String = s
    .replace(Regex("!\\[\\[[^\\]]*\\]\\]"), "")
    .replace(Regex("\\[\\[([^\\]|]+)\\|([^\\]]+)\\]\\]"), "$2")
    .replace(Regex("\\[\\[([^\\]]+)\\]\\]"), "$1")
    .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")

fun cleanTask(s: String): String = stripMd(s
    .replace(Regex("^(?:[-*+]\\s+\\[.\\]\\s*)+"), "")
    .replace(Regex("(?:^|\\s)[A-Za-z][\\w-]*::\\s+(?:\\{[^}]*\\}|\"[^\"]*\"|\\S+)"), " ")
    .replace(Regex("(?:📅|⏳|🛫|✅|➕|❌)\\s*\\d{4}-\\d{2}-\\d{2}"), "")
    .replace(Regex("🔁[^📅⏳🛫✅➕❌⏫🔼🔽🔺⏬#\\[]*"), "")
    .replace(Regex("\\[[\\w-]+::[^\\]]*\\]"), "")
    .replace(Regex("[⏫🔼🔽🔺⏬]"), "")
    .replace(Regex("\\s\\^[\\w-]+$"), ""))
    .replace(Regex("\\s{2,}"), " ").trim()

fun parseTask(raw: String, status: String, path: String, line: Int, section: String): Task {
    val st = when { status.equals("x", true) -> "done"; status == "-" -> "cancelled"; else -> "open" }
    val due = DUE_RE.find(raw)?.groupValues?.get(1)
    val completion = (DONE_RE.find(raw) ?: DONE_FIELD.find(raw))?.groupValues?.get(1)
    var prio = 2
    for ((e, v) in PRIO_MAP) if (raw.contains(e)) { prio = v; break }
    if (prio == 2) {
        val named = mapOf("highest" to 5, "high" to 4, "medium" to 3, "normal" to 2, "low" to 1, "lowest" to 0)
        PRIO_FIELD.find(raw)?.groupValues?.get(1)?.lowercase()?.let { named[it]?.let { p -> prio = p } }
    }
    return Task(path, line, raw, section, cleanTask(raw).ifEmpty { "(untitled task)" }, st, due, completion, prio,
        TAG_RE.findAll(raw).map { it.value.lowercase() }.toList(), path.substringAfterLast('/').removeSuffix(".md"),
        raw.contains("🔁") || Regex("(?:^|\\s)repeat::").containsMatchIn(raw))
}

/** All tasks in a note, skipping fenced code and the habit section (habits are not tasks for XP). */
fun tasksIn(path: String, text: String, habitHeading: String): List<Task> {
    if (!text.contains("[")) return emptyList()
    val out = ArrayList<Task>(); var section = ""; var fence = false; var inFm = false
    text.split("\n").forEachIndexed { i, l0 ->
        val l = l0.trimEnd('\r')
        if (i == 0 && l == "---") { inFm = true; return@forEachIndexed }
        if (inFm) { if (l == "---") inFm = false; return@forEachIndexed }
        if (l.trimStart().startsWith("```")) { fence = !fence; return@forEachIndexed }
        if (fence) return@forEachIndexed
        HEADING.find(l)?.let { section = it.groupValues[2].trim(); return@forEachIndexed }
        val m = TASK_LINE.find(l) ?: return@forEachIndexed
        if (section.equals(habitHeading, true)) return@forEachIndexed
        val t = parseTask(m.groupValues[4], m.groupValues[2], path, i, section)
        if (t.status != "cancelled") out.add(t)
    }
    return out
}

fun splitLines(text: String): Pair<MutableList<String>, String> {
    val nl = if (text.contains("\r\n")) "\r\n" else "\n"
    return text.split(Regex("\\r?\\n")).toMutableList() to nl
}

/** [start, end) of a heading's section, ending at the next heading of any level. */
fun section(lines: List<String>, heading: String): IntRange? {
    val re = Regex("^#{1,6}\\s+" + Regex.escape(heading) + "\\s*$", RegexOption.IGNORE_CASE)
    val start = lines.indexOfFirst { re.matches(it.trim()) }
    if (start < 0) return null
    var end = lines.size
    for (i in start + 1 until lines.size) if (Regex("^#{1,6}\\s").containsMatchIn(lines[i])) { end = i; break }
    return start until end
}

private const val HABIT_TAIL = "(?=\\s*$|\\s+[^\\p{L}\\p{N}\\s])"

fun scanHabits(text: String, habitHeading: String, habits: List<String>): Pair<Set<String>, Set<String>> {
    val done = HashSet<String>(); val present = HashSet<String>()
    val (lines, _) = splitLines(text)
    val sec = section(lines, habitHeading) ?: return done to present
    val known = habits.map { it.lowercase() to Regex("^" + Regex.escape(it) + HABIT_TAIL, RegexOption.IGNORE_CASE) }
    val re = Regex("^\\s*[-*+]\\s*\\[(.)\\]\\s+(.+?)\\s*$")
    for (i in sec.first + 1..sec.last) {
        val m = re.find(lines[i]) ?: continue
        var name = m.groupValues[2].lowercase()
        known.firstOrNull { (h, r) -> name == h || r.containsMatchIn(m.groupValues[2]) }?.let { name = it.first }
        present.add(name); if (m.groupValues[1] != " ") done.add(name)
    }
    return done to present
}

fun writeHabitText(text: String, headingLine: String, habit: String, checked: Boolean): String {
    val box = if (checked) "x" else " "
    val (lines, nl) = splitLines(text)
    val heading = headingLine.trimStart('#').trim()
    val sec = section(lines, heading)
    if (sec == null) {
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        if (lines.isNotEmpty()) lines.add("")
        lines.add(headingLine); lines.add("- [$box] $habit")
        return lines.joinToString(nl) + nl
    }
    val re = Regex("^(\\s*[-*+]\\s*\\[)(.)(\\]\\s+" + Regex.escape(habit) + ")" + HABIT_TAIL + "(.*)$", RegexOption.IGNORE_CASE)
    for (i in sec.first + 1..sec.last) {
        val m = re.find(lines[i]) ?: continue
        lines[i] = m.groupValues[1] + box + m.groupValues[3] + m.groupValues[4]
        return lines.joinToString(nl)
    }
    var at = sec.last + 1
    while (at - 1 > sec.first && lines[at - 1].isBlank()) at--
    lines.add(at, "- [$box] $habit")
    return lines.joinToString(nl)
}

/** Insert lines at the end of a heading's section, creating the heading at the end of the note if needed. */
fun appendToSection(text: String, headingLine: String, newLines: List<String>, blankBefore: Boolean = false): String {
    val (lines, nl) = splitLines(text)
    val sec = section(lines, headingLine.trimStart('#').trim())
    if (sec == null) {
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        if (lines.isNotEmpty()) lines.add("")
        lines.add(headingLine); lines.addAll(newLines)
        return lines.joinToString(nl) + nl
    }
    var at = sec.last + 1
    while (at - 1 > sec.first && lines[at - 1].isBlank()) at--
    val ins = if (blankBefore && at - 1 > sec.first) listOf("") + newLines else newLines
    lines.addAll(at, ins)
    return lines.joinToString(nl)
}

/** ## Workout / **Push** · 18:20 · 45 min · 4,820 kg / - Bench press: 60×8, 60×8 */
fun scanWorkouts(text: String, heading: String): List<Workout> {
    val (lines, _) = splitLines(text)
    val sec = section(lines, heading) ?: return emptyList()
    val out = ArrayList<Workout>(); var name: String? = null; var meta = ""; var ex = ArrayList<WEx>()
    fun flush() { if (name != null) out.add(Workout(name!!, meta, ex)); ex = ArrayList() }
    val bold = Regex("^\\*\\*(.+?)\\*\\*(.*)$"); val exRe = Regex("^[-*+]\\s+([^:]+):\\s*(.+)$")
    val setRe = Regex("([\\d.]+)\\s*[×xX*]\\s*(\\d+)"); val repsOnly = Regex("^\\s*(\\d+)\\s*$")
    for (i in sec.first + 1..sec.last) {
        val l = lines[i].trim()
        bold.find(l)?.let { flush(); name = it.groupValues[1].trim(); meta = it.groupValues[2].trim().removePrefix("·").trim(); return@let }
        if (bold.containsMatchIn(l) || l.contains("[[")) continue
        val e = exRe.find(l) ?: continue
        if (name == null) { name = "Workout"; meta = "" }
        val sets = e.groupValues[2].split(",").mapNotNull { s ->
            setRe.find(s)?.let { WSet(it.groupValues[1], it.groupValues[2]) } ?: repsOnly.find(s)?.let { WSet("", it.groupValues[1]) }
        }
        ex.add(WEx(e.groupValues[1].trim(), sets))
    }
    flush()
    return out
}

fun parseRoutineItem(s0: String): RoutineItem {
    val s = s0.trim(); val m = EMOJI_LEAD.find(s)
    return if (m != null && s.length > m.value.length) RoutineItem(m.groupValues[1], s.substring(m.value.length).trim()) else RoutineItem("", s)
}

fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|#^\\[\\]]"), " ").replace(Regex("\\s+"), " ").trim().take(120)
