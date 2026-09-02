package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.FootingViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun FootingScreen(
    viewModel: FootingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Footing Design")
        Button(onClick = {
            viewModel.calculateFooting(
                type = CalculatorEngine.FootingType.ISOLATED,
                p = 1000.0,
                fcu = 25.0,
                fy = 400.0,
                soil = 200.0,
                colB = 400.0,
                colT = 400.0,
                code = CalculatorEngine.AppDesignCode.EGYPTIAN,
                preferredDiameter = 16,
                preferredSpacing = 200.0
            )
        }) {
            Text("Calculate")
        }
    }
}
