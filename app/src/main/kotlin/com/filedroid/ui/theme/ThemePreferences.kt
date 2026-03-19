package com.filedroid.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM, MATERIAL_YOU }

enum class AccentColor(
    val label: String,
    val light: Color,
    val lightContainer: Color,
    val dark: Color,
    val darkContainer: Color,
    /** Text color on top of [light] — white for dark primaries, near-black for light ones */
    val onLight: Color = Color.White,
    /** Text color on top of [dark] — dark for light primaries */
    val onDark: Color = Color(0xFF1C1C1E),
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
        dark  = Color(0xFFFFF176), darkContainer  = Color(0xFF5C3D00),
        onLight = Color(0xFF1C1C1E)),
    CYAN("Cyan",
        light = Color(0xFF00838F), lightContainer = Color(0xFFB2EBF2),
        dark  = Color(0xFF80DEEA), darkContainer  = Color(0xFF003D47)),
    INDIGO("Indigo",
        light = Color(0xFF283593), lightContainer = Color(0xFFD5D9FF),
        dark  = Color(0xFF9FA8DA), darkContainer  = Color(0xFF0D1A5C)),
    LIME("Lime",
        light = Color(0xFF558B2F), lightContainer = Color(0xFFDCEDC8),
        dark  = Color(0xFFAED581), darkContainer  = Color(0xFF2A4A10),
        onLight = Color(0xFF1C1C1E)),
    ROSE("Rose",
        light = Color(0xFFB71C1C), lightContainer = Color(0xFFFFCDD2),
        dark  = Color(0xFFFF8A80), darkContainer  = Color(0xFF5C0A0A)),
    AMBER("Amber",
        light = Color(0xFFFF6F00), lightContainer = Color(0xFFFFECB3),
        dark  = Color(0xFFFFD54F), darkContainer  = Color(0xFF5C3600),
        onLight = Color(0xFF1C1C1E)),
    DEEP_PURPLE("Deep Purple",
        light = Color(0xFF4527A0), lightContainer = Color(0xFFEDE7F6),
        dark  = Color(0xFFB39DDB), darkContainer  = Color(0xFF1A0A5C)),
    BROWN("Brown",
        light = Color(0xFF4E342E), lightContainer = Color(0xFFD7CCC8),
        dark  = Color(0xFFBCAAA4), darkContainer  = Color(0xFF2A1A16)),
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
