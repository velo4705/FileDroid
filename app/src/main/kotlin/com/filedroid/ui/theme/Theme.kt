package com.filedroid.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp

@Composable
fun FileDroidTheme(
    prefs: ThemePreferences = ThemePreferences(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = prefs.mode == ThemeMode.DARK ||
            (prefs.mode == ThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT >= 31)

    val colorScheme = when {
        prefs.mode == ThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT >= 31 ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> darkColorScheme(
            primary = prefs.accent.dark,
            onPrimary = prefs.accent.light.copy(alpha = 0.12f),
            secondary = prefs.accent.dark.copy(alpha = 0.7f),
        )
        else -> lightColorScheme(
            primary = prefs.accent.light,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = prefs.accent.light.copy(alpha = 0.7f),
        )
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
