package com.civileg.app.ui.compose.components

import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentNavigationDrawer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════════
// Color Palette
// ══════════════════════════════════════════════════════════════════
private val NavBarBackground   = Color(0xFF1A1A2E)
private val NavBarAccent       = Color(0xFF0F9DFF)
private val NavBarAccentGlow   = Color(0xFF0F9DFF).copy(alpha = 0.12f)
private val TabSelectedColor   = Color(0xFF0F9DFF)
private val TabUnselectedColor = Color(0xFF6B7280)
private val BadgeRed           = Color(0xFFFF3D00)
private val TopBorderGradient  = Color(0xFF0F9DFF).copy(alpha = 0.35f)
private val IndicatorColor     = Color(0xFF0F9DFF)
private val RailBackgroundColor = Color(0xFF1A1A2E)

// ══════════════════════════════════════════════════════════════════
// Navigation Item Model
// ══════════════════════════════════════════════════════════════════
@Immutable
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val adaptiveNavItems = listOf(
    BottomNavItem("الرئيسية",        Icons.Default.Home,           "home"),
    BottomNavItem("التصميم",         Icons.Default.AccountBalance,  "design"),
    BottomNavItem("المنشآت المعدنية", Icons.Default.Build,          "steel"),
    BottomNavItem("الأدوات",          Icons.Default.Calculate,       "tools"),
    BottomNavItem("المزيد",          Icons.Default.MoreHoriz,       "more")
)

// ══════════════════════════════════════════════════════════════════
// Window Size Classification
// ══════════════════════════════════════════════════════════════════
enum class WindowSizeClass {
    COMPACT,    // Phone portrait (< 600dp width)
    MEDIUM,    // Phone landscape / small tablet (600-840dp)
    EXPANDED   // Tablet / large screen (> 840dp width)
}

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    return when {
        screenWidthDp >= 840 -> WindowSizeClass.EXPANDED
        screenWidthDp >= 600 -> WindowSizeClass.MEDIUM
        else -> WindowSizeClass.COMPACT
    }
}

@Composable
fun isTablet(): Boolean {
    val sizeClass = rememberWindowSizeClass()
    return sizeClass == WindowSizeClass.EXPANDED || sizeClass == WindowSizeClass.MEDIUM
}

// ══════════════════════════════════════════════════════════════════
// Adaptive Navigation Scaffold
// Automatically switches between:
//   COMPACT  → Bottom Navigation Bar
//   MEDIUM   → Navigation Rail
//   EXPANDED → Permanent Navigation Drawer
// ══════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveNavigationScaffold(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    designCount: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val windowSize = rememberWindowSizeClass()

    when (windowSize) {
        WindowSizeClass.COMPACT -> {
            // Phone: Bottom Navigation Bar
            Column(modifier = modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
                ProfessionalBottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    designCount = designCount
                )
            }
        }

        WindowSizeClass.MEDIUM -> {
            // Small tablet / Phone landscape: Navigation Rail
            Row(modifier = modifier.fillMaxSize()) {
                AdaptiveNavigationRail(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    designCount = designCount
                )
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }

        WindowSizeClass.EXPANDED -> {
            // Large tablet: Permanent Navigation Drawer
            PermanentNavigationDrawer(
                drawerContent = {
                    AdaptiveNavigationDrawerContent(
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        designCount = designCount
                    )
                },
                modifier = modifier
            ) {
                content()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Navigation Rail (Medium screens)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun AdaptiveNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    designCount: Int = 0
) {
    Surface(
        color = RailBackgroundColor,
        shadowElevation = 8.dp
    ) {
        NavigationRail(
            containerColor = Color.Transparent,
            header = {
                // App logo area at top
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 12.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NavBarAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CE",
                        color = NavBarAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        ) {
            adaptiveNavItems.forEachIndexed { index, item ->
                NavigationRailItem(
                    icon = {
                        Box {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selectedTab == index) TabSelectedColor else TabUnselectedColor,
                                modifier = Modifier.size(24.dp)
                            )
                            // Badge for design count
                            if (index == 1 && designCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(16.dp)
                                        .background(BadgeRed, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (designCount > 9) "9+" else designCount.toString(),
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            color = if (selectedTab == index) TabSelectedColor else TabUnselectedColor,
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    indicator = {
                        if (selectedTab == index) {
                            NavigationDrawerItemDefaults.Indicator(
                                shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50),
                                color = TabSelectedColor.copy(alpha = 0.2f),
                                modifier = Modifier.width(4.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Navigation Drawer Content (Expanded screens)
// ══════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptiveNavigationDrawerContent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    designCount: Int = 0
) {
    Surface(
        color = RailBackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // App header in drawer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Column {
                    Text(
                        text = "Civil Engineer Pro",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Professional Suite",
                        color = TabUnselectedColor,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )

            Spacer(Modifier.height(8.dp))

            // Navigation items
            adaptiveNavItems.forEachIndexed { index, item ->
                NavigationDrawerItem(
                    icon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selectedTab == index) TabSelectedColor else TabUnselectedColor,
                                modifier = Modifier.size(22.dp)
                            )
                            // Badge
                            if (index == 1 && designCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(BadgeRed, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (designCount > 9) "9+" else designCount.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            color = if (selectedTab == index) Color.White else TabUnselectedColor,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = TabSelectedColor.copy(alpha = 0.12f),
                        unselectedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Developer info at bottom
            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Eng. Ahmed Magdy",
                        color = TabUnselectedColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "eng.ahmedmagdy121314@gmail.com",
                        color = TabUnselectedColor.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Original Bottom Navigation Bar (kept for COMPACT screens)
// ══════════════════════════════════════════════════════════════════
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
                spotColor = Color.Black.copy(alpha = 0.3f)
            ),
        color = NavBarBackground
    ) {
        Column {
            // Top accent gradient line
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

            // Tab row with indicator
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
                    adaptiveNavItems.forEachIndexed { index, item ->
                        TabItem(
                            item = item,
                            isSelected = selectedTab == index,
                            showBadge = (index == 1 && designCount > 0),
                            badgeCount = if (index == 1) designCount else 0,
                            onClick = { onTabSelected(index) }
                        )
                    }
                }

                // Animated sliding indicator bar
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val tabCenterFraction = (selectedTab.toFloat() + 0.5f) / adaptiveNavItems.size
                val targetOffset = tabCenterFraction * screenWidthDp - 16f

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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(42.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NavBarAccentGlow.copy(alpha = glowAlpha * 0.9f))
            )

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

        Text(
            text = item.label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

// Utility
fun getBottomNavItems(): List<BottomNavItem> = adaptiveNavItems