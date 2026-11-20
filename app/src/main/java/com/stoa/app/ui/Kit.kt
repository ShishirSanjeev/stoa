package com.stoa.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoa.app.vault.Engine
import com.stoa.app.vault.Rpg
import kotlinx.coroutines.delay

fun fmtMs(ms0: Long): String {
    val ms = maxOf(0L, ms0); val s = (ms + 999) / 1000; val h = s / 3600; val m = (s % 3600) / 60; val r = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%02d:%02d".format(m, r)
}
fun fmtDur(ms: Long): String { val m = ms / 60000; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = LocalInk.current.mute) =
    Text(text.uppercase(), modifier, color = color, fontSize = 10.5.sp, letterSpacing = 1.8.sp)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 13, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = LocalInk.current.mute, fontSize = size.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, lineHeight = (size + 5).sp)

@Composable
fun H1(text: String) = Text(text, fontFamily = Serif, fontSize = 40.sp, color = LocalInk.current.ink, lineHeight = 44.sp)

@Composable
fun StoaCard(title: String? = null, modifier: Modifier = Modifier, flat: Boolean = false, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val ink = LocalInk.current
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp))
        .then(if (flat) Modifier else Modifier.background(ink.card).border(1.dp, ink.line, RoundedCornerShape(18.dp)))
        .padding(horizontal = if (flat) 2.dp else 16.dp, vertical = 14.dp)) {
        if (title != null || action != null) {
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Kicker(title ?: "", Modifier.weight(1f)); action?.invoke()
            }
            Spacer(Modifier.height(4.dp))
        }
        content()
    }
}

@Composable
fun Empty(text: String) = Muted(text, Modifier.padding(vertical = 8.dp), 14)

/** Press feedback that feels physical: a quick spring scale. */
@Composable
fun Modifier.press(onClick: () -> Unit, scaleTo: Float = 0.96f): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleTo else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    return this.scale(s).clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, icon: ImageVector? = null, warn: Boolean = false, small: Boolean = false, light: Boolean = false) {
    val ink = LocalInk.current
    val fg = when { light -> Color.White; primary -> ink.inv; warn -> ink.warn; else -> ink.ink }
    val bg = if (primary) ink.ink else Color.Transparent
    val bd = when { light -> Color(0x40FFFFFF); primary -> ink.ink; warn -> ink.warn.copy(alpha = .55f); else -> ink.line2 }
    Row(modifier.clip(CircleShape).background(bg).border(1.dp, bd, CircleShape).press(onClick)
        .padding(horizontal = if (small) 12.dp else 18.dp, vertical = if (small) 7.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, Modifier.size(if (small) 15.dp else 17.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
        Text(text, color = fg, fontSize = if (small) 13.sp else 14.5.sp, maxLines = 1)
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = LocalInk.current.mute, size: Dp = 20.dp) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).press(onClick, .88f), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(size), tint = tint)
    }
}

/** A checklist row. The tick is instant and bouncy; the file write happens behind it. */
@Composable
fun CheckRow(checked: Boolean, text: String, onToggle: (Boolean) -> Unit, lead: String = "", trailing: (@Composable () -> Unit)? = null, onOpen: (() -> Unit)? = null, divider: Boolean = true) {
    val ink = LocalInk.current; val haptic = LocalHapticFeedback.current
    var on by remember(checked) { mutableStateOf(checked) }
    var bumps by remember { mutableStateOf(0) }
    val boxBg by animateColorAsState(if (on) ink.ink else Color.Transparent, tween(180), label = "box")
    val txt by animateColorAsState(if (on) ink.mute else ink.ink, tween(220), label = "txt")
    val sc = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(bumps) { if (bumps > 0) { sc.snapTo(.78f); sc.animateTo(1f, spring(dampingRatio = .38f, stiffness = 500f)) } }
    Column {
        Row(Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            on = !on; bumps++; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggle(on)
        }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.scale(sc.value).size(23.dp).clip(RoundedCornerShape(8.dp)).background(boxBg).border(1.5.dp, if (on) ink.ink else ink.faint, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                AnimatedVisibility(on, enter = scaleIn(spring(dampingRatio = .45f)) + fadeIn(), exit = fadeOut(tween(120))) { Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = ink.inv) }
            }
            Spacer(Modifier.width(12.dp))
            if (lead.isNotEmpty()) Text("$lead ", fontSize = 15.sp)
            Text(text, Modifier.weight(1f), color = txt, fontSize = 15.sp, textDecoration = if (on) TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis)
            trailing?.invoke()
            if (onOpen != null) IconBtn(Icons.AutoMirrored.Outlined.OpenInNew, "Open note", onOpen, size = 16.dp)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(ink.line))
    }
}

@Composable
fun XpHint(n: Int) = Text("+$n", color = LocalInk.current.faint, fontSize = 11.sp)

