package com.civileg.app.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class EngineeringDimens(
    val screenPadding: Dp,
    val sectionSpacing: Dp,
    val itemSpacing: Dp,
    val cardInnerPadding: Dp,
    val touchTarget: Dp,
    val toolbarHeight: Dp,
    val contextBarHeight: Dp,
    val actionBarHeight: Dp,
    val cornerSmall: Shape,
    val cornerMedium: Shape,
    val cornerLarge: Shape,
    val elevationLevel0: Dp,
    val elevationLevel1: Dp,
    val elevationLevel2: Dp,
    val strokeWidthThin: Dp,
    val strokeWidthRegular: Dp
)

val LightEngineeringDimens = EngineeringDimens(
    screenPadding = 16.dp,
    sectionSpacing = 16.dp,
    itemSpacing = 8.dp,
    cardInnerPadding = 12.dp,
    touchTarget = 48.dp,
    toolbarHeight = 56.dp,
    contextBarHeight = 32.dp,
    actionBarHeight = 64.dp,
    cornerSmall = RoundedCornerShape(6.dp),
    cornerMedium = RoundedCornerShape(10.dp),
    cornerLarge = RoundedCornerShape(16.dp),
    elevationLevel0 = 0.dp,
    elevationLevel1 = 1.dp,
    elevationLevel2 = 3.dp,
    strokeWidthThin = 0.75.dp,
    strokeWidthRegular = 1.dp
)

val TabletEngineeringDimens = LightEngineeringDimens.copy(
    screenPadding = 24.dp,
    sectionSpacing = 24.dp
)

val LocalEngineeringDimens = staticCompositionLocalOf { LightEngineeringDimens }
