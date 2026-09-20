package io.github.ddmoyu.picomic.data

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色模式"), DARK("深色模式");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromPreferences(preferences: Map<String, String>): ThemeMode =
            entries.firstOrNull { it.label == preferences["themeMode"] }
                // The old switch was only persisted after an explicit user choice.
                ?: when (preferences["dark"]) {
                    "true" -> DARK
                    "false" -> LIGHT
                    else -> SYSTEM
                }
    }
}
