package com.civileg.app.ui.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Structure
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════════
// Color Palette – Deep Navy Dark Theme for Bottom Nav
// ══════════════════════════════════════════════════════════════════
private val NavBarBackground  = Color(0xFF1A1A2E)
private val NavBarAccent      = Color(0xFF0F9DFF)
private val NavBarAccentGlow  = Color(0xFF0F9DFF).copy(alpha = 0.12f)
private val TabSelectedColor  = Color(0xFF0F9DFF)
private val TabUnselectedColor = Color(0xFF6B7280)
private val BadgeRed          = Color(0xFFFF3D00)
private val TopBorderGradient = Color(0xFF0F9DFF).copy(alpha = 0.35f)
private val IndicatorColor    = Color(0xFF0F9DFF)

// ══════════════════════════════════════════════════════════════════
// Tab Model
// ══════════════════════════════════════════════════════════════════
@Immutable
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private val bottomNavItems = listOf(
    BottomNavItem("الرئيسية",          Icons.Default.Home,      "home"),
    BottomNavItem("التصميم",           Icons.Default.Structure, "design"),
    BottomNavItem("المنشآت المعدنية",   Icons.Default.Build,    "steel"),
    BottomNavItem("الأدوات",            Icons.Default.Calculate, "tools"),
    BottomNavItem("المزيد",            Icons.Default.MoreHoriz,  "more")
)

// ══════════════════════════════════════════════════════════════════
// Public API
// ══════════════════════════════════════════════════════════════════
/**
 * Professional Bottom Navigation Bar.
 *
 * Deep navy background (#1A1A2E), animated sliding indicator, scale + color
 * transitions on icons, and a red notification badge on the Design tab.
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
                elevation = 20.dp,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor  = Color.Black.copy(alpha = 0.3f)
            ),
        color = NavBarBackground
    ) {
        Column {
            // ── Top accent gradient line ──
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
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

                // ── Animated sliding indicator bar ──
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val tabCenterFraction = (selectedTab.toFloat() + 0.5f) / bottomNavItems.size
                val targetOffset = tabCenterFraction * screenWidthDp - 16f // 16 dp = half of 32 dp indicator

                val animatedOffset by animateDpAsState(
                    targetValue = targetOffset.dp,
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

// ══════════════════════════════════════════════════════════════════
// Individual Tab Item
// ══════════════════════════════════════════════════════════════════
@Composable
private fun TabItem(
    item: BottomNavItem,
    isSelected: Boolean,
    showBadge: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    // Animated properties
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

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
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
            modifier = Modifier.size(42.dp)
        ) {
            // Glow circle behind icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NavBarAccentGlow.copy(alpha = glowAlpha * 0.9f))
            )

            // The actual icon
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            )

            // Notification badge
            if (showBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(if (badgeCount > 9) 22.dp else 18.dp)
                        .background(BadgeRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Label
        Text(
            text = item.label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

// ══════════════════════════════════════════════════════════════════
// Utility – expose items for external use
// ══════════════════════════════════════════════════════════════════
fun getBottomNavItems(): List<BottomNavItem> = bottomNavItems