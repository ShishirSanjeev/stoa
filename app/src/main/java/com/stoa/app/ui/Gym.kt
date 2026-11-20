package com.stoa.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoa.app.Prefs
import com.stoa.app.vault.WEx
import com.stoa.app.vault.WSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val LIB = listOf(
    "Bench press" to "Chest", "Incline DB press" to "Chest", "Cable fly" to "Chest", "Push-up" to "Chest",
    "Back squat" to "Legs", "Leg press" to "Legs", "Romanian deadlift" to "Legs", "Walking lunge" to "Legs", "Leg curl" to "Legs", "Leg extension" to "Legs", "Calf raise" to "Legs",
    "Deadlift" to "Back", "Pull-up" to "Back", "Barbell row" to "Back", "Lat pulldown" to "Back", "Seated cable row" to "Back",
    "Overhead press" to "Shoulders", "Lateral raise" to "Shoulders", "Face pull" to "Shoulders", "Rear delt fly" to "Shoulders",
    "Barbell curl" to "Arms", "Hammer curl" to "Arms", "Triceps pushdown" to "Arms", "Skull crusher" to "Arms", "Dip" to "Arms",
    "Plank (seconds)" to "Core", "Hanging leg raise" to "Core", "Cable crunch" to "Core")
val MUSCLES = listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core")

/* the workout in progress lives in prefs so it survives the app being closed */
data class ASet(val kg: String = "", val reps: String = "", val done: Boolean = false)
data class AEx(val name: String, val sets: List<ASet>)
data class Active(val start: Long, val name: String, val ex: List<AEx>) {
    fun json(): String = JSONObject().put("start", start).put("name", name).put("ex", JSONArray(ex.map { e ->
        JSONObject().put("name", e.name).put("sets", JSONArray(e.sets.map { JSONObject().put("kg", it.kg).put("reps", it.reps).put("done", it.done) }))
    })).toString()
    companion object {
        fun load(p: Prefs): Active? = p.json("active")?.let { j ->
            val ea = j.optJSONArray("ex") ?: JSONArray()
            Active(j.optLong("start"), j.optString("name", "Workout"), (0 until ea.length()).map { i -> val e = ea.getJSONObject(i); val sa = e.optJSONArray("sets") ?: JSONArray()
                AEx(e.optString("name"), (0 until sa.length()).map { k -> val s = sa.getJSONObject(k); ASet(s.optString("kg"), s.optString("reps"), s.optBoolean("done")) }) })
        }
    }
}

