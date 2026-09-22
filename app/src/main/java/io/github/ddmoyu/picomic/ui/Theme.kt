package io.github.ddmoyu.picomic.ui

import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary=Color(0xFF405FAD), onPrimary=Color.White,
    secondaryContainer=Color(0xFFE6EDFB), onSecondaryContainer=Color(0xFF243E73), primaryContainer=Color(0xFFE6EDFB), onPrimaryContainer=Color(0xFF243E73), background=Color(0xFFF8F9FD),
    surface=Color(0xFFF8F9FD), surfaceContainer=Color.White, surfaceContainerLow=Color(0xFFEAF0FA), surfaceContainerHigh=Color(0xFFE8ECF5), surfaceContainerHighest=Color(0xFFDDE5F6),
    onSurface=Color(0xFF222C3C), onSurfaceVariant=Color(0xFF58667C), outline=Color(0xFF627089), outlineVariant=Color(0xFFE3E8F0))
private val Dark = darkColorScheme(primary=Color(0xFFAFC7FF), onPrimary=Color(0xFF15274E),
    secondaryContainer=Color(0xFF253754), onSecondaryContainer=Color(0xFFBFD1FF), primaryContainer=Color(0xFF253754), onPrimaryContainer=Color(0xFFBFD1FF), background=Color(0xFF12151E), surface=Color(0xFF12151E),
    surfaceContainer=Color(0xFF1B202D), surfaceContainerLow=Color(0xFF202E45), surfaceContainerHigh=Color(0xFF212838), surfaceContainerHighest=Color(0xFF303C55),
    onSurface=Color(0xFFE5EAF4), onSurfaceVariant=Color(0xFFB0BDD0), outline=Color(0xFFADBCD3), outlineVariant=Color(0xFF2B3446))
@Composable fun UiState.isDarkTheme(): Boolean = themeMode.isDark(isSystemInDarkTheme())
@Composable fun PiComicTheme(dark: Boolean, pureBlack: Boolean, content: @Composable () -> Unit) {
    val colors = if(dark) { if(pureBlack) Dark.copy(background=Color.Black,surface=Color.Black) else Dark } else Light
    MaterialTheme(colorScheme=colors, typography=Typography(), content=content)
}
