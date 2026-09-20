package io.github.ddmoyu.picomic.data

import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {
    @Test fun unsetThemeFollowsBothSystemAppearances() {
        val mode = ThemeMode.fromPreferences(emptyMap())
        assertEquals(ThemeMode.SYSTEM, mode)
        assertFalse(mode.isDark(false))
        assertTrue(mode.isDark(true))
    }

    @Test fun explicitChoicesOverrideSystemAndLegacySwitch() {
        for (systemDark in listOf(false, true)) {
            assertFalse(ThemeMode.fromPreferences(mapOf("themeMode" to "浅色模式", "dark" to "true")).isDark(systemDark))
            assertTrue(ThemeMode.fromPreferences(mapOf("themeMode" to "深色模式", "dark" to "false")).isDark(systemDark))
            assertEquals(systemDark, ThemeMode.fromPreferences(mapOf("themeMode" to "跟随系统", "dark" to "true")).isDark(systemDark))
        }
    }

    @Test fun existingManualChoicesRemainValid() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromPreferences(mapOf("dark" to "true")))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPreferences(mapOf("dark" to "false")))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferences(mapOf("themeMode" to "unknown", "dark" to "unknown")))
    }
}
