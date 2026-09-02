package com.civileg.app.ui.compose.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.civileg.app.R
import com.civileg.app.data.local.UserType

/**
 * Onboarding flow (research-and-ux-protocol.md §2 + navigation-architecture.md §2):
 * Splash (1.6s, auto-advance) → Language selection → User type selection.
 * Runs ONCE per user; afterwards MainActivity routes straight to the main graphs.
 */

private object OnboardingRoutes {
    const val SPLASH = "onboarding/splash"
    const val LANGUAGE = "onboarding/language"
    const val USER_TYPE = "onboarding/user_type"
}

@Composable
fun OnboardingHost(
    language: String,
    onComplete: (language: String, userType: UserType) -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val navController = rememberNavController()
    var selectedLanguage by remember { mutableStateOf(language) }

    NavHost(
        navController = navController,
        startDestination = OnboardingRoutes.SPLASH,
        enterTransition = { fadeIn(tween(280)) + slideInHorizontally(tween(280)) { it / 8 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(220)) { it / 8 } }
    ) {
        composable(OnboardingRoutes.SPLASH) {
            OnboardingSplashScreen(onTimeout = {
                navController.navigate(OnboardingRoutes.LANGUAGE) { launchSingleTop = true }
            })
        }
        composable(OnboardingRoutes.LANGUAGE) {
            LanguageSelectionScreen(
                selectedLanguage = selectedLanguage,
                onLanguagePicked = { lang ->
                    selectedLanguage = lang
                    onLanguageSelected(lang)
                    navController.navigate(OnboardingRoutes.USER_TYPE) { launchSingleTop = true }
                }
            )
        }
        composable(OnboardingRoutes.USER_TYPE) {
            UserTypeSelectionScreen(
                onUserTypePicked = { type ->
                    onComplete(selectedLanguage, type)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1) SPLASH — logo fade-in/scale, zero interaction, 1.6s then advance
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OnboardingSplashScreen(onTimeout: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(700), label = "splashAlpha")
    val scale by animateFloatAsState(if (visible) 1f else 0.85f, tween(900), label = "splashScale")

    LaunchedEffect(Unit) {
        visible = true
        kotlinx.coroutines.delay(1600)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha).scale(scale)
        ) {
            Icon(
                imageVector = Icons.Default.Engineering,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "CivilEG",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(36.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(140.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2) LANGUAGE SELECTION — two large tappable cards only (no dropdown)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LanguageSelectionScreen(
    selectedLanguage: String,
    onLanguagePicked: (String) -> Unit
) {
    OnboardingScaffold(titleRes = R.string.onboarding_choose_language) {
        SelectionCard(
            title = "العربية",
            subtitle = stringResource(R.string.onboarding_language_arabic_desc),
            icon = null,
            emojiFallback = "🇪🇬",
            selected = selectedLanguage == "ar",
            onClick = { onLanguagePicked("ar") }
        )
        Spacer(Modifier.height(16.dp))
        SelectionCard(
            title = "English",
            subtitle = stringResource(R.string.onboarding_language_english_desc),
            icon = null,
            emojiFallback = "🌍",
            selected = selectedLanguage == "en",
            onClick = { onLanguagePicked("en") }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3) USER TYPE SELECTION — two big cards with icon + short description
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UserTypeSelectionScreen(onUserTypePicked: (UserType) -> Unit) {
    OnboardingScaffold(titleRes = R.string.onboarding_choose_user_type) {
        SelectionCard(
            title = stringResource(R.string.onboarding_user_normal),
            subtitle = stringResource(R.string.onboarding_user_normal_desc),
            icon = Icons.Default.HomeWork,
            emojiFallback = "🏠",
            selected = false,
            onClick = { onUserTypePicked(UserType.NORMAL) }
        )
        Spacer(Modifier.height(16.dp))
        SelectionCard(
            title = stringResource(R.string.onboarding_user_engineer),
            subtitle = stringResource(R.string.onboarding_user_engineer_desc),
            icon = Icons.Default.Engineering,
            emojiFallback = "📐",
            selected = false,
            onClick = { onUserTypePicked(UserType.ENGINEER) }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_user_type_changeable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared building blocks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingScaffold(
    titleRes: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(32.dp))
            content()
        }
    }
}

@Composable
private fun SelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector?,
    emojiFallback: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 110.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            } else {
                Text(emojiFallback, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
