package com.stoa.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoa.app.Apps
import com.stoa.app.vault.Task
import com.stoa.app.vault.taskXP
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val PRIO_EMOJI = mapOf(5 to "🔺", 4 to "⏫", 3 to "🔼", 2 to "", 1 to "🔽")

fun whenText(t: Task): String {
    val d = t.due ?: return ""
    val n = try { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d)) } catch (e: Exception) { return d }
    return when { n < -1 -> "${-n}d overdue"; n == -1L -> "yesterday"; n == 0L -> "today"; n == 1L -> "tomorrow"; n < 7 -> "in ${n}d"; else -> d }
}

/** All open tasks across the vault, filtered and sorted like the dashboard's task widget. */
@Composable
fun TasksPipeline() {
    val E = LocalEngine.current; val ui = LocalUi.current; val p = ui.prefs; val ctx = LocalContext.current; val ink = LocalInk.current; val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(p.str("tk_status", "open")) }
    var due by remember { mutableStateOf(p.str("tk_due", "any")) }
    var prio by remember { mutableStateOf(p.str("tk_prio", "any")) }
    var sort by remember { mutableStateOf(p.str("tk_sort", "due")) }
    var tag by remember { mutableStateOf(p.str("tk_tag", "")) }
    var note by remember { mutableStateOf(p.str("tk_note", "")) }
    var q by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(40) }
    fun save(k: String, v: String) { p.put(k, v); limit = 40 }

    // add
    var add by remember { mutableStateOf("") }; var addPrio by remember { mutableStateOf(2) }; var addDue by remember { mutableStateOf("") }
    StoaCard("New task", action = { Muted("→ " + E.inbox.substringAfterLast('/').removeSuffix(".md"), size = 12) }) {
        Field(add, { add = it }, "What needs doing?", onDone = {
            val t = add.trim(); if (t.isEmpty()) return@Field
            val dd = when (addDue) { "today" -> LocalDate.now().toString(); "tomorrow" -> LocalDate.now().plusDays(1).toString(); "week" -> LocalDate.now().plusDays(7).toString(); else -> "" }
            val line = listOf(t, PRIO_EMOJI[addPrio] ?: "").filter { it.isNotEmpty() }.joinToString(" ") + (if (dd.isNotEmpty()) " 📅 $dd" else "")
            add = ""; scope.launch {
                if (!E.exists(E.inbox)) E.create(E.inbox, "# Inbox\n\n")
                E.addTask(E.inbox, null, line); E.toast("Task added", E.inbox.substringAfterLast('/').removeSuffix(".md"), "ach")
            }
        })
        Chips(listOf(5, 4, 3, 2), addPrio, { if (it == 2) "Normal" else (PRIO_EMOJI[it] ?: "") + when (it) { 5 -> " Highest"; 4 -> " High"; else -> " Medium" } }) { addPrio = it }
        Chips(listOf("", "today", "tomorrow", "week"), addDue, { when (it) { "" -> "No date"; "week" -> "In a week"; else -> it.replaceFirstChar { c -> c.uppercase() } } }) { addDue = it }
    }

    val all = E.allTasks()
    val today = LocalDate.now().toString(); val soon = LocalDate.now().plusDays(7).toString()
    val tags = remember(all.size) { all.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(24).map { it.key } }
    val notes = remember(all.size) { all.filter { it.status == "open" }.groupingBy { it.path }.eachCount().entries.sortedByDescending { it.value }.take(24).map { it.key } }
    val shown = all.asSequence()
        .filter { when (status) { "open" -> it.status == "open"; "done" -> it.status == "done"; else -> true } }
        .filter { t ->
            val d = t.due
            when (due) {
                "attention" -> d != null && d <= soon || t.prio >= 4
                "overdue" -> d != null && d < today
                "today" -> d == today
                "soon" -> d != null && d > today && d <= soon
                "later" -> d != null && d > soon
                "none" -> d == null
                else -> true
            }
        }
        .filter { t -> when (prio) { "any" -> true; "1" -> t.prio <= 1; else -> t.prio == prio.toIntOrNull() } }
        .filter { tag.isEmpty() || it.tags.contains(tag) }
        .filter { note.isEmpty() || it.path == note }
        .filter { q.isBlank() || it.text.contains(q, true) || it.file.contains(q, true) }
        .toList()
        .let { l ->
            when (sort) {
                "due" -> l.sortedWith(compareBy<Task> { it.due == null }.thenBy { it.due ?: "" }.thenByDescending { it.prio })
                "due-desc" -> l.sortedWith(compareBy<Task> { it.due == null }.thenByDescending { it.due ?: "" })
                "prio" -> l.sortedWith(compareByDescending<Task> { it.prio }.thenBy { it.due ?: "9999" })
                "bounty" -> l.sortedByDescending { taskXP(it) }
                "title" -> l.sortedBy { it.text.lowercase() }
                "note" -> l.sortedWith(compareBy<Task> { it.file.lowercase() }.thenBy { it.line })
                "done" -> l.sortedByDescending { it.completion ?: "" }
                else -> l
            }
        }

    val overdue = all.count { it.status == "open" && it.due != null && it.due < today }
    val dueToday = all.count { it.status == "open" && it.due == today }
    val open = all.count { it.status == "open" }
    StoaCard { StatRow("$open" to "open", "$dueToday" to "due today", "$overdue" to "overdue") }

    StoaCard("Tasks", action = { Muted("${shown.size} shown", size = 12) }) {
        Seg(listOf("open" to "Open", "done" to "Done", "all" to "All"), status, { status = it; save("tk_status", it) })
        Field(q, { q = it }, "Search tasks…")
        Kicker("Due", Modifier.padding(top = 10.dp))
        Chips(listOf("any", "attention", "overdue", "today", "soon", "later", "none"), due, { when (it) { "any" -> "Any"; "attention" -> "Needs attention"; "soon" -> "Next 7 days"; "none" -> "No date"; else -> it.replaceFirstChar { c -> c.uppercase() } } }) { due = it; save("tk_due", it) }
        Kicker("Priority", Modifier.padding(top = 6.dp))
        Chips(listOf("any", "5", "4", "3", "2", "1"), prio, { when (it) { "any" -> "Any"; "5" -> "🔺 Highest"; "4" -> "⏫ High"; "3" -> "🔼 Medium"; "2" -> "Normal"; else -> "🔽 Low" } }) { prio = it; save("tk_prio", it) }
        Kicker("Sort", Modifier.padding(top = 6.dp))
        Chips(listOf("due", "due-desc", "prio", "bounty", "title", "note", "done"), sort, { when (it) { "due" -> "Due ↑"; "due-desc" -> "Due ↓"; "prio" -> "Priority"; "bounty" -> "XP bounty"; "title" -> "A–Z"; "note" -> "Note"; else -> "Completed" } }) { sort = it; save("tk_sort", it) }
        Row(Modifier.fillMaxWidth().clickable { more = !more }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Kicker(if (more) "− Fewer filters" else "+ More filters" + (if (tag.isNotEmpty() || note.isNotEmpty()) " · on" else ""), Modifier.weight(1f))
            if (tag.isNotEmpty() || note.isNotEmpty() || due != "any" || prio != "any") Text("RESET", Modifier.clickable {
                due = "any"; prio = "any"; tag = ""; note = ""; listOf("tk_due" to "any", "tk_prio" to "any", "tk_tag" to "", "tk_note" to "").forEach { (k, v) -> save(k, v) }
            }.padding(4.dp), fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.2.sp, color = ink.mute)
        }
        AnimatedVisibility(more) {
            Column {
                Kicker("Tag")
                Chips(listOf("") + tags, tag, { if (it.isEmpty()) "Any" else it }) { tag = it; save("tk_tag", it) }
                Kicker("Note", Modifier.padding(top = 6.dp))
                Chips(listOf("") + notes, note, { if (it.isEmpty()) "Any" else it.substringAfterLast('/').removeSuffix(".md") }) { note = it; save("tk_note", it) }
            }
        }
        Spacer(Modifier.height(6.dp)); Divider()
        if (shown.isEmpty()) Empty(if (status == "open") "Nothing matches. Clear skies." else "Nothing here.")
        val list = shown.take(limit)
        list.forEachIndexed { i, t ->
            CheckRow(t.status == "done", (PRIO_EMOJI[t.prio]?.takeIf { it.isNotEmpty() }?.let { "$it " } ?: "") + t.text, { E.setTask(t, it) },
                onOpen = { Apps.openInObsidian(ctx, E.vaultName, t.path) },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        val w = whenText(t)
                        if (w.isNotEmpty()) Text(w.uppercase(), fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp, color = if (t.due != null && t.due < today && t.status == "open") ink.warn else ink.mute)
                        Text(t.file.removeSuffix(".md"), Modifier.width(110.dp), fontSize = 10.sp, color = ink.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                    Spacer(Modifier.width(6.dp)); XpHint(taskXP(t))
                }, divider = i < list.size - 1)
        }
        if (shown.size > limit) Pill("Show ${minOf(40, shown.size - limit)} more", { limit += 40 }, Modifier.fillMaxWidth().padding(top = 10.dp), small = true)
    }
}
