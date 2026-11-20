package com.stoa.app.ui

import androidx.compose.ui.text.font.FontWeight
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stoa.app.Apps
import com.stoa.app.vault.safeName
import com.stoa.app.vault.taskXP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotesPage() {
    val ui = LocalUi.current
    val subs = ui.layout.on(ui.layout.notes).ifEmpty { listOf("tasks") }
    SubPaged("Notes", null, "notes", subs.map { it to (when (it) { "tasks" -> "Tasks"; "shots" -> "Shots"; else -> SECTIONS[it] ?: it }) }, ui.notesSub, { ui.notesSub = it }) { s ->
        when (s) {
            "ideas" -> Ideas()
            "canvas" -> CanvasList()
            "shots" -> Shots()
            "gym" -> GymView()
            "writing" -> { Writing(); BookTasks(); WritingPieces() }
            else -> TasksPipeline()
        }
    }
}

fun ago(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60000
    return when { m < 1 -> "just now"; m < 60 -> "$m min ago"; m < 1440 -> "${m / 60} h ago"; m < 2880 -> "yesterday"; m < 10080 -> "${m / 1440} days ago"
        else -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM")) }
}

@Composable
fun BookHeader(onClick: () -> Unit) {
    val E = LocalEngine.current; val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(24.dp).height(34.dp).clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 6.dp, bottomEnd = 6.dp)).background(ink.ink))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Kicker("Writing for"); Text(E.bookName, fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 19.sp, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        IconBtn(Icons.AutoMirrored.Outlined.OpenInNew, "Open book note", onClick, size = 17.dp)
    }
}

/* ── writing: write here, upload into the book ── */
@Composable
fun Writing() {
    val E = LocalEngine.current; val ui = LocalUi.current; val p = ui.prefs; val ctx = LocalContext.current; val ink = LocalInk.current; val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(p.str("draft_title", "")) }
    var text by remember { mutableStateOf(p.str("draft_text", "")) }
    var asking by remember { mutableStateOf(false) }
    val words = remember(text) { Regex("\\S+").findAll(text).count() }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) try {
            val t = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            text = t.replace(Regex("^---[\\s\\S]*?\\n---\\s*"), ""); p.put("draft_text", text)
            if (title.isBlank()) { title = (uri.lastPathSegment ?: "").substringAfterLast('/').substringBeforeLast('.'); p.put("draft_title", title) }
        } catch (e: Exception) { E.toast("Couldn't read that file", "", "xp") }
    }
    StoaCard {
        BookHeader { Apps.openInObsidian(ctx, E.vaultName, E.bookNote) }
        Spacer(Modifier.height(6.dp)); Divider()
        Field(title, { title = it; p.put("draft_title", it) }, "Title", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 24.sp), border = false)
        Field(text, { text = it; p.put("draft_text", it) }, "Write. It saves as you go.", singleLine = false, border = false,
            style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 19.sp, lineHeight = 30.sp), modifier = Modifier.heightIn(min = 240.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Muted("$words words", Modifier.weight(1f), 12)
            Pill("Import", { importer.launch("text/*") }, small = true, icon = Icons.Outlined.AttachFile)
            Spacer(Modifier.width(8.dp))
            Pill("Upload", { if (text.isBlank()) E.toast("Nothing to upload", "Write something first") else asking = true }, primary = true, small = true, icon = Icons.Outlined.Upload)
        }
    }
    if (asking) AskDialog("Upload to ${E.bookName}", listOf("Title" to title), "Upload", { asking = false }) { v ->
        val t = v[0].trim(); if (t.isEmpty()) return@AskDialog
        asking = false; val body = text
        scope.launch { E.uploadWriting(t, body); title = ""; text = ""; p.put("draft_title", ""); p.put("draft_text", "") }
    }
}

