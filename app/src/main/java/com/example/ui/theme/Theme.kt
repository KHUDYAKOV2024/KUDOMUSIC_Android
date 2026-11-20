package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SoundPulseDarkColorScheme = darkColorScheme(
    primary = PulseGreen,
    onPrimary = ObsidianDeep,
    primaryContainer = PulseGreenDark,
    onPrimaryContainer = TextPrimary,
    secondary = PulseGreenBright,
    onSecondary = ObsidianDeep,
    secondaryContainer = ElevatedCardHighlight,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentMidnight,
    onTertiary = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianDeep,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = ElevatedCard,
    surfaceContainerHigh = ElevatedCardHighlight,
    surfaceContainerHighest = SubtleBorder,
    outline = SubtleBorder,
    outlineVariant = ElevatedCardHighlight,
    error = HeartRed,
    onError = TextPrimary
)

@Composable
fun SoundPulseTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SoundPulseDarkColorScheme,
        typography = SoundPulseTypography,
        content = content
    )
}
