@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.stoa.app.ui

import androidx.compose.ui.text.font.FontWeight
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.withStyle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
    var color by mutableStateOf(obj.optString("color", ""))
    fun flush(): JSONObject = obj.put("id", id).put("x", x.toInt()).put("y", y.toInt()).put("width", w.toInt()).put("height", h.toInt()).also {
        if (type == "text") it.put("text", text)
        if (color.isNotEmpty()) it.put("color", color) else it.remove("color")
    }
    fun contains(p: Offset) = p.x >= x && p.x <= x + w && p.y >= y && p.y <= y + h
}

/** Obsidian's six canvas colours, plus hex. */
fun canvasColor(c: String): Color? = when (c) {
    "1" -> Color(0xFFFB464C); "2" -> Color(0xFFE9973F); "3" -> Color(0xFFE0DE71); "4" -> Color(0xFF44CF6E); "5" -> Color(0xFF53DFDD); "6" -> Color(0xFFA882FF)
    "" -> null
    else -> try { Color(android.graphics.Color.parseColor(c)) } catch (e: Exception) { null }
}
private fun side(a: CNode, b: CNode): String {
    val dx = (b.x + b.w / 2) - (a.x + a.w / 2); val dy = (b.y + b.h / 2) - (a.y + a.h / 2)
    return if (kotlin.math.abs(dx) * .8f > kotlin.math.abs(dy)) (if (dx > 0) "right" else "left") else (if (dy > 0) "bottom" else "top")
}
private fun anchor(n: CNode, side: String) = when (side) {
    "left" -> Offset(n.x, n.y + n.h / 2); "right" -> Offset(n.x + n.w, n.y + n.h / 2); "top" -> Offset(n.x + n.w / 2, n.y); else -> Offset(n.x + n.w / 2, n.y + n.h)
}
fun hex16() = (1..16).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")

