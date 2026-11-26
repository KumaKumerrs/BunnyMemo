package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

val LocalIsDarkTheme = compositionLocalOf { false }

private val ForgetMeNotLightColorScheme = lightColorScheme(
    primary = LightDeepBlue,
    onPrimary = LightPureWhite,
    primaryContainer = LightSkyBlue,
    onPrimaryContainer = LightMidnightNavy,
    secondary = LightForgetMeNotBlue,
    onSecondary = LightPureWhite,
    secondaryContainer = LightSoftIceSurface,
    onSecondaryContainer = LightMidnightNavy,
    tertiary = LightPollenYellow,
    onTertiary = LightMidnightNavy,
    tertiaryContainer = LightPollenSoft,
    onTertiaryContainer = LightMidnightNavy,
    background = LightCloudWhite,
    onBackground = LightMidnightNavy,
    surface = LightCloudWhite,
    onSurface = LightMidnightNavy,
    surfaceVariant = LightSoftIceSurface,
    onSurfaceVariant = LightMutedNavy,
    surfaceContainerLowest = LightPureWhite,
    surfaceContainerLow = LightCloudWhite,
    surfaceContainer = LightSoftIceSurface,
    surfaceContainerHigh = LightSkyBlue.copy(alpha = 0.72f),
    outline = LightSubtleBorder,
    outlineVariant = LightSubtleBorder,
    error = LightOverdueTerracotta,
    onError = LightPureWhite,
    errorContainer = LightOverdueContainer,
    onErrorContainer = LightOverdueTerracotta
)

private val ForgetMeNotDarkColorScheme = darkColorScheme(
    primary = DarkDeepBlue,
    onPrimary = DarkPureWhite,
    primaryContainer = DarkSkyBlue,
    onPrimaryContainer = DarkMidnightNavy,
    secondary = DarkForgetMeNotBlue,
    onSecondary = DarkPureWhite,
    secondaryContainer = DarkSoftIceSurface,
    onSecondaryContainer = DarkMidnightNavy,
    tertiary = DarkPollenYellow,
    onTertiary = DarkPureWhite,
    tertiaryContainer = DarkPollenSoft,
    onTertiaryContainer = DarkMidnightNavy,
    background = DarkCloudWhite,
    onBackground = DarkMidnightNavy,
    surface = DarkCloudWhite,
    onSurface = DarkMidnightNavy,
    surfaceVariant = DarkSoftIceSurface,
    onSurfaceVariant = DarkMutedNavy,
    surfaceContainerLowest = DarkPureWhite,
    surfaceContainerLow = DarkCloudWhite,
    surfaceContainer = DarkSoftIceSurface,
    surfaceContainerHigh = DarkSkyBlue.copy(alpha = 0.72f),
    outline = DarkSubtleBorder,
    outlineVariant = DarkSubtleBorder,
    error = DarkOverdueTerracotta,
    onError = DarkPureWhite,
    errorContainer = DarkOverdueContainer,
    onErrorContainer = DarkOverdueTerracotta
)

val ForgetMeNotShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ForgetMeNotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Synchronously update reactive palette flag before composing children
    isDarkPaletteActive = darkTheme

    val scheme = if (darkTheme) ForgetMeNotDarkColorScheme else ForgetMeNotLightColorScheme
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = ForgetMeNotShapes,
            content = content
        )
    }
}
