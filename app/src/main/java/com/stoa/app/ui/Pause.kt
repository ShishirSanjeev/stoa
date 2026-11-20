package com.stoa.app.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Breathe for six seconds, then choose. Used by the accessibility pause and by the drawer. */
@Composable
fun PauseScreen(label: String, resisted: Int, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val ink = LocalInk.current
    var left by remember { mutableIntStateOf(6) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
    val breath = rememberInfiniteTransition(label = "breath").animateFloat(.55f, 1f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Box(Modifier.fillMaxSize().background(ink.bg).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(180.dp).graphicsLayer { scaleX = breath.value; scaleY = breath.value }.clip(CircleShape).background(ink.line).border(1.dp, ink.ink, CircleShape))
            Spacer(Modifier.height(8.dp))
            Text(if (breath.value > .8f) "Breathe out." else "Breathe in.", fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 32.sp, color = ink.ink)
            Text("You're about to open $label.\nIs this a choice, or a reflex?", color = ink.mute, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
            if (left > 0) Text("$left", fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 44.sp, color = ink.ink)
            AnimatedVisibility(left == 0, enter = fadeIn() + slideInVertically { it / 3 }) {
                Column(Modifier.widthIn(max = 320.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Not now — go back", onBack, Modifier.fillMaxWidth(), primary = true)
                    Pill("Open $label for 10 minutes", { onOpen(10) }, Modifier.fillMaxWidth())
                }
            }
            Text("$resisted urge${if (resisted == 1) "" else "s"} resisted today", color = ink.faint, fontSize = 12.sp)
        }
    }
}
