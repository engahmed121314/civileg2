package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.TankViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun TankScreen(
    viewModel: TankViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Tank Design")
        Button(onClick = {
            // Simplified
        }) {
            Text("Calculate")
        }
    }
}
