package com.stoa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stoa.app.Apps
import com.stoa.app.vault.Frontmatter
import com.stoa.app.vault.asList
import com.stoa.app.vault.stripMd
import com.stoa.app.vault.taskXP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun Page(nest: NestedScrollConnection? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().then(if (nest != null) Modifier.nestedScroll(nest) else Modifier).verticalScroll(rememberScrollState())
        .statusBarsPadding().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 28.dp)) { content() }
}

@Composable
fun HomePage(nest: NestedScrollConnection, openDrawer: () -> Unit) {
    val E = LocalEngine.current; val ui = LocalUi.current; val ink = LocalInk.current
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { time = LocalTime.now(); delay(1000L * (60 - time.second).coerceAtLeast(1)) } }
    Page(nest) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), fontFamily = Serif, fontSize = 78.sp, lineHeight = 78.sp, color = ink.ink)
                Muted(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), size = 15)
            }
            IconBtn(Icons.Outlined.Tune, "Settings & layout", { ui.settings = true })
        }
        Spacer(Modifier.height(10.dp))
        E.error?.let { err -> StoaCard("Set up") { Muted(err, size = 14); Spacer(Modifier.height(10.dp)); Pill("Open settings", { ui.settings = true }, primary = true) } }
        for (w in ui.layout.on(ui.layout.home)) when (w) {
            "level" -> StoaCard(flat = true) { LevelBlock(E.rpg) }
            "three" -> ThreeCard()
            "screen" -> ScreenTimeCard()
            "due" -> DueCard()
            "seren" -> SerenCard()
            "capture" -> CaptureCard()
        }
        // swipe-up affordance + dock
        val bob = rememberInfiniteTransition(label = "bob").animateFloat(0f, -5f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "bob")
        Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { openDrawer() },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.KeyboardArrowUp, null, Modifier.graphicsLayer { translationY = bob.value * density }, tint = ink.faint)
            Kicker("Swipe up for apps", color = ink.faint)
        }
        val all = entries(ui)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ui.layout.dock.mapNotNull { k -> all.find { it.key == k } }.take(4).forEach { AppTile(it, false) }
        }
    }
}

@Composable
fun ThreeCard() {
    val E = LocalEngine.current; val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    val pri = E.priorities()
    StoaCard("Today's three", action = { IconBtn(Icons.Outlined.Edit, "Edit", { editing = true }, size = 18.dp) }) {
        if (pri == null) {
            Muted("What three things would make today count? They live in today's daily note, so tomorrow starts clean.", size = 14)
            Spacer(Modifier.height(12.dp)); Pill("Set today's three", { editing = true }, primary = true)
        } else pri.forEachIndexed { i, t -> CheckRow(t.status == "done", t.text, { E.setTask(t, it) }, trailing = { XpHint(taskXP(t)) }, divider = i < pri.size - 1) }
    }
    if (editing) {
        val cur = pri?.map { it.text } ?: emptyList()
        AskDialog("Today's three", (0..2).map { "${it + 1}." to (cur.getOrNull(it) ?: "") }, "Commit", { editing = false }) { vals ->
            editing = false; val items = vals.map { it.trim() }.filter { it.isNotEmpty() }
            if (items.isNotEmpty()) scope.launch { E.setPriorities(items); E.toast("Committed", "Saved to today's daily note", "ach") }
        }
    }
}

@Composable
fun DueCard() {
    val E = LocalEngine.current; val ctx = LocalContext.current
    if (!E.ready) return
    val xs = E.dueTasks(); if (xs.isEmpty()) return
    val tk = E.today()
    StoaCard("Due soon", action = { Muted("${xs.size} open", size = 12) }) {
        xs.take(6).forEachIndexed { i, t ->
            CheckRow(false, t.text, { E.setTask(t, it) }, trailing = {
                val late = t.due!! < tk
                Text(if (late) "Overdue" else if (t.due == tk) "Today" else "Tomorrow", Modifier.border(1.dp, if (late) LocalInk.current.warn else LocalInk.current.line2, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 11.sp, color = if (late) LocalInk.current.warn else LocalInk.current.mute)
            }, onOpen = { Apps.openInObsidian(ctx, E.vaultName, t.path) }, divider = i < minOf(xs.size, 6) - 1)
        }
    }
}

@Composable
fun ScreenTimeCard() {
    val ctx = LocalContext.current; val ui = LocalUi.current; val ink = LocalInk.current
    val has = remember(ui.resume) { Apps.hasUsage(ctx) }
    val data by produceState(emptyList<Pair<String, Long>>(), ui.resume) { value = withContext(Dispatchers.IO) { Apps.screenTime(ctx) } }
    val resisted = remember(ui.resume) { ui.prefs.resisted(LocalDate.now().toString()) }
    StoaCard("Screen time") {
        if (!has) { Muted("Let Stoa see how long you spend in each app today.", size = 14); Spacer(Modifier.height(10.dp)); Pill("Allow usage access", { Apps.askUsage(ctx) }, small = true); return@StoaCard }
        val total = data.sumOf { it.second }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmtDur(total), fontFamily = Serif, fontSize = 34.sp, color = ink.ink, modifier = Modifier.weight(1f))
            Muted("$resisted urge${if (resisted == 1) "" else "s"} resisted", size = 12)
        }
        val mx = (data.firstOrNull()?.second ?: 1L).toFloat()
        data.take(3).forEach { (pkg, ms) ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Apps.labelOf(ctx, pkg), Modifier.weight(.4f), fontSize = 13.sp, color = ink.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(.45f).height(5.dp).clip(CircleShape).background(ink.line2)) { Box(Modifier.fillMaxWidth(ms / mx).height(5.dp).clip(CircleShape).background(ink.ink)) }
                Text(fmtDur(ms), Modifier.weight(.15f).padding(start = 8.dp), fontSize = 12.sp, color = ink.mute)
            }
        }
    }
}

