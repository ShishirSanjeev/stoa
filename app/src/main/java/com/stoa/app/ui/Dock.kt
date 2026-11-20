package com.stoa.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoa.app.Apps
import com.stoa.app.LApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private val GREY_CF = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Frosted glass: your wallpaper, blurred and lined up behind this shape (or a translucent tint without one). */
@Composable
fun Glass(shape: Shape, modifier: Modifier = Modifier) {
    val ui = LocalUi.current; val ink = LocalInk.current
    val bmp = ui.wpBmp
    var pos by remember { mutableStateOf(Offset.Zero) }; var root by remember { mutableStateOf(IntSize(1, 1)) }
    Box(modifier.clip(shape)) {
        if (ui.wpOn && bmp != null) {
            Canvas(Modifier.fillMaxSize().onGloballyPositioned { pos = it.positionInRoot(); root = it.findRootCoordinates().size }.blur(26.dp)) {
                val sc = maxOf(root.width / bmp.width.toFloat(), root.height / bmp.height.toFloat())
                val w = (bmp.width * sc).roundToInt(); val h = (bmp.height * sc).roundToInt()
                drawImage(bmp, dstOffset = IntOffset(((root.width - w) / 2f - pos.x).roundToInt(), ((root.height - h) / 2f - pos.y).roundToInt()), dstSize = IntSize(w, h))
            }
            Box(Modifier.fillMaxSize().background(if (ink.dark) Color(0x33FFFFFF) else Color(0x66FFFFFF)))
        } else Box(Modifier.fillMaxSize().background(if (ink.dark) Color(0xCC1C1C1E) else Color(0xCCF7F6F3)))
        Box(Modifier.fillMaxSize().border(1.dp, if (ink.dark) Color(0x24FFFFFF) else Color(0x14000000), shape))
    }
}

fun appFor(ui: UiState, key: String): LApp? = if (key.startsWith("app:")) key.removePrefix("app:").let { k -> ui.apps.firstOrNull { it.pkg + "/" + it.cls == k } ?: ui.apps.firstOrNull { it.pkg == k.substringBefore('/') } } else null
fun labelFor(ui: UiState, key: String): String = appFor(ui, key)?.label ?: SECTIONS[key.removePrefix("sec:")] ?: key.substringAfterLast('/').substringAfterLast('.')

@Composable
fun AppIcon(key: String, size: Dp) {
    val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current
    val app = appFor(ui, key)
    if (app == null) {
        Box(Modifier.size(size).clip(RoundedCornerShape(size * .28f)).background(ink.ink), contentAlignment = Alignment.Center) {
            Text(labelFor(ui, key).take(1), fontFamily = Display, fontWeight = FontWeight.Light, fontSize = (size.value * .46f).sp, color = ink.inv)
        }
        return
    }
    val bmp = produceState<ImageBitmap?>(null, key, ui.iconPack) { value = withContext(Dispatchers.IO) { Apps.icon(ctx, app) } }.value
    Box(Modifier.size(size), contentAlignment = Alignment.Center) { if (bmp != null) Image(bmp, app.label, Modifier.size(size), colorFilter = if (ui.greyIcons) GREY_CF else null) }
}

@Composable
fun FolderIcon(item: DockItem, size: Dp) {
    val ink = LocalInk.current
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .28f)).background(if (ink.dark) Color(0x2EFFFFFF) else Color(0x1A000000)).padding(size * .14f)) {
        Column(verticalArrangement = Arrangement.spacedBy(size * .06f)) {
            item.apps.take(4).chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(size * .06f)) { row.forEach { AppIcon(it, size * .33f) } } }
        }
    }
}

