package com.stoa.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoa.app.Apps
import com.stoa.app.Reminders
import com.stoa.app.SyncNow
import kotlinx.coroutines.launch

/** One search result. Lower rank runs first on Enter; Google is always shown first but only runs when nothing else fits. */
class Hit(val title: String, val sub: String, val icon: ImageVector, val rank: Int, val run: suspend () -> Unit)

private val SETTINGS_TOPICS = listOf(
    "Wallpaper blur & dim", "Layout & home widgets", "Pinned apps & folders", "Mindful pause apps", "Vault folder", "Notifications",
    "Usage access", "Display over other apps", "Book note", "Workouts folder", "Quick capture folder", "Web clippings folder",
    "Habits heading", "Greeting & home name", "Hidden apps", "Re-read the vault")

/** The command bar: search the web, your apps, notes and settings, or tell Stoa to do something. */
@Composable
fun SearchCard() {
    val E = LocalEngine.current; val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current
    val scope = rememberCoroutineScope(); val focus = LocalFocusManager.current
    var q by remember { mutableStateOf("") }
    val all = entries(ui)
    val hits = remember(q, all, E.mdPaths, E.habitLayout()) {
        val s = q.trim(); val out = ArrayList<Hit>()
        if (s.isEmpty()) return@remember out
        out.add(Hit("Search Google for “$s”", "google.com", Icons.Outlined.Public, 99) { Apps.webSearch(ctx, s) })
        Regex("^(?:task|todo|remind me to|remind me|remind|t)\\s+(.+)$", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            val p = Reminders.parse(m.groupValues[1])
            if (p.text.isNotBlank()) out.add(Hit("Add task: ${p.text}", Reminders.describe(p).replaceFirstChar { it.uppercase() } + " · Inbox", if (p.at != null) Icons.Outlined.NotificationsActive else Icons.Outlined.TaskAlt, 0) {
                E.addTask(E.inbox, null, Reminders.taskLine(p)); p.at?.let { Reminders.schedule(ctx, p.text, it) }
                E.toast("Task added", Reminders.describe(p), "ach")
            })
        }
        Regex("^(?:idea|note|capture|i)\\s+(.+)$", RegexOption.IGNORE_CASE).find(s)?.let { m -> out.add(Hit("Capture idea: ${m.groupValues[1]}", "Quick Capture", Icons.Outlined.Lightbulb, 0) { E.captureIdea(m.groupValues[1]) }) }
        Regex("^(?:tick|done|did|habit)\\s+(.+)$", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            E.habitList().firstOrNull { it.contains(m.groupValues[1].trim(), true) }?.let { h -> out.add(Hit("Tick $h", "Habit · today", Icons.Outlined.CheckCircle, 0) { E.setHabit(h, true) }) }
        }
        if (s.equals("sync", true) || s.startsWith("sync ", true)) out.add(Hit("Sync with Obsidian", "Opens Obsidian, then comes back", Icons.Outlined.Sync, 0) { SyncNow.start(ctx) })
        if (Regex("^(daily|today'?s? note|journal)$", RegexOption.IGNORE_CASE).matches(s)) out.add(Hit("Open today's daily note", "Obsidian", Icons.Outlined.Description, 0) { Apps.openInObsidian(ctx, E.vaultName, E.ensureDaily()) })
        all.mapNotNull { e -> matchScore(e.label, s)?.let { e to it } }.sortedWith(compareBy<Pair<Entry, Int>> { it.second }.thenBy { it.first.label.length }).take(4).forEach { (e, sc) ->
            out.add(Hit(e.label, if (e.app != null) "App" else "Stoa", if (e.app != null) Icons.Outlined.Apps else Icons.Outlined.ViewAgenda, if (sc <= 1) 1 else 5) { openEntry(ctx, ui, e) })
        }
        if (s.length > 1) E.searchNotes(s).take(4).forEach { rel -> out.add(Hit(rel.substringAfterLast('/').removeSuffix(".md"), rel.substringBeforeLast('/', "Vault"), Icons.Outlined.Description, 3) { Apps.openInObsidian(ctx, E.vaultName, rel) }) }
        if (s.length > 2) SETTINGS_TOPICS.filter { it.contains(s, true) }.take(2).forEach { t -> out.add(Hit(t, "Settings", Icons.Outlined.Settings, 4) { ui.settings = true }) }
        out
    }
    fun run(h: Hit) { q = ""; focus.clearFocus(); scope.launch { h.run() } }
    fun enter() { if (hits.isEmpty()) return; run(hits.filter { it.rank < 99 }.minByOrNull { it.rank } ?: hits.first()) }
    StoaCard("Search") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, ink.line2, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = ink.mute)
            Spacer(Modifier.width(10.dp))
            BasicTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, cursorBrush = SolidColor(ink.ink),
                textStyle = TextStyle(fontFamily = Display, fontSize = 17.sp, color = ink.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { enter() }, onDone = { enter() }, onGo = { enter() }),
                decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search, or “task call mum at 6pm”", style = TextStyle(fontFamily = Display, fontSize = 17.sp, color = ink.faint), maxLines = 1); inner() } })
            if (q.isNotEmpty()) IconBtn(Icons.Outlined.Close, "Clear", { q = "" }, size = 16.dp)
        }
        AnimatedVisibility(hits.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 6.dp)) {
                val best = hits.filter { it.rank < 99 }.minByOrNull { it.rank } ?: hits.firstOrNull()
                hits.take(9).forEach { h ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (h === best) ink.line else ink.bg.copy(alpha = 0f)).clickable { run(h) }.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(h.icon, null, Modifier.size(18.dp), tint = if (h === best) ink.ink else ink.mute)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.title, color = ink.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(h.sub.uppercase(), fontFamily = Mono, fontSize = 9.5.sp, letterSpacing = 1.1.sp, color = ink.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (h === best) Text("↵", fontFamily = Mono, color = ink.mute, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
