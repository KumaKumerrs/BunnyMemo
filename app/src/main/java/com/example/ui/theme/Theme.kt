package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ForgetMeNotColorScheme = lightColorScheme(
    primary = DeepBlue,
    onPrimary = PureWhite,
    primaryContainer = SkyBlue,
    onPrimaryContainer = MidnightNavy,
    secondary = ForgetMeNotBlue,
    onSecondary = PureWhite,
    secondaryContainer = SoftIceSurface,
    onSecondaryContainer = MidnightNavy,
    tertiary = PollenYellow,
    onTertiary = MidnightNavy,
    tertiaryContainer = PollenSoft,
    onTertiaryContainer = MidnightNavy,
    background = CloudWhite,
    onBackground = MidnightNavy,
    surface = CloudWhite,
    onSurface = MidnightNavy,
    surfaceVariant = SoftIceSurface,
    onSurfaceVariant = MutedNavy,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = CloudWhite,
    surfaceContainer = SoftIceSurface,
    surfaceContainerHigh = SkyBlue.copy(alpha = 0.72f),
    outline = SubtleBorder,
    outlineVariant = SubtleBorder,
    error = OverdueTerracotta,
    onError = PureWhite,
    errorContainer = OverdueContainer,
    onErrorContainer = OverdueTerracotta
)

val ForgetMeNotShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ForgetMeNotTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ForgetMeNotColorScheme,
        typography = Typography,
        shapes = ForgetMeNotShapes,
        content = content
    )
}
