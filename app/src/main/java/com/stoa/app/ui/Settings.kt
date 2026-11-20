package com.stoa.app.ui

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoa.app.Apps
import java.io.File

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val ink = LocalInk.current
    val bg by animateColorAsState(if (on) ink.ink else ink.line2, label = "sw")
    val x by animateDpAsState(if (on) 20.dp else 2.dp, spring(dampingRatio = .55f, stiffness = 600f), label = "swx")
    Box(Modifier.width(46.dp).height(28.dp).clip(CircleShape).background(bg).clickable { onChange(!on) }) {
        Box(Modifier.offset(x = x, y = 2.dp).size(24.dp).clip(CircleShape).background(if (on && ink.dark) Color.Black else Color.White))
    }
}

fun treeToPath(uri: Uri): String? = try {
    val id = DocumentsContract.getTreeDocumentId(uri)
    val vol = id.substringBefore(':'); val rel = id.substringAfter(':', "")
    val base = if (vol.equals("primary", true)) Environment.getExternalStorageDirectory().absolutePath else "/storage/$vol"
    if (rel.isEmpty()) base else "$base/$rel"
} catch (e: Exception) { null }

fun findVaults(): List<File> {
    val roots = listOf(Environment.getExternalStorageDirectory(), File(Environment.getExternalStorageDirectory(), "Documents"), File(Environment.getExternalStorageDirectory(), "Obsidian"))
    return roots.flatMap { r -> r.listFiles()?.filter { it.isDirectory && File(it, ".obsidian").isDirectory } ?: emptyList() }.distinctBy { it.absolutePath }
}

@Composable
fun SettingsScreen() {
    val E = LocalEngine.current; val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current; val p = ui.prefs
    var path by remember { mutableStateOf(p.vaultPath) }
    var ver by remember { mutableIntStateOf(0) }
    val r = ui.resume + ver
    val notifAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ver++ }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { treeToPath(it) }?.let { path = it; p.vaultPath = it; E.scan(true); ver++ } }
    Box(Modifier.fillMaxSize().background(ink.bg)) {
        Page {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { H1("Settings") }; IconBtn(Icons.Outlined.Close, "Close", { ui.settings = false }, tint = ink.ink) }
            Spacer(Modifier.height(8.dp))
            StoaCard("Set up") {
                val all = remember(r) { Apps.hasAllFiles(ctx) }; val home = remember(r) { Apps.isDefaultHome(ctx) }
                val ovl = remember(r) { Apps.hasOverlay(ctx) }; val notif = remember(r) { Apps.hasNotifications(ctx) }; val use = remember(r) { Apps.hasUsage(ctx) }; val img = remember(r) { hasImages(ctx) }
                SetupRow("1. All-files access", "So Stoa can read and write your vault's notes.", all) { Apps.askAllFiles(ctx) }
                SetupRow("2. Your vault", if (E.root != null) path else "Pick the folder Obsidian syncs your vault into.", E.root != null) { tree.launch(null) }
                if (E.root == null && all) { val found = remember(r) { findVaults() }; found.forEach { f -> Pill("Use ${f.name}", { path = f.absolutePath; p.vaultPath = path; E.scan(true); ver++ }, Modifier.padding(bottom = 6.dp), small = true) } }
                SetupRow("3. Make Stoa your home screen", "Press Home and you land here.", home) { Apps.askHome(ctx) }
                SetupRow("4. Usage access", "For the screen-time graph and to notice when a mindful app opens.", use) { Apps.askUsage(ctx) }
                SetupRow("5. Display over other apps", "Lets the breathing pause appear over Instagram & co. (No Accessibility needed, so GPay and banking apps are happy.)", ovl) { Apps.askOverlay(ctx) }
                SetupRow("7. Notifications", "For task reminders, and the quiet “mindful pause is on” notice.", notif) { if (android.os.Build.VERSION.SDK_INT >= 33) notifAsk.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Mindful pause", color = ink.ink, fontWeight = FontWeight.Medium); Muted(if (com.stoa.app.Watch.running) "On — watching for the apps you chose." else if (use && ovl) "Off" else "Needs steps 4 and 5.", size = 12) }
                    Toggle(p.bool("watch_on", true) && use && ovl) { v -> p.put("watch_on", v); com.stoa.app.Watch.sync(ctx); ver++ }
                }
                Divider()
                SetupRow("6. Photos", "For Notes → Screenshots. You'll be asked the first time you open it.", img) { ui.settings = false; ui.go("shots") }
                if (E.error != null) Muted(E.error ?: "", Modifier.padding(top = 8.dp), 13)
                else if (E.ready) Muted("Reading ${E.mdPaths.size} notes from ${E.vaultName}.", Modifier.padding(top = 8.dp), 12)
            }
            HomeSettings()
            WallpaperSettings()
            DockSettings()
            CrashCard()
            LayoutEditor()
            MindfulEditor()
            StoaCard("Notes & folders") {
                Muted("Paths are relative to the vault.", size = 12)
                listOf("bookNote" to ("Book note" to E.bookNote), "writingFolder" to ("Writing folder" to E.writingFolder), "writingPrefix" to ("Writing prefix" to E.writingPrefix),
                    "captureFolder" to ("Quick capture folder" to E.capture), "clippingsFolder" to ("Web clippings folder" to E.clippings), "inboxPath" to ("Inbox note (/task)" to E.inbox),
                    "canvasFolder" to ("Canvas folder" to E.canvasFolder), "attachmentsFolder" to ("Attachments folder" to E.attachments), "workoutsFolder" to ("Workouts folder" to E.workoutsFolder), "dashboardState" to ("Dashboard state note" to E.dashState),
                    "habitHeading" to ("Habits heading" to E.habitHeading), "priorityHeading" to ("Today's three heading" to E.priorityHeading), "workoutHeading" to ("Workout heading" to E.workoutHeading),
                    "gymHabit" to ("Habit ticked after a workout" to p.str("gymHabit", "Gym")), "readingHabit" to ("…after reading" to p.str("readingHabit", "Reading")),
                    "meditateHabit" to ("…after stillness" to p.str("meditateHabit", "Meditate")), "writingHabit" to ("…after uploading writing" to p.str("writingHabit", "Writing"))
                ).forEach { (key, lv) ->
                    var v by remember { mutableStateOf(lv.second) }
                    Kicker(lv.first, Modifier.padding(top = 12.dp, bottom = 2.dp))
                    Field(v, { v = it; p.put(key, it) }, lv.first)
                }
                Pill("Re-read the vault", { E.scan(true) }, Modifier.padding(top = 14.dp), small = true)
            }
        }
    }
}

