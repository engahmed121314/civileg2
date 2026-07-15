package com.civileg.app

import android.os.Bundle
import java.util.Locale
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.civileg.app.ui.compose.components.ProfessionalBottomNavBar
import com.civileg.app.ui.compose.screens.*
import com.civileg.app.ui.theme.CivilEngineerTheme
import com.civileg.app.ui.theme.ThemeMode
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var openDrawerAction: (() -> Unit)? = null

    fun openDrawer() {
        openDrawerAction?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedLang = LocaleHelper.getLocale(this)
        val config = resources.configuration
        val locale = Locale(savedLang)
        Locale.setDefault(locale)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val projectViewModel: ProjectViewModel = hiltViewModel()
            val designCount by projectViewModel.allDesigns
                .collectAsStateWithLifecycle(initialValue = emptyList())

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
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(Unit) {
                        openDrawerAction = {
                            scope.launch { drawerState.open() }
                        }
                    }

                    AppNavigation(drawerState, designCount.size)
                }
            }
        }
    }

    fun setLocale(lang: String) {
        LocaleHelper.setLocale(this, lang)
        recreate()
    }
}

@Composable
fun AppNavigation(drawerState: DrawerState, designCount: Int = 0) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var selectedBottomTab by remember { mutableIntStateOf(0) }

    // Map bottom nav tabs to routes
    val bottomTabRoutes = listOf("home", "design_hub", "steel", "tools_hub", "more_hub")

    fun navigateToTab(index: Int) {
        selectedBottomTab = index
        val route = bottomTabRoutes[index]
        if (index == 0) {
            navController.navigate(AppScreen.Home.route) {
                popUpTo(AppScreen.Home.route) { inclusive = true }
            }
        } else {
            navController.navigate(route) {
                popUpTo(AppScreen.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
                    onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreen.Home.route) { popUpTo(0) { inclusive = true } } }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Folder, null) },
                    label = { Text("مشاريعي") },
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
                    icon = { Icon(painterResource(id = R.drawable.ic_costing), null) },
                    label = { Text("مخزن الموقع") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreen.Inventory.route) }
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                ProfessionalBottomNavBar(
                    selectedTab = selectedBottomTab,
                    onTabSelected = { navigateToTab(it) },
                    designCount = designCount
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppScreen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                // ═══ MAIN TABS ═══
                composable(AppScreen.Home.route) {
                    selectedBottomTab = 0
                    HomeScreen(
                        onNavigateTo = { route -> navController.navigate(route) },
                        onShowSettings = { navController.navigate(AppScreen.Settings.route) }
                    )
                }

                // ═══ DESIGN HUB (Tab 1) ═══
                composable("design_hub") {
                    DesignHubScreen(
                        onNavigateTo = { route -> navController.navigate(route) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // ═══ STEEL (Tab 2) ═══
                composable(AppScreen.SteelDesign.route) {
                    selectedBottomTab = 2
                    SteelDesignScreen(onNavigateBack = { navController.popBackStack() })
                }

                // ═══ TOOLS HUB (Tab 3) ═══
                composable("tools_hub") {
                    ToolsHubScreen(
                        onNavigateTo = { route -> navController.navigate(route) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // ═══ MORE HUB (Tab 4) ═══
                composable("more_hub") {
                    MoreHubScreen(
                        onNavigateTo = { route -> navController.navigate(route) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // ═══ DESIGN MODULES ═══
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
                composable(AppScreen.SeismicAnalysis.route) {
                    SeismicScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.FrameAnalysis.route) {
                    FrameAnalysisScreen(onNavigateBack = { navController.popBackStack() })
                }

                // ═══ QUICK TOOLS ═══
                composable(AppScreen.Calculator.route) {
                    CalculatorScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.SteelTables.route) {
                    SteelTablesScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.UnitConverter.route) {
                    UnitConverterScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.BOQ.route) {
                    BOQScreen(onNavigateBack = { navController.popBackStack() })
                }

                // ═══ PROJECT & SETTINGS ═══
                composable(AppScreen.Projects.route) {
                    ArchiveScreen(
                        viewModel = hiltViewModel(),
                        onProjectClick = { },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(AppScreen.Settings.route) {
                    SettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.Inventory.route) {
                    InventoryScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}