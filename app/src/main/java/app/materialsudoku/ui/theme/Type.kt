package app.materialsudoku.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.materialsudoku.R

/**
 * Google Sans Flex, the one bundled face (OFL). The binary is the six-axis build from
 * `clock/tools/build_gsflex.py`: `wght`, `wdth`, `opsz`, `GRAD`, `ROND`, `slnt`.
 *
 * Tiles leans on two axes the type scale leaves at default: `ROND` separates the player's digits
 * (ROND 100, rounded terminals) from givens (ROND 0), and `wdth` narrows the timer.
 * `opsz` always equals the point size.
 */
private data class Key(val opsz: Int, val wght: Int, val wdth: Int, val rond: Int, val grad: Int)

private val cache = HashMap<Key, FontFamily>()

fun flex(size: Float, weight: Int, wdth: Int = 100, rond: Int = 0, grad: Int = 0): FontFamily {
    val k = Key(size.coerceIn(6f, 144f).toInt(), weight, wdth, rond, grad)
    return cache.getOrPut(k) {
        FontFamily(
            Font(
                resId = R.font.google_sans_flex,
                weight = FontWeight(weight.coerceIn(1, 1000)),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight.coerceIn(1, 1000)),
                    FontVariation.Setting("opsz", k.opsz.toFloat()),
                    FontVariation.Setting("wdth", wdth.toFloat()),
                    FontVariation.Setting("ROND", rond.toFloat()),
                    FontVariation.Setting("GRAD", grad.toFloat()),
                ),
            ),
        )
    }
}

/** One style with opsz locked to its size and a real weight cut (never synthetic bold). */
fun flexStyle(size: Float, lineHeight: Float, weight: Int, tracking: Float = 0f, wdth: Int = 100, rond: Int = 0) =
    TextStyle(
        fontFamily = flex(size, weight, wdth, rond),
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
    )

/** Typography().copy(…) is the safe way to set all 30 styles: the no-argument constructor leaves the emphasized set equal to the plain one. */
fun sudokuTypography(): Typography = Typography().copy(
    displayLarge = flexStyle(57f, 64f, 400),
    displayMedium = flexStyle(45f, 52f, 400),
    displaySmall = flexStyle(36f, 44f, 400),
    headlineLarge = flexStyle(32f, 40f, 400),
    headlineMedium = flexStyle(28f, 36f, 400),
    headlineSmall = flexStyle(24f, 32f, 400),
    titleLarge = flexStyle(22f, 28f, 400),
    titleMedium = flexStyle(16f, 24f, 500),
    titleSmall = flexStyle(14f, 20f, 500),
    bodyLarge = flexStyle(16f, 24f, 400),
    bodyMedium = flexStyle(14f, 20f, 400),
    bodySmall = flexStyle(12f, 16f, 400, 0.1f),
    labelLarge = flexStyle(14f, 20f, 500),
    labelMedium = flexStyle(12f, 16f, 500, 0.1f),
    labelSmall = flexStyle(11f, 16f, 500, 0.1f),
    displayLargeEmphasized = flexStyle(57f, 64f, 500),
    displayMediumEmphasized = flexStyle(45f, 52f, 500),
    displaySmallEmphasized = flexStyle(36f, 44f, 500),
    headlineLargeEmphasized = flexStyle(32f, 40f, 500),
    headlineMediumEmphasized = flexStyle(28f, 36f, 500),
    headlineSmallEmphasized = flexStyle(24f, 32f, 500),
    titleLargeEmphasized = flexStyle(22f, 28f, 500),
    titleMediumEmphasized = flexStyle(16f, 24f, 700),
    titleSmallEmphasized = flexStyle(14f, 20f, 700),
    bodyLargeEmphasized = flexStyle(16f, 24f, 500),
    bodyMediumEmphasized = flexStyle(14f, 20f, 500),
    bodySmallEmphasized = flexStyle(12f, 16f, 500, 0.1f),
    labelLargeEmphasized = flexStyle(14f, 20f, 700),
    labelMediumEmphasized = flexStyle(12f, 16f, 700, 0.1f),
    labelSmallEmphasized = flexStyle(11f, 16f, 700, 0.1f),
)