/** Segmented control with a sliding indicator. */
@Composable
fun Seg(items: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(14.dp)).background(ink.line).padding(3.dp)) {
        val w = maxWidth / items.size.coerceAtLeast(1)
        val idx = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        val x by animateDpAsState(w * idx, spring(dampingRatio = .8f, stiffness = 420f), label = "seg")
        Box(Modifier.offset(x = x).width(w).height(36.dp).clip(RoundedCornerShape(11.dp)).background(ink.card))
        Row(Modifier.fillMaxWidth().height(36.dp)) {
            items.forEach { (k, label) ->
                val c by animateColorAsState(if (k == selected) ink.ink else ink.mute, label = "segc")
                Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(k) }, contentAlignment = Alignment.Center) {
                    Text(label, color = c, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun <T> Chips(values: List<T>, selected: T?, label: (T) -> String, onPick: (T) -> Unit) {
    val ink = LocalInk.current
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            val on = v == selected
            val bg by animateColorAsState(if (on) ink.ink else Color.Transparent, label = "chip")
            Text(label(v), Modifier.clip(CircleShape).background(bg).border(1.dp, if (on) ink.ink else ink.line2, CircleShape).press({ onPick(v) }).padding(horizontal = 13.dp, vertical = 7.dp),
                color = if (on) ink.inv else ink.ink, fontSize = 13.sp)
        }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = true, onDone: (() -> Unit)? = null,
          style: TextStyle? = null, border: Boolean = true, minHeight: Dp = 0.dp, light: Boolean = false) {
    val ink = LocalInk.current
    val fg = if (light) Color.White else ink.ink
    val ts = (style ?: TextStyle(fontSize = 15.5.sp)).copy(color = fg)
    BasicTextField(value, onChange, modifier.fillMaxWidth(), textStyle = ts, singleLine = singleLine, cursorBrush = SolidColor(fg),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().then(if (border) Modifier.border(1.dp, if (light) Color(0x40FFFFFF) else ink.line2, RoundedCornerShape(12.dp)).padding(horizontal = 13.dp, vertical = 12.dp) else Modifier.padding(vertical = 6.dp))
                .then(if (minHeight > 0.dp) Modifier.height(minHeight) else Modifier)) {
                if (value.isEmpty()) Text(placeholder, style = ts.copy(color = if (light) Color(0x80FFFFFF) else ink.faint))
                inner()
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val ink = LocalInk.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ink.card, contentColor = ink.ink) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(title, fontFamily = Serif, fontSize = 26.sp, color = ink.ink)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun AskDialog(title: String, fields: List<Pair<String, String>>, ok: String, onDismiss: () -> Unit, onOk: (List<String>) -> Unit) {
    val ink = LocalInk.current
    val vals = remember { mutableStateListOf(*fields.map { it.second }.toTypedArray()) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = ink.card, titleContentColor = ink.ink,
        title = { Text(title, fontFamily = Serif, fontSize = 24.sp) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { fields.forEachIndexed { i, f -> Field(vals[i], { vals[i] = it }, f.first, onDone = { if (i == fields.size - 1) onOk(vals.toList()) }) } } },
        confirmButton = { TextButton({ onOk(vals.toList()) }) { Text(ok, color = ink.ink, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = ink.mute) } })
}

@Composable
fun LevelBlock(rpg: Rpg?, compact: Boolean = false) {
    val ink = LocalInk.current
    val size = if (compact) 46.dp else 58.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        val pct by animateFloatAsState(if (rpg == null) 0f else rpg.into.toFloat() / rpg.need, tween(700), label = "lvl")
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(size)) {
                val st = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(ink.line2, 0f, 360f, false, style = st)
                drawArc(ink.ink, -90f, 360f * pct, false, style = st)
            }
            Text(rpg?.level?.toString() ?: "·", fontFamily = Serif, fontSize = if (compact) 21.sp else 26.sp, color = ink.ink)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (rpg == null) { Muted("Reading your vault…") ; return@Column }
            Row { Text("Level ${rpg.level}", color = ink.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Text(" · ${rpg.rank}", color = ink.mute, fontSize = 15.sp) }
            Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(5.dp).clip(CircleShape).background(ink.line2)) {
                Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(ink.ink))
            }
            Muted("%,d XP · %,d to level %d".format(java.util.Locale.US, rpg.xp, rpg.need - rpg.into, rpg.level + 1), size = 12)
        }
    }
}

/** XP and achievement toasts float in at the top. */
@Composable
fun ToastHost(engine: Engine) {
    val ink = LocalInk.current
    val items = remember { mutableStateListOf<Pair<Long, com.stoa.app.vault.Toast>>() }
    LaunchedEffect(Unit) { engine.toasts.collect { t -> items.add(System.nanoTime() to t); while (items.size > 3) items.removeAt(0) } }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (id, t) ->
            androidx.compose.runtime.key(id) {
                val state = remember { MutableTransitionState(false).apply { targetState = true } }
                LaunchedEffect(Unit) { delay(if (t.kind == "level") 4000 else 2300); state.targetState = false; delay(400); items.removeAll { it.first == id } }
                AnimatedVisibility(state, enter = slideInVertically(spring(dampingRatio = .6f)) { -it } + fadeIn(), exit = slideOutVertically { -it / 2 } + fadeOut()) {
                    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(ink.ink).padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t.title, color = ink.inv, fontSize = if (t.kind == "level") 22.sp else 14.sp, fontFamily = when (t.kind) { "xp" -> Mono; "level" -> Serif; else -> null }, fontWeight = if (t.kind == "level") null else FontWeight.SemiBold)
                        if (t.sub.isNotEmpty()) Text(t.sub, color = ink.inv.copy(alpha = .7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(LocalInk.current.line)) }

@Composable
fun StatRow(vararg stats: Pair<String, String>) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth()) {
        stats.forEach { (n, l) -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(n, fontFamily = Serif, fontSize = 30.sp, color = ink.ink); Text(l, color = ink.mute, fontSize = 11.5.sp)
        } }
    }
}

val Border1 @Composable get() = BorderStroke(1.dp, LocalInk.current.line2)
