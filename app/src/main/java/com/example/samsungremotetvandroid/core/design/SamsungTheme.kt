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
    primary = Color(0xFF1B5EBE),
    onPrimary = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F8FC),
    onSurface = Color(0xFF101726),
    surfaceVariant = Color(0xFFE5EBF4),
    onSurfaceVariant = Color(0xFF3D4A61),
    secondary = Color(0xFF1E4D8C),
    onSecondary = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val SamsungDarkScheme = darkColorScheme(
    primary = Color(0xFF9EC6FF),
    onPrimary = Color(0xFF00315F),
    surface = Color(0xFF111726),
    onSurface = Color(0xFFE2E9F7),
    surfaceVariant = Color(0xFF2A3448),
    onSurfaceVariant = Color(0xFFC2CEE3),
    secondary = Color(0xFFB6CCF5),
    onSecondary = Color(0xFF0E325F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val SamsungTypography = Typography(
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
