package com.stoa.app.ui

import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.graphics.Color
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
fun Page(nest: NestedScrollConnection? = null, scroll: androidx.compose.foundation.ScrollState = rememberScrollState(), content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().then(if (nest != null) Modifier.nestedScroll(nest) else Modifier).verticalScroll(scroll)
        .statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 28.dp)) { content() }
}

fun greetingFor(p: com.stoa.app.Prefs): String {
    val h = LocalTime.now().hour
    return if (h < 5) p.str("greet_night", "Quiet night") else if (h < 12) p.str("greet_morning", "Good morning") else if (h < 18) p.str("greet_afternoon", "Good afternoon") else p.str("greet_evening", "Good evening")
}

@Composable
fun HomePage(nest: NestedScrollConnection) {
    val E = LocalEngine.current; val ui = LocalUi.current; val ink = LocalInk.current
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { time = LocalTime.now(); delay(1000L * (60 - time.second).coerceAtLeast(1)) } }
    val scroll = rememberScrollState(); val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().homeGestures(scroll, ui, ctx)) {
    Page(null, scroll) {
        // greeting · home name ............ date
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(greetingFor(ui.prefs), fontFamily = Display, fontSize = 15.sp, color = ink.ink)
            Text("  ·  " + ui.homeName.uppercase(), fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.8.sp, color = ink.mute, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase(), fontFamily = Mono, fontSize = 13.sp, letterSpacing = 1.4.sp, color = ink.mute)
            Spacer(Modifier.width(4.dp))
            IconBtn(Icons.Outlined.Tune, "Settings & layout", { ui.settings = true }, tint = LocalInk.current.ink, size = 26.dp)
        }
        Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-2).sp, color = ink.ink)
        SyncLine()
        Spacer(Modifier.height(6.dp))
        E.error?.let { err -> StoaCard("Set up") { Muted(err, size = 14); Spacer(Modifier.height(10.dp)); Pill("Open settings", { ui.settings = true }, primary = true) } }
        for (w in ui.layout.on(ui.layout.home)) when (w) {
            "level" -> StoaCard(flat = true) { LevelBlock(E.rpg) }
            "screen" -> UsageGraph()
            "three" -> ThreeCard()
            "search" -> SearchCard()
            "capture" -> { CaptureCard(); NowPlaying() }
            "seren" -> SerenCard()
            "due" -> DueCard()
        }
        Spacer(Modifier.height(if (ui.dockCollapsed) 60.dp else 146.dp))
    }
    Dock(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 4.dp))
    FolderOverlay()
    }
}

/** Today's screen time by hour — a quiet bar chart. */
/** Hours for a day: live for today, the daily note's screen_hours for earlier days, else Android's usage history. */
suspend fun hoursFor(ctx: android.content.Context, E: com.stoa.app.vault.Engine, day: LocalDate): IntArray {
    if (day == LocalDate.now()) return withContext(Dispatchers.IO) { Apps.hourlyUsage(ctx, day) }
    E.days[day.toString()]?.screenHours?.takeIf { it.size == 24 }?.let { return it.toIntArray() }
    return withContext(Dispatchers.IO) { Apps.hourlyUsage(ctx, day) }
}

