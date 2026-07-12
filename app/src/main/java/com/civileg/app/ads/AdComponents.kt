package com.civileg.app.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.civileg.app.ads.AdManager
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

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
        AndroidView(
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.ADAPTIVE_BANNER)
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
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF1565C0),
                        androidx.compose.ui.graphics.Color(0xFF0D47A1)
                    )
                )
            )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = "Civil Engineer Pro",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = androidx.compose.ui.unit.sp(14)
                )
                Text(
                    text = "إزالة الإعلانات — استمتع بتجربة نظيفة",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                    fontSize = androidx.compose.ui.unit.sp(11)
                )
            }

            androidx.compose.material3.OutlinedButton(
                onClick = onUpgradeClick,
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    borderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 4.dp
                )
            ) {
                Text(
                    text = "ترقية Pro",
                    fontSize = androidx.compose.ui.unit.sp(12),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            }
        }
    }
}