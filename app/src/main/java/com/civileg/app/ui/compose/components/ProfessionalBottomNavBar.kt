package com.civileg.app.ui.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.AccountBalance
import com.civileg.app.ui.theme.PremiumColorsLight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R

// ============================================================================
// PREMIUM GEOMETRIC COLOR PALETTE – Deep Navy Dark Theme for Bottom Nav
// ============================================================================

private val NavBarBackground  = PremiumColorsLight.primary
private val NavBarAccent      = PremiumColorsLight.accent
private val NavBarAccentGlow  = PremiumColorsLight.accent.copy(alpha = 0.12f)
private val TabSelectedColor  = PremiumColorsLight.accent
private val TabUnselectedColor = PremiumColorsLight.textSecondary
private val BadgeRed          = PremiumColorsLight.unsafe
private val TopBorderGradient = PremiumColorsLight.primary.copy(alpha = 0.35f)
private val IndicatorColor    = PremiumColorsLight.primary

// ============================================================================
// Tab Model
// ============================================================================

@Immutable
data class BottomNavItem(
    val labelRes: Int,
    val icon: ImageVector,
    val route: String
)

// Premium bottom nav items with geometric proportions
private val bottomNavItems = listOf(
    BottomNavItem(R.string.nav_home,      Icons.Default.Home,      "home"),
    BottomNavItem(R.string.nav_design_hub, Icons.Default.AccountBalance, "design"),
    BottomNavItem(R.string.home_section_tools_title, Icons.Default.Build, "building_designer"),
    BottomNavItem(R.string.nav_tools,    Icons.Default.Calculate, "tools"),
    BottomNavItem(R.string.nav_more,     Icons.Default.MoreHoriz,  "more")
)

// ============================================================================
// Professional Bottom Navigation Bar - PREMIUM GEOMETRIC VERSION
// ============================================================================

/**
 * Premium Professional Bottom Navigation Bar.
 * Deep navy background (#0F172A), animated sliding indicator,
 * scale + color transitions on icons, and a red notification badge
 * on the Design tab. Geometric pill-shaped tab items with premium gradients.
 *
 * @param selectedTab  Currently selected tab index (0‑4).
 * @param onTabSelected Called when a tab is tapped.
 * @param designCount  Saved‑designs count shown as a badge on the Design tab.
 * @param modifier     Optional Modifier.
 */
@Composable
fun ProfessionalBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    designCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor  = Color.Black.copy(alpha = 0.1f)
            ),
        color = NavBarBackground
    ) {
        Column {
            // ── Top accent geometric gradient line ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, TopBorderGradient, Color.Transparent)
                        )
                    )
            )

            // ── Tab row with indicator ──
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                val barWidth = maxWidth
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomNavItems.forEachIndexed { index, item ->
                        TabItem(
                            item = item,
                            isSelected = selectedTab == index,
                            showBadge = (index == 1 && designCount > 0),
                            badgeCount = if (index == 1) designCount else 0,
                            onClick = { onTabSelected(index) }
                        )
                    }
                }

                // ── Animated sliding geometric indicator bar ──
                val tabCenterFraction = (selectedTab.toFloat() + 0.5f) / bottomNavItems.size
                val targetOffset = barWidth * tabCenterFraction - 16.dp

                val animatedOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = 350f
                    ),
                    label = "indicator_offset"
                )

                Box(
                    modifier = Modifier
                        .offset(x = animatedOffset)
                        .width(32.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(topStartPercent = 50, topEndPercent = 50))
                        .background(IndicatorColor)
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

// ============================================================================
// Individual Tab Item - PREMIUM GEOMETRIC PILL STYLE
// ============================================================================

@Composable
private fun TabItem(
    item: BottomNavItem,
    isSelected: Boolean,
    showBadge: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    // Animated properties with geometric easing
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) TabSelectedColor else TabUnselectedColor,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "icon_tint"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) TabSelectedColor else TabUnselectedColor,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "label_color"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "scale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(300),
        label = "glow_alpha"
    )

    // Geometric pill container with gradient background on select
    val backgroundByState = if (isSelected) {
        PremiumColorsLight.primaryGradient
    } else {
        PremiumColorsLight.surfaceVariant.copy(alpha = 0.3f)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))  // Premium geometric pill radius
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon container with optional glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp)
        ) {
            // Glow circle behind icon (enhanced for selected state)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NavBarAccentGlow.copy(alpha = glowAlpha * 0.9f))
            )

            // The actual icon with geometric scaling
            Icon(
                imageVector = item.icon,
                contentDescription = stringResource(item.labelRes),
                tint = iconTint,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            )

            // Notification badge with geometric pill shape
            if (showBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .size(if (badgeCount > 9) 28.dp else 22.dp)
                        .clip(RoundedCornerShape(50.dp))  // Geometric pill badge
                        .background(BadgeRed, RoundedCornerShape(50.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Premium geometric label
        Text(
            text = stringResource(item.labelRes),
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

// ============================================================================
// Utility – expose items for external use
// ============================================================================

fun getBottomNavItems(): List<BottomNavItem> = bottomNavItems