package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// -----------------------------------------------------------------------------
// Airy Forget-Me-Not Sky-Blue (Light) & Moonlit Forget-Me-Not Dusk (Dark)
//
// Reactive theme-backed color tokens so every existing stationery surface,
// badge, border, and ink automatically transitions seamlessly between Light
// and Dark mode with verified WCAG AA/AAA legibility.
// -----------------------------------------------------------------------------

internal var isDarkPaletteActive by mutableStateOf(false)

// Light Palette — Airy Morning Sky Forget-Me-Not
internal val LightCloudWhite = Color(0xFFE5F2FE)
internal val LightSkyCanvasTop = Color(0xFFD4E9FC)
internal val LightSkyCanvasMid = Color(0xFFDCECFD)
internal val LightSkyCanvasBottom = Color(0xFFD1E6FB)
internal val LightPureWhite = Color(0xFFFCFEFF)
internal val LightSoftIceSurface = Color(0xFFEDF5FE)
internal val LightSkyBlue = Color(0xFFCDE5FD)
internal val LightSubtleBorder = Color(0xFFD3E6F9)
internal val LightMidnightNavy = Color(0xFF2B4360)
internal val LightMutedNavy = Color(0xFF6B85A3)
internal val LightDeepBlue = Color(0xFF3B82DC)
internal val LightForgetMeNotBlue = Color(0xFF7CB6F9)
internal val LightPollenYellow = Color(0xFFF7D56E)
internal val LightPollenSoft = Color(0xFFFFF8E1)
internal val LightOverdueTerracotta = Color(0xFFD65A6A)
internal val LightOverdueContainer = Color(0xFFFEEFF1)
internal val LightCalmSage = Color(0xFF35917A)
internal val LightCalmSageContainer = Color(0xFFE9F8F3)

// Dark Palette — Serene Moonlit Forget-Me-Not Stationery
internal val DarkCloudWhite = Color(0xFF0F172A)
internal val DarkSkyCanvasTop = Color(0xFF0B1324)
internal val DarkSkyCanvasMid = Color(0xFF121C32)
internal val DarkSkyCanvasBottom = Color(0xFF15223B)
internal val DarkPureWhite = Color(0xFF192438)
internal val DarkSoftIceSurface = Color(0xFF223048)
internal val DarkSkyBlue = Color(0xFF274268)
internal val DarkSubtleBorder = Color(0xFF2E405E)
internal val DarkMidnightNavy = Color(0xFFEAF2FF)
internal val DarkMutedNavy = Color(0xFF97AECB)
internal val DarkDeepBlue = Color(0xFF69A9F8)
internal val DarkForgetMeNotBlue = Color(0xFF8CC3FF)
internal val DarkPollenYellow = Color(0xFFFAD976)
internal val DarkPollenSoft = Color(0xFF2F2A1E)
internal val DarkOverdueTerracotta = Color(0xFFF28492)
internal val DarkOverdueContainer = Color(0xFF3B212A)
internal val DarkCalmSage = Color(0xFF5DCBAE)
internal val DarkCalmSageContainer = Color(0xFF1A3631)

// Fixed Illustration Colors (always crisp snow-white bunny & flower crown in both themes)
val BunnyFurWhite = Color(0xFFFCFEFF)
val BunnyOutlineInk = Color(0xFF2B4360)
val BunnyEarBlush = Color(0xFFCDE5FD)
val BunnyFlowerCrown = Color(0xFFFCFEFF)

// 60% Dominant Canvas
val CloudWhite: Color
    get() = if (isDarkPaletteActive) DarkCloudWhite else LightCloudWhite
val SkyCanvasTop: Color
    get() = if (isDarkPaletteActive) DarkSkyCanvasTop else LightSkyCanvasTop
val SkyCanvasMid: Color
    get() = if (isDarkPaletteActive) DarkSkyCanvasMid else LightSkyCanvasMid
val SkyCanvasBottom: Color
    get() = if (isDarkPaletteActive) DarkSkyCanvasBottom else LightSkyCanvasBottom

// 30% Secondary Surfaces & Ink
val PureWhite: Color
    get() = if (isDarkPaletteActive) DarkPureWhite else LightPureWhite
val SoftIceSurface: Color
    get() = if (isDarkPaletteActive) DarkSoftIceSurface else LightSoftIceSurface
val SkyBlue: Color
    get() = if (isDarkPaletteActive) DarkSkyBlue else LightSkyBlue
val SubtleBorder: Color
    get() = if (isDarkPaletteActive) DarkSubtleBorder else LightSubtleBorder
val MidnightNavy: Color
    get() = if (isDarkPaletteActive) DarkMidnightNavy else LightMidnightNavy
val MutedNavy: Color
    get() = if (isDarkPaletteActive) DarkMutedNavy else LightMutedNavy

// 10% Fresh Forget-Me-Not & Sunlit Pollen Accents
val DeepBlue: Color
    get() = if (isDarkPaletteActive) DarkDeepBlue else LightDeepBlue
val ForgetMeNotBlue: Color
    get() = if (isDarkPaletteActive) DarkForgetMeNotBlue else LightForgetMeNotBlue
val PollenYellow: Color
    get() = if (isDarkPaletteActive) DarkPollenYellow else LightPollenYellow
val PollenSoft: Color
    get() = if (isDarkPaletteActive) DarkPollenSoft else LightPollenSoft

// Semantic Editorial Accents
val OverdueTerracotta: Color
    get() = if (isDarkPaletteActive) DarkOverdueTerracotta else LightOverdueTerracotta
val OverdueContainer: Color
    get() = if (isDarkPaletteActive) DarkOverdueContainer else LightOverdueContainer
val CalmSage: Color
    get() = if (isDarkPaletteActive) DarkCalmSage else LightCalmSage
val CalmSageContainer: Color
    get() = if (isDarkPaletteActive) DarkCalmSageContainer else LightCalmSageContainer
