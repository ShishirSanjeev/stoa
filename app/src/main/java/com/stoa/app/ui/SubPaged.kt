package com.stoa.app.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** A page whose sections sit side by side; swipe on the top half of the screen (or tap the tabs) to move between them. */
@Composable
fun SubPaged(title: String, kicker: String?, key: String, subs: List<Pair<String, String>>, current: String, onCurrent: (String) -> Unit, content: @Composable (String) -> Unit) {
    val ui = LocalUi.current
    val start = subs.indexOfFirst { it.first == current }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { subs.size }
    DisposableEffect(pager, key) { ui.subPagers[key] = pager; onDispose { if (ui.subPagers[key] === pager) ui.subPagers.remove(key) } }
    LaunchedEffect(pager, subs) { snapshotFlow { pager.settledPage }.collect { i -> subs.getOrNull(i)?.first?.let { if (it != current) onCurrent(it) } } }
    LaunchedEffect(current) { val i = subs.indexOfFirst { it.first == current }; if (i >= 0 && i != pager.currentPage && !pager.isScrollInProgress) pager.animateScrollToPage(i) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp)) {
            H1(title); if (kicker != null) Kicker(kicker)
        }
        HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), userScrollEnabled = false, key = { subs[it].first }) { i ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 28.dp)) { content(subs[i].first) }
        }
        if (subs.size > 1) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) { SegPager(subs, pager) { i -> scope.launch { pager.animateScrollToPage(i) } } }
    }
}

/** Tabs whose underline follows the pager as you swipe. */
@Composable
fun SegPager(items: List<Pair<String, String>>, pager: PagerState, onPick: (Int) -> Unit) {
    val ink = LocalInk.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
        val w = maxWidth / items.size.coerceAtLeast(1)
        val pos = pager.currentPage + pager.currentPageOffsetFraction
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.line)) { Box(Modifier.offset(x = w * pos + w * .2f).width(w * .6f).height(1.dp).background(ink.ink)) }
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                items.forEachIndexed { i, (_, label) ->
                    val near = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                    Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(i) }, contentAlignment = Alignment.Center) {
                        Text(label.uppercase(), color = lerp(ink.faint, ink.ink, near), fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
