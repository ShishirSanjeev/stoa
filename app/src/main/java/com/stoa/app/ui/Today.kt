package com.stoa.app.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoa.app.Apps
import com.stoa.app.vault.RoutineItem
import com.stoa.app.vault.XP
import com.stoa.app.vault.parseRoutineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayPage() {
    val ui = LocalUi.current
    val subs = ui.layout.on(ui.layout.today).ifEmpty { listOf("today") }
    SubPaged("Today", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE  d MMMM")), "today", subs.map { it to (if (it == "today") "Today" else SECTIONS[it] ?: it) }, ui.todaySub, { ui.todaySub = it }) { s ->
        when (s) {
            "reading" -> ReadingView()
            "still" -> StillView()
            "writing" -> Writing()
            else -> TodayView()
        }
    }
}

@Composable
fun TodayView() {
    val ui = LocalUi.current
    for (s in ui.layout.on(ui.layout.todaySections)) when (s) {
        "three" -> ThreeCard()
        "routines" -> RoutinesCard()
        "habits" -> HabitsCard()
        "projects" -> ProjectsCard()
    }
}

/* ── routines: daily-note properties morning_routine / evening_routine, like the dashboard ── */
@Composable
fun RoutinesCard() {
    val E = LocalEngine.current; val ink = LocalInk.current
    var edit by remember { mutableStateOf(false) }
    val R = E.routines(); val d = E.day()
    StoaCard("Routines", action = { IconBtn(Icons.Outlined.Edit, "Edit routines", { edit = true }, size = 18.dp) }) {
        listOf("morning" to "Morning", "evening" to "Evening").forEach { (k, label) ->
            val list = R[k] ?: emptyList(); val set = if (k == "morning") d.morning else d.evening
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((if (k == "morning") "☀ " else "☾ ") + label + (if (d.complete.contains(k)) "  ✓" else ""), fontWeight = FontWeight.SemiBold, color = ink.ink, modifier = Modifier.weight(1f))
                Muted("${list.count { set.contains(it.text) }}/${list.size}", size = 12)
            }
            if (list.isEmpty()) Empty("No steps yet.")
            list.forEachIndexed { i, it -> CheckRow(set.contains(it.text), it.text, { _ -> E.toggleRoutine(k, it.text) }, lead = it.emoji, trailing = { XpHint(XP.routineStep) }, divider = i < list.size - 1) }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (edit) RoutineSheet { edit = false }
}

@Composable
fun RoutineSheet(dismiss: () -> Unit) {
    val E = LocalEngine.current
    var R by remember { mutableStateOf(E.routines()) }
    fun save(n: Map<String, List<RoutineItem>>) { R = n; E.saveRoutines(n) }
    Sheet("Routines", dismiss) {
        listOf("morning" to "Morning", "evening" to "Evening").forEach { (k, label) ->
            Kicker(label, Modifier.padding(top = 14.dp, bottom = 4.dp))
            (R[k] ?: emptyList()).forEachIndexed { i, it ->
                EditRow(it.label, onUp = if (i > 0) ({ val l = R[k]!!.toMutableList(); val x = l.removeAt(i); l.add(i - 1, x); save(R + (k to l)) }) else null,
                    onDelete = { save(R + (k to R[k]!!.filterIndexed { j, _ -> j != i })) })
            }
            var add by remember { mutableStateOf("") }
            Field(add, { add = it }, "Add a step (emoji first is fine: 🪥 Brush teeth)", onDone = { if (add.isNotBlank()) { save(R + (k to (R[k] ?: emptyList()) + parseRoutineItem(add))); add = "" } })
        }
        Spacer(Modifier.height(10.dp)); Muted("Shared with your Serendipity dashboard through Dashboard State.md.", size = 12)
    }
}

@Composable
fun EditRow(text: String, onUp: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = LocalInk.current.ink, fontSize = 15.sp)
        trailing?.invoke()
        if (onUp != null) IconBtn(Icons.Outlined.ArrowUpward, "Move up", onUp, size = 18.dp)
        if (onDelete != null) IconBtn(Icons.Outlined.Delete, "Remove", onDelete, size = 18.dp)
    }
    Divider()
}

