package com.stoa.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoa.app.Apps
import com.stoa.app.LApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** An entry in the dock / drawer: one of Stoa's own sections, or an installed app. */
data class Entry(val key: String, val label: String, val glyph: String, val app: LApp? = null)

fun entries(ui: UiState): List<Entry> {
    val L = ui.layout
    val on = (L.on(L.tabs) + L.on(L.today) + L.on(L.notes)).toSet()
    val secs = SECTIONS.filter { on.contains(it.key) }.map { (k, v) -> Entry("sec:$k", v, if (k == "shots") "▣" else if (k == "tasks") "✓" else v.take(1)) }
    return secs + ui.apps.filter { !ui.hidden.contains(it.pkg) }.map { Entry("app:${it.pkg}/${it.cls}", it.label, it.label.take(1).uppercase(), it) }
}

fun openEntry(ctx: Context, ui: UiState, e: Entry) {
    val a = e.app
    if (a == null) { ui.go(e.key.removePrefix("sec:")); ui.homeTick++; return }
    val prefs = ui.prefs
    if (ui.mindful.contains(a.pkg) && prefs.passUntil(a.pkg) < System.currentTimeMillis() && !Apps.hasAccessibility(ctx)) { ui.pauseFor = a; return }
    Apps.launch(ctx, a)
}

@Composable
fun Root() {
    val ui = LocalUi.current; val ink = LocalInk.current; val E = LocalEngine.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().background(ink.bg)) {
        val H = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        // drawer position as a fraction (0 = open, 1 = closed) so the keyboard resizing the screen never moves it
        var frac by remember { mutableFloatStateOf(1f) }
        var job by remember { mutableStateOf<Job?>(null) }
        fun settle(target: Float, v: Float = 0f) {
            job?.cancel()
            job = scope.launch { animate(frac, target, v / H, spring(dampingRatio = .86f, stiffness = 330f)) { x, _ -> frac = x } }
        }
        fun moveBy(px: Float): Float { val n = (frac + px / H).coerceIn(0f, 1f); val used = (n - frac) * H; frac = n; return used }
        val drawerOpen = frac < .999f
        LaunchedEffect(ui.drawerReq) { if (ui.drawerReq > 0) settle(0f) }
        LaunchedEffect(ui.homeTick) { if (ui.homeTick > 0) settle(1f) }
        BackHandler(true) {
            when {
                ui.canvas != null -> ui.canvas = null
                ui.viewer != null -> ui.viewer = null
                ui.pauseFor != null -> ui.pauseFor = null
                ui.settings -> ui.settings = false
                drawerOpen -> settle(1f)
                ui.tab != "home" -> ui.tab = "home"
            }
        }
        val homeNest = remember(H) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (frac < 1f && available.y > 0 && source == NestedScrollSource.UserInput) { job?.cancel(); return Offset(0f, moveBy(available.y)) }
                    return Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0 && source == NestedScrollSource.UserInput) { job?.cancel(); return Offset(0f, moveBy(available.y)) }
                    return Offset.Zero
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (frac < .999f && frac > 0f) { settle(if (available.y < -700f || (available.y <= 700f && frac < .72f)) 0f else 1f, available.y); return available }
                    return Velocity.Zero
                }
            }
        }
        val drawerNest = remember(H) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (frac > 0f && available.y < 0 && source == NestedScrollSource.UserInput) { job?.cancel(); return Offset(0f, moveBy(available.y)) }
                    return Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0 && source == NestedScrollSource.UserInput) { job?.cancel(); return Offset(0f, moveBy(available.y)) }
                    return Offset.Zero
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (frac > 0f && frac < 1f) { settle(if (available.y > 700f || (available.y >= -700f && frac > .22f)) 1f else 0f, available.y); return available }
                    return Velocity.Zero
                }
            }
        }

        val tabs = ui.layout.on(ui.layout.tabs).ifEmpty { listOf("home") }
        if (!tabs.contains(ui.tab)) ui.tab = tabs.first()
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(ui.tab, Modifier.weight(1f), label = "tabs", transitionSpec = {
                val dir = if (tabs.indexOf(targetState) >= tabs.indexOf(initialState)) 1 else -1
                (fadeIn(tween(220, 60)) + slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { dir * it / 10 }) togetherWith fadeOut(tween(110))
            }) { t ->
                when (t) {
                    "today" -> TodayPage()
                    "notes" -> NotesPage()
                    "gym" -> GymPage()
                    else -> HomePage(homeNest) { settle(0f) }
                }
            }
            BottomNav(tabs)
        }
        if (frac < 1f) Box(Modifier.fillMaxSize().offset { IntOffset(0, (frac * H).roundToInt()) }.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color.Black)) {
            Drawer(drawerNest) { settle(1f) }
        }
        ui.canvas?.let { CanvasEditor(it) }
        ui.viewer?.let { ShotViewer(it) }
        if (ui.settings) SettingsScreen()
        ui.pauseFor?.let { a ->
            val ctx = LocalContext.current
            Box(Modifier.fillMaxSize().background(ink.bg)) {
                PauseScreen(a.label, ui.prefs.resisted(LocalDate.now().toString()),
                    onBack = { ui.prefs.addResisted(LocalDate.now().toString()); ui.pauseFor = null; E.toast("Good call.", "Urge resisted", "ach") },
                    onOpen = { m -> ui.prefs.grantPass(a.pkg, m); ui.pauseFor = null; Apps.launch(ctx, a) })
            }
        }
        TimerWatcher()
        ToastHost(E)
    }
}


