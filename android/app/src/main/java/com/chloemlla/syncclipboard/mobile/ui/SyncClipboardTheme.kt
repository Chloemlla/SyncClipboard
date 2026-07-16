package com.chloemlla.syncclipboard.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Palette / surface language mirrored from Project-Lumen soft Material 3.
private val LumenTeal = Color(0xFF126B66)
private val LumenOnPrimary = Color.White
private val LumenTealContainer = Color(0xFFCDEDEA)
private val LumenOnTealContainer = Color(0xFF083B38)
private val LumenCoral = Color(0xFFB85C38)
private val LumenOnCoral = Color.White
private val LumenCoralContainer = Color(0xFFFFDBCC)
private val LumenOnCoralContainer = Color(0xFF5A1C0A)
private val LumenIndigo = Color(0xFF525DAA)
private val LumenOnIndigo = Color.White
private val LumenIndigoContainer = Color(0xFFE1E3FF)
private val LumenOnIndigoContainer = Color(0xFF181D62)
private val LumenSurface = Color(0xFFFFFCFA)
private val LumenSurfaceVariant = Color(0xFFE0E7E4)
private val LumenSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LumenSurfaceContainerLow = Color(0xFFF4F7F4)
private val LumenSurfaceContainer = Color(0xFFF0F4F1)
private val LumenSurfaceContainerHigh = Color(0xFFE8EEEA)
private val LumenBackground = Color(0xFFF8FAF7)
private val LumenOnBackground = Color(0xFF263331)
private val LumenOnSurface = Color(0xFF263331)
private val LumenOnSurfaceVariant = Color(0xFF556360)
private val LumenOutline = Color(0xFF6D7A76)
private val LumenOutlineVariant = Color(0xFFC4CECA)

private val LumenTealDark = Color(0xFF8ED6D1)
private val LumenOnPrimaryDark = Color(0xFF003734)
private val LumenTealContainerDark = Color(0xFF0A504C)
private val LumenOnTealContainerDark = Color(0xFFCDEDEA)
private val LumenCoralDark = Color(0xFFFFB59A)
private val LumenOnCoralDark = Color(0xFF612100)
private val LumenCoralContainerDark = Color(0xFF8B3F21)
private val LumenOnCoralContainerDark = Color(0xFFFFDBCC)
private val LumenIndigoDark = Color(0xFFC2C6FF)
private val LumenOnIndigoDark = Color(0xFF242B75)
private val LumenIndigoContainerDark = Color(0xFF3A438F)
private val LumenOnIndigoContainerDark = Color(0xFFE1E3FF)
private val LumenSurfaceDark = Color(0xFF111815)
private val LumenSurfaceVariantDark = Color(0xFF303A36)
private val LumenSurfaceContainerLowestDark = Color(0xFF0C1210)
private val LumenSurfaceContainerLowDark = Color(0xFF161D1A)
private val LumenSurfaceContainerDark = Color(0xFF1A211E)
private val LumenSurfaceContainerHighDark = Color(0xFF242C28)
private val LumenBackgroundDark = Color(0xFF0C1210)
private val LumenOnBackgroundDark = Color(0xFFDEE4E0)
private val LumenOnSurfaceDark = Color(0xFFDEE4E0)
private val LumenOnSurfaceVariantDark = Color(0xFFBFC9C4)
private val LumenOutlineDark = Color(0xFF8A9691)
private val LumenOutlineVariantDark = Color(0xFF404B47)

private val SyncClipboardLightColors = lightColorScheme(
    primary = LumenTeal,
    onPrimary = LumenOnPrimary,
    primaryContainer = LumenTealContainer,
    onPrimaryContainer = LumenOnTealContainer,
    secondary = LumenCoral,
    onSecondary = LumenOnCoral,
    secondaryContainer = LumenCoralContainer,
    onSecondaryContainer = LumenOnCoralContainer,
    tertiary = LumenIndigo,
    onTertiary = LumenOnIndigo,
    tertiaryContainer = LumenIndigoContainer,
    onTertiaryContainer = LumenOnIndigoContainer,
    background = LumenBackground,
    onBackground = LumenOnBackground,
    surface = LumenSurface,
    onSurface = LumenOnSurface,
    surfaceVariant = LumenSurfaceVariant,
    onSurfaceVariant = LumenOnSurfaceVariant,
    surfaceContainerLowest = LumenSurfaceContainerLowest,
    surfaceContainerLow = LumenSurfaceContainerLow,
    surfaceContainer = LumenSurfaceContainer,
    surfaceContainerHigh = LumenSurfaceContainerHigh,
    outline = LumenOutline,
    outlineVariant = LumenOutlineVariant,
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
)

private val SyncClipboardDarkColors = darkColorScheme(
    primary = LumenTealDark,
    onPrimary = LumenOnPrimaryDark,
    primaryContainer = LumenTealContainerDark,
    onPrimaryContainer = LumenOnTealContainerDark,
    secondary = LumenCoralDark,
    onSecondary = LumenOnCoralDark,
    secondaryContainer = LumenCoralContainerDark,
    onSecondaryContainer = LumenOnCoralContainerDark,
    tertiary = LumenIndigoDark,
    onTertiary = LumenOnIndigoDark,
    tertiaryContainer = LumenIndigoContainerDark,
    onTertiaryContainer = LumenOnIndigoContainerDark,
    background = LumenBackgroundDark,
    onBackground = LumenOnBackgroundDark,
    surface = LumenSurfaceDark,
    onSurface = LumenOnSurfaceDark,
    surfaceVariant = LumenSurfaceVariantDark,
    onSurfaceVariant = LumenOnSurfaceVariantDark,
    surfaceContainerLowest = LumenSurfaceContainerLowestDark,
    surfaceContainerLow = LumenSurfaceContainerLowDark,
    surfaceContainer = LumenSurfaceContainerDark,
    surfaceContainerHigh = LumenSurfaceContainerHighDark,
    outline = LumenOutlineDark,
    outlineVariant = LumenOutlineVariantDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Shape scale aligned with Project-Lumen: 8 / 12 / 16 / 20 / 28.
private val SyncClipboardShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val SyncCardShape = RoundedCornerShape(20.dp)
val SyncPreferenceShape = RoundedCornerShape(20.dp)
val SyncIconChipShape = RoundedCornerShape(12.dp)
val SyncPillShape = RoundedCornerShape(12.dp)
val SyncBannerShape = RoundedCornerShape(16.dp)

@Composable
fun syncCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
fun syncCardElevation() = CardDefaults.cardElevation(
    defaultElevation = 0.dp,
    pressedElevation = 0.dp,
    focusedElevation = 0.dp,
    hoveredElevation = 0.dp,
    draggedElevation = 1.dp,
    disabledElevation = 0.dp,
)

@Composable
fun syncCardBorder(): BorderStroke? = null

@Composable
fun SyncClipboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SyncClipboardDarkColors else SyncClipboardLightColors,
        shapes = SyncClipboardShapes,
        content = content,
    )
}
