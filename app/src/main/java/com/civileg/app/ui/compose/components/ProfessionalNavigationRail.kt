package com.civileg.app.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.ui.theme.PremiumColorsLight
import com.civileg.app.ui.theme.PremiumColorsDark

private val RailBackground = PremiumColorsLight.primary
private val RailAccent = PremiumColorsLight.accent
private val RailTextMuted = PremiumColorsLight.textMuted

/**
 * Tablet / landscape navigation rail with Premium Geometric styling.
 *
 * Mirrors the visual language of [ProfessionalBottomNavBar] while using the
 * available horizontal space of larger windows instead of stealing vertical
 * space from the content.
 */
@Composable
fun ProfessionalNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    designCount: Int = 0,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dimensions = MaterialTheme.dimens
    val items = getBottomNavItems()

    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .width(dimensions.navigationRailWidth),
        containerColor = RailBackground,
        contentColor = PremiumColorsLight.onSurface,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(RailAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = null,
                        tint = PremiumColorsLight.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "CivilEG",
                    color = PremiumColorsLight.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    ) {
        Spacer(Modifier.height(6.dp))

        items.forEachIndexed { index, item ->
            val selected = selectedTab == index
            NavigationRailItem(
                selected = selected,
                onClick = { onTabSelected(index) },
                icon = {
                    if (index == 1 && designCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = PremiumColorsLight.unsafe,
                                    contentColor = PremiumColorsLight.onPrimary
                                ) {
                                    Text(
                                        text = if (designCount > 99) "99+" else designCount.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        ) {
                            Icon(item.icon, contentDescription = stringResource(item.labelRes))
                        }
                    } else {
                        Icon(item.icon, contentDescription = stringResource(item.labelRes))
                    }
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = RailAccent,
                    selectedTextColor = RailAccent,
                    indicatorColor = RailAccent.copy(alpha = 0.14f),
                    unselectedIconColor = RailTextMuted,
                    unselectedTextColor = RailTextMuted
                )
            )
        }

        Spacer(Modifier.weight(1f))

        if (onOpenSettings != null) {
            HorizontalDivider(color = PremiumColorsLight.onSurface.copy(alpha = 0.08f))
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                    tint = RailTextMuted
                )
            }
        }
    }
}