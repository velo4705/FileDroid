package com.filedroid.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode { LIGHT, DARK, MATERIAL_YOU }

enum class AccentColor(val label: String, val light: Color, val dark: Color) {
    BLUE("Blue",        Color(0xFF1565C0), Color(0xFF90CAF9)),
    RED("Red",          Color(0xFFC62828), Color(0xFFEF9A9A)),
    GREEN("Green",      Color(0xFF2E7D32), Color(0xFFA5D6A7)),
    PURPLE("Purple",    Color(0xFF6A1B9A), Color(0xFFCE93D8)),
    ORANGE("Orange",    Color(0xFFE65100), Color(0xFFFFCC80)),
    TEAL("Teal",        Color(0xFF00695C), Color(0xFF80CBC4)),
    PINK("Pink",        Color(0xFFAD1457), Color(0xFFF48FB1)),
    YELLOW("Yellow",    Color(0xFFF9A825), Color(0xFFFFF176)),
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
