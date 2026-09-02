package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.BeamViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun BeamScreen(
    viewModel: BeamViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Beam Design")
        Button(onClick = {
            // Simplified
        }) {
            Text("Calculate")
        }
    }
}
