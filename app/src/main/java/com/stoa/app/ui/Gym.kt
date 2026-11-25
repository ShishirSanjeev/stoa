package com.stoa.app.ui

import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Remove
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

/** A workout template: exercise order plus the sets to pre-fill (kg × reps), kept in the vault's Stoa state. */
data class Tpl(val name: String, val ex: List<String>, val sets: Map<String, List<WSet>> = emptyMap()) {
    fun toMap(): Map<String, Any> = mapOf("name" to name, "ex" to ex, "sets" to sets.mapValues { (_, l) -> l.map { (if (it.kg.isNotBlank()) it.kg + "x" else "") + it.reps } })
    fun start(): Active = Active(System.currentTimeMillis(), name, ex.map { n -> AEx(n, sets[n]?.takeIf { it.isNotEmpty() }?.map { ASet(it.kg, it.reps) } ?: listOf(ASet())) })
}
fun templatesOf(E: com.stoa.app.vault.Engine): List<Tpl> = (E.stoa["gym_templates"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { m ->
    Tpl(m["name"].toString(), (m["ex"] as? List<*>)?.map { x -> x.toString() } ?: emptyList(),
        (m["sets"] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to ((v as? List<*>)?.map { x -> x.toString().let { t -> if (t.contains('x')) WSet(t.substringBefore('x'), t.substringAfter('x')) else WSet("", t) } } ?: emptyList()) } ?: emptyMap())
} } ?: emptyList()
fun saveTemplate(E: com.stoa.app.vault.Engine, t: Tpl, replacing: String? = null) {
    val l = templatesOf(E).filter { it.name != (replacing ?: t.name) && it.name != t.name } + t
    E.setStoa("gym_templates", l.map { it.toMap() })
}
fun tplFromWorkout(name: String, ex: List<WEx>) = Tpl(name, ex.map { it.name }, ex.associate { it.name to it.sets })

@Composable
fun GymView() {
    val E = LocalEngine.current; val ui = LocalUi.current; val p = ui.prefs; val ink = LocalInk.current; val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(Active.load(p)) }
    fun set(a: Active?) { active = a; p.put("active", a?.json()) }
    var tplFrom by remember { mutableStateOf<List<WEx>?>(null) }
    val custom = (E.stoa["gym_custom"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { m -> m["name"].toString() to (m["muscle"]?.toString() ?: "Other") } } ?: emptyList()
    val templates = templatesOf(E)
    var editTpl by remember { mutableStateOf<Tpl?>(null) }; var editOrig by remember { mutableStateOf<String?>(null) }
    fun muscleOf(n: String) = (LIB + custom).firstOrNull { it.first.equals(n, true) }?.second ?: "Other"
    Column {
        val a = active
        editTpl?.let { t -> TemplateEditor(t, custom, { editTpl = null }) { nt -> saveTemplate(E, nt, editOrig); editTpl = null; E.toast("Template saved", nt.name, "ach") } }
        if (a != null) { ActiveWorkout(a, ::set, custom, ::muscleOf) { done ->
            set(null); p.put("rest_end", 0L)
            val ex = done.ex.map { e -> WEx(e.name, e.sets.filter { it.done || (it.reps.toIntOrNull() ?: 0) > 0 }.map { WSet(it.kg, it.reps) }) }.filter { it.sets.isNotEmpty() }
            if (ex.isEmpty()) E.toast("Nothing logged", "Workout discarded")
            else { if (templates.none { it.name == done.name }) tplFrom = ex; scope.launch { E.writeWorkout(done.name, done.start, System.currentTimeMillis(), ex) } }
        }; return@Column }

        tplFrom?.let { names ->
            var n by remember { mutableStateOf("") }
            StoaCard("Save as a template?") {
                Field(n, { n = it }, "e.g. Push day")
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Skip", { tplFrom = null }); Pill("Save", { if (n.isNotBlank()) { saveTemplate(E, tplFromWorkout(n.trim(), names)); tplFrom = null; E.toast("Template saved", n, "ach") } }, primary = true)
                }
            }
        }
        StoaCard {
            Pill("Start empty workout", { set(Active(System.currentTimeMillis(), "Workout", emptyList())) }, Modifier.fillMaxWidth(), primary = true, icon = Icons.Outlined.PlayArrow)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Kicker("Templates", Modifier.weight(1f))
                Text("+ NEW", Modifier.clip(RoundedCornerShape(6.dp)).clickable { editOrig = null; editTpl = Tpl("", emptyList()) }.padding(horizontal = 8.dp, vertical = 6.dp), fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.2.sp, color = ink.ink)
            }
            if (templates.isEmpty()) Muted("Make one here, or save any past workout below as a template.", size = 12)
            templates.forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { editOrig = t.name; editTpl = t }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(t.name, color = ink.ink); Muted(t.ex.joinToString(", ") { n -> n + (t.sets[n]?.size?.takeIf { it > 1 }?.let { " ×$it" } ?: "") }, size = 12, maxLines = 1) }
                    Pill("Start", { set(t.start()) }, small = true, primary = true)
                    IconBtn(Icons.Outlined.Delete, "Delete", { E.setStoa("gym_templates", templatesOf(E).filter { it.name != t.name }.map { it.toMap() }) }, size = 17.dp)
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
                if (open) {
                    w.ex.forEach { e -> Row(Modifier.padding(top = 6.dp)) { Text(e.name + "  ", fontSize = 13.sp, color = ink.ink, fontWeight = FontWeight.Medium); Muted(e.sets.joinToString(", ") { (if (it.kg.isNotBlank()) it.kg + "×" else "") + it.reps }, size = 13) } }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Do again", { set(tplFromWorkout(w.name, w.ex).start()) }, small = true, primary = true, icon = Icons.Outlined.PlayArrow)
                        Pill("Save as template", { editOrig = null; editTpl = tplFromWorkout(w.name, w.ex) }, small = true)
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveWorkout(a: Active, set: (Active?) -> Unit, custom: List<Pair<String, String>>, muscleOf: (String) -> String, finish: (Active) -> Unit) {
    val E = LocalEngine.current; val p = LocalUi.current.prefs; val ink = LocalInk.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    fun upd(i: Int, e: AEx) = set(a.copy(ex = a.ex.toMutableList().also { it[i] = e }))
    StoaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicTextField(a.name, { set(a.copy(name = it)) }, textStyle = TextStyle(color = ink.mute, fontSize = 11.sp, letterSpacing = 1.6.sp, fontFamily = Mono), cursorBrush = SolidColor(ink.ink), singleLine = true)
                Text(fmtMs(now - a.start), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 34.sp, color = ink.ink)
            }
        }
    }
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
            Row(Modifier.padding(top = 8.dp)) { listOf("SET" to .5f, "KG" to 1f, "REPS" to 1f, "" to .6f, "" to .4f).forEach { (t, w) -> Text(t, Modifier.weight(w), fontSize = 10.5.sp, letterSpacing = 1.2.sp, color = ink.mute, textAlign = TextAlign.Center) } }
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
                        }, contentAlignment = Alignment.Center) { if (s.done) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = ink.inv) }
                    }
                    Box(Modifier.weight(.4f).size(32.dp).clip(CircleShape).clickable { upd(i, e.copy(sets = e.sets.filterIndexed { j, _ -> j != si })) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Remove set", Modifier.size(15.dp), tint = ink.faint)
                    }
                }
            }
            Pill("+ Set", { val l = e.sets.lastOrNull() ?: ASet(); upd(i, e.copy(sets = e.sets + ASet(l.kg, l.reps))) }, Modifier.padding(top = 8.dp), small = true)
        }
    }
    Pill("Add exercise", { picking = true }, Modifier.fillMaxWidth().padding(vertical = 8.dp), icon = Icons.Outlined.Add)
    if (a.ex.isNotEmpty()) Pill("Save as template", {
        saveTemplate(E, Tpl(a.name.ifBlank { "Workout" }, a.ex.map { it.name }, a.ex.associate { e -> e.name to e.sets.map { WSet(it.kg, it.reps) } }))
        E.toast("Template saved", a.name, "ach")
    }, Modifier.fillMaxWidth().padding(bottom = 8.dp), small = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(if (confirm) "Tap again to discard" else "Discard", { if (confirm) { set(null); p.put("rest_end", 0L) } else confirm = true }, warn = true)
        Pill("Finish workout", { finish(a) }, Modifier.weight(1f), primary = true, icon = Icons.Outlined.Check)
    }
    LaunchedEffect(confirm) { if (confirm) { delay(3000); confirm = false } }
    if (picking) Sheet("Add exercise", { picking = false }) { ExercisePicker(custom) { n -> set(a.copy(ex = a.ex + AEx(n, listOf(ASet())))); picking = false } }
}

