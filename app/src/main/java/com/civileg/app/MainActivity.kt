package com.civileg.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.civileg.app.ui.compose.components.ProfessionalBottomNavBar
import com.civileg.app.ui.compose.screens.*
import com.civileg.app.ui.screens.*
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    fun openDrawer() {
        openDrawerAction?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved language before any UI is created
        LocaleHelper.applySavedLocale(this)

        setContent {
            val mainViewModel: com.civileg.app.viewmodel.MainViewModel = hiltViewModel()
            val boot by mainViewModel.bootstrap.collectAsStateWithLifecycle()
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

            CivilEngineerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // ── Two-phase navigation gate (navigation-architecture.md §1) ──
                    val bootState = boot
                    when {
                        bootState == null -> {
                            // DataStore still loading — minimal branded splash, no interaction
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        !bootState.onboardingComplete -> {
                            com.civileg.app.ui.compose.onboarding.OnboardingHost(
                                language = bootState.language,
                                onComplete = { lang, type ->
                                    LocaleHelper.setLocale(this, lang)
                                    mainViewModel.completeOnboarding(lang, type)
                                },
                                onLanguageSelected = { lang ->
                                    LocaleHelper.setLocale(this, lang)
                                }
                            )
                        }
                        else -> {
                            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                            val scope = rememberCoroutineScope()

                            LaunchedEffect(Unit) {
                                openDrawerAction = {
                                    scope.launch { drawerState.open() }
                                }
                            }

                            if (bootState.userType ==
                                com.civileg.app.data.local.UserType.ENGINEER
                            ) {
                                val allProjectsList by projectViewModel.allProjects.observeAsState(initial = emptyList())
                                AppNavigation(drawerState, designCount, projectViewModel, allProjectsList)
                            } else {
                                com.civileg.app.ui.compose.screens.normal.NormalUserAppHost()
                            }
                        }
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

@Composable
fun AppNavigation(
    drawerState: DrawerState,
    designCount: Int = 0,
    projectViewModel: ProjectViewModel,
    allProjects: List<com.civileg.app.db.Project>
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var selectedBottomTab by remember { mutableIntStateOf(0) }
    var showCommandPalette by remember { mutableStateOf(false) }

    val commandPaletteItems = remember {
        listOf(
            com.civileg.app.ui.designsystem.components.EngineeringCommand("تصميم كمرة", "Beam Design", AppScreen.BeamDesign.route, "beam كمرات"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("تصميم عمود", "Column Design", AppScreen.ColumnDesign.route, "column أعمدة"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("تصميم بلاطة", "Slab Design", AppScreen.SlabDesign.route, "slab بلاطات"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("تصميم قاعدة", "Footing Design", AppScreen.FootingDesign.route, "footing قواعد"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("حائط استنادي", "Retaining Wall", AppScreen.RetainingWall.route, "retaining حوائط"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("خزان مياه", "Water Tank", AppScreen.TankDesign.route, "tank خزانات"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("سلم خرساني", "Stair Design", AppScreen.StairDesign.route, "stair سلالم"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("تحليل زلازل", "Seismic Analysis", AppScreen.SeismicAnalysis.route, "seismic زلازل earthquake"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("تحليل إطارات", "Frame Analysis", AppScreen.FrameAnalysis.route, "frame إطارات"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("خوازيق", "Pile Foundation", AppScreen.PileFoundation.route, "pile خوازيق"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("بلاطة مسطحة", "Flat Slab", AppScreen.FlatSlab.route, "flat slab flatslab"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("حائط قص", "Shear Wall", AppScreen.ShearWall.route, "shear wall حوائط قص"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("تصميم معدني", "Steel Design", AppScreen.SteelDesign.route, "steel معدني حديد"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("جدول كميات", "BOQ", AppScreen.BOQ.route, "boq كميات"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("آلة حاسبة", "Calculator", AppScreen.Calculator.route, "calculator حاسبة"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("محول وحدات", "Unit Converter", AppScreen.UnitConverter.route, "units converter وحدات"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("جداول الحديد", "Steel Tables", AppScreen.SteelTables.route, "steel tables جداول"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("المشاريع", "Projects", AppScreen.Projects.route, "projects مشاريع"),
            com.civileg.app.ui.designsystem.components.EngineeringCommand("الإعدادات", "Settings", AppScreen.Settings.route, "settings إعدادات")
        )
    }

    if (showCommandPalette) {
        com.civileg.app.ui.designsystem.components.EngineeringCommandPalette(
            commands = commandPaletteItems,
            onDismiss = { showCommandPalette = false },
            onCommandSelected = { cmd ->
                if (cmd.route == AppScreen.Home.route) {
                    navController.navigate(AppScreen.Home.route) { popUpTo(0) { inclusive = true } }
                } else {
                    navController.navigate(cmd.route)
                }
            }
        )
    }

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
                Text(stringResource(R.string.nav_drawer_title), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Search, null) },
                    label = { Text(stringResource(R.string.eg_quick_search)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; showCommandPalette = true }
                )
                HorizontalDivider()
                HorizontalDivider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text(stringResource(R.string.nav_home)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreen.Home.route) { popUpTo(0) { inclusive = true } } }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Folder, null) },
                    label = { Text(stringResource(R.string.nav_projects)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreen.Projects.route) }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreen.Settings.route) }
                )
                NavigationDrawerItem(
                    icon = { Icon(painterResource(id = R.drawable.ic_costing), null) },
                    label = { Text(stringResource(R.string.nav_inventory)) },
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
                composable(AppScreen.PileFoundation.route) {
                    PileFoundationScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.FlatSlab.route) {
                    FlatSlabScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.ShearWall.route) {
                    ShearWallScreen(onNavigateBack = { navController.popBackStack() })
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
                    BOQScreen(
                        onNavigateToSummary = { id -> navController.navigate("project_summary/$id") },
                        onNavigateToExecution = { id -> navController.navigate("execution_log/$id") },
                        onNavigateToMasterBbs = { id -> navController.navigate("master_bbs/$id") },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(AppScreen.WaterLevel.route) {
                    WaterLevelScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.RebarTool.route) {
                    RebarToolScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.ConcreteMix.route) {
                    ConcreteMixScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.SoilBearing.route) {
                    SoilBearingScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.ExportCenter.route) {
                    ExportCenterScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.WindLoad.route) {
                    WindLoadScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.GeneralEstimation.route) {
                    GeneralEstimationScreen(onBack = { navController.popBackStack() })
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
                
                composable(AppScreen.SiteLayout.route) {
                    SiteLayoutScreen()
                }
                
                composable(AppScreen.ProjectSummary.route) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
                    val project = allProjects.find { it.id == projectId }
                    val summaryFlow = projectViewModel.getProjectSummary(projectId).collectAsState(initial = com.civileg.core.calculations.entities.ProjectSummary())
                    ProjectSummaryScreen(
                        summary = summaryFlow.value,
                        projectName = project?.name ?: "Summary",
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(AppScreen.ExecutionLog.route) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
                    ExecutionLogScreen(projectId = projectId, onNavigateBack = { navController.popBackStack() })
                }

                composable(AppScreen.MasterBbs.route) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
                    val project = allProjects.find { it.id == projectId }
                    
                    // W12-FIX: Master BBS feed from ProjectViewModel (correctly wired)
                    val bbsEntries by projectViewModel.getProjectBbs(projectId).observeAsState(initial = emptyList())
                    
                    MasterBbsScreen(
                        projectName = project?.name ?: "Master BBS",
                        allEntries = bbsEntries,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}