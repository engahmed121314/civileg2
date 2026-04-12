package com.civilengineer.assistant.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.civilengineer.assistant.ui.screens.*
import com.civilengineer.assistant.ui.screens.columns.*
import com.civilengineer.assistant.ui.screens.slabs.*
import com.civilengineer.assistant.ui.screens.foundations.*
import com.civilengineer.assistant.ui.screens.beams.*
import com.civilengineer.assistant.ui.screens.retaining.*
import com.civilengineer.assistant.ui.screens.tanks.*
import com.civilengineer.assistant.ui.screens.stairs.*
import com.civilengineer.assistant.ui.screens.earthquake.*
import com.civilengineer.assistant.ui.screens.quantity.*
import com.civilengineer.assistant.ui.screens.cost.*
import com.civilengineer.assistant.ui.screens.converter.*
import com.civilengineer.assistant.ui.screens.coderef.*

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // الشاشة الرئيسية
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        // ═══════════════════════════════════════════
        // قوائم الأقسام الفرعية
        // ═══════════════════════════════════════════

        composable(Screen.ColumnsMenu.route) {
            ColumnsMenuScreen(navController = navController)
        }
        composable(Screen.SlabsMenu.route) {
            SlabsMenuScreen(navController = navController)
        }
        composable(Screen.FoundationsMenu.route) {
            FoundationsMenuScreen(navController = navController)
        }
        composable(Screen.BeamsMenu.route) {
            BeamsMenuScreen(navController = navController)
        }
        composable(Screen.RetainingMenu.route) {
            RetainingMenuScreen(navController = navController)
        }
        composable(Screen.TanksMenu.route) {
            TanksMenuScreen(navController = navController)
        }
        composable(Screen.StairsMenu.route) {
            StairsMenuScreen(navController = navController)
        }
        composable(Screen.EarthquakeMenu.route) {
            EarthquakeMenuScreen(navController = navController)
        }

        // ═══════════════════════════════════════════
        // شاشات تصميم الأعمدة
        // ═══════════════════════════════════════════

        composable(Screen.ColumnShortRect.route) {
            ColumnDesignScreen(
                navController = navController,
                columnType = com.civilengineer.assistant.models.ColumnType.SHORT_RECTANGULAR
            )
        }
        composable(Screen.ColumnShortCirc.route) {
            ColumnDesignScreen(
                navController = navController,
                columnType = com.civilengineer.assistant.models.ColumnType.SHORT_CIRCULAR
            )
        }
        composable(Screen.ColumnLongRect.route) {
            ColumnDesignScreen(
                navController = navController,
                columnType = com.civilengineer.assistant.models.ColumnType.LONG_RECTANGULAR
            )
        }
        composable(Screen.ColumnLongCirc.route) {
            ColumnDesignScreen(
                navController = navController,
                columnType = com.civilengineer.assistant.models.ColumnType.LONG_CIRCULAR
            )
        }
        composable(Screen.ColumnBiaxial.route) {
            ColumnDesignScreen(
                navController = navController,
                columnType = com.civilengineer.assistant.models.ColumnType.BIAXIAL
            )
        }

        // ═══════════════════════════════════════════
        // شاشات تصميم البلاطات
        // ═══════════════════════════════════════════

        composable(Screen.SlabSolidOneWay.route) {
            SlabDesignScreen(
                navController = navController,
                slabType = com.civilengineer.assistant.models.SlabType.SOLID_ONE_WAY
            )
        }
        composable(Screen.SlabSolidTwoWay.route) {
            SlabDesignScreen(
                navController = navController,
                slabType = com.civilengineer.assistant.models.SlabType.SOLID_TWO_WAY
            )
        }
        composable(Screen.SlabHollowBlock.route) {
            SlabDesignScreen(
                navController = navController,
                slabType = com.civilengineer.assistant.models.SlabType.HOLLOW_BLOCK
            )
        }
        composable(Screen.SlabFlat.route) {
            SlabDesignScreen(
                navController = navController,
                slabType = com.civilengineer.assistant.models.SlabType.FLAT_SLAB
            )
        }
        composable(Screen.SlabRibbed.route) {
            SlabDesignScreen(
                navController = navController,
                slabType = com.civilengineer.assistant.models.SlabType.RIBBED
            )
        }
        composable(Screen.SlabCantilever.route) {
            SlabDesignScreen(
                navController = navController,
                slabType = com.civilengineer.assistant.models.SlabType.CANTILEVER
            )
        }

        // ═══════════════════════════════════════════
        // شاشات تصميم القواعد
        // ═══════════════════════════════════════════

        composable(Screen.FootingIsolated.route) {
            FoundationDesignScreen(
                navController = navController,
                foundationType = com.civilengineer.assistant.models.FoundationType.ISOLATED_CENTRIC
            )
        }
        composable(Screen.FootingCombined.route) {
            FoundationDesignScreen(
                navController = navController,
                foundationType = com.civilengineer.assistant.models.FoundationType.COMBINED_RECTANGULAR
            )
        }
        composable(Screen.FootingStrip.route) {
            FoundationDesignScreen(
                navController = navController,
                foundationType = com.civilengineer.assistant.models.FoundationType.STRIP
            )
        }
        composable(Screen.FootingRaft.route) {
            FoundationDesignScreen(
                navController = navController,
                foundationType = com.civilengineer.assistant.models.FoundationType.RAFT
            )
        }
        composable(Screen.FootingPile.route) {
            FoundationDesignScreen(
                navController = navController,
                foundationType = com.civilengineer.assistant.models.FoundationType.PILE
            )
        }

        // ═══════════════════════════════════════════
        // شاشات تصميم الكمرات
        // ═══════════════════════════════════════════

        composable(Screen.BeamSimpleRect.route) {
            BeamDesignScreen(
                navController = navController,
                beamType = com.civilengineer.assistant.models.BeamType.SIMPLE_RECTANGULAR
            )
        }
        composable(Screen.BeamSimpleT.route) {
            BeamDesignScreen(
                navController = navController,
                beamType = com.civilengineer.assistant.models.BeamType.SIMPLE_T
            )
        }
        composable(Screen.BeamContinuous.route) {
            BeamDesignScreen(
                navController = navController,
                beamType = com.civilengineer.assistant.models.BeamType.CONTINUOUS
            )
        }
        composable(Screen.BeamDeep.route) {
            BeamDesignScreen(
                navController = navController,
                beamType = com.civilengineer.assistant.models.BeamType.DEEP
            )
        }
        composable(Screen.BeamCantilever.route) {
            BeamDesignScreen(
                navController = navController,
                beamType = com.civilengineer.assistant.models.BeamType.CANTILEVER
            )
        }
        composable(Screen.BeamDoubly.route) {
            BeamDesignScreen(
                navController = navController,
                beamType = com.civilengineer.assistant.models.BeamType.DOUBLY_REINFORCED
            )
        }

        // ═══════════════════════════════════════════
        // شاشات حوائط السند
        // ═══════════════════════════════════════════

        composable(Screen.RetainingGravity.route) {
            RetainingWallDesignScreen(
                navController = navController,
                wallType = com.civilengineer.assistant.models.RetainingWallType.GRAVITY
            )
        }
        composable(Screen.RetainingCantilever.route) {
            RetainingWallDesignScreen(
                navController = navController,
                wallType = com.civilengineer.assistant.models.RetainingWallType.CANTILEVER
            )
        }
        composable(Screen.RetainingCounterfort.route) {
            RetainingWallDesignScreen(
                navController = navController,
                wallType = com.civilengineer.assistant.models.RetainingWallType.COUNTERFORT
            )
        }

        // ═══════════════════════════════════════════
        // شاشات الخزانات
        // ═══════════════════════════════════════════

        composable(Screen.TankUndergroundRect.route) {
            TankDesignScreen(
                navController = navController,
                tankType = com.civilengineer.assistant.models.TankType.UNDERGROUND_RECTANGULAR
            )
        }
        composable(Screen.TankUndergroundCirc.route) {
            TankDesignScreen(
                navController = navController,
                tankType = com.civilengineer.assistant.models.TankType.UNDERGROUND_CIRCULAR
            )
        }
        composable(Screen.TankElevated.route) {
            TankDesignScreen(
                navController = navController,
                tankType = com.civilengineer.assistant.models.TankType.ELEVATED_RECTANGULAR
            )
        }

        // ═══════════════════════════════════════════
        // شاشات السلالم
        // ═══════════════════════════════════════════

        composable(Screen.StairsStraight.route) {
            StairsDesignScreen(
                navController = navController,
                stairsType = com.civilengineer.assistant.models.StairsType.STRAIGHT
            )
        }
        composable(Screen.StairsDogLegged.route) {
            StairsDesignScreen(
                navController = navController,
                stairsType = com.civilengineer.assistant.models.StairsType.DOG_LEGGED
            )
        }
        composable(Screen.StairsSpiral.route) {
            StairsDesignScreen(
                navController = navController,
                stairsType = com.civilengineer.assistant.models.StairsType.SPIRAL
            )
        }

        // ═══════════════════════════════════════════
        // شاشات الزلازل
        // ═══════════════════════════════════════════

        composable(Screen.EarthquakeEquivalentStatic.route) {
            EarthquakeAnalysisScreen(navController = navController)
        }

        // ═══════════════════════════════════════════
        // أدوات إضافية
        // ═══════════════════════════════════════════

        composable(Screen.QuantitySurvey.route) {
            QuantitySurveyScreen(navController = navController)
        }
        composable(Screen.CostEstimation.route) {
            CostEstimationScreen(navController = navController)
        }
        composable(Screen.UnitConverter.route) {
            UnitConverterScreen(navController = navController)
        }
        composable(Screen.CodeReference.route) {
            CodeReferenceScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
    }
}
