package com.stoa.app.vault

import java.time.LocalDate
import kotlin.math.roundToInt

/* XP rules ported line-for-line from the Serendipity v3.9.0 dashboard, so both always show the same level. */
object XP {
    const val task = 10
    val priority = mapOf(5 to 15, 4 to 10, 3 to 5)
    const val onTime = 5
    const val habit = 8
    const val perfectDay = 15
    const val perfectWeek = 100
    const val strongWeek = 40
    const val areaWeek = 20
    const val routineStep = 3
    const val routineDone = 10
}
val TIER_XP = mapOf("bronze" to 25, "silver" to 75, "gold" to 200, "legendary" to 500)
val TIER_NAME = mapOf("bronze" to "Bronze", "silver" to "Silver", "gold" to "Gold", "legendary" to "Legendary")
fun levelStart(l: Int) = 100 * (l - 1) + 25 * (l - 1) * (l - 2)
data class Level(val level: Int, val into: Int, val need: Int)
fun levelFor(xp: Int): Level { var l = 1; while (xp >= levelStart(l + 1)) l++; return Level(l, xp - levelStart(l), levelStart(l + 1) - levelStart(l)) }
private val RANKS = listOf(1 to "Wanderer", 3 to "Apprentice", 5 to "Adept", 8 to "Artisan", 12 to "Sage", 17 to "Archivist", 23 to "Luminary", 30 to "Archmage", 40 to "Legend")
fun rankFor(level: Int) = RANKS.last { level >= it.first }.second
fun taskXP(t: Task) = XP.task + (XP.priority[t.prio] ?: 0)

