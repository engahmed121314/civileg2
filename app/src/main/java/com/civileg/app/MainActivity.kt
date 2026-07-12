package com.civileg.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.civileg.app.ui.compose.components.WindowSizeClass
import com.civileg.app.ui.compose.components.rememberWindowSizeClass
import com.civileg.app.ui.compose.screens.*
import com.civileg.app.ui.theme.CivilEngineerTheme
import com.civileg.app.ui.theme.ThemeMode
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState()

            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            CivilEngineerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    fun setLocale(lang: String) {
        LocaleHelper.setLocale(this, lang)
        recreate()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val windowSize = rememberWindowSizeClass()

    // For compact screens, use a modal drawer for additional navigation
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Determine if we need the modal drawer overlay (compact only, for "More" items)
    val useModalDrawer = windowSize == WindowSizeClass.COMPACT

    // Adaptive scaffold: on large screens the navigation is handled by the parent
    if (useModalDrawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    Text("القائمة الرئيسية", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("الرئيسية") },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreen.Home.route) { popUpTo(0) } }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Folder, null) },
                        label = { Text("المشاريع") },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreen.Projects.route) }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("الإعدادات") },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreen.Settings.route) }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(painterResource(id = R.drawable.ic_tools), null) },
                        label = { Text("مخزن الموقع") },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreen.Inventory.route) }
                    )
                }
            }
        ) {
            NavHostContent(navController = navController)
        }
    } else {
        NavHostContent(navController = navController)
    }
}

@Composable
private fun NavHostContent(navController: androidx.navigation.NavController) {
    NavHost(
        navController = navController,
        startDestination = AppScreen.Home.route
    ) {
        composable(AppScreen.Home.route) {
            HomeScreen(
                onNavigateTo = { route -> navController.navigate(route) },
                onShowSettings = { navController.navigate(AppScreen.Settings.route) }
            )
        }

        composable(AppScreen.ColumnDesign.route) {
            ColumnScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.BeamDesign.route) {
            BeamScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.SlabDesign.route) {
            SlabScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.FootingDesign.route) {
            FootingScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.StairDesign.route) {
            StairScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.RetainingWall.route) {
            RetainingWallScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.TankDesign.route) {
            TankScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.UnitConverter.route) {
            UnitConverterScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.Projects.route) {
            ArchiveScreen(
                viewModel = hiltViewModel(),
                onProjectClick = {},
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.BOQ.route) {
            BOQScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.SeismicAnalysis.route) {
            SeismicScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.SteelDesign.route) {
            SteelDesignScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.FrameAnalysis.route) {
            FrameAnalysisScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppScreen.Inventory.route) {
            InventoryScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}