/* ── habits: "## Habits / - [x] Gym" in the daily note ── */
@Composable
fun HabitsCard() {
    val E = LocalEngine.current; val ink = LocalInk.current
    var edit by remember { mutableStateOf(false) }
    val d = E.day(); val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()).toString() }
    val layout = E.habitLayout()
    StoaCard("Habits", action = { IconBtn(Icons.Outlined.Edit, "Edit habits", { edit = true }, size = 18.dp) }) {
        if (layout.none { !it.trim().startsWith("---") }) Empty("No habits yet — tap ✎ to add one.")
        layout.forEachIndexed { i, h ->
            if (h.trim().startsWith("---")) { val lab = h.trim().removePrefix("---").trim(); if (lab.isNotEmpty()) Kicker(lab, Modifier.padding(top = 12.dp)); return@forEachIndexed }
            val l = h.lowercase()
            CheckRow(d.done.contains(l), h, { E.setHabit(h, it) }, lead = E.habitIcon(h), trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    days.forEach { k -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (E.days[k]?.done?.contains(l) == true) ink.ink else ink.line2)) }
                    val st = E.streak(h); Text(if (st > 0) "${st}d" else "", Modifier.width(28.dp).padding(start = 4.dp), fontSize = 11.5.sp, color = ink.mute)
                }
            }, divider = layout.getOrNull(i + 1)?.trim()?.startsWith("---") == false)
        }
    }
    if (edit) Sheet("Habits", { edit = false }) {
        E.habitLayout().forEach { h -> EditRow(if (h.trim().startsWith("---")) "— " + h.trim().removePrefix("---").trim().uppercase() else h, onDelete = { E.removeHabit(h) }) }
        var add by remember { mutableStateOf("") }
        Spacer(Modifier.height(8.dp))
        Field(add, { add = it }, "New habit (or --- Group for a heading)", onDone = {
            val s = add.trim(); if (s.startsWith("---")) E.addHabitDivider(s.removePrefix("---").trim()) else E.addHabit(s); add = ""
        })
        Spacer(Modifier.height(8.dp)); Muted("The same list your dashboard uses. Old ticks stay in your daily notes when you remove a habit.", size = 12)
    }
}

/* ── projects: notes tagged #project, ranked by priority_rank like the dashboard's board ── */
@Composable
fun ProjectsCard() {
    val E = LocalEngine.current; val ctx = LocalContext.current; val ui = LocalUi.current; val ink = LocalInk.current; val scope = rememberCoroutineScope()
    var bump by remember { mutableIntStateOf(0) }
    val list by produceState(emptyList<Pair<String, Double?>>(), E.mdPaths.size, ui.resume, bump) { value = withContext(Dispatchers.IO) { E.projects() } }
    StoaCard("Projects", action = { if (list.isNotEmpty()) Muted("by priority", size = 12) }) {
        if (list.isEmpty()) { Empty("Tag a note #project and it shows up here."); return@StoaCard }
        list.take(8).forEachIndexed { i, (rel, _) ->
            val open = E.tasks[rel]?.count { it.status == "open" } ?: 0
            Row(Modifier.fillMaxWidth().clickable { Apps.openInObsidian(ctx, E.vaultName, rel) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(24.dp), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 19.sp, color = ink.faint)
                Text(rel.substringAfterLast('/').removeSuffix(".md"), Modifier.weight(1f), color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (open > 0) Muted("$open open", size = 12)
                if (i > 0) IconBtn(Icons.Outlined.ArrowUpward, "Move up", {
                    scope.launch {
                        val o = list.toMutableList(); val x = o.removeAt(i); o.add(i - 1, x)
                        val prev = o.getOrNull(i - 2)?.second; val next = o[i].second
                        if (next != null && o.all { it.second != null }) E.setRank(rel, if (prev == null) next - 1000 else (prev + next) / 2)
                        else o.forEachIndexed { k, p -> E.setRank(p.first, ((k + 1) * 1000).toDouble()) }
                        bump++
                    }
                }, size = 17.dp)
            }
        }
    }
}

/* ── timers: kept in prefs, watched from the root so they finish even if you switch tabs ── */
@Composable
fun TimerWatcher() {
    val E = LocalEngine.current; val p = LocalUi.current.prefs; val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val end = p.long("still_end")
            if (end > 0) {
                val start = p.long("still_start"); val bellEvery = p.int("still_bell")
                if (bellEvery > 0) { val n = ((now - start) / (bellEvery * 60_000L)).toInt(); if (n > p.int("still_rung") && end - now > 3000) { p.put("still_rung", n); Bell.ring(640.0, 2.5) } }
                if (now >= end) { val m = p.int("still_min"); p.put("still_end", 0L); Bell.ring(432.0, 5.0); scope.launch { E.logStillness(m) } }
            }
            val rest = p.long("rest_end")
            if (rest in 1..now) { p.put("rest_end", 0L); Bell.ring(660.0, 2.0); E.toast("Rest over", "Next set", "ach") }
            delay(500)
        }
    }
}

