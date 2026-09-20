package io.github.ddmoyu.picomic.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary=Color(0xFF405FAD), onPrimary=Color.White,
    secondaryContainer=Color(0xFFDCE5FF), onSecondaryContainer=Color(0xFF243D72), primaryContainer=Color(0xFFDCE5FF), onPrimaryContainer=Color(0xFF243D72), background=Color(0xFFF8F9FD),
    surface=Color(0xFFF8F9FD), surfaceContainer=Color(0xFFEEF1F8), surfaceContainerHigh=Color(0xFFE8ECF5),
    onSurface=Color(0xFF202633), onSurfaceVariant=Color(0xFF6C7487), outlineVariant=Color(0xFFE1E5EF))
private val Dark = darkColorScheme(primary=Color(0xFFAEC6FF), onPrimary=Color(0xFF15274E),
    secondaryContainer=Color(0xFF263959), onSecondaryContainer=Color(0xFFAEC6FF), primaryContainer=Color(0xFF263959), background=Color(0xFF12151E), surface=Color(0xFF12151E),
    surfaceContainer=Color(0xFF1B202D), surfaceContainerHigh=Color(0xFF212838), onSurface=Color(0xFFE5E9F2), onSurfaceVariant=Color(0xFFA6B0C4))
@Composable fun PiComicTheme(dark: Boolean, pureBlack: Boolean, content: @Composable () -> Unit) {
    val colors = if(dark) { if(pureBlack) Dark.copy(background=Color.Black,surface=Color.Black) else Dark } else Light
    MaterialTheme(colorScheme=colors, typography=Typography(), content=content)
}
