package com.rupeelog.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Finance App Color Palette
object FinanceColors {
    // Primary - Green for positive/savings
    val Primary = Color(0xFF10B981)
    val PrimaryVariant = Color(0xFF059669)
    val OnPrimary = Color.White

    // Secondary - Blue for info
    val Secondary = Color(0xFF3B82F6)
    val SecondaryVariant = Color(0xFF2563EB)
    val OnSecondary = Color.White

    // Semantic Colors
    val Positive = Color(0xFF10B981)  // Green - Income, savings
    val Negative = Color(0xFFEF4444)  // Red - Expenses
    val Warning = Color(0xFFF59E0B)   // Amber - Budget warnings
    val Info = Color(0xFF3B82F6)      // Blue - Information

    // Category Colors
    val CategoryFood = Color(0xFFF97316)
    val CategoryGroceries = Color(0xFF22C55E)
    val CategoryTransport = Color(0xFF3B82F6)
    val CategoryShopping = Color(0xFFEC4899)
    val CategoryUtilities = Color(0xFF8B5CF6)
    val CategoryEntertainment = Color(0xFFF43F5E)
    val CategoryHealth = Color(0xFF14B8A6)
    val CategoryTransfers = Color(0xFF6366F1)
    val CategoryOther = Color(0xFF6B7280)

    // Light Theme
    val BackgroundLight = Color(0xFFF9FAFB)
    val SurfaceLight = Color.White
    val OnBackgroundLight = Color(0xFF111827)
    val OnSurfaceLight = Color(0xFF111827)
    val SurfaceVariantLight = Color(0xFFF3F4F6)

    // Dark Theme
    val BackgroundDark = Color(0xFF111827)
    val SurfaceDark = Color(0xFF1F2937)
    val OnBackgroundDark = Color(0xFFF9FAFB)
    val OnSurfaceDark = Color(0xFFF9FAFB)
    val SurfaceVariantDark = Color(0xFF374151)
}

private val LightColorScheme = lightColorScheme(
    primary = FinanceColors.Primary,
    onPrimary = FinanceColors.OnPrimary,
    primaryContainer = FinanceColors.Primary.copy(alpha = 0.1f),
    onPrimaryContainer = FinanceColors.PrimaryVariant,
    secondary = FinanceColors.Secondary,
    onSecondary = FinanceColors.OnSecondary,
    secondaryContainer = FinanceColors.Secondary.copy(alpha = 0.1f),
    onSecondaryContainer = FinanceColors.SecondaryVariant,
    background = FinanceColors.BackgroundLight,
    onBackground = FinanceColors.OnBackgroundLight,
    surface = FinanceColors.SurfaceLight,
    onSurface = FinanceColors.OnSurfaceLight,
    surfaceVariant = FinanceColors.SurfaceVariantLight,
    onSurfaceVariant = FinanceColors.OnSurfaceLight.copy(alpha = 0.7f),
    error = FinanceColors.Negative,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = FinanceColors.Primary,
    onPrimary = FinanceColors.OnPrimary,
    primaryContainer = FinanceColors.Primary.copy(alpha = 0.2f),
    onPrimaryContainer = FinanceColors.Primary,
    secondary = FinanceColors.Secondary,
    onSecondary = FinanceColors.OnSecondary,
    secondaryContainer = FinanceColors.Secondary.copy(alpha = 0.2f),
    onSecondaryContainer = FinanceColors.Secondary,
    background = FinanceColors.BackgroundDark,
    onBackground = FinanceColors.OnBackgroundDark,
    surface = FinanceColors.SurfaceDark,
    onSurface = FinanceColors.OnSurfaceDark,
    surfaceVariant = FinanceColors.SurfaceVariantDark,
    onSurfaceVariant = FinanceColors.OnSurfaceDark.copy(alpha = 0.7f),
    error = FinanceColors.Negative,
    onError = Color.White
)

@Composable
fun RupeeLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

val Typography = Typography()
