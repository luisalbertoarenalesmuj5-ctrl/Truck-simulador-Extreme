package com.example.sim

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lightweight procedural Android AudioTrack synthesizer for authentic dual-tone truck air horn
 * and subtle cockpit alerts without requiring large external audio asset bundles.
 */
class TruckAudioEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    fun playAirHorn(durationMs: Int = 520) {
        activeJob?.cancel()
        activeJob = scope.launch {
            playDualTone(freqA = 292f, freqB = 368f, durationMs = durationMs, volume = 0.42f, brassy = true)
        }
    }

    fun playTollBeep() {
        scope.launch {
            playDualTone(freqA = 880f, freqB = 1320f, durationMs = 140, volume = 0.22f, brassy = false)
        }
    }

    fun release() {
        activeJob?.cancel()
        scope.cancel()
    }

    private fun playDualTone(
        freqA: Float,
        freqB: Float,
        durationMs: Int,
        volume: Float,
        brassy: Boolean
    ) {
        val sampleRate = 22050
        val totalSamples = (sampleRate * durationMs) / 1000
        if (totalSamples <= 0) return

        val pcm = ShortArray(totalSamples)
        val attackSamples = (sampleRate * 0.045f).toInt().coerceAtLeast(1)
        val releaseSamples = (sampleRate * 0.11f).toInt().coerceAtLeast(1)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate
            val env = when {
                i < attackSamples -> i.toFloat() / attackSamples
                i > totalSamples - releaseSamples -> ((totalSamples - i).toFloat() / releaseSamples).coerceAtLeast(0f)
                else -> 1f
            }
            val w1 = sin(2.0 * PI * freqA * t)
            val w2 = sin(2.0 * PI * freqB * t)
            val harmonic = if (brassy) {
                0.35 * sin(2.0 * PI * (freqA * 2.0) * t) + 0.22 * sin(2.0 * PI * (freqB * 3.0) * t)
            } else 0.0
            val mixed = ((w1 * 0.48 + w2 * 0.48 + harmonic * 0.35) * volume * env).coerceIn(-0.98, 0.98)
            pcm[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(totalSamples * 2)

        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 40L)
        } catch (_: Throwable) {
            // Audio hardware unavailable (e.g., Robolectric/headless unit test) — fail silently.
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Throwable) {
            }
        }
    }
}
