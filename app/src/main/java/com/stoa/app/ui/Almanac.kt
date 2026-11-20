package com.stoa.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoa.app.vault.areaScores
import com.stoa.app.vault.dayIdx
import com.stoa.app.vault.keyOfIdx
import com.stoa.app.vault.weekStartIdx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/** Almanac — your week at a glance: balance across life areas, habits, routines and what you clipped. */
@Composable
fun AlmanacPage() {
    val E = LocalEngine.current; val ink = LocalInk.current
    var back by remember { mutableIntStateOf(0) }
    val mon = E.prefs.bool("weekStartsMonday", false)
    val ws = weekStartIdx(dayIdx(E.today()), mon) - 7 * back
    val keys = (0 until 7).map { keyOfIdx(ws + it) }
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 28.dp)) {
        H1("Almanac")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker((if (back == 0) "This week · " else if (back == 1) "Last week · " else "") + LocalDate.parse(keys[0]).format(fmt) + " – " + LocalDate.parse(keys[6]).format(fmt), Modifier.weight(1f))
            IconBtn(Icons.Outlined.ChevronLeft, "Previous week", { back++ }, size = 18.dp)
            IconBtn(Icons.Outlined.ChevronRight, "Next week", { if (back > 0) back-- }, tint = if (back > 0) ink.mute else ink.faint, size = 18.dp)
        }
        Balance(ws)
        BioMetrics(keys)
        RoutineWeek(keys)
        ClippedThisWeek(keys)
    }
}

@Composable
private fun Balance(ws: Int) {
    val E = LocalEngine.current; val ink = LocalInk.current
    val scores = remember(ws, E.days, E.tasks, E.noteDays, E.dash) { areaScores(E.allTasks(), E.days, E.noteDays, E.rpgOpts(), ws) }
    val overall = if (scores.isEmpty()) 0 else scores.sumOf { it.score } / scores.size
    StoaCard("Weekly balance", action = { Text("$overall", fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 22.sp, color = ink.ink) }) {
        val anim = scores.map { s -> animateFloatAsState((s.score / 150f).coerceIn(0f, 1f), spring(dampingRatio = .7f, stiffness = 120f), label = s.id).value }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val line = ink.line2; val fill = ink.ink
            Canvas(Modifier.weight(1f).aspectRatio(1f).padding(16.dp)) {
                val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2; val n = scores.size.coerceAtLeast(3)
                fun pt(i: Int, f: Float): Offset { val a = -Math.PI / 2 + 2 * Math.PI * i / n; return Offset(c.x + (r * f * cos(a)).toFloat(), c.y + (r * f * sin(a)).toFloat()) }
                listOf(1f / 3f, 2f / 3f, 1f).forEach { f -> val p = Path(); for (i in 0 until n) { val q = pt(i, f); if (i == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y) }; p.close(); drawPath(p, line, style = Stroke(1.dp.toPx())) }
                for (i in 0 until n) drawLine(line, c, pt(i, 1f), 1.dp.toPx())
                if (scores.isNotEmpty()) {
                    val p = Path(); anim.forEachIndexed { i, f -> val q = pt(i, f.coerceAtLeast(.02f)); if (i == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y) }; p.close()
                    drawPath(p, fill.copy(alpha = .14f)); drawPath(p, fill, style = Stroke(1.6.dp.toPx()))
                    anim.forEachIndexed { i, f -> drawCircle(fill, 3.dp.toPx(), pt(i, f.coerceAtLeast(.02f))) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                scores.forEach { a ->
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(a.icon + "  " + a.label, Modifier.weight(1f), fontSize = 14.sp, color = ink.ink, maxLines = 1)
                            Text(if (a.empty) "–" else "${a.score}", fontFamily = Mono, fontSize = 12.sp, color = ink.mute)
                        }
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(ink.line2)) {
                            Box(Modifier.fillMaxWidth((a.score / 100f).coerceIn(0f, 1f)).height(3.dp).clip(CircleShape).background(ink.ink))
                        }
                    }
                }
            }
        }
        Muted("100 = every weekly limit met (set them on the dashboard). Past a limit keeps counting.", size = 11)
    }
}

private val DAY_W = 21.dp
private val TALLY_W = 28.dp