data class Ach(val id: String, val icon: String, val name: String, val desc: String, val tier: String, val stat: String, val goal: Int, val area: String)
val ACHIEVEMENTS = listOf(
    Ach("t1", "⚔️", "First Blood", "Complete your first task", "bronze", "tasksDone", 1, "core"),
    Ach("t10", "🗡️", "Warming Up", "Complete 10 tasks", "bronze", "tasksDone", 10, "core"),
    Ach("t50", "🛡️", "Quest Runner", "Complete 50 tasks", "silver", "tasksDone", 50, "core"),
    Ach("t100", "🏰", "Centurion", "Complete 100 tasks", "silver", "tasksDone", 100, "core"),
    Ach("t500", "👑", "Taskmaster", "Complete 500 tasks", "gold", "tasksDone", 500, "core"),
    Ach("t1000", "🐉", "Mythic Doer", "Complete 1,000 tasks", "legendary", "tasksDone", 1000, "core"),
    Ach("p5", "🔥", "Priority Hunter", "Complete 5 high-priority tasks (⏫ / 🔺)", "silver", "prioDone", 5, "core"),
    Ach("p25", "💀", "Boss Slayer", "Complete 25 high-priority tasks", "gold", "prioDone", 25, "core"),
    Ach("o10", "⏱️", "Punctual", "Finish 10 tasks on or before their due date", "silver", "onTime", 10, "core"),
    Ach("o50", "🕰️", "Clockwork", "Finish 50 tasks on or before their due date", "gold", "onTime", 50, "core"),
    Ach("z0", "🧹", "Clean Slate", "Zero overdue tasks, with 10+ tasks completed", "bronze", "zeroOverdue", 1, "core"),
    Ach("h1", "✨", "First Spark", "Check off your first habit", "bronze", "habitChecks", 1, "core"),
    Ach("h50", "🌱", "Habit Former", "50 habit check-ins", "bronze", "habitChecks", 50, "core"),
    Ach("h250", "🔁", "Ritualist", "250 habit check-ins", "silver", "habitChecks", 250, "core"),
    Ach("h1000", "🧘", "Creature of Habit", "1,000 habit check-ins", "gold", "habitChecks", 1000, "core"),
    Ach("hc5", "🧰", "Collector", "Track 5 habits at once", "bronze", "habitCount", 5, "core"),
    Ach("s3", "🕯️", "Three in a Row", "A 3-day habit streak", "bronze", "bestStreak", 3, "core"),
    Ach("s7", "🔥", "Week Warrior", "A 7-day habit streak", "silver", "bestStreak", 7, "core"),
    Ach("s14", "⚡", "Fortnight Fire", "A 14-day habit streak", "silver", "bestStreak", 14, "core"),
    Ach("s30", "🌙", "Monthly Monk", "A 30-day habit streak", "gold", "bestStreak", 30, "core"),
    Ach("s100", "💎", "Unbreakable", "A 100-day habit streak", "legendary", "bestStreak", 100, "core"),
    Ach("d1", "🌟", "Flawless Day", "Complete every habit in a single day", "bronze", "perfectDays", 1, "core"),
    Ach("d10", "🎯", "Ten Perfect Days", "10 days with every habit done", "silver", "perfectDays", 10, "core"),
    Ach("d50", "🏆", "Fifty Perfect Days", "50 days with every habit done", "gold", "perfectDays", 50, "core"),
    Ach("w1", "📜", "Perfect Week", "A full week with every habit every day", "silver", "perfectWeeks", 1, "core"),
    Ach("w4", "🗓️", "Perfect Month", "4 perfect weeks", "gold", "perfectWeeks", 4, "core"),
    Ach("w12", "🌌", "Quarter of Fire", "12 perfect weeks", "legendary", "perfectWeeks", 12, "core"),
    Ach("g1", "🌤️", "Solid Week", "A completed week with 80%+ of habits done", "bronze", "goodWeeks", 1, "core"),
    Ach("g8", "🌳", "Deep Roots", "8 completed weeks at 80%+", "gold", "goodWeeks", 8, "core"),
    Ach("n100", "✍️", "Scribe", "Keep 100 notes", "bronze", "notes", 100, "core"),
    Ach("n500", "📚", "Archivist", "Keep 500 notes", "silver", "notes", 500, "core"),
    Ach("n2000", "🏛️", "Great Library", "Keep 2,000 notes", "gold", "notes", 2000, "core"),
    Ach("l5", "🚀", "Rising Star", "Reach level 5", "silver", "level", 5, "core"),
    Ach("l10", "🌠", "Veteran", "Reach level 10", "gold", "level", 10, "core"),
    Ach("l20", "🔱", "Ascended", "Reach level 20", "legendary", "level", 20, "core"),
    Ach("rd10", "📖", "Bookworm", "Read on 10 days", "bronze", "h_reading", 10, "creativity"),
    Ach("rd50", "📚", "Chapter by Chapter", "Read on 50 days", "silver", "h_reading", 50, "creativity"),
    Ach("rd100", "🦉", "Voracious Reader", "Read on 100 days", "gold", "h_reading", 100, "creativity"),
    Ach("rd365", "🏛️", "Living Library", "Read on 365 days", "legendary", "h_reading", 365, "creativity"),
    Ach("rds7", "🔖", "Can't Put It Down", "A 7-day reading streak", "silver", "hs_reading", 7, "creativity"),
    Ach("rds30", "📕", "Novel Habit", "A 30-day reading streak", "gold", "hs_reading", 30, "creativity"),
    Ach("wr10", "✒️", "Wordsmith", "Write on 10 days", "bronze", "h_writing", 10, "creativity"),
    Ach("wr50", "🖋️", "Ink-Stained", "Write on 50 days", "silver", "h_writing", 50, "creativity"),
    Ach("wr100", "📜", "Author", "Write on 100 days", "gold", "h_writing", 100, "creativity"),
    Ach("wrs7", "✍️", "On a Roll", "A 7-day writing streak", "silver", "hs_writing", 7, "creativity"),
    Ach("cw1", "🎭", "Muse Visits", "A completed week at 80%+ in Creativity", "bronze", "w80_creativity", 1, "creativity"),
    Ach("cw8", "🌈", "Creative Flow", "8 weeks at 80%+ in Creativity", "gold", "w80_creativity", 8, "creativity"),
    Ach("gy10", "🐀", "Gym Rat", "10 gym sessions", "bronze", "h_gym", 10, "physical"),
    Ach("gy50", "🏋️", "Iron Regular", "50 gym sessions", "silver", "h_gym", 50, "physical"),
    Ach("gy100", "🦾", "Iron Will", "100 gym sessions", "gold", "h_gym", 100, "physical"),
    Ach("gy250", "🗿", "Built Different", "250 gym sessions", "legendary", "h_gym", 250, "physical"),
    Ach("bd10", "🏸", "Shuttle Starter", "Play badminton 10 times", "bronze", "h_badminton", 10, "physical"),
    Ach("bd50", "💥", "Smash Hit", "Play badminton 50 times", "silver", "h_badminton", 50, "physical"),
    Ach("bd100", "👑", "Court King", "Play badminton 100 times", "gold", "h_badminton", 100, "physical"),
    Ach("ph1", "🏃", "Active Week", "A completed week at 80%+ in Physical", "bronze", "w80_physical", 1, "physical"),
    Ach("ph8", "🏅", "Athlete", "8 weeks at 80%+ in Physical", "silver", "w80_physical", 8, "physical"),
    Ach("ph20", "🔥", "Peak Form", "20 weeks at 80%+ in Physical", "gold", "w80_physical", 20, "physical"),
    Ach("md10", "🧘", "Still Waters", "Meditate on 10 days", "bronze", "h_meditate", 10, "mental"),
    Ach("md50", "🪷", "Calm Mind", "Meditate on 50 days", "silver", "h_meditate", 50, "mental"),
    Ach("md100", "☯️", "Zen Master", "Meditate on 100 days", "gold", "h_meditate", 100, "mental"),
    Ach("md365", "🌅", "Enlightened", "Meditate on 365 days", "legendary", "h_meditate", 365, "mental"),
    Ach("mds7", "🌬️", "Seven Breaths", "A 7-day meditation streak", "silver", "hs_meditate", 7, "mental"),
    Ach("mds30", "🏔️", "Monk Mode", "A 30-day meditation streak", "gold", "hs_meditate", 30, "mental"),
    Ach("mds100", "🕊️", "Unshakeable Calm", "A 100-day meditation streak", "legendary", "hs_meditate", 100, "mental"),
    Ach("mn1", "💡", "Clear Head", "A completed week at 80%+ in Mental", "bronze", "w80_mental", 1, "mental"),
    Ach("mn8", "🧠", "Sound Mind", "8 weeks at 80%+ in Mental", "gold", "w80_mental", 8, "mental"),
    Ach("pm5", "📈", "Metric Mover", "Complete 5 #metric tasks", "bronze", "metricDone", 5, "professional"),
    Ach("pm25", "🎯", "Needle Mover", "Complete 25 #metric tasks", "silver", "metricDone", 25, "professional"),
    Ach("pm100", "🚀", "Impact Player", "Complete 100 #metric tasks", "gold", "metricDone", 100, "professional"),
    Ach("pm250", "🏆", "Rainmaker", "Complete 250 #metric tasks", "legendary", "metricDone", 250, "professional"),
    Ach("pw1", "💼", "On Target", "A completed week at 80%+ in Professional", "silver", "w80_professional", 1, "professional"),
    Ach("pw8", "🏢", "Quarter Crusher", "8 weeks at 80%+ in Professional", "gold", "w80_professional", 8, "professional"),
    Ach("lq10", "💻", "Warm-Up", "Practice Leetquiz on 10 days", "bronze", "h_leetquiz", 10, "professional"),
    Ach("lq50", "🧩", "Problem Solver", "Practice Leetquiz on 50 days", "silver", "h_leetquiz", 50, "professional"),
    Ach("lq100", "🏁", "Algorithm Ace", "Practice Leetquiz on 100 days", "gold", "h_leetquiz", 100, "professional"),
    Ach("lqs7", "⌨️", "Daily Grind", "A 7-day Leetquiz streak", "silver", "hs_leetquiz", 7, "professional"),
    Ach("lqs30", "🎓", "Interview Ready", "A 30-day Leetquiz streak", "gold", "hs_leetquiz", 30, "professional"),
    Ach("pz", "🧼", "Zero Backlog", "No overdue #metric tasks, with 10+ completed", "silver", "metricClear", 1, "professional"),
    Ach("bl1", "⚖️", "Balanced Week", "A completed week with all four areas at 60%+", "silver", "balanced60", 1, "balance"),
    Ach("bl4", "🧭", "Balanced Month", "4 weeks with all four areas at 60%+", "gold", "balanced60", 4, "balance"),
    Ach("fc1", "⭕", "Full Circle", "A completed week with all four areas at 80%+", "gold", "full80", 1, "balance"),
    Ach("fc4", "💫", "Perfect Orbit", "4 weeks with all four areas at 80%+", "legendary", "full80", 4, "balance"),
    Ach("rn25", "🎓", "Renaissance Soul", "25 check-ins in every one of the four areas", "silver", "minArea", 25, "balance"),
    Ach("rn100", "🌍", "Polymath", "100 check-ins in every one of the four areas", "gold", "minArea", 100, "balance"),
    Ach("rs1", "🪥", "First Ritual", "Tick your first routine step", "bronze", "routineSteps", 1, "routines"),
    Ach("rs100", "✅", "Step by Step", "Tick 100 routine steps", "bronze", "routineSteps", 100, "routines"),
    Ach("rs500", "🪜", "Ritual Master", "Tick 500 routine steps", "silver", "routineSteps", 500, "routines"),
    Ach("rm7", "🌅", "Early Riser", "Finish your morning routine on 7 days", "bronze", "fullMornings", 7, "routines"),
    Ach("rm30", "☀️", "Sunrise Ritual", "Finish your morning routine on 30 days", "silver", "fullMornings", 30, "routines"),
    Ach("rm100", "🌄", "Dawn Keeper", "Finish your morning routine on 100 days", "gold", "fullMornings", 100, "routines"),
    Ach("re7", "🌙", "Wind-Down", "Finish your evening routine on 7 days", "bronze", "fullEvenings", 7, "routines"),
    Ach("re30", "🌌", "Night Owl's Discipline", "Finish your evening routine on 30 days", "silver", "fullEvenings", 30, "routines"),
    Ach("re100", "🛌", "Sleep Sage", "Finish your evening routine on 100 days", "gold", "fullEvenings", 100, "routines"),
    Ach("rb7", "🌗", "Bookends", "Both routines finished on 7 days", "silver", "fullBoth", 7, "routines"),
    Ach("rb30", "♾️", "Steady Days", "Both routines finished on 30 days", "gold", "fullBoth", 30, "routines"),
    Ach("rb100", "🏯", "Temple of Habit", "Both routines finished on 100 days", "legendary", "fullBoth", 100, "routines"),)

