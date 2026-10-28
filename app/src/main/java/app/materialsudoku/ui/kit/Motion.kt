package app.materialsudoku.ui.kit

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.compositionLocalOf

/**
 * The expressive springs: spatial under-damped, effects critically damped. That
 * asymmetry is what reads as expressive. With reduced motion every spatial spring becomes a snap
 * and effects stay (a fade is not motion).
 */
class Springs(val reduced: Boolean) {
    fun <T> fast(): FiniteAnimationSpec<T> = if (reduced) snap() else spring(0.6f, 800f)
    fun <T> spatial(): FiniteAnimationSpec<T> = if (reduced) snap() else spring(0.8f, 380f)
    fun <T> slow(): FiniteAnimationSpec<T> = if (reduced) snap() else spring(0.8f, 200f)
    /** LoadingIndicator's morph spring, used for a ripple's way back. */
    fun <T> loading(): FiniteAnimationSpec<T> = if (reduced) snap() else spring(0.6f, 200f)
    fun <T> effects(): FiniteAnimationSpec<T> = spring(1f, 1600f)
    fun <T> effectsFast(): FiniteAnimationSpec<T> = spring(1f, 3800f)
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = spring(1f, 800f)

    // ── Gameplay (user, 2026-10-27): "the start should be super fast; it can fade out slower" ────
    // Anything that answers a touch on the board or keypad attacks almost instantly (a stiff
    // spring still overshoots a hair, so it stays expressive) and releases on a softer spring.
    // UI chrome keeps the springs above.
    /** Shape, scale, lift arriving: ~60 ms to the target, a small overshoot. */
    fun <T> hitIn(): FiniteAnimationSpec<T> = if (reduced) snap() else spring(0.62f, 2600f)
    /** Shape, scale, lift going back to rest: unhurried. */
    fun <T> hitOut(): FiniteAnimationSpec<T> = if (reduced) snap() else spring(0.85f, 600f)
    /** Colour lighting up: effectively immediate. */
    fun <T> glowIn(): FiniteAnimationSpec<T> = spring(1f, 9000f)
    /** Colour fading out. */
    fun <T> glowOut(): FiniteAnimationSpec<T> = spring(1f, 700f)
}

val LocalSprings = compositionLocalOf { Springs(false) }
