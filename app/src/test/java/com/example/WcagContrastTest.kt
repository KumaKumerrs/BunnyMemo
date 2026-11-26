package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import com.example.ui.theme.DarkForgetMeNotColors
import com.example.ui.theme.ForgetMeNotColors
import com.example.ui.theme.ForgetMeNotDarkColorScheme
import com.example.ui.theme.ForgetMeNotLightColorScheme
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LightForgetMeNotColors
import com.example.ui.theme.NunitoFontFamily
import com.example.ui.theme.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Programmatic WCAG 2.1 AA verification of every foreground/background token pairing used for
 * text in both Light and Dark themes, plus typography/font integrity checks.
 */
class WcagContrastTest {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(fg: Color, bg: Color): Double {
        val opaqueBg = if (bg.alpha < 1f) bg.compositeOver(Color.White) else bg
        val opaqueFg = if (fg.alpha < 1f) fg.compositeOver(opaqueBg) else fg
        val l1 = relativeLuminance(opaqueFg)
        val l2 = relativeLuminance(opaqueBg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun textOnSurfacePairs(p: ForgetMeNotColors): List<Triple<String, Color, Color>> {
        val texts = listOf(
            "midnightNavy" to p.midnightNavy,
            "mutedNavy" to p.mutedNavy,
            "deepBlue" to p.deepBlue,
            "overdueTerracotta" to p.overdueTerracotta,
            "calmSage" to p.calmSage,
            "pollenInk" to p.pollenInk,
            "sharedInviteAccent" to p.sharedInviteAccent
        )
        val surfaces = listOf(
            "cloudWhite" to p.cloudWhite,
            "pureWhite" to p.pureWhite,
            "softIceSurface" to p.softIceSurface,
            "skyBlue" to p.skyBlue,
            "skyCanvasTop" to p.skyCanvasTop,
            "skyCanvasMid" to p.skyCanvasMid,
            "skyCanvasBottom" to p.skyCanvasBottom,
            "calmSageContainer" to p.calmSageContainer,
            "overdueContainer" to p.overdueContainer,
            "pollenSoft" to p.pollenSoft
        )
        return texts.flatMap { (tn, tc) -> surfaces.map { (sn, sc) -> Triple("$tn on $sn", tc, sc) } }
    }

    private fun schemePairs(s: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("onPrimary/primary", s.onPrimary, s.primary),
        Triple("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Triple("onSecondary/secondary", s.onSecondary, s.secondary),
        Triple("onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
        Triple("onTertiary/tertiary", s.onTertiary, s.tertiary),
        Triple("onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
        Triple("onBackground/background", s.onBackground, s.background),
        Triple("onSurface/surface", s.onSurface, s.surface),
        Triple("onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant),
        Triple("onSurface/surfaceContainer", s.onSurface, s.surfaceContainer),
        Triple("onSurface/surfaceContainerLowest", s.onSurface, s.surfaceContainerLowest),
        Triple("onSurfaceVariant/surfaceContainerLowest", s.onSurfaceVariant, s.surfaceContainerLowest),
        Triple("onError/error", s.onError, s.error),
        Triple("onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer),
        Triple("primary/surface", s.primary, s.surface),
        Triple("error/surface", s.error, s.surface)
    )

    private fun assertAllPass(label: String, pairs: List<Triple<String, Color, Color>>) {
        val failures = pairs.mapNotNull { (name, fg, bg) ->
            val ratio = contrast(fg, bg)
            if (ratio < 4.5) "$name = ${"%.2f".format(ratio)}:1" else null
        }
        assertTrue("$label WCAG AA failures: $failures", failures.isEmpty())
    }

    @Test
    fun contrastFormula_matchesKnownReferenceValues() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrast(Color.White, Color.White), 0.001)
    }

    @Test
    fun lightPalette_allTextPairs_meetAA() {
        assertAllPass("Light palette", textOnSurfacePairs(LightForgetMeNotColors))
    }

    @Test
    fun darkPalette_allTextPairs_meetAA() {
        assertAllPass("Dark palette", textOnSurfacePairs(DarkForgetMeNotColors))
    }

    @Test
    fun lightMaterialColorScheme_meetsAA() {
        assertAllPass("Light ColorScheme", schemePairs(ForgetMeNotLightColorScheme))
    }

    @Test
    fun darkMaterialColorScheme_meetsAA() {
        assertAllPass("Dark ColorScheme", schemePairs(ForgetMeNotDarkColorScheme))
    }

    @Test
    fun palettes_reportCorrectDarkFlag() {
        assertTrue(!LightForgetMeNotColors.isDark)
        assertTrue(DarkForgetMeNotColors.isDark)
        assertNotEquals(LightForgetMeNotColors.cloudWhite, DarkForgetMeNotColors.cloudWhite)
    }

    @Test
    fun typography_hasNoHardcodedTextColors() {
        val styles = mapOf(
            "displayLarge" to Typography.displayLarge,
            "displayMedium" to Typography.displayMedium,
            "displaySmall" to Typography.displaySmall,
            "headlineLarge" to Typography.headlineLarge,
            "headlineMedium" to Typography.headlineMedium,
            "headlineSmall" to Typography.headlineSmall,
            "titleLarge" to Typography.titleLarge,
            "titleMedium" to Typography.titleMedium,
            "titleSmall" to Typography.titleSmall,
            "bodyLarge" to Typography.bodyLarge,
            "bodyMedium" to Typography.bodyMedium,
            "bodySmall" to Typography.bodySmall,
            "labelLarge" to Typography.labelLarge,
            "labelMedium" to Typography.labelMedium,
            "labelSmall" to Typography.labelSmall
        )
        val hardcoded = styles.filter { (_, style) -> style.color != Color.Unspecified }.keys
        assertTrue("TextStyles with hardcoded colors: $hardcoded", hardcoded.isEmpty())
    }

    private fun resIdForWeight(family: FontFamily, weight: FontWeight): Int {
        val fonts = (family as FontListFontFamily).fonts
        val font = fonts.firstOrNull { it.weight == weight }
            ?: error("No font registered for weight $weight")
        return (font as ResourceFont).resId
    }

    @Test
    fun nunito_mapsDistinctStaticWeights() {
        val regular = resIdForWeight(NunitoFontFamily, FontWeight.Normal)
        val semiBold = resIdForWeight(NunitoFontFamily, FontWeight.SemiBold)
        val bold = resIdForWeight(NunitoFontFamily, FontWeight.Bold)
        assertEquals(R.font.nunito_regular, regular)
        assertEquals(R.font.nunito_semibold, semiBold)
        assertEquals(R.font.nunito_bold, bold)
        assertEquals(3, setOf(regular, semiBold, bold).size)
    }

    @Test
    fun inter_mapsDistinctStaticWeights() {
        val ids = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
            .map { resIdForWeight(InterFontFamily, it) }
        assertEquals(
            listOf(R.font.inter_regular, R.font.inter_medium, R.font.inter_semibold, R.font.inter_bold),
            ids
        )
        assertEquals(4, ids.toSet().size)
    }
}