@Composable
fun BottomNav(tabs: List<String>) {
    val ui = LocalUi.current; val ink = LocalInk.current
    val icons = mapOf("home" to Icons.Outlined.Home, "today" to Icons.Outlined.WbSunny, "notes" to Icons.Outlined.EditNote, "gym" to Icons.Outlined.FitnessCenter)
    Column(Modifier.fillMaxWidth().background(ink.bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.line))
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            val w = maxWidth / tabs.size
            val x by animateDpAsState(w * tabs.indexOf(ui.tab).coerceAtLeast(0), spring(dampingRatio = .78f, stiffness = 380f), label = "nav")
            Box(Modifier.offset(x = x).width(w).height(52.dp).clip(RoundedCornerShape(15.dp)).background(ink.line))
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                tabs.forEach { t ->
                    val on = t == ui.tab
                    Column(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ui.tab = t },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(icons[t] ?: Icons.Outlined.Home, null, Modifier.size(21.dp), tint = if (on) ink.ink else ink.mute)
                        Text(SECTIONS[t] ?: t, fontSize = 11.sp, color = if (on) ink.ink else ink.mute)
                    }
                }
            }
        }
    }
}

private val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(e: Entry, light: Boolean, onLong: (() -> Unit)? = null) {
    val ctx = LocalContext.current; val ui = LocalUi.current; val ink = LocalInk.current
    val fg = if (light) Color.White else ink.ink
    val mindful = e.app != null && ui.mindful.contains(e.app.pkg)
    Column(Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = { openEntry(ctx, ui, e) }, onLongClick = onLong).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val icon = if (e.app != null) produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, e.key) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Apps.icon(ctx, e.app) } }.value else null
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).then(
            if (e.app == null) Modifier.background(if (light) Color.White else ink.ink)
            else if (mindful) Modifier.border(1.5.dp, if (light) Color(0x66FFFFFF) else ink.line2, RoundedCornerShape(18.dp)) else Modifier
        ), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, null, Modifier.size(if (mindful) 40.dp else 52.dp), colorFilter = GREY, alpha = if (mindful) .55f else 1f)
            else Text(e.glyph, fontFamily = Serif, fontSize = 26.sp, color = if (e.app == null) (if (light) Color.Black else ink.inv) else fg)
        }
        Spacer(Modifier.height(5.dp))
        Text(e.label, fontSize = 11.5.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Swipe-up app drawer: pure black & white, with today's numbers at the top. */
@Composable
fun Drawer(nest: NestedScrollConnection, close: () -> Unit) {
    val E = LocalEngine.current; val ui = LocalUi.current; val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<Entry?>(null) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(5000) } }
    val all = entries(ui)
    val shown = if (q.isBlank()) all else all.filter { it.label.contains(q, true) }
    val notes = if (q.length > 1) E.searchNotes(q) else emptyList()
    val d = E.day(); val habits = E.habitList()
    val st = remember(ui.resume) { Apps.screenTime(ctx).sumOf { it.second } }
    val mono = androidx.compose.ui.text.TextStyle(fontFamily = Mono, color = Color.White)
    LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize().nestedScroll(nest), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding()) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), contentAlignment = Alignment.Center) { Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x55FFFFFF))) }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = mono, fontSize = 48.sp, modifier = Modifier.weight(1f))
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase(), style = mono, fontSize = 12.sp, letterSpacing = 1.6.sp, color = Color(0xB3FFFFFF))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().border(1.dp, Color(0x2EFFFFFF))) {
                    val rd = listOf("morning", "evening").count { d.complete.contains(it) }
                    listOf((E.rpg?.let { "LV ${it.level}" } ?: "LV –") to (E.rpg?.rank?.uppercase() ?: ""), "${habits.count { d.done.contains(it.lowercase()) }}/${habits.size}" to "HABITS",
                        "$rd/2" to "ROUTINES", (if (st > 0) fmtDur(st) else "–") to "SCREEN").forEach { (a, b) ->
                        Column(Modifier.weight(1f).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(a, style = mono, fontSize = 16.sp); Text(b, style = mono, fontSize = 9.sp, letterSpacing = 1.4.sp, color = Color(0x99FFFFFF))
                        }
                    }
                }
                E.rpg?.let { r -> Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x26FFFFFF))) { Box(Modifier.fillMaxWidth(r.into.toFloat() / r.need).fillMaxHeight().background(Color.White)) } }
                Spacer(Modifier.height(16.dp))
                Field(q, { q = it }, "Search apps & notes", light = true)
                Spacer(Modifier.height(14.dp))
            }
        }
        items(notes, span = { GridItemSpan(maxLineSpan) }) { rel ->
            Column(Modifier.fillMaxWidth().clickable { close(); Apps.openInObsidian(ctx, E.vaultName, rel) }.padding(vertical = 10.dp)) {
                Text(rel.substringAfterLast('/').removeSuffix(".md"), color = Color.White, fontSize = 15.sp)
                Text(rel.substringBeforeLast('/', ""), color = Color(0x80FFFFFF), fontSize = 11.5.sp)
            }
        }
        items(shown, key = { it.key }) { e -> Box(contentAlignment = Alignment.Center) { AppTile(e, true) { menu = e } } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                Pill("Settings & layout", { ui.settings = true }, light = true, small = true)
                if (ui.hidden.isNotEmpty()) Pill("${ui.hidden.size} hidden", { ui.settings = true }, light = true, small = true)
            }
        }
    }
    menu?.let { e -> AppMenu(e) { menu = null } }
}