@Composable
fun GymPage() {
    val E = LocalEngine.current; val ui = LocalUi.current; val p = ui.prefs; val ink = LocalInk.current; val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(Active.load(p)) }
    fun set(a: Active?) { active = a; p.put("active", a?.json()) }
    var tplFrom by remember { mutableStateOf<List<String>?>(null) }
    val custom = (E.stoa["gym_custom"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { m -> m["name"].toString() to (m["muscle"]?.toString() ?: "Other") } } ?: emptyList()
    val templates = (E.stoa["gym_templates"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { m -> m["name"].toString() to ((m["ex"] as? List<*>)?.map { x -> x.toString() } ?: emptyList()) } } ?: emptyList()
    fun muscleOf(n: String) = (LIB + custom).firstOrNull { it.first.equals(n, true) }?.second ?: "Other"
    Page {
        H1("Gym")
        val a = active
        if (a != null) { ActiveWorkout(a, ::set, custom, ::muscleOf) { done ->
            set(null); p.put("rest_end", 0L)
            val ex = done.ex.map { e -> WEx(e.name, e.sets.filter { it.done || (it.reps.toIntOrNull() ?: 0) > 0 }.map { WSet(it.kg, it.reps) }) }.filter { it.sets.isNotEmpty() }
            if (ex.isEmpty()) E.toast("Nothing logged", "Workout discarded")
            else { if (templates.none { it.first == done.name }) tplFrom = ex.map { it.name }; scope.launch { E.writeWorkout(done.name, done.start, System.currentTimeMillis(), ex) } }
        }; return@Page }

        tplFrom?.let { names ->
            var n by remember { mutableStateOf("") }
            StoaCard("Save as a template?") {
                Field(n, { n = it }, "e.g. Push day")
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Skip", { tplFrom = null }); Pill("Save", { if (n.isNotBlank()) { E.setStoa("gym_templates", (E.stoa["gym_templates"] as? List<*> ?: emptyList<Any>()) + mapOf("name" to n.trim(), "ex" to names)); tplFrom = null; E.toast("Template saved", n, "ach") } }, primary = true)
                }
            }
        }
        StoaCard {
            Pill("Start empty workout", { set(Active(System.currentTimeMillis(), "Workout", emptyList())) }, Modifier.fillMaxWidth(), primary = true, icon = Icons.Outlined.PlayArrow)
            if (templates.isNotEmpty()) {
                Kicker("Templates", Modifier.padding(top = 16.dp, bottom = 4.dp))
                templates.forEach { (name, ex) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(name, color = ink.ink); Muted(ex.joinToString(", "), size = 12, maxLines = 1) }
                        Pill("Start", { set(Active(System.currentTimeMillis(), name, ex.map { AEx(it, listOf(ASet())) })) }, small = true, primary = true)
                        IconBtn(Icons.Outlined.Delete, "Delete", { E.setStoa("gym_templates", (E.stoa["gym_templates"] as? List<*>)?.filter { (it as? Map<*, *>)?.get("name") != name } ?: emptyList<Any>()) }, size = 17.dp)
                    }
                }
            }
        }
        val since = LocalDate.now().minusDays(6).toString()
        val hist = E.workoutsDesc()
        val counts = MUSCLES.associateWith { m -> hist.filter { it.first >= since }.sumOf { (_, w) -> w.ex.filter { muscleOf(it.name) == m }.sumOf { it.sets.size } } }
        val mx = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
        StoaCard("Sets per muscle · last 7 days") {
            MUSCLES.forEach { m ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m, Modifier.width(84.dp), fontSize = 13.sp, color = ink.mute)
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(ink.line2)) { Box(Modifier.fillMaxWidth(counts[m]!!.toFloat() / mx).height(5.dp).clip(CircleShape).background(ink.ink)) }
                    Text("${counts[m]}", Modifier.width(28.dp), fontSize = 12.sp, color = ink.mute, textAlign = TextAlign.End)
                }
            }
        }
        if (hist.isNotEmpty()) Kicker("History", Modifier.padding(top = 14.dp, start = 4.dp))
        hist.take(30).forEach { (day, w) ->
            var open by remember(day + w.meta) { mutableStateOf(false) }
            StoaCard(modifier = Modifier.animateContentSize().clickable { open = !open }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.name, Modifier.weight(1f), color = ink.ink, fontWeight = FontWeight.SemiBold)
                    Muted(LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMM")), size = 12)
                }
                Muted(w.meta.ifBlank { "${w.ex.size} exercises" }, size = 12)
                if (open) w.ex.forEach { e -> Row(Modifier.padding(top = 6.dp)) { Text(e.name + "  ", fontSize = 13.sp, color = ink.ink, fontWeight = FontWeight.Medium); Muted(e.sets.joinToString(", ") { (if (it.kg.isNotBlank()) it.kg + "×" else "") + it.reps }, size = 13) } }
            }
        }
    }
}

