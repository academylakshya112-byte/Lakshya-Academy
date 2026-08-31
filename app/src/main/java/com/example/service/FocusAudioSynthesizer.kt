package com.example.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import java.util.Random
import kotlin.math.sin

/**
 * High-performance, 100% offline procedural audio synthesizer for Focus Study Mode.
 * Synthesizes calming ambient soundscapes (Rain, White Noise, Nature, Lo-Fi Ambience, Cosmic)
 * without requiring large audio files or internet connectivity.
 */
object FocusAudioSynthesizer {
    private const val TAG = "FocusAudioSynth"
    private const val SAMPLE_RATE = 22050
    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var currentSoundType: String = "RAIN" // RAIN, LOFI, NATURE, WHITE_NOISE, COSMIC

    @Volatile
    var volume: Float = 0.7f
        set(value) {
            field = value.coerceIn(0f, 1f)
            audioTrack?.setVolume(field)
        }

    fun playSound(soundType: String, soundVolume: Float = volume) {
        if (soundType.equals("NONE", ignoreCase = true)) {
            stopSound()
            return
        }
        currentSoundType = soundType
        volume = soundVolume

        if (isPlaying) {
            // Already generating, sound loop will seamlessly morph to new type
            return
        }

        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.setVolume(volume)
            audioTrack?.play()
            isPlaying = true

            synthJob?.cancel()
            synthJob = scope.launch {
                val random = Random()
                val chunk = ShortArray(2048)
                var phase1 = 0.0
                var phase2 = 0.0
                var filterState = 0.0
                var tick = 0L

                while (isActive && isPlaying) {
                    val mode = currentSoundType
                    for (i in chunk.indices) {
                        tick++
                        val sample: Double = when (mode.uppercase()) {
                            "WHITE_NOISE" -> {
                                (random.nextDouble() * 2.0 - 1.0) * 0.35
                            }
                            "RAIN" -> {
                                // Filtered pink noise + occasional raindrop pop
                                val white = random.nextDouble() * 2.0 - 1.0
                                filterState = filterState * 0.94 + white * 0.06
                                val drop = if (random.nextInt(400) == 0) (random.nextDouble() - 0.5) * 0.4 else 0.0
                                (filterState * 2.2 + drop) * 0.4
                            }
                            "NATURE" -> {
                                // Gentle undulating water/breeze wave
                                val lfo = sin(tick * 0.0003) * 0.5 + 0.5
                                val white = random.nextDouble() * 2.0 - 1.0
                                filterState = filterState * (0.85 + lfo * 0.1) + white * 0.08
                                filterState * 0.45
                            }
                            "LOFI" -> {
                                // Warm chord drone (110Hz + 165Hz + 220Hz) + vinyl crackle
                                phase1 += 2.0 * Math.PI * 110.0 / SAMPLE_RATE
                                phase2 += 2.0 * Math.PI * 165.0 / SAMPLE_RATE
                                val drone = (sin(phase1) * 0.35 + sin(phase2) * 0.25)
                                val crackle = if (random.nextInt(600) == 0) (random.nextDouble() - 0.5) * 0.2 else 0.0
                                (drone + crackle) * 0.4
                            }
                            "COSMIC" -> {
                                // Binaural deep cosmic focus sine swell
                                phase1 += 2.0 * Math.PI * 96.0 / SAMPLE_RATE
                                phase2 += 2.0 * Math.PI * 102.0 / SAMPLE_RATE
                                val swell = sin(tick * 0.00015) * 0.3 + 0.7
                                ((sin(phase1) + sin(phase2)) * 0.3 * swell)
                            }
                            else -> 0.0
                        }

                        val clamped = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        chunk[i] = clamped.toShort()
                    }

                    audioTrack?.write(chunk, 0, chunk.size)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack: ${e.message}", e)
            isPlaying = false
        }
    }

    fun stopSound() {
        isPlaying = false
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio: ${e.message}")
        } finally {
            audioTrack = null
        }
    }
}