@Composable
fun WritingPieces() {
    val E = LocalEngine.current; val ctx = LocalContext.current; val ink = LocalInk.current
    val pre = E.writingPrefix
    val pieces = remember(E.mdPaths) { E.mdPaths.filter { it.substringAfterLast('/').startsWith(pre) }.map { it to E.file(it).lastModified() }.sortedByDescending { it.second } }
    StoaCard("Pieces", action = { Muted("${pieces.size} notes", size = 12) }) {
        if (pieces.isEmpty()) Empty("Uploads appear here.")
        pieces.take(12).forEach { (rel, m) ->
            Row(Modifier.fillMaxWidth().clickable { Apps.openInObsidian(ctx, E.vaultName, rel) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rel.substringAfterLast('/').removeSuffix(".md").removePrefix(pre), Modifier.weight(1f), color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(ago(m), size = 12)
            }
        }
    }
}

/* ── tasks for the book (live in the book note) ── */
@Composable
fun BookTasks() {
    val E = LocalEngine.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf("") }
    val xs = E.tasks[E.bookNote] ?: emptyList()
    val open = xs.filter { it.status == "open" }; val done = xs.filter { it.status == "done" }.sortedByDescending { it.completion ?: "" }
    StoaCard(E.bookName, action = { IconBtn(Icons.AutoMirrored.Outlined.OpenInNew, "Open", { Apps.openInObsidian(ctx, E.vaultName, E.bookNote) }, size = 17.dp) }) {
        Field(add, { add = it }, "Add a task for the book…", onDone = { val t = add.trim(); add = ""; if (t.isNotEmpty()) scope.launch { E.addTask(E.bookNote, "## Tasks", t); E.toast("Task added", E.bookName, "ach") } })
        Spacer(Modifier.height(6.dp))
        if (!E.exists(E.bookNote)) Empty("Couldn't find “${E.bookNote}”. Set the book note in Settings.")
        else if (open.isEmpty()) Empty("No open tasks for the book.")
        var sec: String? = null
        open.forEachIndexed { i, t ->
            if (t.section != sec) { sec = t.section; if (t.section.isNotEmpty()) Kicker(t.section, Modifier.padding(top = 10.dp)) }
            CheckRow(false, t.text, { E.setTask(t, it) }, trailing = { XpHint(taskXP(t)) }, divider = i < open.size - 1)
        }
    }
    if (done.isNotEmpty()) StoaCard("Done") { done.take(15).forEachIndexed { i, t -> CheckRow(true, t.text, { E.setTask(t, it) }, divider = i < minOf(done.size, 15) - 1) } }
}

