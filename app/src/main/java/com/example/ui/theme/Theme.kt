package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AhaLightColorScheme = lightColorScheme(
    primary = BrandNavy,
    onPrimary = BrandWhite,
    primaryContainer = LightNavyContainer,
    onPrimaryContainer = BrandNavy,
    secondary = BrandRed,
    onSecondary = BrandWhite,
    secondaryContainer = LightRedContainer,
    onSecondaryContainer = BrandDarkRed,
    tertiary = BrandOrange,
    onTertiary = BrandWhite,
    tertiaryContainer = LightOrangeContainer,
    onTertiaryContainer = ColorTokens.DarkOrangeText,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = BrandDarkRed,
    onError = BrandWhite
)

private val AhaDarkColorScheme = darkColorScheme(
    primary = BrandNavyLight,
    onPrimary = BrandWhite,
    primaryContainer = DarkNavyContainer,
    onPrimaryContainer = BrandWhite,
    secondary = BrandRed,
    onSecondary = BrandWhite,
    secondaryContainer = DarkRedContainer,
    onSecondaryContainer = BrandWhite,
    tertiary = BrandOrange,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkOrangeContainer,
    onTertiaryContainer = BrandOrange,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = BrandRed,
    onError = BrandWhite
)

private object ColorTokens {
    val DarkOrangeText = androidx.compose.ui.graphics.Color(0xFF7A4800)
}

val AhaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AhaPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AhaDarkColorScheme else AhaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AhaTypography,
        shapes = AhaShapes,
        content = content
    )
}
