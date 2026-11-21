package com.civileg.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.civileg.app.features.navigation.AppNavigation
import com.civileg.app.ui.theme.CivilEngineerTheme
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.ui.theme.ThemeMode
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.features.project.ProjectViewModel
import com.civileg.app.features.project.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.civileg.app.security.AppIntegrityChecker
import com.civileg.app.support.DonationDialog
import com.civileg.app.support.DonationPromptState
import com.civileg.app.support.openSponsorsPage
import android.util.Log

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var openDrawerAction: (() -> Unit)? = null

    /**
     * Evaluated once per process, on first creation only, so rotating the device
     * does not count as an extra launch.
     */
    private var shouldPromptDonation = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    fun openDrawer() {
        openDrawerAction?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!AppIntegrityChecker.isDeviceSecure(applicationContext)) {
                    Log.w("Security", "Device security compromised - Root, Emulator or Hooking detected")
                }
            } catch (error: Exception) {
                Log.w("Security", "Device integrity check failed", error)
            }
        }

        // Apply saved language before any UI is created
        LocaleHelper.applySavedLocale(this)

        if (savedInstanceState == null) {
            shouldPromptDonation = try {
                DonationPromptState(this).registerLaunchAndCheck()
            } catch (error: Exception) {
                Log.w("Donation", "Donation prompt state unavailable", error)
                false
            }
        }

        setContent {
            val donationState = remember { DonationPromptState(this@MainActivity) }
            var showDonationPrompt by rememberSaveable { mutableStateOf(shouldPromptDonation) }

            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val projectViewModel: ProjectViewModel = hiltViewModel()
            val designs by projectViewModel.allDesigns.observeAsState(initial = emptyList())
            val designCount = designs.size

            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            CivilEngineerPremiumTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(Unit) {
                        openDrawerAction = {
                            scope.launch { drawerState.open() }
                        }
                    }

                    val allProjectsList by projectViewModel.allProjects.observeAsState(initial = emptyList())
                    AppNavigation(drawerState, designCount, projectViewModel, allProjectsList)

                    if (showDonationPrompt) {
                        DonationDialog(
                            onDonate = {
                                showDonationPrompt = false
                                donationState.markAnswered()
                                val opened = openSponsorsPage(this@MainActivity)
                                if (!opened) {
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.donation_unavailable),
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onDecline = {
                                showDonationPrompt = false
                                donationState.markAnswered()
                            }
                        )
                    }
                }
            }
        }
    }

    fun setLocale(lang: String) {
        LocaleHelper.setLocale(this, lang)
        recreate()
    }
}

