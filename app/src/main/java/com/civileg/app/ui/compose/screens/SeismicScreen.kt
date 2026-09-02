package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.ProjectViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SeismicScreen(
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Seismic Analysis")
        Button(onClick = {
            // Simplified call
        }) {
            Text("Calculate")
        }
    }
}
