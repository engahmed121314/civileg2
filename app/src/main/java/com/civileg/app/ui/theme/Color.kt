package com.civileg.app.ui.theme

import androidx.compose.ui.graphics.Color

// ===== الألوان الأساسية (Light Theme) =====
val PrimaryLight = Color(0xFF1976D2)
val PrimaryVariantLight = Color(0xFF0D47A1)
val SecondaryLight = Color(0xFFFF9800)
val SecondaryVariantLight = Color(0xFFE65100)
val BackgroundLight = Color(0xFFF5F5F5)
val SurfaceLight = Color(0xFFFFFFFF)
val ErrorLight = Color(0xFFB00020)

// ===== الألوان الأساسية (Dark Theme) =====
val PrimaryDark = Color(0xFF90CAF9)
val PrimaryVariantDark = Color(0xFF42A5F5)
val SecondaryDark = Color(0xFFFFB74D)
val SecondaryVariantDark = Color(0xFFFFA726)
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val ErrorDark = Color(0xFFCF6679)

// ===== ألوان النصوص =====
val TextPrimaryLight = Color(0xFF212121)
val TextSecondaryLight = Color(0xFF757575)
val TextPrimaryDark = Color(0xFFE0E0E0)
val TextSecondaryDark = Color(0xFFB0B0B0)

// ===== ألوان الحالة =====
val SafeLight = Color(0xFF4CAF50)
val SafeDark = Color(0xFF81C784)
val WarningLight = Color(0xFFFFC107)
val WarningDark = Color(0xFFFFD54F)
val UnsafeLight = Color(0xFFE53935)
val UnsafeDark = Color(0xFFEF5350)

// ===== ألوان العناصر الإنشائية =====
val ConcreteLight = Color(0xFF9E9E9E)
val ConcreteDark = Color(0xFFBDBDBD)
val SteelLight = Color(0xFF5D4037)
val SteelDark = Color(0xFF8D6E63)
val DimensionLight = Color(0xFF1976D2)
val DimensionDark = Color(0xFF64B5F6)

// ===== ألوان الرسوم البيانية =====
val ChartColorsLight = listOf(
    Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF57C00),
    Color(0xFF7B1FA2), Color(0xFFC62828), Color(0xFF00838F)
)

val ChartColorsDark = listOf(
    Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
    Color(0xFFBA68C8), Color(0xFFEF5350), Color(0xFF4DD0E1)
)

// Legacy compatibility aliases (optional, if needed by existing code)
val PrimaryColor = PrimaryLight
val PrimaryVariant = PrimaryVariantLight
val SecondaryColor = SecondaryLight
val SecondaryVariant = SecondaryVariantLight
val BackgroundColor = BackgroundLight
val SurfaceColor = SurfaceLight
val ErrorColor = ErrorLight
val TextPrimary = TextPrimaryLight
val TextSecondary = TextSecondaryLight
val SafeColor = SafeLight
val WarningColor = WarningLight
val UnsafeColor = UnsafeLight
val ConcreteColor = ConcreteLight
val SteelColor = SteelLight