@Composable
fun KeepScreenOn() {
    val act = LocalContext.current as? ComponentActivity
    DisposableEffect(Unit) { act?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); onDispose { act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
}

/* ── reading ── */
@Composable
fun ReadingView() {
    val E = LocalEngine.current; val ui = LocalUi.current; val p = ui.prefs; val ctx = LocalContext.current; val ink = LocalInk.current; val scope = rememberCoroutineScope()
    val books = (E.stoa["books"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("path")?.toString() }?.filter { E.exists(it) } ?: emptyList()
    val cur = (E.stoa["currentBook"]?.toString()?.takeIf { books.contains(it) }) ?: books.firstOrNull()
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ver by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis(); delay(500) } }
    var picking by remember { mutableStateOf(false) }
    val running = p.bool("read_running", false); val acc = p.long("read_acc"); val at = p.long("read_at"); val active = p.str("read_path", "").isNotEmpty()
    val elapsed = acc + if (running) tick - at else 0L
    val refresh = ver + 0; if (refresh < 0) return
    StoaCard("Now reading") {
        if (cur == null) Empty("Add the book you're reading — any note in your vault works as the book's note.")
        else {
            Row(Modifier.fillMaxWidth().clickable { Apps.openInObsidian(ctx, E.vaultName, cur) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(30.dp).height(44.dp).clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 6.dp, bottomEnd = 6.dp)).background(ink.ink))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(cur.substringAfterLast('/').removeSuffix(".md"), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 22.sp, color = ink.ink, lineHeight = 26.sp); Muted("Open the book's note in Obsidian", size = 12) }
                IconBtn(Icons.AutoMirrored.Outlined.OpenInNew, "Open", { Apps.openInObsidian(ctx, E.vaultName, cur) }, size = 18.dp)
            }
            val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(1f, if (running) .65f else 1f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "p")
            Text(fmtMs(elapsed), Modifier.fillMaxWidth().padding(vertical = 18.dp).graphicsLayer { alpha = pulse.value }, fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 64.sp, color = ink.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!active) Pill("Start reading", { p.put("read_path", cur); p.put("read_acc", 0L); p.put("read_at", System.currentTimeMillis()); p.put("read_running", true); ver++ }, Modifier.weight(1f), primary = true, icon = Icons.Outlined.PlayArrow)
                else {
                    Pill(if (running) "Pause" else "Resume", {
                        if (running) { p.put("read_acc", acc + System.currentTimeMillis() - at); p.put("read_running", false) } else { p.put("read_at", System.currentTimeMillis()); p.put("read_running", true) }; ver++
                    }, Modifier.weight(1f), icon = if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow)
                    Pill("Finish", {
                        val ms = acc + if (running) System.currentTimeMillis() - at else 0L; val path = p.str("read_path", cur)
                        p.put("read_path", ""); p.put("read_running", false); p.put("read_acc", 0L); ver++
                        val min = (ms / 60000).toInt()
                        if (min < 1) E.toast("Under a minute", "Nothing logged") else scope.launch { E.logReading(path, min) }
                    }, Modifier.weight(1f), primary = true, icon = Icons.Outlined.Check)
                }
            }
        }
    }
    val week = (0..6).sumOf { E.days[LocalDate.now().minusDays(it.toLong()).toString()]?.readMin ?: 0 }
    StoaCard { StatRow("${E.day().readMin}" to "min today", "$week" to "min this week", "${books.size}" to "books") }
    StoaCard("Your books", action = { Pill("Add", { picking = true }, small = true, icon = Icons.Outlined.Add) }) {
        if (books.isEmpty()) Empty("No books yet.")
        books.forEach { b ->
            Row(Modifier.fillMaxWidth().clickable { E.setStoa("currentBook", b) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((if (b == cur) "● " else "○ ") + b.substringAfterLast('/').removeSuffix(".md"), Modifier.weight(1f), color = ink.ink, fontWeight = if (b == cur) FontWeight.SemiBold else null)
                IconBtn(Icons.Outlined.Close, "Remove", { E.setStoa("books", books.filter { it != b }.map { mapOf("path" to it) }) }, size = 16.dp)
            }
        }
    }
    Muted("Finishing logs the session under “Reading log” in the book's note, adds reading_minutes to today's daily note and ticks your Reading habit.", Modifier.padding(4.dp), 12)
    if (picking) NotePicker("Which note is the book?", { picking = false }) { rel ->
        picking = false; E.setStoa("books", listOf(mapOf("path" to rel)) + books.filter { it != rel }.map { mapOf("path" to it) }); E.setStoa("currentBook", rel)
    }
}

