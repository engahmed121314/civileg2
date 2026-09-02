package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.StairViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun StairScreen(
    viewModel: StairViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Stair Design")
        Button(onClick = {
            viewModel.calculateStairPro(
                type = CalculatorEngine.StairType.STRAIGHT,
                span = 3.0,
                riser = 150.0,
                tread = 300.0,
                deadLoad = 5.0,
                liveLoad = 2.0,
                fcu = 25.0,
                fy = 400.0,
                preferredDiameter = 12,
                code = CalculatorEngine.AppDesignCode.EGYPTIAN
            )
        }) {
            Text("Calculate")
        }
    }
}
