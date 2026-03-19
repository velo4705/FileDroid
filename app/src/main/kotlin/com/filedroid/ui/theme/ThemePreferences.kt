package com.filedroid.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode { LIGHT, DARK, MATERIAL_YOU }

enum class AccentColor(
    val label: String,
    val light: Color,
    val lightContainer: Color,
    val dark: Color,
    val darkContainer: Color,
) {
    BLUE("Blue",
        light = Color(0xFF1565C0), lightContainer = Color(0xFFD0E4FF),
        dark  = Color(0xFF90CAF9), darkContainer  = Color(0xFF1A3A5C)),
    RED("Red",
        light = Color(0xFFC62828), lightContainer = Color(0xFFFFDAD6),
        dark  = Color(0xFFEF9A9A), darkContainer  = Color(0xFF5C1A1A)),
    GREEN("Green",
        light = Color(0xFF2E7D32), lightContainer = Color(0xFFB8F0B8),
        dark  = Color(0xFFA5D6A7), darkContainer  = Color(0xFF1A3D1A)),
    PURPLE("Purple",
        light = Color(0xFF6A1B9A), lightContainer = Color(0xFFEDD9FF),
        dark  = Color(0xFFCE93D8), darkContainer  = Color(0xFF3A1A5C)),
    ORANGE("Orange",
        light = Color(0xFFE65100), lightContainer = Color(0xFFFFDBC8),
        dark  = Color(0xFFFFCC80), darkContainer  = Color(0xFF5C2800)),
    TEAL("Teal",
        light = Color(0xFF00695C), lightContainer = Color(0xFFB2DFDB),
        dark  = Color(0xFF80CBC4), darkContainer  = Color(0xFF003D36)),
    PINK("Pink",
        light = Color(0xFFAD1457), lightContainer = Color(0xFFFFD8E8),
        dark  = Color(0xFFF48FB1), darkContainer  = Color(0xFF5C0A2E)),
    YELLOW("Yellow",
        light = Color(0xFFF57F17), lightContainer = Color(0xFFFFF3C4),
        dark  = Color(0xFFFFF176), darkContainer  = Color(0xFF5C3D00)),
}

enum class FontSize(val label: String, val scale: Float) {
    NORMAL("Normal", 1.0f),
    LARGE("Large",   1.15f),
    EXTRA_LARGE("Extra Large", 1.30f),
}

data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.DARK,
    val accent: AccentColor = AccentColor.BLUE,
    val fontSize: FontSize = FontSize.NORMAL,
)