fun fmtMin(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

@Composable
fun UsageGraph() {
    val ctx = LocalContext.current; val ui = LocalUi.current; val ink = LocalInk.current; val E = LocalEngine.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(300_000); tick++ } }
    var back by remember { mutableIntStateOf(0) }
    LaunchedEffect(ui.homeTick) { back = 0 }
    val day = LocalDate.now().minusDays(back.toLong())
    val has = remember(ui.resume) { Apps.hasUsage(ctx) }
    val hours by produceState(IntArray(24), ui.resume, tick, back, E.days[day.toString()]?.screenHours) { value = hoursFor(ctx, E, day) }
    val grow by androidx.compose.animation.core.animateFloatAsState(if (hours.sum() > 0) 1f else 0f, tween(900), label = "grow")
    val now = if (back == 0) LocalTime.now().hour else -1
    StoaCard("Screen time", action = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.ChevronLeft, "Previous day", { if (back < 27) back++ }, size = 18.dp)
            Text(when (back) { 0 -> "TODAY"; 1 -> "YESTERDAY"; else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase() }, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.2.sp, color = ink.mute)
            IconBtn(Icons.Outlined.ChevronRight, "Next day", { if (back > 0) back-- }, tint = if (back > 0) ink.mute else ink.faint, size = 18.dp)
        }
    }) {
        if (!has) { Muted("Allow usage access to see your day's screen time.", size = 13); Spacer(Modifier.height(8.dp)); Pill("Allow usage access", { Apps.askUsage(ctx) }, small = true); return@StoaCard }
        val total = hours.sum()
        Column(Modifier.pointerInput(Unit) {
            var acc = 0f
            androidx.compose.foundation.gestures.detectHorizontalDragGestures(onDragStart = { acc = 0f }, onDragEnd = {
                if (acc > 60f && back < 27) back++ else if (acc < -60f && back > 0) back--
            }) { ch, d -> ch.consume(); acc += d }
        }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmtMin(total), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 30.sp, color = ink.ink, modifier = Modifier.weight(1f))
            val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
            if (total > 0) Kicker("peak ${"%02d".format(peak)}:00 · ${ui.prefs.resisted(day.toString())} resisted")
        }
        Spacer(Modifier.height(10.dp))
        val barC = ink.ink; val dimC = ink.line2; val faint = ink.faint
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val n = 24; val gap = 3.dp.toPx(); val w = (size.width - gap * (n - 1)) / n
            val mx = maxOf(30, hours.maxOrNull() ?: 0).toFloat()
            for (i in 0 until n) {
                val v = hours[i] / mx * size.height * grow
                val x = i * (w + gap)
                drawRoundRect(dimC, androidx.compose.ui.geometry.Offset(x, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(w, 2.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                if (v > 0f) drawRoundRect(if (i == now) barC else barC.copy(alpha = .55f), androidx.compose.ui.geometry.Offset(x, size.height - v), androidx.compose.ui.geometry.Size(w, v), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                if (i == now) drawCircle(faint, 1.5.dp.toPx(), androidx.compose.ui.geometry.Offset(x + w / 2, size.height + 5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) { listOf("00", "06", "12", "18", "24").forEachIndexed { i, l -> Text(l, Modifier.weight(1f), fontFamily = Mono, fontSize = 9.sp, color = ink.faint, textAlign = if (i == 4) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start) } }
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
            Text(fmtDur(total), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 34.sp, color = ink.ink, modifier = Modifier.weight(1f))
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
        Column(Modifier.animateContentSize().clickable { ui.reader = pick }) {
            if (c?.thumb != null) AsyncImage(c.thumb, null, Modifier.fillMaxWidth().height(150.dp).padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
            Text(c?.title ?: pick.substringAfterLast('/').removeSuffix(".md"), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 24.sp, lineHeight = 28.sp, color = ink.ink)
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

/** A quick sheet for an idea (→ Quick Capture) or a task (→ Inbox). */
@Composable
fun CaptureSheet(kind: String) {
    val E = LocalEngine.current; val ui = LocalUi.current; val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { delay(250); try { fr.requestFocus() } catch (e: Exception) { } }
    Sheet(if (kind == "task") "New task" else "New idea", { ui.capture = null }) {
        Field(text, { text = it }, if (kind == "task") "What needs doing?" else "What just crossed your mind?", singleLine = kind == "task",
            modifier = Modifier.focusRequester(fr).heightIn(min = if (kind == "task") 0.dp else 110.dp), style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
            onDone = { if (kind == "task") { val t = text.trim(); if (t.isNotEmpty()) scope.launch { E.addTask(E.inbox, null, t); E.toast("Task added", "Inbox", "ach") }; ui.capture = null } })
        Spacer(Modifier.height(12.dp))
        Pill(if (kind == "task") "Add to Inbox" else "Capture", {
            val t = text.trim(); if (t.isNotEmpty()) scope.launch { if (kind == "task") { E.addTask(E.inbox, null, t); E.toast("Task added", "Inbox", "ach") } else E.captureIdea(t) }; ui.capture = null
        }, Modifier.fillMaxWidth(), primary = true)
    }
}

/** Read a web clipping right here, in calm type. */
@Composable
fun ClipReader(rel: String) {
    val E = LocalEngine.current; val ui = LocalUi.current; val ink = LocalInk.current; val ctx = LocalContext.current
    val data by produceState<Pair<Map<String, Any?>, String>?>(null, rel) { value = withContext(Dispatchers.IO) { E.readText(rel)?.let { val sp = Frontmatter.split(it); sp.fm to sp.body } } }
    Box(Modifier.fillMaxSize().background(ink.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 22.dp).padding(top = 56.dp, bottom = 60.dp)) {
            val fm = data?.first ?: emptyMap(); val body = data?.second ?: ""
            val thumb = (fm["thumbnail"] ?: fm["image"] ?: fm["cover"])?.toString()
            if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
            Spacer(Modifier.height(18.dp))
            Text(rel.substringAfterLast('/').removeSuffix(".md"), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 36.sp, color = ink.ink)
            val host = (fm["source"] ?: fm["url"])?.toString()?.let { try { java.net.URI(it).host?.removePrefix("www.") } catch (e: Exception) { null } }
            Text(listOfNotNull(fm["author"].asList().firstOrNull()?.let { stripMd(it) }, host, fm["published"]?.toString()).joinToString("  ·  ").uppercase(),
                Modifier.padding(top = 10.dp, bottom = 18.dp), fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.2.sp, color = ink.mute)
            body.split("\n").map { it.trimEnd() }.forEach { l ->
                val t = l.trim()
                when {
                    t.isEmpty() || t.startsWith("![") || t.startsWith("%%") || t.startsWith("_No captions") -> {}
                    t.startsWith("#") -> Text(stripMd(t.trimStart('#').trim()), Modifier.padding(top = 18.dp, bottom = 6.dp), fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = ink.ink)
                    Regex("^[-*+]\\s+").containsMatchIn(t) -> Row(Modifier.padding(vertical = 3.dp)) { Text("·  ", color = ink.mute, fontSize = 17.sp); Text(clean(t.replace(Regex("^[-*+]\\s+(\\[.\\]\\s+)?"), "")), color = ink.ink, fontSize = 17.sp, lineHeight = 27.sp) }
                    t.startsWith(">") -> Text(clean(t.trimStart('>').trim()), Modifier.padding(vertical = 6.dp).padding(start = 14.dp), color = ink.mute, fontSize = 17.sp, lineHeight = 27.sp, fontWeight = FontWeight.Light)
                    else -> Text(clean(t), Modifier.padding(vertical = 6.dp), color = ink.ink, fontSize = 17.sp, lineHeight = 27.sp)
                }
            }
            val src = (fm["source"] ?: fm["url"])?.toString()
            Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Open in Obsidian", { Apps.openInObsidian(ctx, E.vaultName, rel) }, small = true)
                if (src != null) Pill("Source", { Apps.openUrl(ctx, src) }, small = true)
            }
        }
        Row(Modifier.fillMaxWidth().background(ink.bg).statusBarsPadding().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.Close, "Close", { ui.reader = null }, tint = ink.ink)
            Box(Modifier.weight(1f)); Kicker("Serendipity")
            Spacer(Modifier.width(14.dp))
        }
    }
}
private fun clean(s: String) = stripMd(s).replace(Regex("[*_`~=]{1,3}"), "").replace(Regex("<[^>]+>"), "")

/** "Synced 2h ago · Sync now" — Obsidian Sync only runs inside Obsidian, so one tap opens it and comes back. */
@Composable
fun SyncLine(modifier: Modifier = Modifier, light: Boolean = false) {
    val ctx = LocalContext.current; val ui = LocalUi.current; val ink = LocalInk.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    val syncing = com.stoa.app.SyncNow.syncing
    val last = remember(ui.resume, syncing, tick, com.stoa.app.SyncNow.lastDone) { com.stoa.app.SyncNow.lastSynced(ctx) }
    val stale = last > 0 && System.currentTimeMillis() - last > 3_600_000L
    val base = if (light) Color(0x99FFFFFF) else ink.mute
    val spin = rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "r")
    Row(modifier.clip(CircleShape).clickable(enabled = !syncing) { com.stoa.app.SyncNow.start(ctx) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Sync, "Sync now", Modifier.size(13.dp).graphicsLayer { rotationZ = if (syncing) -spin.value else 0f }, tint = if (stale && !syncing) ink.warn else base)
        Spacer(Modifier.width(6.dp))
        Text((if (syncing) "SYNCING WITH OBSIDIAN…" else "SYNCED ${com.stoa.app.SyncNow.ago(last).uppercase()}  ·  SYNC NOW"),
            fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.4.sp, color = if (stale && !syncing) ink.warn else base)
    }
}

/** Home gestures: pull down (from the top of the page) → notification shade; double-tap empty space → lock. */
fun Modifier.homeGestures(scroll: androidx.compose.foundation.ScrollState, ui: UiState, ctx: android.content.Context): Modifier = this
    .pointerInput(ui) {
        // watch (never consume) so taps and scrolls keep working
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val atTop = scroll.value == 0
            var fired = false
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                val d = c.position - down.position
                if (!fired && ui.gSwipeDown && atTop && d.y > 70.dp.toPx() && d.y > kotlin.math.abs(d.x) * 1.6f) { fired = true; com.stoa.app.Apps.expandNotifications(ctx) }
            }
        }
    }
    .pointerInput(ui) {
        var lastUp = 0L; var lastPos = androidx.compose.ui.geometry.Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Final)
            var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                if (!c.pressed) { up = c; break }
            }
            val u = up ?: run { lastUp = 0L; return@awaitEachGesture }
            // a tap that a button or field already handled doesn't count
            if (u.isConsumed || down.isConsumed) { lastUp = 0L; return@awaitEachGesture }
            val now = u.uptimeMillis
            if (lastUp != 0L && now - lastUp < viewConfiguration.doubleTapTimeoutMillis && (u.position - lastPos).getDistance() < 60.dp.toPx()) {
                lastUp = 0L
                if (ui.gDoubleLock) com.stoa.app.LockAdmin.lock(ctx)
            } else { lastUp = now; lastPos = u.position }
        }
    }
