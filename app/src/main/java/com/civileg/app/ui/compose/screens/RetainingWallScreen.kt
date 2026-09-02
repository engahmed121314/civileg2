package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.RetainingWallViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RetainingWallScreen(
    viewModel: RetainingWallViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Retaining Wall Design")
        Button(onClick = {
            viewModel.calculateRetainingWallPro(
                height = 5.0,
                soilDensity = 18.0,
                frictionAngle = 30.0,
                surcharge = 5.0,
                fcu = 25.0,
                fy = 400.0,
                preferredDiameter = 16,
                code = CalculatorEngine.AppDesignCode.EGYPTIAN
            )
        }) {
            Text("Calculate")
        }
    }
}
