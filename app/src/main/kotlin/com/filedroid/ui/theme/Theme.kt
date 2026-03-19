package com.filedroid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp

// Neutral surface colors — same for all accents
private val SurfaceLight   = Color(0xFFF8F9FA)
private val SurfaceDark    = Color(0xFF1C1C1E)
private val BackgroundLight = Color(0xFFFFFFFF)
private val BackgroundDark  = Color(0xFF121212)

@Composable
fun FileDroidTheme(
    prefs: ThemePreferences = ThemePreferences(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when (prefs.mode) {
        ThemeMode.MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= 31) {
                // Use system wallpaper-derived colors; respect system dark mode
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            } else {
                // Fallback to blue accent on older devices
                buildColorScheme(AccentColor.BLUE, dark = false)
            }
        }
        ThemeMode.DARK  -> buildColorScheme(prefs.accent, dark = true)
        ThemeMode.LIGHT -> buildColorScheme(prefs.accent, dark = false)
    }

    val typography = FileDroidTypography.run {
        val s = prefs.fontSize.scale
        copy(
            bodyLarge    = bodyLarge.copy(fontSize    = (bodyLarge.fontSize.value    * s).sp),
            bodyMedium   = bodyMedium.copy(fontSize   = (bodyMedium.fontSize.value   * s).sp),
            bodySmall    = bodySmall.copy(fontSize    = (bodySmall.fontSize.value    * s).sp),
            labelSmall   = labelSmall.copy(fontSize   = (labelSmall.fontSize.value   * s).sp),
            labelMedium  = labelMedium.copy(fontSize  = (labelMedium.fontSize.value  * s).sp),
            titleMedium  = titleMedium.copy(fontSize  = (titleMedium.fontSize.value  * s).sp),
            titleSmall   = titleSmall.copy(fontSize   = (titleSmall.fontSize.value   * s).sp),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}



private fun buildColorScheme(accent: AccentColor, dark: Boolean) = if (dark) {
    val primary   = accent.dark
    val container = accent.darkContainer
    darkColorScheme(
        primary            = primary,
        onPrimary          = Color.Black,
        primaryContainer   = container,
        onPrimaryContainer = primary,
        secondary          = accent.dark.copy(alpha = 0.8f),
        onSecondary        = Color.Black,
        secondaryContainer = container.copy(alpha = 0.6f),
        onSecondaryContainer = primary,
        tertiary           = accent.dark.copy(alpha = 0.6f),
        onTertiary         = Color.Black,
        background         = BackgroundDark,
        onBackground       = Color(0xFFE1E1E1),
        surface            = SurfaceDark,
        onSurface          = Color(0xFFE1E1E1),
        surfaceVariant     = Color(0xFF2C2C2E),
        onSurfaceVariant   = Color(0xFFAAAAAA),
        outline            = Color(0xFF636366),
        error              = Color(0xFFCF6679),
        onError            = Color.Black,
    )
} else {
    val primary   = accent.light
    val container = accent.lightContainer
    lightColorScheme(
        primary            = primary,
        onPrimary          = Color.White,
        primaryContainer   = container,
        onPrimaryContainer = accent.light.copy(alpha = 0.9f),
        secondary          = accent.light.copy(alpha = 0.75f),
        onSecondary        = Color.White,
        secondaryContainer = container.copy(alpha = 0.5f),
        onSecondaryContainer = primary,
        tertiary           = accent.light.copy(alpha = 0.55f),
        onTertiary         = Color.White,
        background         = BackgroundLight,
        onBackground       = Color(0xFF1C1C1E),
        surface            = SurfaceLight,
        onSurface          = Color(0xFF1C1C1E),
        surfaceVariant     = Color(0xFFE5E5EA),
        onSurfaceVariant   = Color(0xFF636366),
        outline            = Color(0xFFAEAEB2),
        error              = Color(0xFFB00020),
        onError            = Color.White,
    )
}