/** The dock: frosted, collapsible, long-press to drag (drop onto another icon to make a folder). Icons shrink to fit as you add more. */
@Composable
fun Dock(modifier: Modifier = Modifier) {
    val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current; val haptic = LocalHapticFeedback.current; val dens = LocalDensity.current
    val items = ui.dock
    var menuFor by remember { mutableStateOf<DockItem?>(null) }
    AnimatedContent(ui.dockCollapsed || items.isEmpty(), modifier, label = "dock", transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = .92f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = .92f)) }) { collapsed ->
        if (collapsed) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.padding(bottom = 6.dp).height(34.dp).widthIn(min = 120.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (items.isNotEmpty()) ui.setCollapsed(false) }) {
                    Glass(RoundedCornerShape(17.dp), Modifier.matchParentSize())
                    Text(if (items.isEmpty()) "LONG-PRESS AN APP → ADD TO DOCK" else "•  •  •", Modifier.align(Alignment.Center).padding(horizontal = 16.dp), fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.4.sp, color = ink.mute)
                }
            }
        } else BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            val n = items.size.coerceAtLeast(1)
            val slot = (maxWidth - 16.dp) / n
            val target = (slot * .78f).coerceIn(34.dp, 58.dp)
            val iconSize by animateDpAsState(target, spring(dampingRatio = .75f, stiffness = 300f), label = "icon")
            val slotPx = with(dens) { slot.toPx() }
            var dragging by remember { mutableStateOf(-1) }; var dragX by remember { mutableFloatStateOf(0f) }
            val pos = if (dragging >= 0) dragging + dragX / slotPx else -1f
            val to = if (dragging >= 0) pos.roundToInt().coerceIn(0, items.size - 1) else -1
            val merging = dragging >= 0 && to != dragging && abs(pos - to) < .22f
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // grabber: tap or swipe down to tuck the dock away
                Box(Modifier.width(60.dp).height(16.dp).pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 6) ui.setCollapsed(true) } }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ui.setCollapsed(true) }, contentAlignment = Alignment.Center) {
                    Box(Modifier.width(30.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(ink.faint))
                }
                Box(Modifier.fillMaxWidth().height(iconSize + 26.dp)) {
                    Glass(RoundedCornerShape(28.dp), Modifier.matchParentSize())
                    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        items.forEachIndexed { i, it ->
                            val shift = when {
                                dragging < 0 || i == dragging || merging -> 0f
                                to > dragging && i in (dragging + 1)..to -> -slotPx
                                to < dragging && i in to until dragging -> slotPx
                                else -> 0f
                            }
                            val sx by animateFloatAsState(shift, spring(dampingRatio = .8f, stiffness = 500f), label = "shift")
                            val lift by animateFloatAsState(if (i == dragging) 1.12f else if (merging && i == to) 1.15f else 1f, spring(dampingRatio = .6f), label = "lift")
                            Box(Modifier.width(slot).fillMaxSize().graphicsLayer { translationX = if (i == dragging) dragX else sx; scaleX = lift; scaleY = lift; alpha = if (i == dragging) .9f else 1f }
                                .pointerInput(it.id, slotPx) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var moved = false
                                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                            var r: androidx.compose.ui.input.pointer.PointerInputChange? = null
                                            while (true) {
                                                val c = awaitPointerEvent().changes.firstOrNull { ch -> ch.id == down.id } ?: break
                                                if (c.changedToUp()) { r = c; break }
                                                if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) { moved = true; break }
                                            }
                                            r
                                        }
                                        if (moved) return@awaitEachGesture
                                        val idx = ui.dock.indexOfFirst { d -> d.id == it.id }
                                        if (up != null) { up.consume(); if (it.isFolder) ui.openFolder = it.id else openEntryKey(ctx, ui, it.key); return@awaitEachGesture }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragging = idx; dragX = 0f
                                        while (true) {
                                            val c = awaitPointerEvent().changes.firstOrNull { ch -> ch.id == down.id } ?: break
                                            if (c.changedToUp()) { c.consume(); break }
                                            dragX += c.positionChange().x; c.consume()
                                        }
                                        val p = idx + dragX / slotPx; val t = p.roundToInt().coerceIn(0, ui.dock.size - 1)
                                        when {
                                            abs(dragX) < 12f -> menuFor = it
                                            t != idx && abs(p - t) < .22f -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); ui.dockMerge(idx, t) }
                                            t != idx -> ui.dockMove(idx, t)
                                        }
                                        dragging = -1; dragX = 0f
                                    }
                                }, contentAlignment = Alignment.Center) {
                                if (it.isFolder) FolderIcon(it, iconSize) else AppIcon(it.key, iconSize)
                            }
                        }
                    }
                }
            }
        }
    }
    menuFor?.let { d -> DockMenu(d) { menuFor = null } }
}