@Composable
fun ExercisePicker(custom: List<Pair<String, String>>, pick: (String) -> Unit) {
    val E = LocalEngine.current; val ink = LocalInk.current
    var q by remember { mutableStateOf("") }
    Field(q, { q = it }, "Search")
    Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
        MUSCLES.plus("Other").forEach { m ->
            val xs = (LIB + custom).filter { it.second == m && (q.isBlank() || it.first.contains(q, true) || m.contains(q, true)) }
            if (xs.isNotEmpty()) Kicker(m, Modifier.padding(top = 12.dp, bottom = 2.dp))
            xs.forEach { (n, _) -> Text(n, Modifier.fillMaxWidth().clickable { pick(n) }.padding(vertical = 11.dp), color = ink.ink, fontSize = 15.sp) }
        }
    }
    var cn by remember { mutableStateOf("") }; var cm by remember { mutableStateOf("Chest") }
    Kicker("Custom", Modifier.padding(top = 12.dp, bottom = 4.dp))
    Field(cn, { cn = it }, "Exercise name")
    Chips(MUSCLES, cm, { it }) { cm = it }
    Pill("Add custom", {
        val n = cn.trim(); if (n.isEmpty()) return@Pill
        if ((LIB + custom).none { it.first.equals(n, true) }) E.setStoa("gym_custom", (E.stoa["gym_custom"] as? List<*> ?: emptyList<Any>()) + mapOf("name" to n, "muscle" to cm))
        cn = ""; pick(n)
    }, Modifier.padding(top = 8.dp), primary = true)
}