@Composable
fun ActiveWorkout(a: Active, set: (Active?) -> Unit, custom: List<Pair<String, String>>, muscleOf: (String) -> String, finish: (Active) -> Unit) {
    val E = LocalEngine.current; val p = LocalUi.current.prefs; val ink = LocalInk.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    var restLen by remember { mutableIntStateOf(p.int("rest_len", 90)) }
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val restEnd = p.long("rest_end")
    fun upd(i: Int, e: AEx) = set(a.copy(ex = a.ex.toMutableList().also { it[i] = e }))
    StoaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicTextField(a.name, { set(a.copy(name = it)) }, textStyle = TextStyle(color = ink.mute, fontSize = 11.sp, letterSpacing = 1.6.sp), cursorBrush = SolidColor(ink.ink), singleLine = true)
                Text(fmtMs(now - a.start), fontFamily = Serif, fontSize = 34.sp, color = ink.ink)
            }
            if (restEnd > now) {
                Column(horizontalAlignment = Alignment.End) { Kicker("Rest"); Text(fmtMs(restEnd - now), fontFamily = Serif, fontSize = 34.sp, color = ink.ink) }
                IconBtn(Icons.Outlined.Close, "Stop rest", { p.put("rest_end", 0L); now = System.currentTimeMillis() }, size = 18.dp)
            } else Chips(listOf(60, 90, 120, 180), restLen, { "${it}s" }) { restLen = it; p.put("rest_len", it) }
        }
    }
    Muted("Tick a set to start the rest timer.", Modifier.padding(horizontal = 4.dp), 12)
    a.ex.forEachIndexed { i, e ->
        StoaCard(modifier = Modifier.animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = ink.ink, fontWeight = FontWeight.SemiBold)
                    val best = remember(e.name) { E.workoutsDesc().firstNotNullOfOrNull { (_, w) -> w.ex.firstOrNull { it.name.equals(e.name, true) } }?.sets?.maxByOrNull { it.kg.toDoubleOrNull() ?: 0.0 } }
                    Muted(muscleOf(e.name) + (best?.let { " · last best " + (if (it.kg.isNotBlank()) it.kg + " kg × " else "") + it.reps } ?: ""), size = 12)
                }
                IconBtn(Icons.Outlined.Delete, "Remove", { set(a.copy(ex = a.ex.filterIndexed { j, _ -> j != i })) }, size = 18.dp)
            }
            Row(Modifier.padding(top = 8.dp)) { listOf("SET" to .5f, "KG" to 1f, "REPS" to 1f, "" to .6f).forEach { (t, w) -> Text(t, Modifier.weight(w), fontSize = 10.5.sp, letterSpacing = 1.2.sp, color = ink.mute, textAlign = TextAlign.Center) } }
            e.sets.forEachIndexed { si, s ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${si + 1}", Modifier.weight(.5f), color = ink.mute, textAlign = TextAlign.Center)
                    NumField(s.kg, { upd(i, e.copy(sets = e.sets.toMutableList().also { l -> l[si] = s.copy(kg = it.replace(',', '.')) })) }, Modifier.weight(1f), s.done, decimal = true)
                    Spacer(Modifier.width(6.dp))
                    NumField(s.reps, { upd(i, e.copy(sets = e.sets.toMutableList().also { l -> l[si] = s.copy(reps = it) })) }, Modifier.weight(1f), s.done)
                    Box(Modifier.weight(.6f), contentAlignment = Alignment.Center) {
                        val bg by animateColorAsState(if (s.done) ink.ink else Color.Transparent, label = "set")
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(bg).border(1.5.dp, if (s.done) ink.ink else ink.faint, RoundedCornerShape(9.dp)).clickable {
                            val d = !s.done; upd(i, e.copy(sets = e.sets.toMutableList().also { l -> l[si] = s.copy(done = d) }))
                            if (d) { p.put("rest_end", System.currentTimeMillis() + restLen * 1000L); now = System.currentTimeMillis() }
                        }, contentAlignment = Alignment.Center) { if (s.done) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = ink.inv) }
                    }
                }
            }
            Pill("+ Set", { val l = e.sets.lastOrNull() ?: ASet(); upd(i, e.copy(sets = e.sets + ASet(l.kg, l.reps))) }, Modifier.padding(top = 8.dp), small = true)
        }
    }
    Pill("Add exercise", { picking = true }, Modifier.fillMaxWidth().padding(vertical = 8.dp), icon = Icons.Outlined.Add)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(if (confirm) "Tap again to discard" else "Discard", { if (confirm) { set(null); p.put("rest_end", 0L) } else confirm = true }, warn = true)
        Pill("Finish workout", { finish(a) }, Modifier.weight(1f), primary = true, icon = Icons.Outlined.Check)
    }
    LaunchedEffect(confirm) { if (confirm) { delay(3000); confirm = false } }
    if (picking) Sheet("Add exercise", { picking = false }) {
        var q by remember { mutableStateOf("") }
        Field(q, { q = it }, "Search")
        Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
            MUSCLES.plus("Other").forEach { m ->
                val xs = (LIB + custom).filter { it.second == m && (q.isBlank() || it.first.contains(q, true) || m.contains(q, true)) }
                if (xs.isNotEmpty()) Kicker(m, Modifier.padding(top = 12.dp, bottom = 2.dp))
                xs.forEach { (n, _) -> Text(n, Modifier.fillMaxWidth().clickable { set(a.copy(ex = a.ex + AEx(n, listOf(ASet())))); picking = false }.padding(vertical = 11.dp), color = ink.ink, fontSize = 15.sp) }
            }
        }
        var cn by remember { mutableStateOf("") }; var cm by remember { mutableStateOf("Chest") }
        Kicker("Custom", Modifier.padding(top = 12.dp, bottom = 4.dp))
        Field(cn, { cn = it }, "Exercise name")
        Chips(MUSCLES, cm, { it }) { cm = it }
        Pill("Add custom", {
            val n = cn.trim(); if (n.isEmpty()) return@Pill
            if ((LIB + custom).none { it.first.equals(n, true) }) E.setStoa("gym_custom", (E.stoa["gym_custom"] as? List<*> ?: emptyList<Any>()) + mapOf("name" to n, "muscle" to cm))
            set(a.copy(ex = a.ex + AEx(n, listOf(ASet())))); picking = false
        }, Modifier.padding(top = 8.dp), primary = true)
    }
}

@Composable
fun NumField(v: String, on: (String) -> Unit, modifier: Modifier, done: Boolean, decimal: Boolean = false) {
    val ink = LocalInk.current
    BasicTextField(v, on, modifier.border(1.dp, ink.line2, RoundedCornerShape(10.dp)).padding(vertical = 9.dp),
        textStyle = TextStyle(color = if (done) ink.mute else ink.ink, fontSize = 16.sp, textAlign = TextAlign.Center), singleLine = true, cursorBrush = SolidColor(ink.ink),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
}
