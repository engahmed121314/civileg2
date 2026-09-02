package com.civileg.app.billing

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Premium paywall bottom sheet.
 * Shows when a free user tries to access a gated feature.
 *
 * Usage:
 * ```kotlin
 * var showPaywall by remember { mutableStateOf(false) }
 * if (showPaywall) {
 *     PremiumPaywallSheet(
 *         feature = PremiumFeature.PDF_EXPORT,
 *         billingManager = billingManager,
 *         onDismiss = { showPaywall = false }
 *     )
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumPaywallSheet(
    feature: PremiumFeature,
    billingManager: BillingManager,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val subscriptionDetails by billingManager.subscriptionDetails.observeAsState(emptyList())
    val billingEvent by billingManager.billingEvent.observeAsState()

    // Handle billing events
    LaunchedEffect(billingEvent) {
        when (billingEvent) {
            is BillingManager.BillingEvent.PurchaseSuccess -> {
                onDismiss()
            }
            is BillingManager.BillingEvent.AlreadySubscribed -> {
                onDismiss()
            }
            else -> {}
        }
    }

    var selectedPlan by remember { mutableStateOf(BillingManager.SUBSCRIPTION_MONTHLY) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lock icon
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Unlock ${feature.titleEn}",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )

            Text(
                feature.descriptionEn,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── Benefits ──
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PremiumBenefit("All calculation engines — 3 codes")
                    PremiumBenefit("Professional PDF reports with drawings")
                    PremiumBenefit("AutoCAD DXF export")
                    PremiumBenefit("BOQ with cost breakdown")
                    PremiumBenefit("Unlimited AI design review")
                    PremiumBenefit("No advertisements")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Plan selector ──
            val monthly = subscriptionDetails.find { it.productId == BillingManager.SUBSCRIPTION_MONTHLY }
            val yearly = subscriptionDetails.find { it.productId == BillingManager.SUBSCRIPTION_YEARLY }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Monthly plan
                PlanCard(
                    title = "Monthly",
                    price = monthly?.price ?: "...",
                    subtitle = "/month",
                    isSelected = selectedPlan == BillingManager.SUBSCRIPTION_MONTHLY,
                    onClick = { selectedPlan = BillingManager.SUBSCRIPTION_MONTHLY },
                    modifier = Modifier.weight(1f)
                )

                // Yearly plan
                PlanCard(
                    title = "Yearly",
                    price = yearly?.price ?: "...",
                    subtitle = "/year",
                    badge = "Save 40%",
                    isSelected = selectedPlan == BillingManager.SUBSCRIPTION_YEARLY,
                    onClick = { selectedPlan = BillingManager.SUBSCRIPTION_YEARLY },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Subscribe button ──
            Button(
                onClick = {
                    val activity = context as? Activity ?: return@Button
                    billingManager.launchSubscription(activity, selectedPlan)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Subscribe Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Cancel anytime. 7-day free trial on yearly plan.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    subtitle: String,
    badge: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder() else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (badge != null) {
                Text(
                    badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier
                        .background(Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                price,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PremiumBenefit(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp)
    }
}