@Composable
fun SetupRow(title: String, sub: String, ok: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = ink.ink, fontWeight = FontWeight.Medium); Muted(sub, size = 12) }
        Text(if (ok) "✓" else "Set up", color = if (ok) ink.ink else ink.warn, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
    }
    Divider()
}

@Composable
fun LayoutEditor() {
    val ui = LocalUi.current
    val L = ui.layout
    StoaCard("Layout") {
        Muted("Show, hide and re-order what Stoa shows. Long-press any app in the drawer to pin it to Home.", size = 12)
        LayoutGroup("Tabs", L.tabs, SECTIONS) { ui.saveLayout(ui.layout.copy(tabs = it)) }
        LayoutGroup("Home widgets", L.home, HOME_WIDGETS) { ui.saveLayout(ui.layout.copy(home = it)) }
        LayoutGroup("Today tab", L.today, SECTIONS + ("today" to "Today")) { ui.saveLayout(ui.layout.copy(today = it)) }
        LayoutGroup("Today · sections", L.todaySections, TODAY_SECTIONS) { ui.saveLayout(ui.layout.copy(todaySections = it)) }
        LayoutGroup("Notes tab", L.notes, SECTIONS) { ui.saveLayout(ui.layout.copy(notes = it)) }
        LayoutGroup("Almanac", L.almanac, ALMANAC_WIDGETS) { ui.saveLayout(ui.layout.copy(almanac = it)) }
    }
}

@Composable
fun MindfulEditor() {
    val ui = LocalUi.current; val ink = LocalInk.current
    var q by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    StoaCard("Pause before these apps") {
        Muted("A breathing screen shows first. Choosing “Open anyway” lets the app through for 10 minutes.", size = 12)
        Spacer(Modifier.height(8.dp))
        Field(q, { q = it }, "Search apps")
        val list = ui.apps.filter { q.isBlank() || it.label.contains(q, true) }.sortedBy { !ui.mindful.contains(it.pkg) }
        (if (showAll || q.isNotBlank()) list else list.take(12)).forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, Modifier.weight(1f), color = ink.ink)
                if (ui.hidden.contains(a.pkg)) Pill("Unhide", { ui.updateHidden(ui.hidden - a.pkg) }, small = true)
                Spacer(Modifier.width(8.dp))
                Toggle(ui.mindful.contains(a.pkg)) { v -> ui.updateMindful(if (v) ui.mindful + a.pkg else ui.mindful - a.pkg) }
            }
        }
        if (!showAll && q.isBlank() && list.size > 12) Pill("Show all ${list.size} apps", { showAll = true }, Modifier.padding(top = 6.dp), small = true)
        if (ui.hidden.isNotEmpty()) {
            Kicker("Hidden from the drawer", Modifier.padding(top = 14.dp))
            ui.apps.filter { ui.hidden.contains(it.pkg) }.forEach { a -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, Modifier.weight(1f), color = ink.ink); Pill("Unhide", { ui.updateHidden(ui.hidden - a.pkg) }, small = true) } }
        }
    }
}

