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
        light = Color(0xFF0057FF), lightContainer = Color(0xFFD6E4FF),
        dark  = Color(0xFF4D9FFF), darkContainer  = Color(0xFF003399)),
    RED("Red",
        light = Color(0xFFE5000A), lightContainer = Color(0xFFFFDAD6),
        dark  = Color(0xFFFF5252), darkContainer  = Color(0xFF7A0008)),
    GREEN("Green",
        light = Color(0xFF00A550), lightContainer = Color(0xFFB6F5D0),
        dark  = Color(0xFF00E676), darkContainer  = Color(0xFF005229)),
    PURPLE("Purple",
        light = Color(0xFF7C00E0), lightContainer = Color(0xFFEDD9FF),
        dark  = Color(0xFFD580FF), darkContainer  = Color(0xFF4A0080)),
    ORANGE("Orange",
        light = Color(0xFFFF6200), lightContainer = Color(0xFFFFDBC8),
        dark  = Color(0xFFFF8C42), darkContainer  = Color(0xFF7A2E00)),
    TEAL("Teal",
        light = Color(0xFF00B4A0), lightContainer = Color(0xFFB2F0EB),
        dark  = Color(0xFF00E5CC), darkContainer  = Color(0xFF005C52)),
    PINK("Pink",
        light = Color(0xFFE91E8C), lightContainer = Color(0xFFFFD6EE),
        dark  = Color(0xFFFF6EC7), darkContainer  = Color(0xFF7A0047)),
    YELLOW("Yellow",
        light = Color(0xFFFFAB00), lightContainer = Color(0xFFFFF3C4),
        dark  = Color(0xFFFFD740), darkContainer  = Color(0xFF7A5200),
        onLight = Color(0xFF1C1C1E)),
    CYAN("Cyan",
        light = Color(0xFF00B0FF), lightContainer = Color(0xFFCCEEFF),
        dark  = Color(0xFF40D4FF), darkContainer  = Color(0xFF005880)),
    INDIGO("Indigo",
        light = Color(0xFF3D5AFE), lightContainer = Color(0xFFD5D9FF),
        dark  = Color(0xFF8187FF), darkContainer  = Color(0xFF1A237E)),
    LIME("Lime",
        light = Color(0xFF76FF03), lightContainer = Color(0xFFE8FFD0),
        dark  = Color(0xFFB2FF59), darkContainer  = Color(0xFF2E6B00),
        onLight = Color(0xFF1C1C1E)),
    ROSE("Rose",
        light = Color(0xFFFF1744), lightContainer = Color(0xFFFFCDD2),
        dark  = Color(0xFFFF6D7A), darkContainer  = Color(0xFF7A0020)),
    AMBER("Amber",
        light = Color(0xFFFF9100), lightContainer = Color(0xFFFFECB3),
        dark  = Color(0xFFFFBD45), darkContainer  = Color(0xFF7A4500),
        onLight = Color(0xFF1C1C1E)),
    DEEP_PURPLE("Deep Purple",
        light = Color(0xFF6200EA), lightContainer = Color(0xFFEDE7F6),
        dark  = Color(0xFFB388FF), darkContainer  = Color(0xFF30007A)),
    BROWN("Brown",
        light = Color(0xFFBF360C), lightContainer = Color(0xFFFFCCBC),
        dark  = Color(0xFFFF8A65), darkContainer  = Color(0xFF5C1A00)),
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
