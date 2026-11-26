package com.example.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

enum class BunnySfx {
    COMPLETE,
    UNCHECK,
    CELEBRATE,
    DISMISS,
    SNOOZE,
    SAVE,
    DELETE,
    POP,
    TICK,
    HABIT_CHECK,
    HABIT_ALL_DONE_REWARD,
    HABIT_STREAK_FANFARE
}

enum class BunnyVoiceMood {
    GREETING,
    REMINDER,
    HAPPY_PAT,
    PINCH_CHEEK,
    SAD_MISSED,
    LISTENING,
    HABIT_REWARD,
    AUDIO_MEMO_FEEDBACK
}

/**
 * Pure Kotlin 16-bit PCM 8-bit chiptune synthesizer for Bunny Memo.
 * Generates warm, non-fatiguing cozy chimes and a seamless subtle 8-bit lullaby loop
 * ("Forget-Me-Not Meadow") with zero external audio asset overhead.
 */
object ChiptuneSynthesizer {

    const val SAMPLE_RATE = 22_050

    private const val TWO_PI = 2.0 * PI

    // MIDI note helper (A4 = 69 = 440 Hz)
    private fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    // Soft 25%-duty chiptune pulse blended with a warm sine fundamental so it sounds cute & cozy
    private fun softPulse25(phase01: Double): Double {
        val rawPulse = if (phase01 < 0.25) 0.72 else -0.24
        val sineWarmth = sin(phase01 * TWO_PI)
        return rawPulse * 0.42 + sineWarmth * 0.58
    }

    // Classic NES-style warm triangle wave
    private fun triangleWave(phase01: Double): Double {
        return when {
            phase01 < 0.25 -> 4.0 * phase01
            phase01 < 0.75 -> 2.0 - 4.0 * phase01
            else -> 4.0 * phase01 - 4.0
        }
    }

    // Gentle sparkle bell (sine + quiet 2nd harmonic + tiny 8-bit square shimmer)
    private fun musicBoxBell(phase01: Double): Double {
        val fund = sin(phase01 * TWO_PI)
        val overtone = sin(phase01 * 2.0 * TWO_PI) * 0.22
        val shimmer = (if (phase01 < 0.5) 1.0 else -1.0) * 0.14
        return (fund * 0.74 + overtone + shimmer).coerceIn(-1.0, 1.0)
    }

    // Warm electric-piano / Rhodes morning tone for relaxed study BGM
    private fun warmRhodesTone(phase01: Double): Double {
        val fund = sin(phase01 * TWO_PI)
        val harm2 = sin(phase01 * 2.0 * TWO_PI) * 0.16
        val harm3 = sin(phase01 * 3.0 * TWO_PI) * 0.04
        return (fund * 0.80 + harm2 + harm3).coerceIn(-1.0, 1.0)
    }