@Composable
fun CanvasEditor(rel: String) {
    val E = LocalEngine.current; val ui = LocalUi.current; val ink = LocalInk.current; val ctx = LocalContext.current; val dens = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val root = remember(rel) { try { JSONObject(E.readText(rel)?.ifBlank { "{}" } ?: "{}") } catch (e: Exception) { JSONObject() } }
    val nodes = remember(rel) { mutableStateListOf<CNode>().apply { val a = root.optJSONArray("nodes") ?: JSONArray(); for (i in 0 until a.length()) a.optJSONObject(i)?.let { add(CNode(it)) } } }
    val edges = remember(rel) { mutableStateListOf<JSONObject>().apply { val a = root.optJSONArray("edges") ?: JSONArray(); for (i in 0 until a.length()) a.optJSONObject(i)?.let { add(it) } } }
    var linking by remember { mutableStateOf<String?>(null) }   // tap ○, then tap the card to connect
    // the new text-field state API: the old TextFieldValue field crashes inside the zoomed canvas (Compose IME bug)
    val tfs = remember { androidx.compose.foundation.text.input.TextFieldState() }
    fun startEdit(n: CNode) { tfs.setTextAndPlaceCursorAtEnd(n.text); editing = n.id }
    var sel by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var tx by remember { mutableFloatStateOf(0f) }; var ty by remember { mutableFloatStateOf(0f) }; var k by remember { mutableFloatStateOf(1f) }
    var dirty by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(Offset(1080f, 1800f)) }

    fun json(): String { root.put("nodes", JSONArray(nodes.map { it.flush() })); root.put("edges", JSONArray(edges.toList())); return root.toString(2) }
    suspend fun saveNow() = withContext(Dispatchers.IO) { try { E.file(rel).writeText(json()) } catch (e: Exception) { } }
    LaunchedEffect(dirty) { if (dirty > 0) { delay(500); saveNow() } }
    fun close() { scope.launch { saveNow(); ui.canvas = null } }
    fun sane() { if (!k.isFinite() || k <= 0f) k = 1f; if (!tx.isFinite()) tx = 0f; if (!ty.isFinite()) ty = 0f }
    fun fitRaw() {
        if (nodes.isEmpty()) { k = 1f; tx = size.x / 2; ty = size.y / 3; return }
        val x0 = nodes.minOf { it.x }; val y0 = nodes.minOf { it.y }; val x1 = nodes.maxOf { it.x + it.w }; val y1 = nodes.maxOf { it.y + it.h }
        k = minOf((size.x - 60) / ((x1 - x0) * dens), (size.y - 60) / ((y1 - y0) * dens)).coerceIn(.15f, 1.4f)
        tx = size.x / 2 - (x0 + x1) / 2 * dens * k; ty = size.y / 2 - (y0 + y1) / 2 * dens * k
    }
    fun fit() { fitRaw(); sane() }
    fun centre() = Offset((size.x / 2 - tx) / k / dens, (size.y / 2 - ty) / k / dens)
    fun addText(at: Offset) { val n = CNode(JSONObject().put("type", "text").put("text", "").put("x", (at.x - 130).toInt()).put("y", (at.y - 60).toInt()).put("width", 260).put("height", 120)); nodes.add(n); sel = n.id; startEdit(n); dirty++ }
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
            Text(rel.substringAfterLast('/').removeSuffix(".canvas"), Modifier.weight(1f), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 21.sp, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            .pointerInput(Unit) { detectTapGestures(onTap = { sel = null; editing = null; linking = null }, onDoubleTap = { o -> addText(Offset((o.x - tx) / k / dens, (o.y - ty) / k / dens)) }) }) {
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
                val lineC = ink.mute
                Canvas(Modifier.size(1.dp)) { try {
                    fun arrow(to: Offset, from: Offset, c: Color) {
                        val ang = kotlin.math.atan2(to.y - from.y, to.x - from.x); val L = 11.dp.toPx()
                        val p = androidx.compose.ui.graphics.Path().apply {
                            moveTo(to.x, to.y)
                            lineTo(to.x - L * kotlin.math.cos(ang - .45f), to.y - L * kotlin.math.sin(ang - .45f))
                            lineTo(to.x - L * kotlin.math.cos(ang + .45f), to.y - L * kotlin.math.sin(ang + .45f)); close()
                        }
                        drawPath(p, c)
                    }
                    for (e in edges) {
                        val a = byId[e.optString("fromNode")] ?: continue; val b = byId[e.optString("toNode")] ?: continue
                        val fs = e.optString("fromSide").ifEmpty { side(a, b) }; val ts = e.optString("toSide").ifEmpty { side(b, a) }
                        val p1 = anchor(a, fs) * density; val p2 = anchor(b, ts) * density
                        val c = canvasColor(e.optString("color")) ?: lineC
                        val bend = 50.dp.toPx()
                        fun ctl(p: Offset, s: String) = when (s) { "left" -> p + Offset(-bend, 0f); "right" -> p + Offset(bend, 0f); "top" -> p + Offset(0f, -bend); else -> p + Offset(0f, bend) }
                        val c1 = ctl(p1, fs); val c2 = ctl(p2, ts)
                        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(p1.x, p1.y); cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y) }, c,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        if (e.optString("toEnd", "arrow") != "none") arrow(p2, c2, c)
                        if (e.optString("fromEnd", "none") == "arrow") arrow(p1, c1, c)
                    }
                    linking?.let { id -> byId[id]?.let { a -> drawRoundRect(lineC, Offset(a.x - 6, a.y - 6) * density, androidx.compose.ui.geometry.Size(a.w + 12, a.h + 12) * density, androidx.compose.ui.geometry.CornerRadius(16 * density), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * density, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 9f)))) } }
                } catch (t: Throwable) { android.util.Log.e("Stoa", "canvas draw", t) } }
                fun connect(from: String, to: CNode) {
                    val f = nodes.firstOrNull { it.id == from } ?: return
                    val ex = edges.firstOrNull { (it.optString("fromNode") == from && it.optString("toNode") == to.id) || (it.optString("fromNode") == to.id && it.optString("toNode") == from) }
                    if (ex != null) edges.remove(ex) else edges.add(JSONObject().put("id", hex16()).put("fromNode", from).put("fromSide", side(f, to)).put("toNode", to.id).put("toSide", side(to, f)).put("toEnd", "arrow"))
                    dirty++
                }
                nodes.sortedBy { if (it.type == "group") 0 else 1 }.forEach { n -> NodeView(n, sel == n.id, editing == n.id, dens, k, tfs,
                    onSelect = {
                        val l = linking
                        if (l != null) { if (l != n.id) connect(l, n); linking = null }
                        else if (sel == n.id && n.type == "text") { startEdit(n) }
                        else { sel = n.id; if (editing != n.id) editing = null }
                    },
                    onPick = { if (sel != n.id) { sel = n.id; editing = null } }, onMoved = { dirty++ }) }
                // tap the little circle, then tap another card to connect (again to disconnect)
                nodes.firstOrNull { it.id == sel }?.takeIf { editing == null }?.let { n ->
                    val hs = (34f / k).coerceIn(28f, 120f)
                    Box(Modifier.offset((n.x + n.w - hs / 2).dp, (n.y + n.h / 2 - hs / 2).dp).size(hs.dp)
                        .pointerInput(n.id) { detectTapGestures { linking = if (linking == n.id) null else n.id } },
                        contentAlignment = Alignment.Center) {
                        val on = linking == n.id
                        Box(Modifier.size((hs * .5f).dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (on) ink.ink else ink.bg).border((2f / k).coerceIn(1.5f, 6f).dp, ink.ink, androidx.compose.foundation.shape.CircleShape))
                    }
                }
            }
            val cur = nodes.firstOrNull { it.id == sel }
            if (cur != null && editing != cur.id) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).clip(RoundedCornerShape(50)).background(ink.card).border(1.dp, ink.line, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                listOf("", "1", "2", "3", "4", "5", "6").forEach { c ->
                    val col = canvasColor(c)
                    Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(col ?: ink.bg).border(if (cur.color == c) 2.5.dp else 1.dp, if (cur.color == c) ink.ink else ink.line2, androidx.compose.foundation.shape.CircleShape)
                        .pointerInput(c, cur.id) { detectTapGestures { cur.color = c; dirty++ } })
                }
            }
            LaunchedEffect(editing) {
                val id = editing ?: return@LaunchedEffect
                androidx.compose.runtime.snapshotFlow { tfs.text.toString() }.collect { t -> nodes.firstOrNull { it.id == id }?.let { n -> if (n.text != t) { n.text = t; dirty++ } } }
            }
            if (cur != null && editing == cur.id) FormatBar(androidx.compose.ui.text.input.TextFieldValue(tfs.text.toString(), tfs.selection), Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)) { v ->
                tfs.edit { replace(0, length, v.text); selection = v.selection }
            }
            Text(when { linking != null -> "Now tap the card to connect to · tap again to disconnect"; editing != null -> "Select text, then format"; cur != null -> "Tap ○ to connect · drag the corner to resize · tap again to edit"; else -> "Double-tap to type · drag cards · pinch to zoom" }, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).clip(RoundedCornerShape(50)).background(ink.card).border(1.dp, ink.line, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 12.sp, color = ink.mute)
        }
    }
}

