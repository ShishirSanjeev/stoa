package com.stoa.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stoa.app.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** One card. Wraps the original JSON object so fields Stoa doesn't know about survive a save. */
class CNode(val obj: JSONObject) {
    val id: String = obj.optString("id").ifEmpty { hex16() }
    val type: String = obj.optString("type", "text")
    var x by mutableFloatStateOf(obj.optDouble("x", 0.0).toFloat())
    var y by mutableFloatStateOf(obj.optDouble("y", 0.0).toFloat())
    var w by mutableFloatStateOf(obj.optDouble("width", 250.0).toFloat())
    var h by mutableFloatStateOf(obj.optDouble("height", 120.0).toFloat())
    var text by mutableStateOf(obj.optString("text", ""))
    val file: String = obj.optString("file", "")
    fun flush(): JSONObject = obj.put("id", id).put("x", x.toInt()).put("y", y.toInt()).put("width", w.toInt()).put("height", h.toInt()).also { if (type == "text") it.put("text", text) }
}
fun hex16() = (1..16).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")

@Composable
fun CanvasEditor(rel: String) {
    val E = LocalEngine.current; val ui = LocalUi.current; val ink = LocalInk.current; val ctx = LocalContext.current; val dens = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val root = remember(rel) { try { JSONObject(E.readText(rel)?.ifBlank { "{}" } ?: "{}") } catch (e: Exception) { JSONObject() } }
    val nodes = remember(rel) { mutableStateListOf<CNode>().apply { val a = root.optJSONArray("nodes") ?: JSONArray(); for (i in 0 until a.length()) a.optJSONObject(i)?.let { add(CNode(it)) } } }
    val edges = remember(rel) { root.optJSONArray("edges") ?: JSONArray() }
    var sel by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var tx by remember { mutableFloatStateOf(0f) }; var ty by remember { mutableFloatStateOf(0f) }; var k by remember { mutableFloatStateOf(1f) }
    var dirty by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(Offset(1080f, 1800f)) }

    fun json(): String { root.put("nodes", JSONArray(nodes.map { it.flush() })); root.put("edges", edges); return root.toString(2) }
    suspend fun saveNow() = withContext(Dispatchers.IO) { try { E.file(rel).writeText(json()) } catch (e: Exception) { } }
    LaunchedEffect(dirty) { if (dirty > 0) { delay(500); saveNow() } }
    fun close() { scope.launch { saveNow(); ui.canvas = null } }
    fun fit() {
        if (nodes.isEmpty()) { k = 1f; tx = size.x / 2; ty = size.y / 3; return }
        val x0 = nodes.minOf { it.x }; val y0 = nodes.minOf { it.y }; val x1 = nodes.maxOf { it.x + it.w }; val y1 = nodes.maxOf { it.y + it.h }
        k = minOf((size.x - 60) / ((x1 - x0) * dens), (size.y - 60) / ((y1 - y0) * dens)).coerceIn(.15f, 1.4f)
        tx = size.x / 2 - (x0 + x1) / 2 * dens * k; ty = size.y / 2 - (y0 + y1) / 2 * dens * k
    }
    fun centre() = Offset((size.x / 2 - tx) / k / dens, (size.y / 2 - ty) / k / dens)
    fun addText(at: Offset) { val n = CNode(JSONObject().put("type", "text").put("text", "").put("x", (at.x - 130).toInt()).put("y", (at.y - 60).toInt()).put("width", 260).put("height", 120)); nodes.add(n); sel = n.id; editing = n.id; dirty++ }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val node = withContext(Dispatchers.IO) {
                try {
                    val ext = (ctx.contentResolver.getType(uri)?.substringAfter('/') ?: "png").let { if (it == "jpeg") "jpg" else it }
                    val name = "Canvas " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss")) + "." + ext
                    val dest = E.attachments.trim('/') + "/" + name
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    E.file(dest).apply { parentFile?.mkdirs(); writeBytes(bytes) }
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    val c = centre(); val w = 300f; val h = w * o.outHeight.coerceAtLeast(1) / o.outWidth.coerceAtLeast(1)
                    CNode(JSONObject().put("type", "file").put("file", dest).put("x", (c.x - w / 2).toInt()).put("y", (c.y - h / 2).toInt()).put("width", w.toInt()).put("height", h.toInt()))
                } catch (e: Exception) { null }
            }
            if (node != null) { nodes.add(node); sel = node.id; dirty++ } else E.toast("Couldn't add that image")
        }
    }

    Column(Modifier.fillMaxSize().background(ink.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.ChevronLeft, "Back", { close() }, tint = ink.ink)
            Text(rel.substringAfterLast('/').removeSuffix(".canvas"), Modifier.weight(1f), fontFamily = Serif, fontSize = 21.sp, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconBtn(Icons.Outlined.TextFields, "Add text", { addText(centre()) }, tint = ink.ink)
            IconBtn(Icons.Outlined.AddPhotoAlternate, "Add image", { picker.launch("image/*") }, tint = ink.ink)
            IconBtn(Icons.Outlined.Delete, "Delete card", { sel?.let { id -> nodes.removeAll { it.id == id }; sel = null; editing = null; dirty++ } }, tint = if (sel != null) ink.ink else ink.faint)
            IconBtn(Icons.Outlined.ZoomOutMap, "Fit", { fit() }, tint = ink.ink)
            IconBtn(Icons.AutoMirrored.Outlined.OpenInNew, "Open in Obsidian", { scope.launch { saveNow(); Apps.openInObsidian(ctx, E.vaultName, rel) } }, tint = ink.ink)
        }
        BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))
            .pointerInput(Unit) { detectTransformGestures { centroid, pan, zoom, _ ->
                val nk = (k * zoom).coerceIn(.1f, 3f)
                tx = centroid.x - (centroid.x - tx) * nk / k + pan.x; ty = centroid.y - (centroid.y - ty) * nk / k + pan.y; k = nk
            } }
            .pointerInput(Unit) { detectTapGestures(onTap = { sel = null; editing = null }, onDoubleTap = { o -> addText(Offset((o.x - tx) / k / dens, (o.y - ty) / k / dens)) }) }) {
            val W = constraints.maxWidth.toFloat(); val Hh = constraints.maxHeight.toFloat()
            LaunchedEffect(W, Hh) { size = Offset(W, Hh); fit() }
            val dot = ink.line2
            Canvas(Modifier.fillMaxSize()) {
                val step = 22.dp.toPx() * k
                if (step > 6f) {
                    var x = tx % step; while (x < this.size.width) { var y = ty % step; while (y < this.size.height) { drawCircle(dot, 1.2f, Offset(x, y)); y += step }; x += step }
                }
            }
            Box(Modifier.graphicsLayer { translationX = tx; translationY = ty; scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f) }) {
                val byId = nodes.associateBy { it.id }
                Canvas(Modifier.size(1.dp)) {
                    for (i in 0 until edges.length()) { val e = edges.optJSONObject(i) ?: continue
                        val a = byId[e.optString("fromNode")] ?: continue; val b = byId[e.optString("toNode")] ?: continue
                        drawLine(dot, Offset((a.x + a.w / 2) * density, (a.y + a.h / 2) * density), Offset((b.x + b.w / 2) * density, (b.y + b.h / 2) * density), 2.dp.toPx()) }
                }
                nodes.sortedBy { if (it.type == "group") 0 else 1 }.forEach { n -> NodeView(n, sel == n.id, editing == n.id, dens,
                    onSelect = { if (sel == n.id && n.type == "text") editing = n.id else { sel = n.id; if (editing != n.id) editing = null } },
                    onPick = { if (sel != n.id) { sel = n.id; editing = null } }, onMoved = { dirty++ }, onText = { n.text = it; dirty++ }) }
            }
            Text("Double-tap to type · drag cards · pinch to zoom", Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).clip(RoundedCornerShape(50)).background(ink.card).border(1.dp, ink.line, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 12.sp, color = ink.mute)
        }
    }
}