@Composable
private fun DayHeader(keys: List<String>) {
    val ink = LocalInk.current; val today = LocalDate.now().toString()
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Spacer(Modifier.weight(1f))
        keys.forEach { k -> Text(LocalDate.parse(k).format(DateTimeFormatter.ofPattern("EEEEE")), Modifier.width(DAY_W), textAlign = TextAlign.Center, fontFamily = Mono, fontSize = 10.sp, color = if (k == today) ink.ink else ink.faint) }
        Spacer(Modifier.width(TALLY_W))
    }
}

/** Bio-metrics: each habit across the week — tap a day to tick it, exactly like the dashboard. */
@Composable
private fun BioMetrics(keys: List<String>) {
    val E = LocalEngine.current; val ink = LocalInk.current; val today = LocalDate.now().toString()
    StoaCard("Bio-metrics") {
        DayHeader(keys)
        E.habitLayout().forEach { h ->
            if (h.trim().startsWith("---")) { val l = h.trim().removePrefix("---").trim(); if (l.isNotEmpty()) Kicker(l, Modifier.padding(top = 10.dp, bottom = 2.dp)); return@forEach }
            val lo = h.lowercase(); val n = keys.count { E.days[it]?.done?.contains(lo) == true }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((E.habitIcon(h).let { if (it.isNotEmpty()) "$it " else "" }) + h, Modifier.weight(1f).padding(end = 6.dp), fontSize = 14.sp, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                keys.forEach { k ->
                    val on = E.days[k]?.done?.contains(lo) == true; val future = k > today
                    Box(Modifier.width(DAY_W), contentAlignment = Alignment.Center) {
                        val sc by animateFloatAsState(if (on) 1f else .0f, spring(dampingRatio = .5f), label = "c")
                        Box(Modifier.size(16.dp).clip(CircleShape).border(1.dp, if (future) ink.line else ink.line2, CircleShape).clickable(enabled = !future) { E.setHabit(h, !on, k) }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size((16 * sc).dp).clip(CircleShape).background(ink.ink))
                        }
                    }
                }
                Text("$n/7", Modifier.width(TALLY_W), textAlign = TextAlign.End, fontFamily = Mono, fontSize = 11.sp, color = ink.mute)
            }
        }
    }
}

/** Routines: every morning and evening step across the week. */
@Composable
private fun RoutineWeek(keys: List<String>) {
    val E = LocalEngine.current; val ink = LocalInk.current
    val R = E.routines()
    StoaCard("Routines") {
        DayHeader(keys)
        listOf("morning" to "☀  Morning", "evening" to "☾  Evening").forEach { (kind, label) ->
            val full = keys.count { E.days[it]?.complete?.contains(kind) == true }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) { Kicker(label, Modifier.weight(1f)); Kicker("$full/7 full") }
            (R[kind] ?: emptyList()).forEach { step ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (step.emoji.isNotEmpty()) step.emoji + " " else "") + step.text, Modifier.weight(1f).padding(end = 6.dp), fontSize = 13.5.sp, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    keys.forEach { k ->
                        val d = E.days[k]; val on = (if (kind == "morning") d?.morning else d?.evening)?.contains(step.text) == true
                        Box(Modifier.width(DAY_W), contentAlignment = Alignment.Center) { Box(Modifier.size(if (on) 10.dp else 6.dp).clip(CircleShape).background(if (on) ink.ink else ink.line2)) }
                    }
                    Spacer(Modifier.width(TALLY_W))
                }
            }
        }
    }
}

@Composable
private fun ClippedThisWeek(keys: List<String>) {
    val E = LocalEngine.current; val ui = LocalUi.current; val ink = LocalInk.current
    val clips by produceState(emptyList<Pair<String, String>>(), keys.first(), E.mdPaths.size) { value = withContext(Dispatchers.IO) { E.clippingsBetween(keys.first(), keys.last()) } }
    StoaCard("Clipped this week", action = { Kicker("${clips.size}") }) {
        if (clips.isEmpty()) Empty("Nothing clipped this week.")
        clips.take(20).forEachIndexed { i, (rel, d) ->
            Row(Modifier.fillMaxWidth().clickable { ui.reader = rel }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(LocalDate.parse(d).format(DateTimeFormatter.ofPattern("EEE")).uppercase(), Modifier.width(40.dp), fontFamily = Mono, fontSize = 10.sp, color = ink.faint)
                Text(rel.substringAfterLast('/').removeSuffix(".md"), Modifier.weight(1f), fontSize = 15.sp, color = ink.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (i < minOf(clips.size, 20) - 1) Divider()
        }
    }
}
