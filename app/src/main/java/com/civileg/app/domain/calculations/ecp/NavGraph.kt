package com.civileg.app.domain.calculations.ecp

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.civileg.app.ui.compose.screens.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.civileg.app.viewmodel.*

@Composable
fun NavGraph() {
    val navController = rememberNavController()

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
            ColumnScreen(
                viewModel = viewModel(),
                projectViewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.BeamDesign.route) {
            BeamScreen(
                viewModel = viewModel(),
                projectViewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.SlabDesign.route) {
            SlabScreen(
                viewModel = viewModel(),
                projectViewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.FootingDesign.route) {
            FootingScreen(
                viewModel = viewModel(),
                projectViewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.TankDesign.route) {
            TankScreen(
                viewModel = viewModel(),
                projectViewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.BOQ.route) {
            BOQScreen(
                projectViewModel = viewModel(),
                boqViewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.RetainingWall.route) {
            RetainingWallScreen(
                viewModel = viewModel(),
                projectViewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.StairDesign.route) {
            StairScreen(
                viewModel = viewModel(),
                projectViewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.SeismicAnalysis.route) {
            SeismicScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.SteelDesign.route) {
            SteelDesignScreen(
                viewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.UnitConverter.route) {
            UnitConverterScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.Projects.route) {
            ArchiveScreen(
                viewModel = viewModel(),
                onProjectClick = { project -> /* Handle project selection or design viewing */ },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppScreen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