/** Create or edit a template: name, exercises (in order) and how many sets each starts with. */
@Composable
fun TemplateEditor(t0: Tpl, custom: List<Pair<String, String>>, dismiss: () -> Unit, save: (Tpl) -> Unit) {
    val ink = LocalInk.current
    var name by remember { mutableStateOf(t0.name) }
    var ex by remember { mutableStateOf(t0.ex) }
    var sets by remember { mutableStateOf(t0.sets) }
    var adding by remember { mutableStateOf(false) }
    if (adding) { Sheet("Add exercise", { adding = false }) { ExercisePicker(custom) { n -> if (n !in ex) ex = ex + n; adding = false } }; return }
    Sheet(if (t0.name.isBlank()) "New template" else "Edit template", dismiss) {
        Field(name, { name = it }, "Name, e.g. Push day")
        Spacer(Modifier.height(8.dp))
        ex.forEachIndexed { i, n ->
            val ss = sets[n] ?: emptyList()
            val count = ss.size.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n, color = ink.ink, fontSize = 15.sp)
                    Muted(ss.filter { it.reps.isNotBlank() }.joinToString(", ") { (if (it.kg.isNotBlank()) it.kg + "×" else "") + it.reps }.ifBlank { "$count set" + if (count > 1) "s" else "" }, size = 11, maxLines = 1)
                }
                IconBtn(Icons.Outlined.Remove, "Fewer sets", { if (count > 1) sets = sets + (n to (if (ss.isEmpty()) emptyList() else ss.dropLast(1))) }, size = 16.dp)
                Text("$count", Modifier.width(18.dp), color = ink.ink, textAlign = TextAlign.Center, fontFamily = Mono, fontSize = 13.sp)
                IconBtn(Icons.Outlined.Add, "More sets", { val base = if (ss.isEmpty()) List(count) { WSet("", "") } else ss; sets = sets + (n to base + (base.lastOrNull() ?: WSet("", ""))) }, size = 16.dp)
                if (i > 0) IconBtn(Icons.Outlined.ArrowUpward, "Move up", { ex = ex.toMutableList().apply { add(i - 1, removeAt(i)) } }, size = 16.dp)
                IconBtn(Icons.Outlined.Close, "Remove", { ex = ex.filterIndexed { j, _ -> j != i }; sets = sets - n }, size = 16.dp)
            }
        }
        Pill("Add exercise", { adding = true }, Modifier.fillMaxWidth().padding(top = 8.dp), icon = Icons.Outlined.Add, small = true)
        Pill("Save template", { if (name.isNotBlank() && ex.isNotEmpty()) save(Tpl(name.trim(), ex, sets.filterKeys { it in ex })) }, Modifier.fillMaxWidth().padding(top = 10.dp), primary = true)
    }
}

@Composable
fun NumField(v: String, on: (String) -> Unit, modifier: Modifier, done: Boolean, decimal: Boolean = false) {
    val ink = LocalInk.current
    BasicTextField(v, on, modifier.border(1.dp, ink.line2, RoundedCornerShape(10.dp)).padding(vertical = 9.dp),
        textStyle = TextStyle(color = if (done) ink.mute else ink.ink, fontSize = 16.sp, textAlign = TextAlign.Center), singleLine = true, cursorBrush = SolidColor(ink.ink),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
}
