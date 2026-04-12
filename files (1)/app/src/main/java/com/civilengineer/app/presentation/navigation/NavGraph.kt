package com.civilengineer.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.civilengineer.app.presentation.screens.*

/**
 * نسق التنقل الرئيسي
 */
@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(navController)
        }

        composable("column_design") {
            ColumnDesignScreen(navController)
        }

        composable("column_details/{columnId}") { backStackEntry ->
            val columnId = backStackEntry.arguments?.getString("columnId")?.toIntOrNull() ?: 0
            ColumnDetailsScreen(columnId, navController)
        }

        composable("slab_design") {
            SlabDesignScreen(navController)
        }

        composable("slab_details/{slabId}") { backStackEntry ->
            val slabId = backStackEntry.arguments?.getString("slabId")?.toIntOrNull() ?: 0
            SlabDetailsScreen(slabId, navController)
        }

        composable("footing_design") {
            FootingDesignScreen(navController)
        }

        composable("footing_details/{footingId}") { backStackEntry ->
            val footingId = backStackEntry.arguments?.getString("footingId")?.toIntOrNull() ?: 0
            FootingDetailsScreen(footingId, navController)
        }

        composable("wall_design") {
            RetainingWallDesignScreen(navController)
        }

        composable("wall_details/{wallId}") { backStackEntry ->
            val wallId = backStackEntry.arguments?.getString("wallId")?.toIntOrNull() ?: 0
            RetainingWallDetailsScreen(wallId, navController)
        }

        composable("settings") {
            SettingsScreen(navController)
        }

        composable("advanced_search") {
            AdvancedSearchScreen(navController)
        }
    }
}