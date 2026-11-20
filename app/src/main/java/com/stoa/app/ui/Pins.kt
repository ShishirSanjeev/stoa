package com.stoa.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoa.app.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/* Pins are keys: "app:pkg/cls", "sec:today", "folder:rel/path", "note:rel/path.md". */
private val GREY2 = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedCard() {
    val E = LocalEngine.current; val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current; val haptic = LocalHapticFeedback.current
    var adding by remember { mutableStateOf(false) }
    val all = entries(ui)
    StoaCard("Pinned", action = { IconBtn(Icons.Outlined.Add, "Pin something", { adding = true }, size = 18.dp) }) {
        val bt = rememberBluetoothAudio()
        val spotify = ui.apps.firstOrNull { it.pkg == "com.spotify.music" }
        androidx.compose.animation.AnimatedVisibility(bt && spotify != null, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(), exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()) {
            spotify?.let { sp ->
                val icon = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, sp.pkg) { value = withContext(Dispatchers.IO) { Apps.icon(ctx, sp) } }.value
                Row(Modifier.fillMaxWidth().clickable { Apps.launch(ctx, sp) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { if (icon != null) Image(icon, null, Modifier.size(26.dp), colorFilter = GREY2) }
                    Spacer(Modifier.width(16.dp))
                    Text(sp.label, Modifier.weight(1f), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 22.sp, color = ink.ink)
                    Text("♪ CONNECTED", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.2.sp, color = ink.mute)
                }
            }
        }
        if (ui.pins.isEmpty() && !(bt && spotify != null)) Muted("Pin apps, folders and notes here. Long-press an app in the drawer, or tap + to pin a vault folder or note.", size = 13)
        ui.pins.forEach { k ->
            val entry = if (k.startsWith("app:") || k.startsWith("sec:")) all.find { it.key == k } else null
            if ((k.startsWith("app:") || k.startsWith("sec:")) && entry == null) return@forEach
            val label = entry?.label ?: k.substringAfter(':').substringAfterLast('/').removeSuffix(".md")
            Row(Modifier.fillMaxWidth().combinedClickable(onClick = {
                when {
                    entry != null -> openEntry(ctx, ui, entry)
                    k.startsWith("folder:") -> ui.folder = k.removePrefix("folder:")
                    k.startsWith("note:") -> Apps.openInObsidian(ctx, E.vaultName, k.removePrefix("note:"))
                }
            }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); ui.togglePin(k); E.toast("Unpinned", label, "ach") }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    val app = entry?.app
                    if (app != null) {
                        val icon = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, k) { value = withContext(Dispatchers.IO) { Apps.icon(ctx, app) } }.value
                        if (icon != null) Image(icon, null, Modifier.size(26.dp), colorFilter = GREY2)
                    } else Icon(if (k.startsWith("folder:")) Icons.Outlined.Folder else if (k.startsWith("note:")) Icons.Outlined.Description else Icons.Outlined.PushPin, null, Modifier.size(20.dp), tint = ink.mute)
                }
                Spacer(Modifier.width(16.dp))
                Text(label, Modifier.weight(1f), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 22.sp, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (adding) Sheet("Pin to Home", { adding = false }) {
        var q by remember { mutableStateOf("") }
        Field(q, { q = it }, "Search folders and notes")
        val folders by produceState(emptyList<String>(), E.mdPaths.size) {
            value = withContext(Dispatchers.IO) {
                val r = E.root ?: return@withContext emptyList<String>()
                r.walkTopDown().onEnter { it == r || !it.name.startsWith(".") }.filter { it.isDirectory && it != r }.map { E.relOf(it) }.sorted().toList()
            }
        }
        val fs = folders.filter { q.isBlank() || it.contains(q, true) }.take(if (q.isBlank()) 12 else 30)
        val ns = if (q.length > 1) E.searchNotes(q).take(10) else emptyList()
        Kicker("Folders", Modifier.padding(top = 12.dp, bottom = 2.dp))
        fs.forEach { f -> PinPickRow(Icons.Outlined.Folder, f, ui.pins.contains("folder:$f")) { ui.togglePin("folder:$f") } }
        if (ns.isNotEmpty()) { Kicker("Notes", Modifier.padding(top = 12.dp, bottom = 2.dp)); ns.forEach { n -> PinPickRow(Icons.Outlined.Description, n.removeSuffix(".md"), ui.pins.contains("note:$n")) { ui.togglePin("note:$n") } } }
        Muted("Long-press any app in the drawer and choose “Pin to Home”. Long-press a pin to remove it.", Modifier.padding(top = 12.dp), 12)
    }
}

@Composable
private fun PinPickRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, on: Boolean, toggle: () -> Unit) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = ink.mute); Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f), color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (on) "PINNED" else "PIN", fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.2.sp, color = if (on) ink.ink else ink.faint)
    }
    Divider()
}

/** Browse a pinned vault folder; notes open in Obsidian. */
@Composable
fun FolderBrowser(rel: String) {
    val E = LocalEngine.current; val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current
    val items by produceState(emptyList<File>(), rel) {
        value = withContext(Dispatchers.IO) { E.file(rel).listFiles()?.filter { !it.name.startsWith(".") && (it.isDirectory || it.extension.lowercase() in setOf("md", "canvas", "pdf", "png", "jpg", "jpeg")) }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenByDescending { if (it.isDirectory) 0L else it.lastModified() }.thenBy { it.name.lowercase() }) ?: emptyList() }
    }
    Box(Modifier.fillMaxSize().background(ink.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Outlined.ChevronLeft, "Back", { val parent = rel.substringBeforeLast('/', ""); ui.folder = if (parent.isEmpty() || !ui.pins.any { it.startsWith("folder:") && rel.startsWith(it.removePrefix("folder:") + "/") }) null else parent }, tint = ink.ink)
                Column(Modifier.weight(1f)) { Kicker(rel.substringBeforeLast('/', "Vault")); Text(rel.substringAfterLast('/'), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 26.sp, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                IconBtn(Icons.Outlined.PushPin, if (ui.pins.contains("folder:$rel")) "Unpin" else "Pin", { ui.togglePin("folder:$rel") }, tint = if (ui.pins.contains("folder:$rel")) ink.ink else ink.faint)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
                if (items.isEmpty()) Empty("Nothing here yet.")
                items.forEach { f ->
                    val r = E.relOf(f)
                    Row(Modifier.fillMaxWidth().clickable { if (f.isDirectory) ui.folder = r else if (f.extension == "canvas") { ui.folder = null; ui.canvas = r } else Apps.openInObsidian(ctx, E.vaultName, r) }.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (f.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description, null, Modifier.size(18.dp), tint = ink.mute)
                        Spacer(Modifier.width(14.dp))
                        Text(f.nameWithoutExtension, Modifier.weight(1f), fontSize = 17.sp, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!f.isDirectory) Muted(ago(f.lastModified()), size = 11)
                    }
                    Divider()
                }
            }
        }
    }
}

/** True while headphones, a speaker or a car is connected over Bluetooth (no permission needed). */
@Composable
fun rememberBluetoothAudio(): Boolean {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(android.media.AudioManager::class.java) }
    fun check() = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (android.os.Build.VERSION.SDK_INT >= 31 && (it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER))
    }
    var on by remember { mutableStateOf(check()) }
    androidx.compose.runtime.DisposableEffect(am) {
        val cb = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>?) { on = check() }
            override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>?) { on = check() }
        }
        am.registerAudioDeviceCallback(cb, null)
        onDispose { am.unregisterAudioDeviceCallback(cb) }
    }
    return on
}
