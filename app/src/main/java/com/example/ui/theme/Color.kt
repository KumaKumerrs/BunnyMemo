package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// -----------------------------------------------------------------------------
// Airy Forget-Me-Not Sky-Blue (Light) & Moonlit Forget-Me-Not Dusk (Dark)
//
// Immutable CompositionLocal-backed color palette where every Light & Dark
// foreground/background pairing is mathematically verified >= 4.5:1 (WCAG 2.1 AA+).
// -----------------------------------------------------------------------------

@Immutable
data class ForgetMeNotColors(
    val isDark: Boolean,
    val cloudWhite: Color,
    val skyCanvasTop: Color,
    val skyCanvasMid: Color,
    val skyCanvasBottom: Color,
    val pureWhite: Color,
    val softIceSurface: Color,
    val skyBlue: Color,
    val subtleBorder: Color,
    val midnightNavy: Color,
    val mutedNavy: Color,
    val deepBlue: Color,
    val forgetMeNotBlue: Color,
    val pollenYellow: Color,
    val pollenSoft: Color,
    val pollenInk: Color,
    val overdueTerracotta: Color,
    val overdueContainer: Color,
    val calmSage: Color,
    val calmSageContainer: Color,
    val sharedInviteAccent: Color
)

// Light Palette — Airy Morning Sky Forget-Me-Not (WCAG 2.1 AA+ verified)
internal val LightCloudWhite = Color(0xFFE5F2FE)
internal val LightSkyCanvasTop = Color(0xFFD4E9FC)
internal val LightSkyCanvasMid = Color(0xFFDCECFD)
internal val LightSkyCanvasBottom = Color(0xFFD1E6FB)
internal val LightPureWhite = Color(0xFFFCFEFF)
internal val LightSoftIceSurface = Color(0xFFEDF5FE)
internal val LightSkyBlue = Color(0xFFCDE5FD)
internal val LightSubtleBorder = Color(0xFFD3E6F9)
internal val LightMidnightNavy = Color(0xFF2B4360)
internal val LightMutedNavy = Color(0xFF4C6582)        // 5.95:1 on PureWhite, 5.29:1 on CloudWhite
internal val LightDeepBlue = Color(0xFF27578A)         // 7.37:1 w/ PureWhite, 5.76:1 on SkyBlue
internal val LightForgetMeNotBlue = Color(0xFF7CB6F9)  // 4.78:1 w/ MidnightNavy
internal val LightPollenYellow = Color(0xFFF7D56E)
internal val LightPollenSoft = Color(0xFFFFF8E1)
internal val LightPollenInk = Color(0xFF7A5C00)        // 5.89:1 on PollenSoft
internal val LightOverdueTerracotta = Color(0xFFAD3545)// >= 4.81:1 on every light surface (incl. SkyBlue), 6.16:1 w/ PureWhite
internal val LightOverdueContainer = Color(0xFFFEEFF1)
internal val LightCalmSage = Color(0xFF1F6E59)         // >= 4.73:1 on every light surface (incl. SkyBlue), 6.05:1 w/ PureWhite
internal val LightCalmSageContainer = Color(0xFFE9F8F3)
internal val LightSharedInviteAccent = Color(0xFF5E3FB8)// 7.21:1 on PureWhite

// Dark Palette — Serene Moonlit Forget-Me-Not Stationery (WCAG 2.1 AA+ verified)
internal val DarkCloudWhite = Color(0xFF0F172A)
internal val DarkSkyCanvasTop = Color(0xFF0B1324)
internal val DarkSkyCanvasMid = Color(0xFF121C32)
internal val DarkSkyCanvasBottom = Color(0xFF15223B)
internal val DarkPureWhite = Color(0xFF192438)
internal val DarkSoftIceSurface = Color(0xFF223048)
internal val DarkSkyBlue = Color(0xFF1E3557)           // 5.40:1 w/ DarkDeepBlue, 10.75:1 w/ DarkMidnightNavy
internal val DarkSubtleBorder = Color(0xFF2E405E)
internal val DarkMidnightNavy = Color(0xFFEAF2FF)
internal val DarkMutedNavy = Color(0xFF97AECB)         // 6.83:1 on DarkPureWhite, 5.83:1 on DarkSoftIceSurface
internal val DarkDeepBlue = Color(0xFF78B4FF)          // 7.18:1 w/ DarkPureWhite, 5.40:1 on DarkSkyBlue
internal val DarkForgetMeNotBlue = Color(0xFF8CC3FF)
internal val DarkPollenYellow = Color(0xFFFAD976)
internal val DarkPollenSoft = Color(0xFF2F2A1E)
internal val DarkPollenInk = Color(0xFFFAD976)         // 10.37:1 on DarkPollenSoft
internal val DarkOverdueTerracotta = Color(0xFFF28492) // 5.91:1 on DarkOverdueContainer, 6.29:1 w/ DarkPureWhite
internal val DarkOverdueContainer = Color(0xFF3B212A)
internal val DarkCalmSage = Color(0xFF5DCBAE)          // 6.57:1 on DarkCalmSageContainer, 7.86:1 w/ DarkPureWhite
internal val DarkCalmSageContainer = Color(0xFF1A3631)
internal val DarkSharedInviteAccent = Color(0xFFB9A0FF)// 7.06:1 on DarkPureWhite

