package com.stoa.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.stoa.app.R

/** Ink on paper: strictly black & white, with greys for hierarchy. */
@Immutable
data class Ink(
    val bg: Color, val card: Color, val ink: Color, val mute: Color, val faint: Color,
    val line: Color, val line2: Color, val inv: Color, val warn: Color, val dark: Boolean
)
private val DarkInk = Ink(Color(0xFF0C0C0D), Color(0xFF161617), Color(0xFFEDEDEB), Color(0xFF8D8B87), Color(0xFF55534F),
    Color(0x14FFFFFF), Color(0x2BFFFFFF), Color(0xFF0C0C0D), Color(0xFFD9775E), true)
private val LightInk = Ink(Color(0xFFF4F2EE), Color(0xFFFBFAF7), Color(0xFF141414), Color(0xFF75726C), Color(0xFFB4B0A8),
    Color(0x17141414), Color(0x2E141414), Color(0xFFF4F2EE), Color(0xFFC2553A), false)

val LocalInk = staticCompositionLocalOf { DarkInk }
/* Outfit for everything you read, JetBrains Mono for numbers and labels (fetched from Google Fonts at build time). */
val Display = FontFamily(
    Font(R.font.outfit_extralight, FontWeight.ExtraLight), Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_regular, FontWeight.Normal), Font(R.font.outfit_medium, FontWeight.Medium), Font(R.font.outfit_semibold, FontWeight.SemiBold))
val Serif = Display
val Mono = FontFamily(Font(R.font.jbmono_regular, FontWeight.Normal), Font(R.font.jbmono_medium, FontWeight.Medium))
private val Body = TextStyle(fontFamily = Display, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp)
private val TYPE = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Display), displayMedium = t.displayMedium.copy(fontFamily = Display), displaySmall = t.displaySmall.copy(fontFamily = Display),
        headlineLarge = t.headlineLarge.copy(fontFamily = Display), headlineMedium = t.headlineMedium.copy(fontFamily = Display), headlineSmall = t.headlineSmall.copy(fontFamily = Display),
        titleLarge = t.titleLarge.copy(fontFamily = Display), titleMedium = t.titleMedium.copy(fontFamily = Display), titleSmall = t.titleSmall.copy(fontFamily = Display),
        bodyLarge = Body, bodyMedium = Body.copy(fontSize = 14.sp), bodySmall = Body.copy(fontSize = 12.sp),
        labelLarge = t.labelLarge.copy(fontFamily = Display), labelMedium = t.labelMedium.copy(fontFamily = Display), labelSmall = t.labelSmall.copy(fontFamily = Display))
}

@Composable
fun StoaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ink = if (dark) DarkInk else LightInk
    val scheme = if (dark) darkColorScheme(background = ink.bg, surface = ink.card, onSurface = ink.ink, onBackground = ink.ink, primary = ink.ink, onPrimary = ink.inv)
        else lightColorScheme(background = ink.bg, surface = ink.card, onSurface = ink.ink, onBackground = ink.ink, primary = ink.ink, onPrimary = ink.inv)
    CompositionLocalProvider(LocalInk provides ink) { MaterialTheme(colorScheme = scheme, typography = TYPE, content = content) }
}