@Composable
fun NodeView(n: CNode, selected: Boolean, editing: Boolean, dens: Float, k: Float, tfs: androidx.compose.foundation.text.input.TextFieldState,
             onSelect: () -> Unit, onPick: () -> Unit, onMoved: () -> Unit) {
    val E = LocalEngine.current; val ink = LocalInk.current
    val shape = RoundedCornerShape(12.dp)
    val isImg = n.type == "file" && n.file.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "avif", "heic")
    Box(Modifier.offset(n.x.dp, n.y.dp).size(n.w.dp, n.h.dp)) {
        Box(Modifier.fillMaxSize().clip(shape)
            .background(canvasColor(n.color)?.copy(alpha = if (n.type == "group") .06f else .14f)?.compositeOver(ink.card) ?: if (n.type == "group") ink.bg.copy(alpha = 0f) else ink.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) ink.ink else (canvasColor(n.color) ?: ink.line2), shape)
            .pointerInput(n.id) { detectTapGestures(onTap = { onSelect() }) }
            .pointerInput(n.id, editing) { if (!editing) detectDragGestures(onDragStart = { onPick() }, onDragEnd = onMoved) { ch, d -> ch.consume(); n.x += d.x / dens; n.y += d.y / dens } }) {
            when (n.type) {
                "text" -> if (editing) {
                    val fr = remember { FocusRequester() }
                    LaunchedEffect(Unit) { fr.requestFocus() }
                    BasicTextField(state = tfs, modifier = Modifier.fillMaxSize().padding(12.dp).focusRequester(fr), textStyle = TextStyle(color = ink.ink, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = Mono), cursorBrush = SolidColor(ink.ink))
                } else Text(renderMd(n.text, ink.ink, ink.mute), Modifier.padding(12.dp), color = ink.ink, fontSize = 16.sp, lineHeight = 22.sp, overflow = TextOverflow.Clip)
                "file" -> {
                    if (isImg) AsyncImage(E.file(n.file), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("📄 " + n.file.substringAfterLast('/'), Modifier.padding(12.dp), color = ink.ink)
                }
                "link" -> Text("🔗 " + n.obj.optString("url"), Modifier.padding(12.dp), color = ink.ink, fontSize = 13.sp)
                "group" -> Text(n.obj.optString("label").uppercase(), Modifier.padding(10.dp), color = ink.mute, fontSize = 11.sp, letterSpacing = 1.4.sp)
            }
        }
        // resize: drag the corner (images keep their shape)
        if (selected && !editing) {
            val hs = (40f / k).coerceIn(30f, 140f)
            Box(Modifier.align(Alignment.BottomEnd).offset((hs * .35f).dp, (hs * .35f).dp).size(hs.dp)
                .pointerInput(n.id, isImg) { detectDragGestures(onDragEnd = onMoved) { ch, d -> ch.consume()
                    val nw = (n.w + d.x / dens).coerceAtLeast(60f)
                    if (isImg) { val r = n.h / n.w; n.w = nw; n.h = nw * r } else { n.w = nw; n.h = (n.h + d.y / dens).coerceAtLeast(40f) }
                } }, contentAlignment = Alignment.Center) {
                Box(Modifier.size((hs * .42f).dp).clip(RoundedCornerShape(4.dp)).background(ink.ink).border(1.dp, ink.bg, RoundedCornerShape(4.dp)))
            }
        }
    }
}

