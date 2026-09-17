package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CleanMinDarkPrimary,
    onPrimary = CleanMinDarkOnPrimary,
    primaryContainer = CleanMinDarkPrimaryContainer,
    onPrimaryContainer = CleanMinDarkOnPrimaryContainer,
    secondary = CleanMinSecondary,
    secondaryContainer = CleanMinSecondaryContainer,
    onSecondaryContainer = CleanMinOnSecondaryContainer,
    background = CleanMinDarkBackground,
    surface = CleanMinDarkSurface,
    surfaceVariant = CleanMinDarkKeypadBackground,
    surfaceContainer = CleanMinDarkKeypadBackground,
    surfaceContainerHigh = CleanMinDarkKeypadBackground,
    onSurface = CleanMinDarkOnSurface,
    onSurfaceVariant = CleanMinDarkOnSurfaceVariant,
    outline = CleanMinDarkDivider,
    error = CleanMinError,
    onError = CleanMinOnError,
    errorContainer = CleanMinErrorContainer,
    onErrorContainer = CleanMinOnErrorContainer
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CleanMinPrimary,
    onPrimary = CleanMinOnPrimary,
    primaryContainer = CleanMinPrimaryContainer,
    onPrimaryContainer = CleanMinOnPrimaryContainer,
    secondary = CleanMinSecondary,
    secondaryContainer = CleanMinSecondaryContainer,
    onSecondaryContainer = CleanMinOnSecondaryContainer,
    background = CleanMinBackground,
    surface = CleanMinSurface,
    surfaceVariant = CleanMinKeypadBackground,
    surfaceContainer = CleanMinSurfaceCard,
    surfaceContainerHigh = CleanMinKeypadBackground,
    onSurface = CleanMinOnSurface,
    onSurfaceVariant = CleanMinOnSurfaceVariant,
    outline = CleanMinDivider,
    error = CleanMinError,
    onError = CleanMinOnError,
    errorContainer = CleanMinErrorContainer,
    onErrorContainer = CleanMinOnErrorContainer
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Use intentional Clean Minimalism scheme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
