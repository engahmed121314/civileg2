package com.civileg.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.civileg.app.ui.compose.screens.*
import com.civileg.app.ui.theme.CivilEngineerTheme
import com.civileg.app.ui.theme.ThemeMode
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    // Global state for drawer to support legacy fragments if any still call it
    private var openDrawerAction: (() -> Unit)? = null

    fun openDrawer() {
        openDrawerAction?.invoke()
    }

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
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    
                    // Link the activity method to the Compose state
                    LaunchedEffect(Unit) {
                        openDrawerAction = {
                            scope.launch { drawerState.open() }
                        }
                    }

                    AppNavigation(drawerState)
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
fun AppNavigation(drawerState: DrawerState) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    
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
                    onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreen.Home.route) }
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
            }
        }
    ) {
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
                    onProjectClick = { project ->
                        // Handle project click if needed
                    },
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("شاشة $title قيد التطوير الاحترافي.\nجاهزة قريباً في التحديث القادم.")
        }
    }
}
