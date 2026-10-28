package app.materialsudoku.ui.kit

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_CLICK
import android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK
import android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
import android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
import android.os.VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
import android.os.VibrationEffect.Composition.PRIMITIVE_SPIN
import android.os.VibrationEffect.Composition.PRIMITIVE_THUD
import android.os.VibrationEffect.Composition.PRIMITIVE_TICK
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.compositionLocalOf
import app.materialsudoku.data.Haptics

/**
 * The haptic vocabulary (report 04 §3.4, board 1's table). The Pixel 10 supports every primitive,
 * frequency control and envelope effects; anything that does not falls back to predefined effects
 * (the Galaxy Tab S9 supports no primitives at all).
 *
 * Levels: OFF plays nothing; SOME plays only mistakes and wins; ALL plays every touch.
 */
class Buzz(context: Context) {
    private val vibrator: Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    private val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
    private val primitives = vibrator.hasVibrator() && vibrator.areAllPrimitivesSupported(
        PRIMITIVE_TICK, PRIMITIVE_LOW_TICK, PRIMITIVE_CLICK, PRIMITIVE_THUD,
        PRIMITIVE_QUICK_RISE, PRIMITIVE_QUICK_FALL, PRIMITIVE_SLOW_RISE, PRIMITIVE_SPIN,
    )
    private val envelopes = Build.VERSION.SDK_INT >= 36 && vibrator.areEnvelopeEffectsSupported()

    var level: Haptics = Haptics.ALL

    /** One step of a composition: primitive, scale 0..1, delay before it in ms. */
    data class P(val id: Int, val scale: Float, val delay: Int = 0)

    private fun play(vararg steps: P, important: Boolean = false, fallback: Int = VibrationEffect.EFFECT_TICK) {
        if (level == Haptics.OFF || (level == Haptics.SOME && !important) || !vibrator.hasVibrator()) return
        val effect = if (primitives) {
            VibrationEffect.startComposition().apply { steps.forEach { addPrimitive(it.id, it.scale, it.delay) } }.compose()
        } else VibrationEffect.createPredefined(fallback)
        vibrator.vibrate(effect, attrs)
    }

    fun select() = play(P(PRIMITIVE_TICK, 0.5f))
    fun highlight() = play(P(PRIMITIVE_TICK, 0.5f), P(PRIMITIVE_LOW_TICK, 0.5f, 60))
    fun key() = play(P(PRIMITIVE_CLICK, 0.7f), fallback = VibrationEffect.EFFECT_CLICK)
    fun noteOn() = play(P(PRIMITIVE_TICK, 0.5f))
    fun noteOff() = play(P(PRIMITIVE_LOW_TICK, 0.5f))
    fun mode() = play(P(PRIMITIVE_TICK, 0.7f), P(PRIMITIVE_LOW_TICK, 0.5f, 50))
    fun undo() = play(P(PRIMITIVE_QUICK_FALL, 0.5f))
    fun hint() = play(P(PRIMITIVE_QUICK_RISE, 0.5f), P(PRIMITIVE_TICK, 0.7f, 50))
    fun toggle() = play(P(PRIMITIVE_TICK, 0.6f))
    fun lowTick(scale: Float = 0.6f) = play(P(PRIMITIVE_LOW_TICK, scale))

    /**
     * A line, box or diagonal completes: one tick per tile as the wave passes it, rising in strength,
     * landing on a click; two or more at once land on a thud. Paced to the board's wave (step ms).
     */
    fun unitDone(count: Int, steps: Int = 9, step: Int = 45) {
        val wave = Array(steps) { k -> P(if (k < steps - 2) PRIMITIVE_TICK else PRIMITIVE_CLICK, 0.25f + 0.7f * k / (steps - 1), if (k == 0) 0 else step) }
        if (count >= 2) play(*wave, P(PRIMITIVE_THUD, 0.8f, 60)) else play(*wave, P(PRIMITIVE_CLICK, 1f, 40))
    }

    /**
     * Mistakes feel violent (H2, H3): a frequency-shaped slam that drops in pitch, then the heart
     * cracks 180 ms later. Fires only when mistakes are flagged, never for a stand-alone wrong digit.
     */
    fun mistake() {
        if (level == Haptics.OFF || !vibrator.hasVibrator()) return
        if (envelopes && Build.VERSION.SDK_INT >= 36) {
            val slam = VibrationEffect.WaveformEnvelopeBuilder()
                .setInitialFrequencyHz(160f)
                .addControlPoint(1.0f, 160f, 20)
                .addControlPoint(1.0f, 60f, 110)
                .addControlPoint(0.7f, 50f, 60)
                .addControlPoint(0.0f, 50f, 80)
                .build()
            vibrator.vibrate(slam, attrs)
        } else if (primitives) {
            vibrator.vibrate(VibrationEffect.startComposition().addPrimitive(PRIMITIVE_THUD, 1f).addPrimitive(PRIMITIVE_SPIN, 0.6f, 20).compose(), attrs)
        } else {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK), attrs)
        }
    }
    fun heartCrack() = play(P(PRIMITIVE_THUD, 1f), P(PRIMITIVE_LOW_TICK, 0.7f, 70), important = true, fallback = VibrationEffect.EFFECT_HEAVY_CLICK)
    fun lost() = play(P(PRIMITIVE_THUD, 1f), P(PRIMITIVE_QUICK_FALL, 1f, 120), important = true, fallback = VibrationEffect.EFFECT_HEAVY_CLICK)
    fun won() = play(
        P(PRIMITIVE_SLOW_RISE, 0.6f), P(PRIMITIVE_CLICK, 0.8f, 40), P(PRIMITIVE_TICK, 0.6f, 60),
        P(PRIMITIVE_TICK, 0.8f, 60), P(PRIMITIVE_THUD, 0.8f, 60), important = true, fallback = VibrationEffect.EFFECT_HEAVY_CLICK,
    )
    /** Halfway: a soft rise and a tick, gentler than a line celebration. */
    fun halfway() = play(P(PRIMITIVE_SLOW_RISE, 0.5f), P(PRIMITIVE_TICK, 0.7f, 30))
    fun start() = play(P(PRIMITIVE_QUICK_RISE, 0.6f), P(PRIMITIVE_THUD, 0.7f, 40))
    fun press() = play(P(PRIMITIVE_CLICK, 0.7f), fallback = VibrationEffect.EFFECT_CLICK)
    fun settle() = play(P(PRIMITIVE_SLOW_RISE, 0.4f), P(PRIMITIVE_THUD, 0.4f, 0))
    /** Difficulty picks sharpen with the shape. */
    fun difficulty(k: Int) = when (k) {
        0 -> play(P(PRIMITIVE_TICK, 0.5f))
        1 -> play(P(PRIMITIVE_TICK, 0.8f))
        2 -> play(P(PRIMITIVE_CLICK, 0.5f))
        3 -> play(P(PRIMITIVE_CLICK, 0.7f))
        4 -> play(P(PRIMITIVE_CLICK, 0.9f))
        else -> play(P(PRIMITIVE_THUD, 0.6f))
    }
    fun length(k: Int) = play(*Array(k + 1) { P(PRIMITIVE_LOW_TICK, 0.7f, if (it == 0) 0 else 50) })
    fun sample(l: Haptics) {
        val was = level; level = Haptics.ALL
        when (l) { Haptics.OFF -> {}; Haptics.SOME -> mistake(); Haptics.ALL -> key() }
        level = was
    }
}

val LocalBuzz = compositionLocalOf<Buzz> { error("Buzz not provided") }
