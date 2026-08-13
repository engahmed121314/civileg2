package com.civileg.app.ads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.civileg.app.ads.AdManager
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

/**
 * Native Compose Banner Ad — Professional placement between content sections.
 *
 * Philosophy: Blends naturally with app content, small and non-intrusive.
 * Only shown between major sections (never at top/bottom of screen).
 *
 * Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com
 */
@Composable
fun AdBannerSection(
    modifier: Modifier = Modifier
) {
    val isPremium = AdManager.isPremium()

    if (isPremium) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdManager.BANNER_AD_UNIT_ID
                    loadAd(AdManager.buildAdRequest())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )
    }
}

/**
 * Compact Banner — Smaller version for tight spaces.
 * Uses ADAPTIVE_BANNER for optimal width.
 */
@Composable
fun AdBannerCompact(
    modifier: Modifier = Modifier
) {
    val isPremium = AdManager.isPremium()

    if (isPremium) return

    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val adWidthPx = with(density) { (LocalConfiguration.current.screenWidthDp.dp).roundToPx() }

        AndroidView(
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adWidthPx))
                    adUnitId = AdManager.BANNER_AD_UNIT_ID
                    loadAd(AdManager.buildAdRequest())
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Native Ad Placeholder — Shows between list items.
 * In production, replace with Google Native Advanced Ad.
 *
 * For now, shows a subtle "sponsored" card that blends with the design modules.
 */
@Composable
fun NativeAdPlaceholder(
    modifier: Modifier = Modifier
) {
    val isPremium = AdManager.isPremium()

    if (isPremium) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Placeholder for Google Native Advanced Ad
        // When you set up native ads, replace this with:
        // AndroidView { context -> NativeAdView(context) ... }
        Text(
            text = "Sponsored",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

/**
 * "Remove Ads" Banner — Shown at the bottom of home screen.
 * Prompts users to upgrade to premium.
 */
@Composable
fun RemoveAdsBanner(
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPremium = AdManager.isPremium()

    if (isPremium) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1565C0),
                        Color(0xFF0D47A1)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Civil Engineer Pro",
                    style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
                Text(
                    text = "إزالة الإعلانات — استمتع بتجربة نظيفة",
                    style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                )
            }

            OutlinedButton(
                onClick = onUpgradeClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(
                    horizontal = 16.dp, vertical = 4.dp
                )
            ) {
                Text(
                    text = "ترقية Pro",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}