package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondary = MangoSecondary,
    onSecondary = MangoOnSecondary,
    secondaryContainer = MangoSecondaryContainer,
    onSecondaryContainer = MangoOnSecondaryContainer,
    tertiary = HibiscusTertiary,
    onTertiary = HibiscusOnTertiary,
    tertiaryContainer = HibiscusTertiaryContainer,
    onTertiaryContainer = HibiscusOnTertiaryContainer,
    background = OatBackground,
    onBackground = OatOnBackground,
    surface = OatSurface,
    onSurface = OatOnSurface,
    surfaceVariant = OatSurfaceVariant,
    onSurfaceVariant = OatOnSurfaceVariant,
    surfaceContainerLowest = OatSurfaceContainerLowest,
    surfaceContainerLow = OatSurfaceContainerLow,
    surfaceContainer = OatSurfaceContainer,
    surfaceContainerHigh = OatSurfaceContainerHigh,
    outline = OatOutline,
    outlineVariant = OatOutlineVariant,
    error = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = MintPrimaryDark,
    onPrimary = MintOnPrimaryDark,
    primaryContainer = MintPrimaryContainerDark,
    onPrimaryContainer = MintOnPrimaryContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = AmberOnSecondaryDark,
    secondaryContainer = AmberSecondaryContainerDark,
    onSecondaryContainer = AmberOnSecondaryContainerDark,
    tertiary = RoseTertiaryDark,
    onTertiary = RoseOnTertiaryDark,
    tertiaryContainer = RoseTertiaryContainerDark,
    onTertiaryContainer = RoseOnTertiaryContainerDark,
    background = ObsidianBackground,
    onBackground = ObsidianOnBackground,
    surface = ObsidianSurface,
    onSurface = ObsidianOnSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianOnSurfaceVariant,
    surfaceContainerLow = ObsidianSurfaceContainerLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceContainerHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant
)

val SegarKuShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SegarKuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SegarKuTypography,
        shapes = SegarKuShapes,
        content = content
    )
}
