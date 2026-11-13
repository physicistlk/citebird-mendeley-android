package com.refmanager.tablet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

object CiteColors {
    val TopBarBg = Color(0xFFF3F4F6)
    val SidebarBg = Color(0xFFF6F7F9)
    val SidebarSelected = Color(0xFF326891)
    val CanvasBg = Color(0xFFF0F2F5)
    val CardBg = Color(0xFFFFFFFF)
    val CardDrawerBg = Color(0xFFF5F7F9)
    val Border = Color(0xFFDDE1E6)
    val BorderSubtle = Color(0xFFE8EBEE)
    val TitleNavy = Color(0xFF1B4D7A)
    val JournalGreen = Color(0xFF2E7D32)
    val FolderYellow = Color(0xFFD4AC2B)
    val AddGreen = Color(0xFF53A551)
    val TextPrimary = Color(0xFF202124)
    val TextSecondary = Color(0xFF5F6368)
    val TextMuted = Color(0xFF80868B)

    val LabelPalette = listOf(
        0xFFD93025.toInt(), // Red (Important)
        0xFF1A73E8.toInt(), // Blue (Backlog)
        0xFF388E3C.toInt(), // Green (Ecology)
        0xFFE67C22.toInt(), // Orange (Behaviour)
        0xFF7B1FA2.toInt(), // Purple (Immune system)
        0xFF00897B.toInt(), // Teal (Genetics)
        0xFF546E7A.toInt()  // Slate
    )
}

private val Brand = Color(0xFF326891)
private val BrandLight = Color(0xFF5893BE)
private val Sand = Color(0xFFF0F2F5)
private val NavyTitle = Color(0xFF1B4D7A)
private val JournalGreen = Color(0xFF2E7D32)

private val LightScheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E7F0),
    onPrimaryContainer = Color(0xFF001F30),
    secondary = JournalGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6EAD0),
    onSecondaryContainer = Color(0xFF0F2A0F),
    tertiary = NavyTitle,
    background = Sand,
    onBackground = Color(0xFF202124),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFE8EBEE),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFDDE1E6)
)

private val DarkScheme = darkColorScheme(
    primary = BrandLight,
    onPrimary = Color(0xFF002438),
    primaryContainer = Color(0xFF1F4E6F),
    onPrimaryContainer = Color(0xFFD6E7F0),
    secondary = Color(0xFF81C784),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1B1E22),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2A2E34),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF3C4043)
)

/** Tablet density is 340 dpi (≈1.9x), tuned for crisp desktop-like information density. */
private val TabletTypography = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 13.5.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
)

@Composable
fun RefManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = TabletTypography,
        content = content
    )
}