/** Markdown the way Obsidian shows it on a canvas card: headings, bold, italic, strike, ==highlight==, `code`, lists, checkboxes. */
fun renderMd(src: String, ink: Color, mute: Color): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    val lines = src.split("\n")
    lines.forEachIndexed { li, raw ->
        var line = raw
        var style: androidx.compose.ui.text.SpanStyle? = null
        val h = Regex("^(#{1,6})\\s+").find(line)
        if (h != null) { val lv = h.groupValues[1].length; line = line.substring(h.range.last + 1)
            style = androidx.compose.ui.text.SpanStyle(fontSize = when (lv) { 1 -> 26.sp; 2 -> 21.sp; 3 -> 18.sp; else -> 16.sp }, fontWeight = if (lv <= 2) FontWeight.Light else FontWeight.SemiBold, fontFamily = Display) }
        Regex("^(\\s*)[-*] \\[( |x|X)\\] ").find(line)?.let { m -> line = line.substring(m.range.last + 1); append(m.groupValues[1] + if (m.groupValues[2] == " ") "☐ " else "☑ ") }
            ?: Regex("^(\\s*)[-*+] ").find(line)?.let { m -> line = line.substring(m.range.last + 1); append(m.groupValues[1] + "•  ") }
            ?: Regex("^(\\s*)(\\d+)\\. ").find(line)?.let { m -> line = line.substring(m.range.last + 1); append(m.groupValues[1] + m.groupValues[2] + ".  ") }
        if (line.startsWith("> ")) { line = line.removePrefix("> "); style = (style ?: androidx.compose.ui.text.SpanStyle()).merge(androidx.compose.ui.text.SpanStyle(color = mute, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)); append("▎ ") }
        val start = length
        val tok = Regex("(\\*\\*|__)(.+?)\\1|(\\*|_)(.+?)\\3|~~(.+?)~~|==(.+?)==|`(.+?)`")
        var i = 0
        for (m in tok.findAll(line)) {
            append(line.substring(i, m.range.first))
            val g = m.groupValues
            val (txt, sp) = when {
                g[2].isNotEmpty() -> g[2] to androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
                g[4].isNotEmpty() -> g[4] to androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                g[5].isNotEmpty() -> g[5] to androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, color = mute)
                g[6].isNotEmpty() -> g[6] to androidx.compose.ui.text.SpanStyle(background = Color(0x66E0DE71))
                else -> g[7] to androidx.compose.ui.text.SpanStyle(fontFamily = Mono, background = mute.copy(alpha = .18f))
            }
            withStyle(sp) { append(txt) }
            i = m.range.last + 1
        }
        append(line.substring(i))
        style?.let { addStyle(it, start, length) }
        if (li < lines.size - 1) append("\n")
    }
}