fun dayIdx(key: String): Int = try { LocalDate.parse(key).toEpochDay().toInt() } catch (e: Exception) { 0 }
fun keyOfIdx(i: Int): String = LocalDate.ofEpochDay(i.toLong()).toString()
fun weekStartIdx(i: Int, mon: Boolean): Int { val wd = Math.floorMod(i + 4, 7); return i - (if (mon) (wd + 6) % 7 else wd) }

class RpgOpts(
    val todayKey: String, val mon: Boolean, val areas: List<Area>, val habits: List<String>, val habitAdded: Map<String, String>,
    val habitCount: Int, val noteCount: Int, val overdue: Int, val since: String = ""
)

fun isPerfectDay(d: Day?, key: String, o: RpgOpts): Boolean {
    if (d == null) return false
    val req = HashSet(d.present)
    o.habits.forEach { h -> val l = h.lowercase(); val added = o.habitAdded[l]; if (added == null || added <= key) req.add(l) }
    if (req.isEmpty()) return false
    return req.all { d.done.contains(it) }
}

data class AreaScore(val id: String, val label: String, val icon: String, val score: Int, val empty: Boolean)

fun areaScores(tasks: List<Task>, days: Map<String, Day>, noteDays: Map<String, Int>, o: RpgOpts, ws: Int): List<AreaScore> {
    val keys = (0 until 7).map { keyOfIdx(ws + it) }
    val wsKey = keys[0]; val weKey = keys[6]; val lookback = keyOfIdx(ws - 14)
    val tracked = o.habits.map { it.lowercase() }.toSet()
    return o.areas.map { a ->
        var got = 0.0; var tgt = 0.0
        for ((name, target) in a.habits) {
            val nm = name.lowercase()
            if (!tracked.contains(nm) || target <= 0) continue
            got += keys.count { days[it]?.done?.contains(nm) == true }; tgt += target
        }
        if (a.notesTarget > 0 && !(a.notesUnless != null && tracked.contains(a.notesUnless.lowercase()))) {
            val raw = keys.sumOf { noteDays[it] ?: 0 }
            if (raw > 0) { got += raw; tgt += a.notesTarget }
        }
        if (a.tag != null) {
            val tag = a.tag.lowercase(); var done = 0; var pending = 0
            for (t in tasks) {
                if (!t.tags.contains(tag)) continue
                val c = if (t.status == "done") t.completion else null
                if (c != null && c >= wsKey && c <= weKey) done++
                else if (t.due != null && t.due <= weKey && t.due >= lookback && (t.status == "open" || (c != null && c > weKey))) pending++
            }
            val limit = maxOf(0, a.minTasks)
            val target = if (limit > 0) maxOf(limit, pending + minOf(done, limit)) else done + pending
            if (target > 0) { got += done; tgt += target }
        }
        AreaScore(a.id, a.label, a.icon, if (tgt > 0) (got / tgt * 100).roundToInt() else 0, tgt == 0.0)
    }
}

