package com.stoa.app.ui

import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import androidx.compose.ui.draw.blur
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
    return secs + ui.apps.filter { !ui.hidden.contains(it.pkg) }.map { val k = "app:${it.pkg}/${it.cls}"; val l = ui.renames[k] ?: it.label; Entry(k, l, l.take(1).uppercase(), it) }
}

/** Section shortcuts in your order, each with whether it's shown. */
fun shortcuts(ui: UiState): List<Pair<Entry, Boolean>> {
    val secs = entries(ui).filter { it.app == null }
    val byKey = secs.associateBy { it.key }
    val out = ArrayList<Pair<Entry, Boolean>>()
    ui.shortcutPrefs.forEach { raw -> val k = raw.removePrefix("!"); byKey[k]?.let { e -> if (out.none { it.first.key == k }) out.add(e to !raw.startsWith("!")) } }
    secs.forEach { e -> if (out.none { it.first.key == e.key }) out.add(e to true) }
    return out
}

@Composable
fun ShortcutEditor() {
    val ui = LocalUi.current; val ink = LocalInk.current
    if (!ui.editShortcuts) return
    val list = shortcuts(ui)
    fun save(l: List<Pair<Entry, Boolean>>) = ui.saveShortcuts(l.map { (if (it.second) "" else "!") + it.first.key })
    Sheet("Shortcuts", { ui.editShortcuts = false }) {
        Muted("Shown in the drawer and under the dock, in this order.", Modifier.padding(bottom = 8.dp), 12)
        list.forEachIndexed { i, (e, on) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.label, Modifier.weight(1f), color = if (on) ink.ink else ink.faint, fontSize = 16.sp)
                IconBtn(Icons.Outlined.KeyboardArrowUp, "Up", { if (i > 0) save(list.toMutableList().apply { add(i - 1, removeAt(i)) }) }, tint = if (i > 0) ink.ink else ink.faint, size = 20.dp)
                IconBtn(Icons.Outlined.KeyboardArrowDown, "Down", { if (i < list.size - 1) save(list.toMutableList().apply { add(i + 1, removeAt(i)) }) }, tint = if (i < list.size - 1) ink.ink else ink.faint, size = 20.dp)
                Spacer(Modifier.width(6.dp))
                Toggle(on) { v -> save(list.toMutableList().also { it[i] = e to v }) }
            }
        }
    }
}

fun openEntry(ctx: Context, ui: UiState, e: Entry) {
    val a = e.app
    if (a == null) { ui.go(e.key.removePrefix("sec:")); return }
    val prefs = ui.prefs
    ui.noteLaunch(e.key)
    if (ui.mindful.contains(a.pkg) && prefs.passUntil(a.pkg) < System.currentTimeMillis() && !com.stoa.app.Watch.running) { ui.pauseFor = a; return }
    Apps.launch(ctx, a)
}