/** Formatting for the card being edited: wraps the selection (or toggles the line prefix). */
@Composable
fun FormatBar(v: androidx.compose.ui.text.input.TextFieldValue, modifier: Modifier, set: (androidx.compose.ui.text.input.TextFieldValue) -> Unit) {
    val ink = LocalInk.current
    fun wrap(m: String) {
        val a = v.selection.min; val b = v.selection.max; val t = v.text
        if (a >= m.length && t.length >= b + m.length && t.substring(a - m.length, a) == m && t.substring(b, b + m.length) == m)
            set(androidx.compose.ui.text.input.TextFieldValue(t.removeRange(b, b + m.length).removeRange(a - m.length, a), androidx.compose.ui.text.TextRange(a - m.length, b - m.length)))
        else set(androidx.compose.ui.text.input.TextFieldValue(t.substring(0, a) + m + t.substring(a, b) + m + t.substring(b), androidx.compose.ui.text.TextRange(a + m.length, b + m.length)))
    }
    fun prefix(p: String) {
        val t = v.text; val ls = t.lastIndexOf('\n', (v.selection.min - 1).coerceAtLeast(0)).let { if (it < 0 || v.selection.min == 0) 0 else it + 1 }
        val strip = Regex("^(#{1,6} |- \\[[ xX]\\] |[-*+] |> |\\d+\\. )")
        val line = t.substring(ls); val cur = strip.find(line)?.value ?: ""
        val nl = if (cur == p) "" else p
        val nt = t.substring(0, ls) + nl + t.substring(ls + cur.length)
        val d = nl.length - cur.length
        set(androidx.compose.ui.text.input.TextFieldValue(nt, androidx.compose.ui.text.TextRange((v.selection.start + d).coerceAtLeast(ls), (v.selection.end + d).coerceAtLeast(ls))))
    }
    Row(modifier.clip(RoundedCornerShape(50)).background(ink.card).border(1.dp, ink.line, RoundedCornerShape(50)).horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        @Composable fun B(label: String, st: androidx.compose.ui.text.SpanStyle = androidx.compose.ui.text.SpanStyle(), on: () -> Unit) =
            Text(androidx.compose.ui.text.buildAnnotatedString { withStyle(st) { append(label) } }, Modifier.clip(RoundedCornerShape(50)).pointerInput(v) { detectTapGestures { on() } }.padding(horizontal = 11.dp, vertical = 8.dp), color = ink.ink, fontSize = 15.sp)
        B("H1", androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)) { prefix("# ") }
        B("H2", androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)) { prefix("## ") }
        B("B", androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) { wrap("**") }
        B("I", androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { wrap("*") }
        B("S", androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) { wrap("~~") }
        B("H", androidx.compose.ui.text.SpanStyle(background = Color(0x66E0DE71))) { wrap("==") }
        B("</>", androidx.compose.ui.text.SpanStyle(fontFamily = Mono)) { wrap("`") }
        B("•") { prefix("- ") }
        B("1.") { prefix("1. ") }
        B("☐") { prefix("- [ ] ") }
        B("❝") { prefix("> ") }
    }
}
