package app.materialsudoku.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Wallpaper colour (null seed) or one of the four fixed seeds; light/dark; optional AMOLED black. */
@Composable
fun SudokuTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    seed: Seed? = null,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val base = when {
        seed == null -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> seed.dark
        else -> seed.light
    }
    val black = dark && amoled
    val scheme = remember(base, black) { if (black) base.toAmoledBlack() else base }

    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(dark, activity) {
        val bar = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
    }

    // Tonal elevation would paint surfaceTint back over a pure-black page.
    CompositionLocalProvider(LocalTonalElevationEnabled provides !black) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = remember { sudokuTypography() },
            content = content,
        )
    }
}

/** Same values as the mockups' `[data-amoled="on"]` block. */
fun ColorScheme.toAmoledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    scrim = Color.Black,
)