/** Pages sit side by side: the app drawer (hidden, to the left of Home), then your tabs. */
@Composable
fun Root() {
    val ui = LocalUi.current; val ink = LocalInk.current; val E = LocalEngine.current; val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tabs = ui.layout.on(ui.layout.tabs).ifEmpty { listOf("home") }
    val pages = listOf("drawer") + tabs
    if (!tabs.contains(ui.tab) && ui.tab != "drawer") ui.tab = tabs.first()
    val pager = rememberPagerState(initialPage = pages.indexOf("home").coerceAtLeast(1)) { pages.size }
    // keep ui.tab and the pager in step both ways
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { i -> pages.getOrNull(i)?.let { if (ui.tab != it) ui.tab = it } } }
    LaunchedEffect(ui.tab, pages) { val i = pages.indexOf(ui.tab); if (i >= 0 && i != pager.currentPage && !pager.isScrollInProgress) pager.animateScrollToPage(i, animationSpec = spring(dampingRatio = .9f, stiffness = 280f)) }
    LaunchedEffect(ui.homeTick) { if (ui.homeTick > 0) { ui.tab = "home"; pager.animateScrollToPage(pages.indexOf("home").coerceAtLeast(0)) } }
    LaunchedEffect(ui.drawerReq) { if (ui.drawerReq > 0) { ui.tab = "drawer"; pager.animateScrollToPage(0) } }
    BackHandler(true) {
        when {
            ui.folder != null -> ui.folder = null
            ui.reader != null -> ui.reader = null
            ui.canvas != null -> ui.canvas = null
            ui.viewer != null -> ui.viewer = null
            ui.pauseFor != null -> ui.pauseFor = null
            ui.settings -> ui.settings = false
            ui.tab != "home" -> ui.tab = "home"
        }
    }
    // pull down on Home → notification shade
    val homeNest = remember {
        object : NestedScrollConnection {
            var pulled = 0f; var fired = false
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) { pulled += available.y; if (!fired && pulled > 140f) { fired = true; Apps.expandNotifications(ctx) } }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity { pulled = 0f; fired = false; return Velocity.Zero }
        }
    }
    Box(Modifier.fillMaxSize().background(ink.bg)) {
        if (ui.wpOn) WallpaperLayer()
        // the drawer darkens the same wallpaper as you swipe toward it, instead of sliding a black page in
        Box(Modifier.fillMaxSize().drawBehind {
            val f = (1f - (pager.currentPage + pager.currentPageOffsetFraction)).coerceIn(0f, 1f)
            if (f > 0f) drawRect(Color.Black, alpha = .82f * f)
        })
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(pager.targetPage != 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) { BottomNav(tabs, pager) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(if (pager.targetPage != 0) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                val half = constraints.maxHeight / 2f
                HorizontalPager(pager, Modifier.fillMaxSize().swipeZones(pager, half, ui), userScrollEnabled = false, beyondViewportPageCount = 1, key = { pages[it] }) { i ->
                    val p = pages[i]
                    // a gentle depth effect while paging
                    val off = (pager.currentPage - i) + pager.currentPageOffsetFraction
                    Box(Modifier.fillMaxSize().graphicsLayer { val f = kotlin.math.abs(off).coerceIn(0f, 1f); alpha = if (p == "drawer") 1f - f else 1f - f * .35f; scaleX = 1f - f * .04f; scaleY = 1f - f * .04f }) {
                        when (p) {
                            "drawer" -> Box(Modifier.fillMaxSize()) { Drawer(pager.targetPage == 0) { ui.tab = "home" } }
                            "today" -> TodayPage()
                            "almanac" -> AlmanacPage()
                            "notes" -> NotesPage()
                            else -> HomePage(homeNest)
                        }
                    }
                }
            }
        }
        ui.folder?.let { FolderBrowser(it) }
        ui.reader?.let { ClipReader(it) }
        ui.capture?.let { CaptureSheet(it) }
        ui.canvas?.let { CanvasEditor(it) }
        ui.viewer?.let { ShotViewer(it) }
        if (ui.settings) SettingsScreen()
        ui.pauseFor?.let { a ->
            Box(Modifier.fillMaxSize().background(ink.bg)) {
                PauseScreen(a.label, ui.prefs.resisted(LocalDate.now().toString()),
                    onBack = { ui.prefs.addResisted(LocalDate.now().toString()); ui.pauseFor = null; E.toast("Good call.", "Urge resisted", "ach") },
                    onOpen = { m -> ui.prefs.grantPass(a.pkg, m); ui.pauseFor = null; Apps.launch(ctx, a) })
            }
        }
        ShortcutEditor()
        TimerWatcher()
        ToastHost(E)
    }
}

/**
 * Horizontal swipes: on the top half they flip a page's sub-pages (Today, Notes), on the bottom half they move
 * between pages. The pager follows your finger and settles with a spring.
 */
fun Modifier.swipeZones(main: PagerState, half: Float, ui: UiState): Modifier = composed {
    val scope = rememberCoroutineScope()
    pointerInput(main, half) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val sub = if (down.position.y < half) ui.subPagers[ui.tab] else null
            val target = sub ?: main
            var over = 0f
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { ch, o -> ch.consume(); over = o } ?: return@awaitEachGesture
            val vt = VelocityTracker(); vt.addPosition(drag.uptimeMillis, drag.position)
            target.dispatchRawDelta(-over)
            horizontalDrag(drag.id) { ch -> vt.addPosition(ch.uptimeMillis, ch.position); target.dispatchRawDelta(-ch.positionChange().x); ch.consume() }
            val v = vt.calculateVelocity().x
            val pos = target.currentPage + target.currentPageOffsetFraction
            val to = (when { v < -500f -> kotlin.math.floor(pos).toInt() + 1; v > 500f -> kotlin.math.ceil(pos).toInt() - 1; else -> kotlin.math.round(pos).toInt() }).coerceIn(0, target.pageCount - 1)
            scope.launch { target.animateScrollToPage(to, animationSpec = spring(dampingRatio = .86f, stiffness = 300f)) }
        }
    }
}

