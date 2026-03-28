package com.example.samsungremotetvandroid.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SamsungLightScheme = lightColorScheme(
    primary = Color(0xFF0D63CE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E7FF),
    onPrimaryContainer = Color(0xFF062C5E),
    surface = Color(0xFFF1F4FA),
    onSurface = Color(0xFF0E1728),
    surfaceVariant = Color(0xFFDCE3EF),
    onSurfaceVariant = Color(0xFF2D3B52),
    secondary = Color(0xFF20518F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE7FF),
    onSecondaryContainer = Color(0xFF06284F),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val SamsungDarkScheme = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF002F67),
    primaryContainer = Color(0xFF123A72),
    onPrimaryContainer = Color(0xFFD9E7FF),
    surface = Color(0xFF0C121D),
    onSurface = Color(0xFFE8EEF9),
    surfaceVariant = Color(0xFF1E283A),
    onSurfaceVariant = Color(0xFFD4DEEF),
    secondary = Color(0xFFAAC7F5),
    onSecondary = Color(0xFF0A315F),
    secondaryContainer = Color(0xFF263957),
    onSecondaryContainer = Color(0xFFDCE7FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val SamsungTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private val SamsungShapes = Shapes(
    small = RoundedCornerShape(SamsungRadii.RadiusSm),
    medium = RoundedCornerShape(SamsungRadii.RadiusMd),
    large = RoundedCornerShape(SamsungRadii.RadiusLg)
)

@Immutable
data class SamsungSemanticColors(
    val destructive: Color,
    val success: Color,
    val warning: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

private val LightSemanticColors = SamsungSemanticColors(
    destructive = Color(0xFFB3261E),
    success = Color(0xFF146C2E),
    warning = Color(0xFFA35A00),
    textPrimary = Color(0xFF101726),
    textSecondary = Color(0xFF4A5870)
)

private val DarkSemanticColors = SamsungSemanticColors(
    destructive = Color(0xFFFFB4AB),
    success = Color(0xFF83D79C),
    warning = Color(0xFFFFC785),
    textPrimary = Color(0xFFE2E9F7),
    textSecondary = Color(0xFFC2CEE3)
)

private val LocalSamsungSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object SamsungTheme {
    val semanticColors: SamsungSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSamsungSemanticColors.current
}

@Composable
fun SamsungRemoteTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = if (isDark) SamsungDarkScheme else SamsungLightScheme
    val semanticColors = if (isDark) DarkSemanticColors else LightSemanticColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SamsungTypography,
        shapes = SamsungShapes,
        content = {
            CompositionLocalProvider(
                LocalSamsungSemanticColors provides semanticColors,
                content = content
            )
        }
    )
}