fun openEntryKey(ctx: android.content.Context, ui: UiState, key: String) {
    val app = appFor(ui, key)
    if (app != null) openEntry(ctx, ui, Entry(key, app.label, app.label.take(1), app))
    else if (key.startsWith("sec:")) { ui.go(key.removePrefix("sec:")) }
}

@Composable
private fun DockMenu(d: DockItem, dismiss: () -> Unit) {
    val ui = LocalUi.current
    Sheet(if (d.isFolder) d.name.ifBlank { "Folder" } else labelFor(ui, d.key), dismiss) {
        if (d.isFolder) {
            var name by remember { mutableStateOf(d.name) }
            Field(name, { name = it; ui.dockRename(d.id, it) }, "Folder name")
            Spacer(Modifier.height(8.dp))
            MenuItem("Open folder") { ui.openFolder = d.id; dismiss() }
            MenuItem("Take apps out of the folder") { val i = ui.dock.indexOfFirst { it.id == d.id }; val l = ui.dock.toMutableList(); l.removeAt(i); l.addAll(i, d.apps.map { DockItem(java.util.UUID.randomUUID().toString().take(8), it) }); ui.saveDock(l); dismiss() }
            MenuItem("Remove folder from dock") { ui.saveDock(ui.dock.filter { it.id != d.id }); dismiss() }
        } else {
            MenuItem("Remove from dock") { ui.dockRemove(d.key); dismiss() }
            ui.dock.filter { it.isFolder }.forEach { f -> MenuItem("Move into “${f.name.ifBlank { "Folder" }}”") { ui.dockAddToFolder(f.id, d.key); dismiss() } }
        }
        Muted("Long-press and drag to re-order. Drop an icon onto another to make a folder.", Modifier.padding(top = 10.dp), 12)
    }
}

/** A folder opens as a frosted card above the dock. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderOverlay() {
    val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current
    val f = ui.dock.firstOrNull { it.id == ui.openFolder }
    AnimatedVisibility(f != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ui.openFolder = null }, contentAlignment = Alignment.Center) {
            val folder = f ?: return@Box
            val sc by animateFloatAsState(1f, spring(dampingRatio = .7f), label = "fold")
            Box(Modifier.padding(28.dp).fillMaxWidth().graphicsLayer { scaleX = sc; scaleY = sc }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }) {
                Glass(RoundedCornerShape(30.dp), Modifier.matchParentSize())
                Column(Modifier.padding(22.dp)) {
                    Text(folder.name.ifBlank { "Folder" }, fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 26.sp, color = ink.ink)
                    Spacer(Modifier.height(16.dp))
                    folder.apps.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                            row.forEach { k ->
                                Column(Modifier.weight(1f).combinedClickable(onClick = { ui.openFolder = null; openEntryKey(ctx, ui, k) }, onLongClick = { ui.dockRemove(k) }), horizontalAlignment = Alignment.CenterHorizontally) {
                                    AppIcon(k, 52.dp); Spacer(Modifier.height(6.dp))
                                    Text(labelFor(ui, k), fontSize = 11.sp, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Muted("Long-press an app to take it out.", size = 11)
                }
            }
        }
    }
}