@Composable
fun NodeView(n: CNode, selected: Boolean, editing: Boolean, dens: Float, onSelect: () -> Unit, onPick: () -> Unit, onMoved: () -> Unit, onText: (String) -> Unit) {
    val E = LocalEngine.current; val ink = LocalInk.current
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.offset(n.x.dp, n.y.dp).size(n.w.dp, n.h.dp).clip(shape)
        .background(if (n.type == "group") ink.bg.copy(alpha = 0f) else ink.card)
        .border(if (selected) 2.dp else 1.dp, if (selected) ink.ink else ink.line2, shape)
        .pointerInput(n.id) { detectTapGestures(onTap = { onSelect() }) }
        .pointerInput(n.id) { detectDragGestures(onDragStart = { onPick() }, onDragEnd = onMoved) { ch, d -> ch.consume(); n.x += d.x / dens; n.y += d.y / dens } }) {
        when (n.type) {
            "text" -> if (editing) {
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) { fr.requestFocus() }
                BasicTextField(n.text, onText, Modifier.fillMaxSize().padding(12.dp).focusRequester(fr), textStyle = TextStyle(color = ink.ink, fontSize = 16.sp, lineHeight = 22.sp), cursorBrush = SolidColor(ink.ink))
            } else Text(n.text, Modifier.padding(12.dp), color = ink.ink, fontSize = 16.sp, lineHeight = 22.sp, overflow = TextOverflow.Clip)
            "file" -> {
                val img = n.file.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "avif", "heic")
                if (img) AsyncImage(E.file(n.file), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("📄 " + n.file.substringAfterLast('/'), Modifier.padding(12.dp), color = ink.ink)
            }
            "link" -> Text("🔗 " + n.obj.optString("url"), Modifier.padding(12.dp), color = ink.ink, fontSize = 13.sp)
            "group" -> Text(n.obj.optString("label").uppercase(), Modifier.padding(10.dp), color = ink.mute, fontSize = 11.sp, letterSpacing = 1.4.sp)
        }
        if (selected) Box(Modifier.align(Alignment.BottomEnd).size(30.dp)
            .pointerInput(n.id) { detectDragGestures(onDragEnd = onMoved) { ch, d -> ch.consume(); n.w = (n.w + d.x / dens).coerceAtLeast(80f); n.h = (n.h + d.y / dens).coerceAtLeast(50f) } }) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) { drawLine(ink.ink, Offset(size.width, 0f), Offset(0f, size.height), 2.dp.toPx()); drawLine(ink.ink, Offset(size.width, size.height * .5f), Offset(size.width * .5f, size.height), 2.dp.toPx()) }
        }
    }
}