@Composable
fun NotePicker(title: String, dismiss: () -> Unit, pick: (String) -> Unit) {
    val E = LocalEngine.current; val ink = LocalInk.current
    var q by remember { mutableStateOf("") }
    Sheet(title, dismiss) {
        Field(q, { q = it }, "Search notes")
        val res = if (q.isBlank()) E.mdPaths.sortedByDescending { E.file(it).lastModified() }.take(20) else E.searchNotes(q)
        res.forEach { rel -> Column(Modifier.fillMaxWidth().clickable { pick(rel) }.padding(vertical = 10.dp)) {
            Text(rel.substringAfterLast('/').removeSuffix(".md"), color = ink.ink); Muted(rel.substringBeforeLast('/', ""), size = 11)
        } }
    }
}

/* ── stillness ── */
@Composable
fun StillView() {
    val E = LocalEngine.current; val p = LocalUi.current.prefs; val ink = LocalInk.current; val scope = rememberCoroutineScope()
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis(); delay(250) } }
    val end = p.long("still_end")
    if (end > tick) {
        KeepScreenOn()
        val breath = rememberInfiniteTransition(label = "breath").animateFloat(.55f, 1f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
        Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(190.dp).graphicsLayer { scaleX = breath.value; scaleY = breath.value }.clip(CircleShape).background(ink.line).border(1.dp, ink.ink, CircleShape))
            Text(fmtMs(end - tick), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 64.sp, color = ink.ink)
            Muted(if (p.int("still_bell") > 0) "Bell every ${p.int("still_bell")} min" else "Sit. Breathe. Notice.", size = 14)
            Pill("End early", {
                val m = ((System.currentTimeMillis() - p.long("still_start")) / 60000).toInt(); p.put("still_end", 0L); tick = 0L
                if (m >= 1) scope.launch { E.logStillness(m) }
            })
        }
        return
    }
    var mins by remember { mutableIntStateOf(10) }; var bell by remember { mutableIntStateOf(0) }
    StoaCard("Duration") {
        Chips(listOf(5, 10, 15, 20, 30), mins, { "$it min" }) { mins = it }
        Kicker("Interval bell", Modifier.padding(top = 14.dp, bottom = 4.dp))
        Chips(listOf(0, 1, 2, 5), bell, { if (it == 0) "Off" else "Every $it min" }) { bell = it }
        Spacer(Modifier.height(14.dp))
        Pill("Begin", {
            val now = System.currentTimeMillis(); Bell.ring()
            p.put("still_start", now); p.put("still_end", now + mins * 60_000L); p.put("still_min", mins); p.put("still_bell", bell); p.put("still_rung", 0); tick = now
        }, Modifier.fillMaxWidth(), primary = true)
    }
    val week = (0..6).sumOf { E.days[LocalDate.now().minusDays(it.toLong()).toString()]?.medMin ?: 0 }
    val med = E.habitNamed(p.str("meditateHabit", "Meditate"))
    StoaCard { StatRow("${E.day().medMin}" to "min today", "$week" to "min this week", (med?.let { "${E.streak(it)}d" } ?: "–") to "streak") }
}
