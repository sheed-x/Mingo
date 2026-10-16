package app.mingo.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.mingo.R

// Google Sans Flex (SIL Open Font License) — the variable typeface used across Pixel apps.
// Its ROND axis softens the letterforms; headlines use it fully rounded for a friendlier look.

@OptIn(ExperimentalTextApi::class)
private fun flexFont(weight: Int, round: Float) = Font(
    R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("ROND", round),
    ),
)

private val TextFamily = FontFamily(
    listOf(400, 500, 600, 700).map { flexFont(it, round = 0f) },
)

private val DisplayFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).map { flexFont(it, round = 100f) },
)

private fun TextStyle.text() = copy(fontFamily = TextFamily)
private fun TextStyle.display() = copy(fontFamily = DisplayFamily)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val MingoTypography: Typography = Typography().let { b ->
    Typography(
        displayLarge = b.displayLarge.display(),
        displayMedium = b.displayMedium.display(),
        displaySmall = b.displaySmall.display(),
        headlineLarge = b.headlineLarge.display(),
        headlineMedium = b.headlineMedium.display(),
        headlineSmall = b.headlineSmall.display(),
        titleLarge = b.titleLarge.display(),
        titleMedium = b.titleMedium.text(),
        titleSmall = b.titleSmall.text(),
        bodyLarge = b.bodyLarge.text(),
        bodyMedium = b.bodyMedium.text(),
        bodySmall = b.bodySmall.text(),
        labelLarge = b.labelLarge.text(),
        labelMedium = b.labelMedium.text(),
        labelSmall = b.labelSmall.text(),
        displayLargeEmphasized = b.displayLargeEmphasized.display(),
        displayMediumEmphasized = b.displayMediumEmphasized.display(),
        displaySmallEmphasized = b.displaySmallEmphasized.display(),
        headlineLargeEmphasized = b.headlineLargeEmphasized.display(),
        headlineMediumEmphasized = b.headlineMediumEmphasized.display(),
        headlineSmallEmphasized = b.headlineSmallEmphasized.display(),
        titleLargeEmphasized = b.titleLargeEmphasized.display(),
        titleMediumEmphasized = b.titleMediumEmphasized.text(),
        titleSmallEmphasized = b.titleSmallEmphasized.text(),
        bodyLargeEmphasized = b.bodyLargeEmphasized.text(),
        bodyMediumEmphasized = b.bodyMediumEmphasized.text(),
        bodySmallEmphasized = b.bodySmallEmphasized.text(),
        labelLargeEmphasized = b.labelLargeEmphasized.text(),
        labelMediumEmphasized = b.labelMediumEmphasized.text(),
        labelSmallEmphasized = b.labelSmallEmphasized.text(),
    )
}