@Composable
fun AppMenu(e: Entry, dismiss: () -> Unit) {
    val ui = LocalUi.current; val ctx = LocalContext.current
    Sheet(e.label, dismiss) {
        val inDock = ui.layout.dock.contains(e.key)
        MenuItem(if (inDock) "Remove from dock" else "Add to dock") {
            val d = if (inDock) ui.layout.dock - e.key else (ui.layout.dock + e.key).takeLast(4)
            ui.saveLayout(ui.layout.copy(dock = d)); dismiss()
        }
        e.app?.let { a ->
            val m = ui.mindful.contains(a.pkg)
            MenuItem(if (m) "Stop pausing before ${a.label}" else "Pause & breathe before ${a.label}") { ui.updateMindful(if (m) ui.mindful - a.pkg else ui.mindful + a.pkg); dismiss() }
            MenuItem("Hide from drawer") { ui.updateHidden(ui.hidden + a.pkg); dismiss() }
            MenuItem("App info") { Apps.appInfo(ctx, a.pkg); dismiss() }
        }
    }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp), fontSize = 16.sp, color = LocalInk.current.ink)
    Divider()
}

@Composable
fun AnimatedIn(content: @Composable () -> Unit) {
    val s = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(s, enter = fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 14 }, exit = fadeOut() + slideOutVertically()) { content() }
}
