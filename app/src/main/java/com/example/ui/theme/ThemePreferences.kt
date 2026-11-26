package com.example.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromRaw(raw: String?): AppThemeMode {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
        }
    }
}

/**
 * Persists the user's preferred appearance mode (System / Light / Dark) locally.
 */
class ThemePreferencesManager private constructor(appContext: Context) {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        AppThemeMode.fromRaw(prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name))
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun toggleDarkLight(currentlyDark: Boolean) {
        setThemeMode(if (currentlyDark) AppThemeMode.LIGHT else AppThemeMode.DARK)
    }

    companion object {
        private const val PREFS_NAME = "bunny_memo_theme_prefs_v1"
        private const val KEY_THEME_MODE = "theme_mode"

        @Volatile
        private var INSTANCE: ThemePreferencesManager? = null

        fun getInstance(context: Context): ThemePreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
