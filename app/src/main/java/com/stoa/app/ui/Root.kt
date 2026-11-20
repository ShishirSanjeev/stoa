package com.stoa.app.ui

import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
                ui.reader != null -> ui.reader = null
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
            Drawer(drawerNest, frac < .5f) { settle(1f) }
        }
        ui.reader?.let { ClipReader(it) }
        ui.capture?.let { CaptureSheet(it) }
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
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 4.dp)) {
            val w = maxWidth / tabs.size
            val x by animateDpAsState(w * tabs.indexOf(ui.tab).coerceAtLeast(0), spring(dampingRatio = .7f, stiffness = 420f), label = "nav")
            Row(Modifier.fillMaxWidth().height(54.dp)) {
                tabs.forEach { t ->
                    val on = t == ui.tab
                    val c by androidx.compose.animation.animateColorAsState(if (on) ink.ink else ink.faint, label = "navc")
                    Column(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ui.tab = t },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(icons[t] ?: Icons.Outlined.Home, null, Modifier.size(20.dp), tint = c)
                        Spacer(Modifier.height(4.dp))
                        Text((SECTIONS[t] ?: t).uppercase(), fontSize = 9.sp, letterSpacing = 1.3.sp, fontFamily = Mono, color = c)
                    }
                }
            }
            Box(Modifier.offset(x = x + w / 2 - 2.dp, y = 50.dp).size(4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ink.ink))
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
            else Text(e.glyph, fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 26.sp, color = if (e.app == null) (if (light) Color.Black else ink.inv) else fg)
        }
        Spacer(Modifier.height(5.dp))
        Text(e.label, fontSize = 11.5.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Lower is better; null = no match. Prefix beats word-start beats substring beats fuzzy. */
fun matchScore(label: String, q: String): Int? {
    val l = label.lowercase(); val s = q.lowercase().trim()
    if (s.isEmpty()) return 0
    if (l.startsWith(s)) return 0
    if (l.split(' ', '-', '.', '_').any { it.startsWith(s) }) return 1
    if (l.contains(s)) return 2
    var i = 0; for (c in l) if (i < s.length && c == s[i]) i++
    return if (i == s.length) 3 else null
}

/** Swipe-up drawer: every app in one quiet alphabetical list, keyboard already up, Enter opens the best match. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Drawer(nest: NestedScrollConnection, open: Boolean, close: () -> Unit) {
    val E = LocalEngine.current; val ui = LocalUi.current; val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var q by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<Entry?>(null) }
    val fr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current; val focus = LocalFocusManager.current
    LaunchedEffect(open) { if (open) { try { fr.requestFocus() } catch (e: Exception) { }; kb?.show() } else { q = ""; focus.clearFocus(); kb?.hide() } }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(10_000) } }
    val all = entries(ui)
    val apps = remember(all) { all.filter { it.app != null }.sortedBy { it.label.lowercase() } }
    val secs = all.filter { it.app == null }
    val results = remember(q, all) { if (q.isBlank()) emptyList() else all.mapNotNull { e -> matchScore(e.label, q)?.let { e to it } }.sortedWith(compareBy<Pair<Entry, Int>> { it.second }.thenBy { it.first.label.length }).map { it.first } }
    val notes = remember(q, E.mdPaths) { if (q.trim().length > 1) E.searchNotes(q.trim()).take(6) else emptyList() }
    fun launch(e: Entry) { openEntry(ctx, ui, e); if (e.app != null) close() }
    fun go() { results.firstOrNull()?.let { launch(it) } ?: notes.firstOrNull()?.let { close(); Apps.openInObsidian(ctx, E.vaultName, it) } }
    val list = rememberLazyListState()
    val dim = Color(0x80FFFFFF); val mono = TextStyle(fontFamily = Mono, color = dim, fontSize = 11.sp, letterSpacing = 1.4.sp)
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 22.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), contentAlignment = Alignment.Center) { Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF))) }
        val d = E.day(); val habits = E.habitList()
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")) + "   " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase() + "   " +
            (E.rpg?.let { "LV ${it.level}" } ?: "") + "   " + "${habits.count { d.done.contains(it.lowercase()) }}/${habits.size} HABITS", style = mono, maxLines = 1)
        SyncLine(Modifier.padding(top = 8.dp), light = true)
        BasicTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp).focusRequester(fr),
            textStyle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 34.sp, color = Color.White), singleLine = true, cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }, onDone = { go() }, onSearch = { go() }),
            decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = 34.sp, color = Color(0x55FFFFFF))); inner() } })
        if (q.isBlank()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            secs.forEach { s -> Text(s.label.uppercase(), Modifier.clickable { launch(s) }.padding(vertical = 6.dp), style = mono.copy(color = Color(0xCCFFFFFF))) }
            Text("SETTINGS", Modifier.clickable { ui.settings = true }.padding(vertical = 6.dp), style = mono)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().nestedScroll(nest).padding(end = if (q.isBlank()) 22.dp else 0.dp), state = list, contentPadding = PaddingValues(bottom = 30.dp)) {
                if (q.isBlank()) items(apps, key = { it.key }) { e -> DrawerRow(e, Modifier.animateItem()) { menu = e } }
                else {
                    itemsIndexed(results, key = { _, e -> "r" + e.key }) { i, e -> DrawerRow(e, Modifier.animateItem(), first = i == 0) { menu = e } }
                    if (notes.isNotEmpty()) item(key = "notes-h") { Text("NOTES", Modifier.padding(top = 18.dp, bottom = 6.dp), style = mono) }
                    items(notes, key = { "n$it" }) { rel ->
                        Column(Modifier.fillMaxWidth().animateItem().clickable { close(); Apps.openInObsidian(ctx, E.vaultName, rel) }.padding(vertical = 8.dp)) {
                            Text(rel.substringAfterLast('/').removeSuffix(".md"), color = Color.White, fontSize = 17.sp, fontFamily = Display, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(rel.substringBeforeLast('/', ""), style = mono.copy(fontSize = 10.sp), maxLines = 1)
                        }
                    }
                    if (results.isEmpty() && notes.isEmpty()) item { Text("Nothing called “$q”.", Modifier.padding(top = 12.dp), color = dim, fontFamily = Display) }
                }
            }
            if (q.isBlank() && apps.isNotEmpty()) {
                val letters = remember(apps) { apps.map { it.label.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }.distinct() }
                var active by remember { mutableStateOf<Char?>(null) }
                val scope = rememberCoroutineScope()
                fun jump(c: Char) {
                    if (c == active) return; active = c; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val idx = apps.indexOfFirst { (it.label.firstOrNull()?.uppercaseChar()?.takeIf { x -> x.isLetter() } ?: '#') == c }
                    if (idx >= 0) scope.launch { list.scrollToItem(idx) }
                }
                BoxWithConstraints(Modifier.align(Alignment.CenterEnd).width(22.dp).fillMaxHeight(.86f)) {
                    val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    fun at(y: Float) = letters[((y / hPx) * letters.size).toInt().coerceIn(0, letters.size - 1)]
                    Column(Modifier.fillMaxSize().pointerInput(letters) {
                        detectVerticalDragGestures(onDragStart = { jump(at(it.y)) }, onDragEnd = { active = null }, onDragCancel = { active = null }) { ch, _ -> ch.consume(); jump(at(ch.position.y)) }
                    }.pointerInput(letters) { detectTapGestures { jump(at(it.y)); active = null } }, verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                        letters.forEach { c ->
                            val on = c == active
                            val sc by animateFloatAsState(if (on) 2.2f else 1f, spring(dampingRatio = .5f, stiffness = 500f), label = "az")
                            Text(c.toString(), Modifier.graphicsLayer { scaleX = sc; scaleY = sc; translationX = if (on) -28f * density else 0f }, style = mono.copy(fontSize = 10.sp, color = if (on) Color.White else dim))
                        }
                    }
                }
            }
        }
    }
    menu?.let { e -> AppMenu(e) { menu = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerRow(e: Entry, modifier: Modifier, first: Boolean = false, onLong: () -> Unit) {
    val ctx = LocalContext.current; val ui = LocalUi.current
    val icon = if (e.app != null) produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, e.key) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Apps.icon(ctx, e.app) } }.value else null
    val mindful = e.app != null && ui.mindful.contains(e.app.pkg)
    Row(modifier.fillMaxWidth().combinedClickable(onClick = { openEntry(ctx, ui, e) }, onLongClick = onLong).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, null, Modifier.size(30.dp), colorFilter = GREY, alpha = if (mindful) .4f else .9f)
            else Text(e.glyph, fontFamily = Mono, fontSize = 15.sp, color = Color.White)
        }
        Spacer(Modifier.width(18.dp))
        Text(e.label, Modifier.weight(1f), fontFamily = Display, fontWeight = if (first) FontWeight.Normal else FontWeight.Light, fontSize = 24.sp,
            color = if (mindful) Color(0x99FFFFFF) else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.2).sp)
        if (first) Text("↵", fontFamily = Mono, color = Color(0x80FFFFFF), fontSize = 14.sp)
        if (mindful) Text("PAUSE", fontFamily = Mono, color = Color(0x66FFFFFF), fontSize = 9.sp, letterSpacing = 1.2.sp)
    }
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
