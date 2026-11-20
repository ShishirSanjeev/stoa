package com.stoa.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** A soft synthesised singing-bowl bell (no sound files needed). */
object Bell {
    fun ring(freq: Double = 528.0, seconds: Double = 3.5) {
        Thread {
            try {
                val rate = 44100; val n = (rate * seconds).toInt()
                val pcm = ShortArray(n)
                val partials = listOf(1.0 to 1.0, 2.01 to .45, 3.02 to .22, 4.2 to .1)
                for (i in 0 until n) {
                    val t = i.toDouble() / rate
                    var v = 0.0
                    for ((m, a) in partials) v += a * exp(-t * (1.1 + m * .9)) * sin(2 * PI * freq * m * t)
                    val attack = if (t < 0.012) t / 0.012 else 1.0
                    pcm[i] = (v * attack * 0.32 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(n * 2).build()
                track.write(pcm, 0, n); track.play()
                Thread.sleep((seconds * 1000).toLong() + 200); track.release()
            } catch (e: Exception) { }
        }.start()
    }
}