/* ── ideas → Quick Capture folder ── */
@Composable
fun Ideas() {
    val E = LocalEngine.current; val ctx = LocalContext.current; val ink = LocalInk.current; val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var ver by remember { mutableIntStateOf(0) }
    StoaCard("New idea") {
        Field(text, { text = it }, "What just crossed your mind?", singleLine = false, modifier = Modifier.heightIn(min = 90.dp))
        Spacer(Modifier.height(10.dp))
        Pill("Capture", { val s = text.trim(); if (s.isNotEmpty()) { text = ""; scope.launch { E.captureIdea(s); ver++ } } }, primary = true, icon = Icons.Outlined.AutoAwesome)
    }
    val list by produceState(emptyList<Triple<String, Long, String>>(), ver, E.mdPaths.size) {
        value = withContext(Dispatchers.IO) { E.mdIn(E.capture).take(25).map { f -> Triple(E.relOf(f), f.lastModified(), f.readText().replace(Regex("^---[\\s\\S]*?\\n---\\s*"), "").trim().take(160)) } }
    }
    StoaCard("Recent captures", action = { Muted(E.capture.substringAfterLast('/'), size = 12) }) {
        if (list.isEmpty()) Empty("Nothing captured yet.")
        list.forEachIndexed { i, (rel, m, ex) ->
            Column(Modifier.fillMaxWidth().clickable { Apps.openInObsidian(ctx, E.vaultName, rel) }.padding(vertical = 10.dp)) {
                Kicker(ago(m), color = ink.faint); Text(ex.ifBlank { rel.substringAfterLast('/') }, color = ink.ink, fontSize = 14.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (i < list.size - 1) Divider()
        }
    }
}

/* ── canvases (JSON Canvas: they open in Obsidian's Canvas too) ── */
@Composable
fun CanvasList() {
    val E = LocalEngine.current; val ui = LocalUi.current; val ink = LocalInk.current; val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf(false) }
    val files by produceState(emptyList<java.io.File>(), ui.canvas, ui.resume) { value = withContext(Dispatchers.IO) { E.filesUnder(E.canvasFolder, setOf("canvas")) } }
    StoaCard("Canvases", action = { Pill("New", { naming = true }, small = true, icon = Icons.Outlined.Add) }) {
        if (files.isEmpty()) Empty("Make a canvas to sketch ideas with text cards and images.")
        files.forEach { f ->
            Row(Modifier.fillMaxWidth().clickable { ui.canvas = E.relOf(f) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Outlined.Dashboard, null, tint = ink.mute); Spacer(Modifier.width(10.dp))
                Text(f.nameWithoutExtension, Modifier.weight(1f), color = ink.ink); Muted(ago(f.lastModified()), size = 12)
            }
        }
    }
    if (naming) AskDialog("New canvas", listOf("Name" to ""), "Create", { naming = false }) { v ->
        naming = false; val n = safeName(v[0]).ifBlank { "Canvas" }
        scope.launch { val rel = E.create(E.canvasFolder.trim('/') + "/" + n + ".canvas", "{\n\t\"nodes\":[],\n\t\"edges\":[]\n}"); ui.canvas = rel }
    }
}

/* ── screenshots from the phone's gallery ── */
fun queryShots(ctx: Context): List<Uri> {
    val out = ArrayList<Uri>()
    val col = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val (sel, args) = if (Build.VERSION.SDK_INT >= 29) "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%Screenshot%")
        else "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("%Screenshot%")
    try {
        ctx.contentResolver.query(col, arrayOf(MediaStore.Images.Media._ID), sel, args, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext() && out.size < 600) out.add(ContentUris.withAppendedId(col, c.getLong(id)))
        }
    } catch (e: Exception) { }
    return out
}
fun hasImages(ctx: Context) = ctx.checkSelfPermission(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

/** Moves screenshots to the phone's trash (recoverable for 30 days); Android asks once for the whole batch. */
@Composable
fun rememberTrasher(onDone: (List<Uri>) -> Unit): (List<Uri>) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r -> if (r.resultCode == android.app.Activity.RESULT_OK) onDone(pending); pending = emptyList() }
    return { uris ->
        if (uris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= 30) {
                pending = uris
                val pi = MediaStore.createTrashRequest(ctx.contentResolver, uris, true)
                launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())
            } else { uris.forEach { try { ctx.contentResolver.delete(it, null, null) } catch (e: Exception) { } }; onDone(uris) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Shots() {
    val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current; val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var granted by remember { mutableStateOf(hasImages(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var n by remember { mutableIntStateOf(60) }
    var sel by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    val trash = rememberTrasher { gone -> ui.shots = ui.shots - gone.toSet(); sel = emptySet() }
    LaunchedEffect(granted, ui.resume) { if (granted) ui.shots = withContext(Dispatchers.IO) { queryShots(ctx) } }
    if (!granted) { StoaCard("Screenshots") { Muted("Let Stoa show the screenshots on your phone.", size = 14); Spacer(Modifier.height(10.dp))
        Pill("Allow photos access", { ask.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) }, primary = true) }; return }
    val selecting = sel.isNotEmpty()
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!selecting) Muted("${ui.shots.size} screenshots · hold to select", Modifier.weight(1f), 12)
        else {
            Text("${sel.size} selected", Modifier.weight(1f), color = ink.ink, fontSize = 15.sp)
            Pill("All", { sel = ui.shots.take(n).toSet() }, small = true); Spacer(Modifier.width(6.dp))
            Pill("Delete", { trash(sel.toList()) }, small = true, warn = true, icon = Icons.Outlined.Delete); Spacer(Modifier.width(2.dp))
            IconBtn(Icons.Outlined.Close, "Cancel", { sel = emptySet() }, size = 18.dp)
        }
    }
    ui.shots.take(n).chunked(3).forEachIndexed { r, row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEachIndexed { c, uri ->
                val on = sel.contains(uri)
                val sc by androidx.compose.animation.core.animateFloatAsState(if (on) .88f else 1f, androidx.compose.animation.core.spring(dampingRatio = .6f), label = "shot")
                Box(Modifier.weight(1f).aspectRatio(9f / 16f).graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(10.dp)).background(ink.line)
                    .combinedClickable(onClick = { if (selecting) sel = if (on) sel - uri else sel + uri else ui.viewer = r * 3 + c },
                        onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); sel = if (on) sel - uri else sel + uri })) {
                    AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (selecting) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (on) Color.White else Color(0x55000000)).border(1.5.dp, Color.White, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        if (on) androidx.compose.material3.Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = Color.Black)
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (ui.shots.size > n) Pill("Show more", { n += 60 }, Modifier.fillMaxWidth().padding(top = 10.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShotViewer(start: Int) {
    val ui = LocalUi.current; val ctx = LocalContext.current
    val shots = ui.shots
    if (shots.isEmpty()) { ui.viewer = null; return }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, shots.size - 1)) { shots.size }
    val trash = rememberTrasher { gone -> ui.shots = ui.shots - gone.toSet(); if (ui.shots.isEmpty()) ui.viewer = null }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i -> AsyncImage(shots[i], null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.Close, "Close", { ui.viewer = null }, tint = Color.White)
            Text("${pager.currentPage + 1} / ${shots.size}", Modifier.weight(1f), color = Color(0xB3FFFFFF), fontSize = 13.sp)
            IconBtn(Icons.Outlined.Share, "Share", {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, shots[pager.currentPage]).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, tint = Color.White)
            IconBtn(Icons.Outlined.Delete, "Delete", { trash(listOf(shots[pager.currentPage])) }, tint = Color.White)
            IconBtn(Icons.Outlined.Description, "Open", {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(shots[pager.currentPage], "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            }, tint = Color.White)
        }
    }
}