/** One web clipping at a time, held for two hours — the dashboard's Serendipity, in your pocket. */
@Composable
fun SerenCard() {
    val E = LocalEngine.current; val ctx = LocalContext.current; val ui = LocalUi.current; val ink = LocalInk.current
    var reroll by remember { mutableIntStateOf(0) }
    val pick = remember(reroll, E.mdPaths.size) {
        val pool = E.clippingFiles(); val p = ui.prefs
        if (pool.isEmpty()) null else {
            val saved = p.str("seren_path", ""); val at = p.long("seren_at")
            if (reroll == 0 && pool.contains(saved) && System.currentTimeMillis() - at < 2 * 3600_000L) saved
            else { val others = if (pool.size > 1) pool.filter { it != saved } else pool; others.random().also { p.put("seren_path", it); p.put("seren_at", System.currentTimeMillis()) } }
        }
    }
    data class Clip(val title: String, val quote: String, val meta: String, val thumb: String?)
    val clip by produceState<Clip?>(null, pick) {
        value = pick?.let { rel -> withContext(Dispatchers.IO) {
            val txt = E.readText(rel) ?: ""; val sp = Frontmatter.split(txt); val fm = sp.fm
            val title = rel.substringAfterLast('/').removeSuffix(".md")
            val desc = fm["description"]?.toString()?.takeIf { it.isNotBlank() && it != title } ?: excerpt(sp.body)
            val bits = ArrayList<String>()
            fm["author"].asList().firstOrNull()?.let { bits.add(stripMd(it).trim('"', '\'')) }
            (fm["source"] ?: fm["url"])?.toString()?.let { u -> try { java.net.URI(u).host?.removePrefix("www.")?.let { bits.add(it) } } catch (e: Exception) { } }
            bits.add("clipped " + (fm["created"]?.toString() ?: ""))
            Clip(title, if (desc.length > 220) desc.take(217).substringBeforeLast(' ') + "…" else desc, bits.filter { it.isNotBlank() && it != "clipped " }.joinToString(" · "),
                (fm["thumbnail"] ?: fm["image"] ?: fm["cover"])?.toString())
        } }
    }
    StoaCard("Serendipity", action = { IconBtn(Icons.Outlined.Refresh, "Another one", { reroll++ }, size = 18.dp) }) {
        val c = clip
        if (pick == null) { Empty("Nothing in “${E.clippings}” yet. Clip a page with the Web Clipper and it will drift up here."); return@StoaCard }
        Column(Modifier.animateContentSize().clickable { Apps.openInObsidian(ctx, E.vaultName, pick) }) {
            if (c?.thumb != null) AsyncImage(c.thumb, null, Modifier.fillMaxWidth().height(150.dp).padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
            Text(c?.title ?: pick.substringAfterLast('/').removeSuffix(".md"), fontFamily = Serif, fontSize = 24.sp, lineHeight = 28.sp, color = ink.ink)
            if (!c?.quote.isNullOrBlank()) { Spacer(Modifier.height(6.dp)); Muted(c!!.quote, size = 14) }
            if (!c?.meta.isNullOrBlank()) { Spacer(Modifier.height(6.dp)); Muted(c!!.meta, size = 12) }
        }
    }
}

fun excerpt(body: String): String {
    val t = body.replace(Regex("```[\\s\\S]*?```"), "").replace(Regex("%%[\\s\\S]*?%%"), "")
    val lines = t.split("\n").map { it.trim() }.filter { it.isNotEmpty() && !Regex("^(#|>\\s*\\[!|!\\[|\\||-{3,}|<|\\$\\$|_No captions)").containsMatchIn(it) }
    val s = lines.firstOrNull { stripMd(it).replace(Regex("[-*>\\[\\]x ]"), "").length > 60 } ?: lines.firstOrNull() ?: ""
    return stripMd(s.replace(Regex("^[-*+]\\s+(\\[.\\]\\s+)?"), "").replace(Regex("^>\\s*"), "")).replace(Regex("[*_`~=]{1,3}"), "").trim()
}

@Composable
fun CaptureCard() {
    val E = LocalEngine.current; val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    StoaCard("Capture") {
        Field(text, { text = it }, "Capture an idea…", onDone = {
            val s = text.trim(); text = ""
            if (s.isNotEmpty()) scope.launch {
                if (s.startsWith("/task ", true)) { E.addTask(E.inbox, null, s.substring(6).trim()); E.toast("Task added", "Inbox", "ach") } else E.captureIdea(s)
            }
        })
        Spacer(Modifier.height(6.dp)); Muted("Ideas land in ${E.capture.substringAfterLast('/')} · start with /task to add to your Inbox", size = 12)
    }
}