    private fun renderToneNote(
        buffer: DoubleArray,
        startSample: Int,
        durationSec: Double,
        freqStartHz: Double,
        freqEndHz: Double = freqStartHz,
        amplitude: Double,
        attackSec: Double = 0.008,
        decayRate: Double = 5.5,
        waveform: (Double) -> Double = ::softPulse25
    ) {
        val totalSamples = (durationSec * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val attackSamples = (attackSec * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val releaseSamples = (0.014 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        var phase = 0.0

        for (i in 0 until totalSamples) {
            val idx = startSample + i
            if (idx !in buffer.indices) continue
            val tNorm = i.toDouble() / totalSamples.toDouble()
            val freq = freqStartHz + (freqEndHz - freqStartHz) * tNorm
            phase = (phase + freq / SAMPLE_RATE) % 1.0

            val attackEnv = if (i < attackSamples) i.toDouble() / attackSamples.toDouble() else 1.0
            val decayEnv = exp(-decayRate * (i.toDouble() / SAMPLE_RATE))
            val releaseEnv = if (i > totalSamples - releaseSamples) {
                ((totalSamples - i).toDouble() / releaseSamples.toDouble()).coerceIn(0.0, 1.0)
            } else {
                1.0
            }

            buffer[idx] += waveform(phase) * amplitude * attackEnv * decayEnv * releaseEnv
        }
    }

    private fun toPcm16(samples: DoubleArray, masterGain: Double = 0.88): ShortArray {
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            // Gentle soft-clipping to avoid any digital harshness
            val scaled = (samples[i] * masterGain).coerceIn(-0.98, 0.98)
            out[i] = (scaled * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    fun synthesizeSfx(sfx: BunnySfx): ShortArray {
        return when (sfx) {
            // Cozy ascending C6 -> E6 -> G6 bloom chime
            BunnySfx.COMPLETE -> {
                val buf = DoubleArray((0.34 * SAMPLE_RATE).toInt())
                renderToneNote(buf, (0.00 * SAMPLE_RATE).toInt(), 0.13, midiToFreq(84), amplitude = 0.36, decayRate = 8.0, waveform = ::musicBoxBell)
                renderToneNote(buf, (0.06 * SAMPLE_RATE).toInt(), 0.15, midiToFreq(88), amplitude = 0.40, decayRate = 7.5, waveform = ::musicBoxBell)
                renderToneNote(buf, (0.12 * SAMPLE_RATE).toInt(), 0.21, midiToFreq(91), amplitude = 0.44, decayRate = 6.0, waveform = ::musicBoxBell)
                // Soft warm bass bloom underneath
                renderToneNote(buf, (0.12 * SAMPLE_RATE).toInt(), 0.18, midiToFreq(67), amplitude = 0.16, decayRate = 7.0, waveform = ::triangleWave)
                toPcm16(buf, 0.78)
            }

            // Gentle soft descending 2-note uncheck blip
            BunnySfx.UNCHECK -> {
                val buf = DoubleArray((0.17 * SAMPLE_RATE).toInt())
                renderToneNote(buf, 0, 0.09, midiToFreq(76), amplitude = 0.28, decayRate = 12.0, waveform = ::softPulse25)
                renderToneNote(buf, (0.06 * SAMPLE_RATE).toInt(), 0.10, midiToFreq(72), amplitude = 0.24, decayRate = 12.0, waveform = ::softPulse25)
                toPcm16(buf, 0.72)
            }

            // Joyful 8-bit forget-me-not bloom celebration arpeggio (C5 -> E5 -> G5 -> B5 -> C6 -> E6)
            BunnySfx.CELEBRATE -> {
                val buf = DoubleArray((0.72 * SAMPLE_RATE).toInt())
                val notes = intArrayOf(72, 76, 79, 83, 84, 88)
                notes.forEachIndexed { idx, midi ->
                    val start = (idx * 0.068 * SAMPLE_RATE).toInt()
                    val dur = if (idx == notes.lastIndex) 0.34 else 0.16
                    renderToneNote(
                        buffer = buf,
                        startSample = start,
                        durationSec = dur,
                        freqStartHz = midiToFreq(midi),
                        amplitude = 0.34 + idx * 0.02,
                        decayRate = if (idx == notes.lastIndex) 4.2 else 7.5,
                        waveform = ::musicBoxBell
                    )
                }
                // Warm C-major triangle chord foundation on the final bloom
                renderToneNote(buf, (0.34 * SAMPLE_RATE).toInt(), 0.36, midiToFreq(60), amplitude = 0.20, decayRate = 4.5, waveform = ::triangleWave)
                renderToneNote(buf, (0.34 * SAMPLE_RATE).toInt(), 0.36, midiToFreq(67), amplitude = 0.15, decayRate = 4.5, waveform = ::triangleWave)
                toPcm16(buf, 0.80)
            }

            // Soft airy downward chirp when dismissing a reminder
            BunnySfx.DISMISS -> {
                val buf = DoubleArray((0.20 * SAMPLE_RATE).toInt())
                renderToneNote(buf, 0, 0.10, midiToFreq(79), midiToFreq(74), amplitude = 0.30, decayRate = 11.0, waveform = ::softPulse25)
                renderToneNote(buf, (0.06 * SAMPLE_RATE).toInt(), 0.13, midiToFreq(74), midiToFreq(69), amplitude = 0.22, decayRate = 11.0, waveform = ::triangleWave)
                toPcm16(buf, 0.72)
            }

            // Dreamy cozy 2-note snooze chime
            BunnySfx.SNOOZE -> {
                val buf = DoubleArray((0.28 * SAMPLE_RATE).toInt())
                renderToneNote(buf, 0, 0.14, midiToFreq(81), amplitude = 0.32, decayRate = 8.0, waveform = ::musicBoxBell)
                renderToneNote(buf, (0.10 * SAMPLE_RATE).toInt(), 0.17, midiToFreq(76), amplitude = 0.28, decayRate = 7.0, waveform = ::musicBoxBell)
                toPcm16(buf, 0.72)
            }

            // Crisp happy 2-note memo saved chime (G5 -> C6)
            BunnySfx.SAVE -> {
                val buf = DoubleArray((0.24 * SAMPLE_RATE).toInt())
                renderToneNote(buf, 0, 0.10, midiToFreq(79), amplitude = 0.32, decayRate = 9.0, waveform = ::musicBoxBell)
                renderToneNote(buf, (0.075 * SAMPLE_RATE).toInt(), 0.16, midiToFreq(84), amplitude = 0.38, decayRate = 6.5, waveform = ::musicBoxBell)
                toPcm16(buf, 0.74)
            }

            // Soft low pluck when deleting an item
            BunnySfx.DELETE -> {
                val buf = DoubleArray((0.21 * SAMPLE_RATE).toInt())
                renderToneNote(buf, 0, 0.10, midiToFreq(62), midiToFreq(59), amplitude = 0.34, decayRate = 11.0, waveform = ::triangleWave)
                renderToneNote(buf, (0.07 * SAMPLE_RATE).toInt(), 0.13, midiToFreq(57), midiToFreq(53), amplitude = 0.28, decayRate = 10.0, waveform = ::triangleWave)
                toPcm16(buf, 0.72)
            }

            // Cute tiny upward bubble pop when opening Quick Note or Add modals
            BunnySfx.POP -> {
                val buf = DoubleArray((0.085 * SAMPLE_RATE).toInt())
                renderToneNote(buf, 0, 0.08, 490.0, 820.0, amplitude = 0.30, attackSec = 0.004, decayRate = 18.0, waveform = ::softPulse25)
                toPcm16(buf, 0.68)
            }

            // Whisper-soft wooden 8-bit tick for tab switches and date taps
            BunnySfx.TICK -> {
                val buf = DoubleArray((0.045 * SAMPLE_RATE).toInt())
                renderToneNote(buf, 0, 0.04, 680.0, 560.0, amplitude = 0.22, attackSec = 0.002, decayRate = 36.0, waveform = ::triangleWave)
                toPcm16(buf, 0.62)
            }

            // Dewdrop petal 8-bit sparkle when checking off a daily habit
            BunnySfx.HABIT_CHECK -> {
                val buf = DoubleArray((0.36 * SAMPLE_RATE).toInt())
                renderToneNote(buf, 0, 0.11, midiToFreq(79), midiToFreq(84), amplitude = 0.34, decayRate = 8.5, waveform = ::musicBoxBell)
                renderToneNote(buf, (0.075 * SAMPLE_RATE).toInt(), 0.12, midiToFreq(86), amplitude = 0.36, decayRate = 7.5, waveform = ::softPulse25)
                renderToneNote(buf, (0.145 * SAMPLE_RATE).toInt(), 0.20, midiToFreq(91), amplitude = 0.42, decayRate = 5.8, waveform = ::musicBoxBell)
                toPcm16(buf, 0.78)
            }

            // Golden 8-bit level-up reward chime for finishing all daily habits
            BunnySfx.HABIT_ALL_DONE_REWARD -> {
                val buf = DoubleArray((0.88 * SAMPLE_RATE).toInt())
                val notes = intArrayOf(72, 76, 79, 84, 88, 91, 96)
                notes.forEachIndexed { idx, midi ->
                    val start = (idx * 0.072 * SAMPLE_RATE).toInt()
                    val dur = if (idx == notes.lastIndex) 0.38 else 0.16
                    renderToneNote(
                        buffer = buf,
                        startSample = start,
                        durationSec = dur,
                        freqStartHz = midiToFreq(midi),
                        amplitude = 0.32 + idx * 0.015,
                        decayRate = if (idx == notes.lastIndex) 3.8 else 7.2,
                        waveform = if (idx % 2 == 0) ::musicBoxBell else ::softPulse25
                    )
                }
                renderToneNote(buf, (0.44 * SAMPLE_RATE).toInt(), 0.40, midiToFreq(60), amplitude = 0.22, decayRate = 4.2, waveform = ::triangleWave)
                renderToneNote(buf, (0.44 * SAMPLE_RATE).toInt(), 0.40, midiToFreq(67), amplitude = 0.16, decayRate = 4.2, waveform = ::triangleWave)
                toPcm16(buf, 0.82)
            }

            // Special retro 8-bit hero victory melody for streak milestones!
            BunnySfx.HABIT_STREAK_FANFARE -> {
                val buf = DoubleArray((1.15 * SAMPLE_RATE).toInt())
                val lead = listOf(
                    0.00 to (79 to 0.10), // G5
                    0.10 to (84 to 0.10), // C6
                    0.20 to (88 to 0.10), // E6
                    0.30 to (91 to 0.20), // G6
                    0.50 to (88 to 0.11), // E6
                    0.62 to (91 to 0.45)  // G6!
                )
                lead.forEach { (startSec, pair) ->
                    val (midi, dur) = pair
                    renderToneNote(
                        buffer = buf,
                        startSample = (startSec * SAMPLE_RATE).toInt(),
                        durationSec = dur,
                        freqStartHz = midiToFreq(midi),
                        amplitude = 0.38,
                        decayRate = if (dur > 0.3) 3.4 else 6.8,
                        waveform = ::softPulse25
                    )
                    renderToneNote(
                        buffer = buf,
                        startSample = (startSec * SAMPLE_RATE).toInt(),
                        durationSec = dur,
                        freqStartHz = midiToFreq(midi + 12),
                        amplitude = 0.16,
                        decayRate = 8.0,
                        waveform = ::musicBoxBell
                    )
                }
                // Warm triangle bass under the fanfare climax
                renderToneNote(buf, (0.30 * SAMPLE_RATE).toInt(), 0.22, midiToFreq(60), amplitude = 0.22, decayRate = 4.5, waveform = ::triangleWave)
                renderToneNote(buf, (0.62 * SAMPLE_RATE).toInt(), 0.48, midiToFreq(60), amplitude = 0.25, decayRate = 3.5, waveform = ::triangleWave)
                renderToneNote(buf, (0.62 * SAMPLE_RATE).toInt(), 0.48, midiToFreq(67), amplitude = 0.18, decayRate = 3.5, waveform = ::triangleWave)
                toPcm16(buf, 0.84)
            }
        }
    }

    /**
     * Cute 8-bit chiptune voice syllables (Animal-Crossing / retro handheld style)
     * tailored to each bunny mood and interaction.
     */
    fun synthesizeBunnyVoice(mood: BunnyVoiceMood): ShortArray {
        data class Syllable(
            val startSec: Double,
            val durationSec: Double,
            val startMidi: Int,
            val endMidi: Int,
            val amp: Double = 0.36
        )

        val syllables: List<Syllable> = when (mood) {
            // "Hii! How are you~?" - curious ascending chatter ending on a bright rising pitch
            BunnyVoiceMood.GREETING -> listOf(
                Syllable(0.00, 0.075, 79, 81, 0.33),
                Syllable(0.085, 0.065, 83, 81, 0.31),
                Syllable(0.165, 0.070, 84, 86, 0.34),
                Syllable(0.250, 0.070, 81, 84, 0.33),
                Syllable(0.335, 0.135, 84, 91, 0.39)
            )
            // "Don't forget your task!" - crisp rhythmic reminder cadence
            BunnyVoiceMood.REMINDER -> listOf(
                Syllable(0.00, 0.065, 84, 84, 0.34),
                Syllable(0.075, 0.065, 88, 86, 0.34),
                Syllable(0.155, 0.060, 84, 84, 0.33),
                Syllable(0.225, 0.075, 86, 89, 0.36),
                Syllable(0.315, 0.095, 91, 88, 0.36)
            )
            // "Hehe~ Happy purr-chirps!" - sweet bouncy joyful upward arpeggio chatter
            BunnyVoiceMood.HAPPY_PAT -> listOf(
                Syllable(0.00, 0.070, 84, 88, 0.34),
                Syllable(0.075, 0.065, 88, 91, 0.36),
                Syllable(0.148, 0.065, 89, 93, 0.37),
                Syllable(0.220, 0.075, 91, 95, 0.38),
                Syllable(0.305, 0.145, 93, 98, 0.40)
            )
            // "Mwee~! Squishy cheek squeak!" - playful fast pitch-bending high squeaks
            BunnyVoiceMood.PINCH_CHEEK -> listOf(
                Syllable(0.00, 0.090, 88, 97, 0.38),
                Syllable(0.100, 0.070, 95, 91, 0.35),
                Syllable(0.180, 0.105, 90, 98, 0.39),
                Syllable(0.295, 0.085, 96, 93, 0.34)
            )
            // "Mweeh... we missed one..." - gentle droopy descending minor sigh
            BunnyVoiceMood.SAD_MISSED -> listOf(
                Syllable(0.00, 0.115, 76, 74, 0.32),
                Syllable(0.140, 0.120, 74, 71, 0.30),
                Syllable(0.290, 0.110, 72, 69, 0.28),
                Syllable(0.430, 0.175, 69, 64, 0.27)
            )
            // "Hmm~? I'm listening!" - attentive perky double chirp
            BunnyVoiceMood.LISTENING -> listOf(
                Syllable(0.00, 0.070, 81, 84, 0.30),
                Syllable(0.085, 0.110, 84, 89, 0.34)
            )
            // "Tada~ Habit streak reward!" - sparkling joyful bunny cheer
            BunnyVoiceMood.HABIT_REWARD -> listOf(
                Syllable(0.00, 0.065, 84, 88, 0.36),
                Syllable(0.072, 0.065, 88, 91, 0.37),
                Syllable(0.145, 0.065, 91, 96, 0.39),
                Syllable(0.220, 0.150, 93, 98, 0.40)
            )
            // "Ooh, got your voice note!" - warm attentive melodic reply
            BunnyVoiceMood.AUDIO_MEMO_FEEDBACK -> listOf(
                Syllable(0.00, 0.075, 81, 84, 0.34),
                Syllable(0.085, 0.070, 86, 84, 0.33),
                Syllable(0.165, 0.075, 88, 91, 0.36),
                Syllable(0.255, 0.120, 89, 93, 0.37)
            )
        }

        val maxEndSec = syllables.maxOf { it.startSec + it.durationSec } + 0.05
        val buf = DoubleArray((maxEndSec * SAMPLE_RATE).toInt().coerceAtLeast(1024))

        val waveform: (Double) -> Double = when (mood) {
            BunnyVoiceMood.SAD_MISSED -> ::triangleWave
            BunnyVoiceMood.PINCH_CHEEK,
            BunnyVoiceMood.HAPPY_PAT,
            BunnyVoiceMood.HABIT_REWARD,
            BunnyVoiceMood.AUDIO_MEMO_FEEDBACK,
            BunnyVoiceMood.LISTENING,
            BunnyVoiceMood.GREETING,
            BunnyVoiceMood.REMINDER -> ::softPulse25
        }

        syllables.forEach { s ->
            val startSample = (s.startSec * SAMPLE_RATE).toInt()
            val fStart = midiToFreq(s.startMidi)
            val fEnd = midiToFreq(s.endMidi)
            renderToneNote(
                buffer = buf,
                startSample = startSample,
                durationSec = s.durationSec,
                freqStartHz = fStart,
                freqEndHz = fEnd,
                amplitude = s.amp,
                attackSec = 0.006,
                decayRate = if (mood == BunnyVoiceMood.SAD_MISSED) 4.8 else 7.2,
                waveform = waveform
            )
            // Soft sparkle harmonic layer on cheerful/pinch/reward moods
            if (mood == BunnyVoiceMood.HAPPY_PAT ||
                mood == BunnyVoiceMood.PINCH_CHEEK ||
                mood == BunnyVoiceMood.HABIT_REWARD ||
                mood == BunnyVoiceMood.AUDIO_MEMO_FEEDBACK
            ) {
                renderToneNote(
                    buffer = buf,
                    startSample = startSample,
                    durationSec = s.durationSec * 0.85,
                    freqStartHz = fStart * 2.0,
                    freqEndHz = fEnd * 2.0,
                    amplitude = s.amp * 0.22,
                    attackSec = 0.004,
                    decayRate = 11.0,
                    waveform = ::musicBoxBell
                )
            }
        }

        return toPcm16(buf, masterGain = 0.80)
    }

    /**
     * Default cozy morning lo-fi Rhodes & gentle acoustic chime loop (~22.8s at 84 BPM)
     * inspired by warm paper-morning study vlogs.
     */
    fun synthesizeSubtleBgmLoop(): ShortArray = synthesizePaperMorningBgmLoop()

    fun synthesizePaperMorningBgmLoop(): ShortArray {
        val bpm = 84.0
        val eighthSec = (60.0 / bpm) / 2.0 // ~0.357s per 8th note
        val totalSteps = 64 // 8 bars * 8 eighth-notes = ~22.86 seconds seamless loop
        val totalSamples = (totalSteps * eighthSec * SAMPLE_RATE).toInt()
        val buf = DoubleArray(totalSamples)

        // Cozy Fmaj7 -> G6 -> Em7 -> Am7 -> Dm7 -> G9sus4 -> Cmaj7 -> G7 morning melody
        val melody = intArrayOf(
            // Bar 1 (Fmaj7): A5 - C6 - E6 - D6 - C6 - A5
            81, 0, 84, 88, 86, 0, 84, 81,
            // Bar 2 (G6):    B5 - D6 - E6 - D6 - B5 - G5
            83, 0, 86, 88, 86, 0, 83, 79,
            // Bar 3 (Em7):   G5 - B5 - D6 - C6 - B5 - G5
            79, 0, 83, 86, 84, 0, 83, 79,
            // Bar 4 (Am7):   A5 - C6 - E6 - G6 - E6 - -
            81, 0, 84, 88, 91, 0, 88, 0,
            // Bar 5 (Dm7):   F5 - A5 - C6 - E6 - D6 - C6
            77, 0, 81, 84, 88, 0, 86, 84,
            // Bar 6 (G9sus): D6 - C6 - B5 - G5 - A5 - B5
            86, 0, 84, 83, 79, 0, 81, 83,
            // Bar 7 (Cmaj7): C6 - E6 - G6 - E6 - D6 - C6
            84, 0, 88, 91, 88, 0, 86, 84,
            // Bar 8 (Turn):  B5 - G5 - A5 - B5 - G5 - -
            83, 0, 79, 81, 83, 0, 79, 0
        )

        val chords = arrayOf(
            intArrayOf(53, 57, 60, 64), // Fmaj7
            intArrayOf(55, 59, 62, 64), // G6
            intArrayOf(52, 55, 59, 62), // Em7
            intArrayOf(57, 60, 64, 67), // Am7
            intArrayOf(50, 53, 57, 60), // Dm7
            intArrayOf(55, 60, 62, 65), // G9sus4
            intArrayOf(48, 55, 60, 64), // Cmaj7
            intArrayOf(55, 59, 62, 65)  // G7 turnaround
        )

        val bassNotes = intArrayOf(
            41, 48, // F2 - C3
            43, 50, // G2 - D3
            40, 47, // E2 - B2
            45, 52, // A2 - E3
            38, 45, // D2 - A2
            43, 50, // G2 - D3
            36, 43, // C2 - G2
            43, 47  // G2 - B2
        )

        for (step in 0 until totalSteps) {
            val stepStart = (step * eighthSec * SAMPLE_RATE).toInt()
            val bar = step / 8
            val beatInBar = step % 8

            // 1. Rolled warm electric-piano chord on beat 1 (with soft repeat on beat 3)
            if (beatInBar == 0 || beatInBar == 4) {
                val chord = chords[bar]
                val rollStepSamples = if (beatInBar == 0) (0.024 * SAMPLE_RATE).toInt() else 0
                val chordAmp = if (beatInBar == 0) 0.11 else 0.065
                chord.forEachIndexed { idx, midi ->
                    renderToneNote(
                        buffer = buf,
                        startSample = stepStart + idx * rollStepSamples,
                        durationSec = eighthSec * 3.8,
                        freqStartHz = midiToFreq(midi),
                        amplitude = chordAmp,
                        attackSec = 0.022,
                        decayRate = 1.9,
                        waveform = ::warmRhodesTone
                    )
                }
            }

            // 2. Soft warm upright-style triangle bass on beats 1 and 3
            if (beatInBar == 0 || beatInBar == 4) {
                val bassMidi = bassNotes[bar * 2 + if (beatInBar == 0) 0 else 1]
                renderToneNote(
                    buffer = buf,
                    startSample = stepStart,
                    durationSec = eighthSec * 3.5,
                    freqStartHz = midiToFreq(bassMidi),
                    amplitude = 0.18,
                    attackSec = 0.025,
                    decayRate = 2.1,
                    waveform = ::triangleWave
                )
            }

            // 3. Gentle lyrical melody (mellow Rhodes + whisper music-box top harmonic)
            val mNote = melody[step]
            if (mNote > 0) {
                val holdSteps = if (step + 1 < totalSteps && melody[step + 1] == 0) 2.1 else 1.05
                renderToneNote(
                    buffer = buf,
                    startSample = stepStart,
                    durationSec = eighthSec * holdSteps,
                    freqStartHz = midiToFreq(mNote),
                    amplitude = 0.21,
                    attackSec = 0.016,
                    decayRate = 2.8,
                    waveform = ::warmRhodesTone
                )
                renderToneNote(
                    buffer = buf,
                    startSample = stepStart,
                    durationSec = eighthSec * 0.85,
                    freqStartHz = midiToFreq(mNote),
                    amplitude = 0.055,
                    attackSec = 0.008,
                    decayRate = 6.5,
                    waveform = ::musicBoxBell
                )
            }
        }

        val fadeSamples = (0.014 * SAMPLE_RATE).toInt().coerceAtMost(totalSamples / 4)
        for (i in 0 until fadeSamples) {
            val factor = i.toDouble() / fadeSamples.toDouble()
            buf[i] *= factor
            buf[totalSamples - 1 - i] *= factor
        }

        return toPcm16(buf, masterGain = 0.80)
    }

    /**
     * Classic 8-bit chiptune lullaby loop ("Forget-Me-Not Meadow", ~19.2s at 100 BPM).
     */
    fun synthesizeMeadow8BitBgmLoop(): ShortArray {
        val bpm = 100.0
        val eighthSec = (60.0 / bpm) / 2.0 // 0.30s per 8th note
        val totalSteps = 64 // 8 bars * 8 eighth-notes = 19.2 seconds exact loop
        val totalSamples = (totalSteps * eighthSec * SAMPLE_RATE).toInt()
        val buf = DoubleArray(totalSamples)

        // 0 = rest; MIDI notes in cozy upper-mid register (C5=72 .. G6=91)
        val melody = intArrayOf(
            // Bar 1 (Cmaj7): E5 - G5 - C6 - B5 - G5 - E5
            76, 0, 79, 84, 83, 0, 79, 76,
            // Bar 2 (Am7):   A5 - G5 - E5 - C5 - D5 - E5
            81, 0, 79, 76, 72, 0, 74, 76,
            // Bar 3 (Fmaj7): F5 - A5 - C6 - A5 - G5 - F5
            77, 0, 81, 84, 81, 0, 79, 77,
            // Bar 4 (G6):    G5 - B5 - D6 - B5 - G5 - -
            79, 0, 83, 86, 83, 0, 79, 0,
            // Bar 5 (Em7):   E5 - G5 - B5 - D6 - C6 - B5
            76, 0, 79, 83, 86, 0, 84, 83,
            // Bar 6 (Am7):   A5 - E5 - G5 - A5 - G5 - E5
            81, 0, 76, 79, 81, 0, 79, 76,
            // Bar 7 (Dm7):   F5 - A5 - D6 - C6 - A5 - F5
            77, 0, 81, 86, 84, 0, 81, 77,
            // Bar 8 (G7sus4 -> G): G5 - C6 - B5 - A5 - G5 - -
            79, 0, 84, 83, 81, 0, 79, 0
        )

        // Warm NES triangle bass (one bar = 8 eighth-notes, gentle root + fifth bounce)
        val bassRoots = intArrayOf(
            48, 55, // Bar 1: C3 - G3
            45, 52, // Bar 2: A2 - E3
            41, 48, // Bar 3: F2 - C3
            43, 50, // Bar 4: G2 - D3
            40, 47, // Bar 5: E2 - B2
            45, 52, // Bar 6: A2 - E3
            50, 57, // Bar 7: D3 - A3
            43, 50  // Bar 8: G2 - D3
        )

        // Dreamy soft chord arpeggio notes per bar (root, 3rd, 5th, 7th)
        val chordArps = arrayOf(
            intArrayOf(60, 64, 67, 71), // Cmaj7
            intArrayOf(57, 60, 64, 67), // Am7
            intArrayOf(53, 57, 60, 64), // Fmaj7
            intArrayOf(55, 59, 62, 64), // G6
            intArrayOf(52, 55, 59, 62), // Em7
            intArrayOf(57, 60, 64, 67), // Am7
            intArrayOf(50, 53, 57, 60), // Dm7
            intArrayOf(55, 60, 62, 65)  // G7sus4
        )

        for (step in 0 until totalSteps) {
            val stepStart = (step * eighthSec * SAMPLE_RATE).toInt()
            val bar = step / 8
            val beatInBar = step % 8

            // 1. Melody voice (cute soft music-box pulse)
            val mNote = melody[step]
            if (mNote > 0) {
                val holdSteps = if (step + 1 < totalSteps && melody[step + 1] == 0) 1.85 else 0.95
                renderToneNote(
                    buffer = buf,
                    startSample = stepStart,
                    durationSec = eighthSec * holdSteps,
                    freqStartHz = midiToFreq(mNote),
                    amplitude = 0.23,
                    attackSec = 0.014,
                    decayRate = 4.2,
                    waveform = ::softPulse25
                )
            }

            // 2. Triangle bass on beats 1 and 3 (steps 0 and 4 of each bar)
            if (beatInBar == 0 || beatInBar == 4) {
                val bassMidi = bassRoots[bar * 2 + if (beatInBar == 0) 0 else 1]
                renderToneNote(
                    buffer = buf,
                    startSample = stepStart,
                    durationSec = eighthSec * 3.4,
                    freqStartHz = midiToFreq(bassMidi),
                    amplitude = 0.24,
                    attackSec = 0.018,
                    decayRate = 2.6,
                    waveform = ::triangleWave
                )
            }

            // 3. Very subtle floating sparkle arpeggio on off-beats
            val chord = chordArps[bar]
            val arpMidi = chord[beatInBar % chord.size] + 12
            if (beatInBar % 2 == 1) {
                renderToneNote(
                    buffer = buf,
                    startSample = stepStart,
                    durationSec = eighthSec * 1.15,
                    freqStartHz = midiToFreq(arpMidi),
                    amplitude = 0.065,
                    attackSec = 0.016,
                    decayRate = 6.2,
                    waveform = ::musicBoxBell
                )
            }
        }

        // Tiny fade-in/out at loop boundaries (~10 ms) to guarantee click-free seamless looping
        val fadeSamples = (0.010 * SAMPLE_RATE).toInt().coerceAtMost(totalSamples / 4)
        for (i in 0 until fadeSamples) {
            val factor = i.toDouble() / fadeSamples.toDouble()
            buf[i] *= factor
            buf[totalSamples - 1 - i] *= factor
        }

        return toPcm16(buf, masterGain = 0.78)
    }

    /**
     * Writes mono 16-bit PCM samples as a standard 44-byte RIFF/WAVE file so [android.media.SoundPool]
     * and [android.media.MediaPlayer] can decode and play it reliably across real devices and emulators.
     */
    fun writeWavToFile(samples: ShortArray, outFile: java.io.File): java.io.File {
        outFile.parentFile?.mkdirs()
        val dataSize = samples.size * 2
        val byteRate = SAMPLE_RATE * 2 // 16-bit mono
        val riffChunkSize = 36 + dataSize

        val bytes = ByteArray(44 + dataSize)
        // "RIFF"
        bytes[0] = 'R'.code.toByte()
        bytes[1] = 'I'.code.toByte()
        bytes[2] = 'F'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        writeIntLE(bytes, 4, riffChunkSize)
        // "WAVE"
        bytes[8] = 'W'.code.toByte()
        bytes[9] = 'A'.code.toByte()
        bytes[10] = 'V'.code.toByte()
        bytes[11] = 'E'.code.toByte()
        // "fmt "
        bytes[12] = 'f'.code.toByte()
        bytes[13] = 'm'.code.toByte()
        bytes[14] = 't'.code.toByte()
        bytes[15] = ' '.code.toByte()
        writeIntLE(bytes, 16, 16) // PCM fmt chunk size
        writeShortLE(bytes, 20, 1) // AudioFormat = PCM (1)
        writeShortLE(bytes, 22, 1) // Channels = Mono (1)
        writeIntLE(bytes, 24, SAMPLE_RATE)
        writeIntLE(bytes, 28, byteRate)
        writeShortLE(bytes, 32, 2) // BlockAlign = 2
        writeShortLE(bytes, 34, 16) // BitsPerSample = 16
        // "data"
        bytes[36] = 'd'.code.toByte()
        bytes[37] = 'a'.code.toByte()
        bytes[38] = 't'.code.toByte()
        bytes[39] = 'a'.code.toByte()
        writeIntLE(bytes, 40, dataSize)

        var offset = 44
        for (sample in samples) {
            val s = sample.toInt()
            bytes[offset++] = (s and 0xFF).toByte()
            bytes[offset++] = ((s ushr 8) and 0xFF).toByte()
        }

        val tmpFile = java.io.File(outFile.parentFile, "${outFile.name}.tmp")
        java.io.FileOutputStream(tmpFile).use { fos ->
            fos.write(bytes)
            fos.fd.sync()
        }
        if (!tmpFile.renameTo(outFile)) {
            outFile.writeBytes(bytes)
            tmpFile.delete()
        }
        return outFile
    }

    private fun writeIntLE(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        dst[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        dst[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }
}