/** The tab dot follows the pager directly (no waiting for the page to settle). */
@Composable
fun BottomNav(tabs: List<String>, pager: PagerState) {
    val ink = LocalInk.current; val scope = rememberCoroutineScope()
    val icons = mapOf("home" to Icons.Outlined.Home, "today" to Icons.Outlined.WbSunny, "almanac" to Icons.Outlined.Insights, "notes" to Icons.Outlined.EditNote, "gym" to Icons.Outlined.FitnessCenter)
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)) {
            val wPx = constraints.maxWidth.toFloat() / tabs.size
            val dotPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }
            Row(Modifier.fillMaxWidth().height(50.dp)) {
                tabs.forEachIndexed { i, t ->
                    val pos = (pager.currentPage + pager.currentPageOffsetFraction - 1f)
                    val near = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                    val c = androidx.compose.ui.graphics.lerp(ink.faint, ink.ink, near)
                    Column(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { scope.launch { pager.animateScrollToPage(i + 1, animationSpec = spring(dampingRatio = .9f, stiffness = 320f)) } },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(icons[t] ?: Icons.Outlined.Home, null, Modifier.size(20.dp), tint = c)
                        Spacer(Modifier.height(4.dp))
                        Text((SECTIONS[t] ?: t).uppercase(), fontSize = 9.sp, letterSpacing = 1.3.sp, fontFamily = Mono, color = c)
                    }
                }
            }
            Box(Modifier.offset { IntOffset(((pager.currentPage + pager.currentPageOffsetFraction - 1f).coerceIn(0f, tabs.size - 1f) * wPx + wPx / 2 - dotPx / 2).roundToInt(), (46 * density).roundToInt()) }
                .size(4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ink.ink))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.line))
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
fun Drawer(open: Boolean, close: () -> Unit) {
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
    val secs = shortcuts(ui).filter { it.second }.map { it.first }
    val results = remember(q, all) { if (q.isBlank()) emptyList() else all.mapNotNull { e -> matchScore(e.label, q)?.let { e to it } }.sortedWith(compareBy<Pair<Entry, Int>> { it.second }.thenBy { it.first.label.length }).map { it.first } }
    val notes = remember(q, E.mdPaths) { if (q.trim().length > 1) E.searchNotes(q.trim()).take(6) else emptyList() }
    fun launch(e: Entry) { if (q.isNotBlank() && e.app != null) ui.noteSearched(e.key); openEntry(ctx, ui, e); if (e.app != null) close() }
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val recentE = remember(open, ui.recent, byKey) { ui.recent.mapNotNull { byKey[it] }.take(2) }
    val topE = remember(open, ui.searched, byKey, recentE) { ui.searched.entries.filter { it.value > 0 }.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }.filter { it !in recentE }.take(4) }
    val head = (if (recentE.isNotEmpty()) 1 + recentE.size else 0) + (if (topE.isNotEmpty()) 1 + topE.size else 0) + (if (recentE.isNotEmpty() || topE.isNotEmpty()) 1 else 0)
    fun go() { results.firstOrNull()?.let { launch(it) } ?: notes.firstOrNull()?.let { close(); Apps.openInObsidian(ctx, E.vaultName, it) } }
    val list = rememberLazyListState()
    val dim = Color(0x80FFFFFF); val mono = TextStyle(fontFamily = Mono, color = dim, fontSize = 11.sp, letterSpacing = 1.4.sp)
    Column(Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues()).imePadding().padding(horizontal = 22.dp)) {
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
            secs.forEach { s -> Text(s.label.uppercase(), Modifier.combinedClickable(onClick = { launch(s) }, onLongClick = { ui.editShortcuts = true }).padding(vertical = 6.dp), style = mono.copy(color = Color(0xCCFFFFFF))) }
            Text("SETTINGS", Modifier.clickable { ui.settings = true }.padding(vertical = 6.dp), style = mono)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = if (q.isBlank()) 22.dp else 0.dp), state = list, contentPadding = PaddingValues(bottom = 30.dp)) {
                if (q.isBlank()) {
                    if (recentE.isNotEmpty()) { item(key = "h-recent") { Text("RECENT", Modifier.padding(top = 4.dp, bottom = 2.dp), style = mono) }
                        items(recentE, key = { "rc" + it.key }) { e -> DrawerRow(e, Modifier.animateItem()) { menu = e } } }
                    if (topE.isNotEmpty()) { item(key = "h-top") { Text("MOST SEARCHED", Modifier.padding(top = 12.dp, bottom = 2.dp), style = mono) }
                        items(topE, key = { "ms" + it.key }) { e -> DrawerRow(e, Modifier.animateItem()) { menu = e } } }
                    if (recentE.isNotEmpty() || topE.isNotEmpty()) item(key = "h-all") { Text("ALL APPS", Modifier.padding(top = 12.dp, bottom = 2.dp), style = mono) }
                    items(apps, key = { it.key }) { e -> DrawerRow(e, Modifier.animateItem()) { menu = e } }
                }
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
                val letters = remember(apps, head) { (if (head > 0) listOf('★') else emptyList()) + ('A'..'Z').toList() + '#' }
                var active by remember { mutableStateOf<Char?>(null) }
                val scope = rememberCoroutineScope()
                fun jump(c: Char) {
                    if (c == active) return; active = c; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (c == '★') { scope.launch { list.scrollToItem(0) }; return }
                    fun l0(e: Entry) = e.label.firstOrNull()?.uppercaseChar()?.takeIf { x -> x in 'A'..'Z' } ?: '#'
                    val idx = if (c == '#') apps.indexOfFirst { l0(it) == '#' } else apps.indexOfFirst { l0(it) != '#' && l0(it) >= c }.let { if (it < 0) apps.size - 1 else it }
                    if (idx >= 0) scope.launch { list.scrollToItem(idx + head) }
                }
                BoxWithConstraints(Modifier.align(Alignment.CenterEnd).offset(x = 12.dp).width(30.dp).fillMaxHeight()) {
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
    val mindful = e.app != null && ui.mindful.contains(e.app.pkg)
    Row(modifier.fillMaxWidth().combinedClickable(onClick = { openEntry(ctx, ui, e) }, onLongClick = onLong).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(e.label, Modifier.weight(1f), fontFamily = Display, fontWeight = if (first) FontWeight.Normal else FontWeight.ExtraLight, fontSize = 27.sp,
            color = if (mindful) Color(0x99FFFFFF) else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.3).sp)
        if (first) Text("↵", fontFamily = Mono, color = Color(0x80FFFFFF), fontSize = 14.sp)
        if (mindful) Text("PAUSE", fontFamily = Mono, color = Color(0x66FFFFFF), fontSize = 9.sp, letterSpacing = 1.2.sp)
        if (e.app == null) Text("STOA", fontFamily = Mono, color = Color(0x55FFFFFF), fontSize = 9.sp, letterSpacing = 1.2.sp)
    }
}