fun computeStats(tasks: List<Task>, days: Map<String, Day>, noteDays: Map<String, Int>, o: RpgOpts): MutableMap<String, Int> {
    val S = HashMap<String, Int>()
    fun inc(k: String, by: Int = 1) { S[k] = (S[k] ?: 0) + by }
    listOf("tasksDone", "prioDone", "onTime", "tasksXP", "habitChecks", "perfectDays", "habitsXP", "bestStreak", "curStreak", "routineSteps", "routineFull",
        "fullMornings", "fullEvenings", "fullBoth", "routinesXP", "perfectWeeks", "strongWeeks", "goodWeeks", "weeklyXP", "areaXP", "metricDone", "metricClear",
        "newNotes", "balanced60", "full80", "minArea", "zeroOverdue").forEach { S[it] = 0 }
    S["notes"] = o.noteCount; S["habitCount"] = o.habitCount
    val since = o.since
    val mtag = o.areas.firstOrNull { it.tag != null }?.tag?.lowercase()
    var metricOverdue = 0
    for (t in tasks) {
        if (mtag != null && t.status == "open" && t.due != null && t.due < o.todayKey && t.tags.contains(mtag)) metricOverdue++
        if (t.status != "done") continue
        if (since.isNotEmpty() && !(t.completion != null && t.completion >= since)) continue
        inc("tasksDone"); inc("tasksXP", taskXP(t))
        if (t.prio >= 4) inc("prioDone")
        if (t.due != null && t.completion != null && t.completion <= t.due) { inc("onTime"); inc("tasksXP", XP.onTime) }
        if (mtag != null && t.tags.contains(mtag)) inc("metricDone")
    }
    S["zeroOverdue"] = if (o.overdue == 0 && S["tasksDone"]!! >= 10) 1 else 0
    S["metricClear"] = if (S["metricDone"]!! >= 10 && metricOverdue == 0) 1 else 0

    val hobbies = HashSet<String>()
    o.areas.forEach { a -> a.habits.keys.forEach { hobbies.add(it.lowercase()) } }
    val hre = Regex("^hs?_(.+)$")
    ACHIEVEMENTS.forEach { x -> hre.find(x.stat)?.let { hobbies.add(it.groupValues[1]) } }
    hobbies.forEach { S["h_$it"] = 0; S["hs_$it"] = 0 }

    val keys = days.keys.filter { it <= o.todayKey && (since.isEmpty() || it >= since) }.sorted()
    val active = ArrayList<Int>()
    for (k in keys) {
        val d = days[k]!!
        val n = d.done.size
        if (n > 0) { inc("habitChecks", n); active.add(dayIdx(k)) }
        if (isPerfectDay(d, k, o)) inc("perfectDays")
        inc("routineSteps", d.morning.size + d.evening.size)
        if (d.complete.contains("morning")) { inc("fullMornings"); inc("routineFull") }
        if (d.complete.contains("evening")) { inc("fullEvenings"); inc("routineFull") }
        if (d.complete.contains("morning") && d.complete.contains("evening")) inc("fullBoth")
        hobbies.forEach { h -> if (d.done.contains(h)) inc("h_$h") }
    }
    var run = 0; var prev: Int? = null
    for (i in active) { run = if (prev != null && i == prev + 1) run + 1 else 1; if (run > S["bestStreak"]!!) S["bestStreak"] = run; prev = i }
    val tIdx = dayIdx(o.todayKey)
    S["curStreak"] = if (prev != null && (prev == tIdx || prev == tIdx - 1)) run else 0
    hobbies.forEach { h ->
        var r = 0; var p: Int? = null
        for (k in keys) { if (days[k]?.done?.contains(h) != true) continue; val i = dayIdx(k); r = if (p != null && i == p + 1) r + 1 else 1; if (r > (S["hs_$h"] ?: 0)) S["hs_$h"] = r; p = i }
    }
    for (a in o.areas) { var c = 0; a.habits.keys.forEach { c += S["h_" + it.lowercase()] ?: 0 }; if (a.tag != null) c += S["metricDone"]!!; S["checks_" + a.id] = c }
    S["minArea"] = if (o.areas.isNotEmpty()) o.areas.minOf { S["checks_" + it.id] ?: 0 } else 0
    noteDays.forEach { (k, v) -> if (k <= o.todayKey && (since.isEmpty() || k >= since)) inc("newNotes", v) }

    val starts = keys.map { weekStartIdx(dayIdx(it), o.mon) }.toSet()
    for (ws in starts) {
        if (ws + 6 >= tIdx) continue
        if (since.isNotEmpty() && ws < dayIdx(since)) continue
        var hit = 0; var tot = 0; var perfect = 0; var notes = 0
        for (i in 0 until 7) {
            val key = keyOfIdx(ws + i); val d = days[key]
            if (d == null) { tot += o.habitCount; continue }
            notes++
            val p = maxOf(d.present.size, o.habitCount)
            hit += minOf(d.done.size, p); tot += p
            if (isPerfectDay(d, key, o)) perfect++
        }
        if (perfect == 7) inc("perfectWeeks")
        else if (tot > 0 && notes >= 5 && hit.toDouble() / tot >= 0.8) inc("strongWeeks")
    }
    if (o.areas.isNotEmpty()) {
        val dates = (keys + noteDays.keys + tasks.mapNotNull { it.completion }).filter { it <= o.todayKey && (since.isEmpty() || it >= since) }
        if (dates.isNotEmpty()) {
            val lastDone = weekStartIdx(tIdx, o.mon) - 7
            var ws = weekStartIdx(dayIdx(dates.min()), o.mon)
            while (ws <= lastDone) {
                if (!(since.isNotEmpty() && ws < dayIdx(since))) {
                    val r = areaScores(tasks, days, noteDays, o, ws)
                    var all60 = true; var all80 = true
                    for (a in r) {
                        if (a.score >= 80) { inc("w80_" + a.id); inc("areaXP", XP.areaWeek) }
                        if (a.score < 60) all60 = false
                        if (a.score < 80) all80 = false
                    }
                    if (all60) inc("balanced60")
                    if (all80) inc("full80")
                }
                ws += 7
            }
        }
    }
    S["goodWeeks"] = S["perfectWeeks"]!! + S["strongWeeks"]!!
    S["habitsXP"] = S["habitChecks"]!! * XP.habit + S["perfectDays"]!! * XP.perfectDay
    S["weeklyXP"] = S["perfectWeeks"]!! * XP.perfectWeek + S["strongWeeks"]!! * XP.strongWeek + S["areaXP"]!!
    S["routinesXP"] = S["routineSteps"]!! * XP.routineStep + S["routineFull"]!! * XP.routineDone
    S["baseXP"] = S["tasksXP"]!! + S["habitsXP"]!! + S["weeklyXP"]!! + S["routinesXP"]!!
    return S
}

data class Rpg(val stats: Map<String, Int>, val unlocked: Set<String>, val bonus: Int, val xp: Int, val level: Int, val into: Int, val need: Int, val rank: String) {
    val baseXP get() = stats["baseXP"] ?: 0
}

fun evaluateRpg(S: MutableMap<String, Int>, list: List<Ach> = ACHIEVEMENTS): Rpg {
    val unlocked = LinkedHashSet<String>(); var bonus = 0
    for (pass in 0 until 6) {
        S["level"] = levelFor((S["baseXP"] ?: 0) + bonus).level
        var changed = false
        for (a in list) if (!unlocked.contains(a.id) && (S[a.stat] ?: 0) >= a.goal) { unlocked.add(a.id); bonus += TIER_XP[a.tier] ?: 0; changed = true }
        if (!changed) break
    }
    val xp = (S["baseXP"] ?: 0) + bonus; val lv = levelFor(xp)
    return Rpg(S, unlocked, bonus, xp, lv.level, lv.into, lv.need, rankFor(lv.level))
}
