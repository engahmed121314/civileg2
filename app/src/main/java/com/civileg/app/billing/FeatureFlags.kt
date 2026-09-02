package com.civileg.app.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.civileg.app.data.local.PreferencesManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized feature-gating utility (Roadmap 2.2 — Freemium Gate).
 *
 * Every gated feature must go through [FeatureFlags] — never check
 * `isPremiumUser` directly in screen code.
 *
 * Free users can:
 *  - Run all calculations
 *  - View results, safety checks, and basic drawings
 *  - Use AI Review (first 3 uses per session, then gated)
 *
 * Premium-only features:
 *  - PDF report export
 *  - DXF/CAD export
 *  - CSV/Excel export
 *  - Complete Package (PDF+DXF+CSV)
 *  - BOQ detailed export
 *  - Unlimited AI Review
 */
@Singleton
class FeatureFlags @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    // ── Composable accessors ──

    @Composable
    fun isPremium(): Boolean {
        val isPremium by preferencesManager.isPremiumUser.collectAsState(initial = false)
        return isPremium
    }

    /** Check if a specific feature is available for the current user. */
    @Composable
    fun isAvailable(feature: PremiumFeature): Boolean {
        return when (feature) {
            PremiumFeature.AI_REVIEW -> true  // free: first 3 uses/session; premium: unlimited
            else -> isPremium()
        }
    }

    /**
     * Non-composable suspend version for use in ViewModels / exporters.
     */
    suspend fun isPremiumAsync(): Boolean {
        var premium = false
        preferencesManager.isPremiumUser.collect { premium = it }
        return premium
    }
}

/**
 * All features that can be gated behind the paywall.
 */
enum class PremiumFeature(val titleEn: String, val titleAr: String, val descriptionEn: String) {
    PDF_EXPORT("PDF Export", "تصدير PDF", "Export professional structural calculation reports"),
    DXF_EXPORT("CAD Export", "تصدير DWG/DXF", "Export drawings to AutoCAD-compatible DXF format"),
    CSV_EXPORT("CSV/Excel Export", "تصدير Excel", "Export data to CSV for spreadsheet analysis"),
    COMPLETE_PACKAGE("Complete Package", "الحزمة الكاملة", "Export PDF + DXF + CSV in one action"),
    BOQ_EXPORT("BOQ Export", "تصدير جدول الكميات", "Export bill of quantities with cost breakdown"),
    AI_REVIEW("AI Review (Unlimited)", "مراجعة AI (غير محدودة)", "Unlimited AI-powered design review checks")
}