@Composable
fun AppMenu(e: Entry, dismiss: () -> Unit) {
    val ui = LocalUi.current; val ctx = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    if (renaming) { AskDialog("Rename ${e.app?.label ?: e.label}", listOf("Name (blank = original)" to e.label), "Save", { renaming = false; dismiss() }) { v -> ui.rename(e.key, v[0]); renaming = false; dismiss() }; return }
    Sheet(e.label, dismiss) {
        val docked = ui.dockHas(e.key)
        MenuItem(if (docked) "Remove from dock" else "Add to dock") { if (docked) ui.dockRemove(e.key) else ui.dockAdd(e.key); dismiss() }
        ui.dock.filter { it.isFolder }.forEach { f -> if (!f.apps.contains(e.key)) MenuItem("Add to folder “${f.name}”") { ui.dockAddToFolder(f.id, e.key); dismiss() } }
        MenuItem("Rename") { renaming = true }
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

/** Your home-screen wallpaper behind everything, blurred and dimmed to taste (Settings → Wallpaper). */
@Composable
fun WallpaperLayer() {
    val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, ui.resume) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                android.app.WallpaperManager.getInstance(ctx).drawable?.let { d ->
                    val iw = d.intrinsicWidth.takeIf { it > 0 } ?: 1080; val ih = d.intrinsicHeight.takeIf { it > 0 } ?: 2400
                    val w = minOf(iw, 1080)
                    d.toBitmap(w, (w.toLong() * ih / iw).toInt().coerceAtLeast(1)).asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }
    LaunchedEffect(bmp) { ui.wpBmp = bmp }
    val blur by animateDpAsState(ui.wpBlur.dp, label = "blur")
    val dim by animateFloatAsState(ui.wpDim / 100f, label = "dim")
    Box(Modifier.fillMaxSize()) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize().blur(blur, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(ink.bg.copy(alpha = if (bmp == null) 1f else dim)))
    }
}