@Composable
private fun LayoutGroup(title: String, list: List<Pair<String, Boolean>>, labels: Map<String, String>, save: (List<Pair<String, Boolean>>) -> Unit) {
    Kicker(title, Modifier.padding(top = 16.dp, bottom = 2.dp))
    list.forEachIndexed { i, (k, on) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(labels[k] ?: k, Modifier.weight(1f), color = LocalInk.current.ink)
            if (i > 0) IconBtn(Icons.Outlined.ArrowUpward, "Move up", { val m = list.toMutableList(); val x = m.removeAt(i); m.add(i - 1, x); save(m) }, size = 17.dp)
            Toggle(on) { v -> if (title == "Tabs" && !v && list.count { it.second } <= 1) return@Toggle; save(list.toMutableList().also { it[i] = k to v }) }
        }
    }
}

@Composable
fun WallpaperSettings() {
    val ui = LocalUi.current; val ink = LocalInk.current
    val colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = ink.ink, activeTrackColor = ink.ink, inactiveTrackColor = ink.line2, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
    StoaCard("Wallpaper") {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Show my wallpaper", color = ink.ink); Muted("Your home-screen wallpaper, softened behind Stoa.", size = 12) }
            Toggle(ui.wpOn) { ui.setWallpaper(on = it) }
        }
        if (ui.wpOn) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) { Kicker("Blur", Modifier.weight(1f)); Kicker("${ui.wpBlur}") }
            androidx.compose.material3.Slider(ui.wpBlur.toFloat(), { ui.setWallpaper(blur = it.toInt()) }, valueRange = 0f..60f, colors = colors)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Kicker("Dim", Modifier.weight(1f)); Kicker("${ui.wpDim}%") }
            androidx.compose.material3.Slider(ui.wpDim.toFloat(), { ui.setWallpaper(dim = it.toInt()) }, valueRange = 0f..95f, colors = colors)
            Muted("Blur needs Android 12 or newer. If the wallpaper doesn't show, make sure All-files access is on.", size = 11)
        }
    }
}

@Composable
fun HomeSettings() {
    val ui = LocalUi.current; val p = ui.prefs
    StoaCard("Home") {
        var name by remember { mutableStateOf(ui.homeName) }
        Kicker("Name of your home space", Modifier.padding(top = 4.dp, bottom = 2.dp))
        Field(name, { name = it; ui.homeName = it.ifBlank { "Atrium" }; p.put("home_name", it) }, "Atrium")
        listOf("greet_morning" to ("Morning greeting" to "Good morning"), "greet_afternoon" to ("Afternoon greeting" to "Good afternoon"),
            "greet_evening" to ("Evening greeting" to "Good evening"), "greet_night" to ("Late-night greeting" to "Quiet night")).forEach { (k, lv) ->
            var v by remember { mutableStateOf(p.str(k, lv.second)) }
            Kicker(lv.first, Modifier.padding(top = 12.dp, bottom = 2.dp))
            Field(v, { v = it; p.put(k, it) }, lv.second)
        }
    }
}

@Composable
fun DockSettings() {
    val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current
    val packs = androidx.compose.runtime.produceState(emptyList<com.stoa.app.IconPacks.Pack>()) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.stoa.app.IconPacks.installed(ctx) } }.value
    fun setPack(p: String) = ui.applyPack(p)
    StoaCard("Dock & icons") {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Monochrome icons", color = ink.ink); Muted("Keeps the dock black and white.", size = 12) }
            Toggle(ui.greyIcons) { ui.greyIcons = it; ui.prefs.put("grey_icons", it) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Collapse the dock", color = ink.ink); Muted("Or swipe down on its handle.", size = 12) }
            Toggle(ui.dockCollapsed) { ui.setCollapsed(it) }
        }
        Kicker("Icon pack", Modifier.padding(top = 10.dp, bottom = 4.dp))
        if (packs.isEmpty()) Muted("No icon packs installed. Any pack that supports Nova or ADW (most on the Play Store) will show up here.", size = 12)
        Chips(listOf("") + packs.map { it.pkg }, ui.iconPack, { k -> if (k.isEmpty()) "System" else packs.firstOrNull { it.pkg == k }?.label ?: k }) { setPack(it) }
        Muted("Long-press an app in the drawer → Add to dock. In the dock, long-press and drag to re-order; drop onto another icon to make a folder.", Modifier.padding(top = 8.dp), 11)
    }
}

@Composable
fun CrashCard() {
    val ctx = LocalContext.current
    var log by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ctx.getSharedPreferences("crash", android.content.Context.MODE_PRIVATE).getString("last", "") ?: "") }
    if (log.isBlank()) return
    StoaCard("Last crash") {
        Muted(log.take(700), size = 11)
        Row(Modifier.padding(top = 10.dp)) {
            Pill("Copy", { (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Stoa crash", log)) }, small = true)
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Pill("Clear", { ctx.getSharedPreferences("crash", android.content.Context.MODE_PRIVATE).edit().clear().apply(); log = "" }, small = true)
        }
    }
}
