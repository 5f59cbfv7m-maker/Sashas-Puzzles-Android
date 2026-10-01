package com.kirillrychkov.sashaspuzzles.support

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.engine.SettleOutcome
import com.kirillrychkov.sashaspuzzles.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

/**
 * Haptic and audio confirmation for snaps, merges and completion, plus the
 * optional background music with a crossfade between the library and board
 * tunes. If audio cannot start for any reason it simply stays off — a silent
 * game is fine, a crashing one is not.
 */
class Feedback(private val context: Context) {

    enum class Tone(val resource: Int) {
        SNAP(R.raw.snap), MERGE(R.raw.merge), COMPLETE(R.raw.complete), ACHIEVEMENT(R.raw.achievement)
    }
    enum class Music(val resource: Int) {
        LIBRARY(R.raw.music_library), BOARD_PIANO(R.raw.music_piano), BOARD_VIBRAPHONE(R.raw.music_vibraphone)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private var pool: SoundPool? = null
    private val sounds = HashMap<Tone, Int>()
    private val tracks = HashMap<Music, MediaPlayer>()
    private val volumes = HashMap<Music, Float>()
    private var current: Music? = null
    private var fade: Job? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun report(outcome: SettleOutcome?, settings: AppSettings) {
        if (outcome == null || !outcome.didSnap) return
        when {
            outcome.didComplete -> { play(Tone.COMPLETE, settings); impact(Strength.STRONG, settings) }
            outcome.didMerge -> { play(Tone.MERGE, settings); impact(Strength.MEDIUM, settings) }
            else -> { play(Tone.SNAP, settings); impact(Strength.LIGHT, settings) }
        }
    }

    enum class Strength { LIGHT, MEDIUM, STRONG }

    fun impact(strength: Strength, settings: AppSettings) {
        if (!settings.hapticsEnabled) return
        val vibrator = vibrator ?: return
        runCatching {
            val effect = if (Build.VERSION.SDK_INT >= 29) {
                VibrationEffect.createPredefined(when (strength) {
                    Strength.LIGHT -> VibrationEffect.EFFECT_TICK
                    Strength.MEDIUM -> VibrationEffect.EFFECT_CLICK
                    Strength.STRONG -> VibrationEffect.EFFECT_HEAVY_CLICK
                })
            } else {
                VibrationEffect.createOneShot(when (strength) {
                    Strength.LIGHT -> 10L
                    Strength.MEDIUM -> 20L
                    Strength.STRONG -> 40L
                }, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        }
    }

    fun play(tone: Tone, settings: AppSettings) {
        if (!settings.soundEnabled) return
        val pool = pool ?: SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build().also { created ->
            pool = created
            for (t in Tone.entries) sounds[t] = created.load(context, t.resource, 1)
        }
        // The very first tone may still be loading; SoundPool simply skips it.
        sounds[tone]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    /**
     * Crossfades to `track`, or fades everything out with `null`. Each loop
     * keeps its place while silent, so coming back resumes rather than restarts.
     */
    fun setMusic(track: Music?, settings: AppSettings) {
        val target = if (settings.musicEnabled) track else null
        if (target == current) return
        current = target
        if (target != null && tracks[target] == null) {
            runCatching {
                MediaPlayer.create(context, target.resource)?.apply {
                    isLooping = true
                    setVolume(0f, 0f)
                }
            }.getOrNull()?.let { tracks[target] = it; volumes[target] = 0f }
        }
        tracks[target]?.let { if (!it.isPlaying) runCatching { it.start() } }
        fade?.cancel()
        // A true crossfade (something already playing) lets the outgoing tune
        // start thinning a moment before the new one rises. On the S-curves
        // below the two are never both above half volume — two keys and
        // tempos at full strength together clash — and the sum dips only about
        // 4 dB: a breath, not a gap.
        val handover = tracks.any { (music, _) -> music != target && (volumes[music] ?: 0f) > 0.01f }
        val start = HashMap(volumes)
        fade = scope.launch {
            val began = SystemClock.uptimeMillis()
            var done = false
            while (!done) {
                delay(FRAME_MILLIS)
                val elapsed = SystemClock.uptimeMillis() - began
                done = true
                for ((music, player) in tracks) {
                    val from = start[music] ?: 0f
                    val rising = music == target
                    val (delayMillis, length) = when {
                        !rising -> 0L to FADE_OUT_MILLIS
                        handover -> ENTRY_DELAY_MILLIS to FADE_IN_MILLIS
                        else -> 0L to FADE_IN_MILLIS
                    }
                    val t = ((elapsed - delayMillis).toFloat() / length).coerceIn(0f, 1f)
                    if (t < 1f) done = false
                    val v = loudness(from, if (rising) MUSIC_VOLUME else 0f, t)
                    volumes[music] = v
                    runCatching { player.setVolume(v, v) }
                }
            }
            for ((music, player) in tracks) if (music != current) runCatching { if (player.isPlaying) player.pause() }
        }
    }

    /**
     * The volume `t` of the way from `from` to `to`, along an S-curve: it
     * starts and settles gently, with no audible step at either end.
     */
    private fun loudness(from: Float, to: Float, t: Float): Float =
        from + (to - from) * (1 - cos(PI * t).toFloat()) / 2

    private companion object {
        const val MUSIC_VOLUME = 0.35f
        /** The outgoing tune thins out over this… */
        const val FADE_OUT_MILLIS = 3000L
        /** …while the incoming one waits this long, then rises more slowly. */
        const val ENTRY_DELAY_MILLIS = 150L
        const val FADE_IN_MILLIS = 3200L
        /** About one display frame: volume steps this small are inaudible. */
        const val FRAME_MILLIS = 16L
    }
}
