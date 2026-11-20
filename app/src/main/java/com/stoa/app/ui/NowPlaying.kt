package com.stoa.app.ui

import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.stoa.app.Apps
import com.stoa.app.MediaListener
import kotlinx.coroutines.delay

private const val SPOTIFY = "com.spotify.music"

/** What Spotify is playing, shown while Bluetooth audio is connected. */
@Composable
fun NowPlaying() {
    val ui = LocalUi.current; val ctx = LocalContext.current; val ink = LocalInk.current
    val bt = rememberBluetoothAudio()
    val spotify = ui.apps.firstOrNull { it.pkg == SPOTIFY }
    val access = remember(ui.resume) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }
    var ctrl by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(access) {
        if (!access) return@DisposableEffect onDispose { }
        val msm = ctx.getSystemService(MediaSessionManager::class.java); val comp = ComponentName(ctx, MediaListener::class.java)
        fun pick(l: List<MediaController>?) = l?.firstOrNull { it.packageName == SPOTIFY }
        ctrl = try { pick(msm.getActiveSessions(comp)) } catch (e: SecurityException) { null }
        val l = MediaSessionManager.OnActiveSessionsChangedListener { ctrl = pick(it) }
        try { msm.addOnActiveSessionsChangedListener(l, comp) } catch (e: SecurityException) { }
        onDispose { try { msm.removeOnActiveSessionsChangedListener(l) } catch (e: Exception) { } }
    }
    var title by remember { mutableStateOf("") }; var artist by remember { mutableStateOf("") }; var art by remember { mutableStateOf<ImageBitmap?>(null) }
    var playing by remember { mutableStateOf(false) }; var dur by remember { mutableLongStateOf(0L) }; var posMs by remember { mutableLongStateOf(0L) }
    DisposableEffect(ctrl) {
        val c = ctrl ?: return@DisposableEffect onDispose { }
        fun meta(m: MediaMetadata?) {
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""; artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            dur = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            art = (m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART))?.asImageBitmap()
        }
        fun state(s: PlaybackState?) { playing = s?.state == PlaybackState.STATE_PLAYING; posMs = s?.position ?: 0L }
        meta(c.metadata); state(c.playbackState)
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { meta(metadata) }
            override fun onPlaybackStateChanged(st: PlaybackState?) { state(st) }
        }
        c.registerCallback(cb)
        onDispose { c.unregisterCallback(cb) }
    }
    LaunchedEffect(playing, ctrl) { while (playing) { ctrl?.playbackState?.let { s -> posMs = s.position + ((android.os.SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong() }; delay(1000) } }

    AnimatedVisibility(bt && spotify != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        StoaCard("Now playing", action = { Text("♪ BLUETOOTH", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.2.sp, color = ink.mute) }) {
            if (!access) {
                Muted("Allow notification access so Stoa can show what Spotify is playing.", size = 13)
                Row(Modifier.padding(top = 10.dp)) {
                    Pill("Allow access", { try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { } }, small = true, primary = true)
                    Spacer(Modifier.width(8.dp)); Pill("Open Spotify", { spotify?.let { Apps.launch(ctx, it) } }, small = true)
                }
                return@StoaCard
            }
            Row(Modifier.fillMaxWidth().clickable { spotify?.let { Apps.launch(ctx, it) } }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).background(ink.line)) { art?.let { Image(it, null, Modifier.size(58.dp), contentScale = ContentScale.Crop) } }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title.ifBlank { if (ctrl == null) "Spotify" else "Nothing playing" }, fontFamily = Display, fontSize = 17.sp, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist.ifBlank { if (ctrl == null) "Tap to open" else "" }.uppercase(), fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.2.sp, color = ink.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (dur > 0) Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(2.dp).background(ink.line2)) {
                        Box(Modifier.fillMaxWidth((posMs.toFloat() / dur).coerceIn(0f, 1f)).fillMaxHeight().background(ink.ink))
                    }
                }
            }
            ctrl?.let { c ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                    IconBtn(Icons.Outlined.SkipPrevious, "Previous", { c.transportControls.skipToPrevious() }, tint = ink.ink, size = 24.dp)
                    IconBtn(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play", { if (playing) c.transportControls.pause() else c.transportControls.play() }, tint = ink.ink, size = 28.dp)
                    IconBtn(Icons.Outlined.SkipNext, "Next", { c.transportControls.skipToNext() }, tint = ink.ink, size = 24.dp)
                }
            }
        }
    }
}