val LightForgetMeNotColors = ForgetMeNotColors(
    isDark = false,
    cloudWhite = LightCloudWhite,
    skyCanvasTop = LightSkyCanvasTop,
    skyCanvasMid = LightSkyCanvasMid,
    skyCanvasBottom = LightSkyCanvasBottom,
    pureWhite = LightPureWhite,
    softIceSurface = LightSoftIceSurface,
    skyBlue = LightSkyBlue,
    subtleBorder = LightSubtleBorder,
    midnightNavy = LightMidnightNavy,
    mutedNavy = LightMutedNavy,
    deepBlue = LightDeepBlue,
    forgetMeNotBlue = LightForgetMeNotBlue,
    pollenYellow = LightPollenYellow,
    pollenSoft = LightPollenSoft,
    pollenInk = LightPollenInk,
    overdueTerracotta = LightOverdueTerracotta,
    overdueContainer = LightOverdueContainer,
    calmSage = LightCalmSage,
    calmSageContainer = LightCalmSageContainer,
    sharedInviteAccent = LightSharedInviteAccent
)

val DarkForgetMeNotColors = ForgetMeNotColors(
    isDark = true,
    cloudWhite = DarkCloudWhite,
    skyCanvasTop = DarkSkyCanvasTop,
    skyCanvasMid = DarkSkyCanvasMid,
    skyCanvasBottom = DarkSkyCanvasBottom,
    pureWhite = DarkPureWhite,
    softIceSurface = DarkSoftIceSurface,
    skyBlue = DarkSkyBlue,
    subtleBorder = DarkSubtleBorder,
    midnightNavy = DarkMidnightNavy,
    mutedNavy = DarkMutedNavy,
    deepBlue = DarkDeepBlue,
    forgetMeNotBlue = DarkForgetMeNotBlue,
    pollenYellow = DarkPollenYellow,
    pollenSoft = DarkPollenSoft,
    pollenInk = DarkPollenInk,
    overdueTerracotta = DarkOverdueTerracotta,
    overdueContainer = DarkOverdueContainer,
    calmSage = DarkCalmSage,
    calmSageContainer = DarkCalmSageContainer,
    sharedInviteAccent = DarkSharedInviteAccent
)

val LocalForgetMeNotColors = staticCompositionLocalOf { LightForgetMeNotColors }

/**
 * Snapshot-backed mirror updated by [ForgetMeNotTheme] solely so non-@Composable DrawScope
 * canvas helpers can read reactive tokens without breaking existing Canvas signatures.
 * Composables should prefer [LocalForgetMeNotColors] or [MaterialTheme.colorScheme].
 */
internal var activeDrawScopePalette by mutableStateOf(LightForgetMeNotColors)

// Fixed Illustration Colors (always crisp snow-white bunny & flower crown in both themes)
val BunnyFurWhite = Color(0xFFFCFEFF)
val BunnyOutlineInk = Color(0xFF2B4360)
val BunnyEarBlush = Color(0xFFCDE5FD)
val BunnyFlowerCrown = Color(0xFFFCFEFF)

// 60% Dominant Canvas
val CloudWhite: Color get() = activeDrawScopePalette.cloudWhite
val SkyCanvasTop: Color get() = activeDrawScopePalette.skyCanvasTop
val SkyCanvasMid: Color get() = activeDrawScopePalette.skyCanvasMid
val SkyCanvasBottom: Color get() = activeDrawScopePalette.skyCanvasBottom

// 30% Secondary Surfaces & Ink
val PureWhite: Color get() = activeDrawScopePalette.pureWhite
val SoftIceSurface: Color get() = activeDrawScopePalette.softIceSurface
val SkyBlue: Color get() = activeDrawScopePalette.skyBlue
val SubtleBorder: Color get() = activeDrawScopePalette.subtleBorder
val MidnightNavy: Color get() = activeDrawScopePalette.midnightNavy
val MutedNavy: Color get() = activeDrawScopePalette.mutedNavy

// 10% Fresh Forget-Me-Not & Sunlit Pollen Accents
val DeepBlue: Color get() = activeDrawScopePalette.deepBlue
val ForgetMeNotBlue: Color get() = activeDrawScopePalette.forgetMeNotBlue
val PollenYellow: Color get() = activeDrawScopePalette.pollenYellow
val PollenSoft: Color get() = activeDrawScopePalette.pollenSoft
val PollenInk: Color get() = activeDrawScopePalette.pollenInk

// Semantic Editorial Accents
val OverdueTerracotta: Color get() = activeDrawScopePalette.overdueTerracotta
val OverdueContainer: Color get() = activeDrawScopePalette.overdueContainer
val CalmSage: Color get() = activeDrawScopePalette.calmSage
val CalmSageContainer: Color get() = activeDrawScopePalette.calmSageContainer
val SharedInviteAccent: Color get() = activeDrawScopePalette.sharedInviteAccent
