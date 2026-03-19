package com.filedroid.ui.theme

import androidx.lifecycle.ViewModel
import com.filedroid.security.CredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val KEY_THEME_MODE   = "theme_mode"
private const val KEY_ACCENT_COLOR = "accent_color"
private const val KEY_FONT_SIZE    = "font_size"

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val store: CredentialStore
) : ViewModel() {

    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<ThemePreferences> = _prefs.asStateFlow()

    fun setMode(mode: ThemeMode) {
        store.putString(KEY_THEME_MODE, mode.name)
        _prefs.update { it.copy(mode = mode) }
    }

    fun setAccent(accent: AccentColor) {
        store.putString(KEY_ACCENT_COLOR, accent.name)
        _prefs.update { it.copy(accent = accent) }
    }

    fun setFontSize(size: FontSize) {
        store.putString(KEY_FONT_SIZE, size.name)
        _prefs.update { it.copy(fontSize = size) }
    }

    private fun load() = ThemePreferences(
        mode   = store.getString(KEY_THEME_MODE)?.let   { runCatching { ThemeMode.valueOf(it) }.getOrNull() }   ?: ThemeMode.DARK,
        accent = store.getString(KEY_ACCENT_COLOR)?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() } ?: AccentColor.BLUE,
        fontSize = store.getString(KEY_FONT_SIZE)?.let  { runCatching { FontSize.valueOf(it) }.getOrNull() }    ?: FontSize.NORMAL,
    )
}
