package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.civileg.app.utils.ColumnLoad
import com.civileg.app.utils.LayoutOptimizer
import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import com.civileg.app.utils.LayoutRecommendation
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SiteLayoutViewModel @Inject constructor() : ViewModel() {

    private val _columns = MutableLiveData<List<ColumnLoad>>(emptyList())
    val columns: LiveData<List<ColumnLoad>> = _columns

    private val _recommendation = MutableLiveData<LayoutRecommendation?>(null)
    val recommendation: LiveData<LayoutRecommendation?> = _recommendation

    private val _plotWidth = MutableLiveData(20.0)
    val plotWidth: LiveData<Double> = _plotWidth

    private val _plotLength = MutableLiveData(30.0)
    val plotLength: LiveData<Double> = _plotLength

    fun setPlotSize(w: Double, l: Double) {
        _plotWidth.value = w
        _plotLength.value = l
    }

    fun addColumn(x: Double, y: Double, load: Double = 1000.0, isNeighbor: Boolean = false) {
        val current = _columns.value?.toMutableList() ?: mutableListOf()
        val nextId = current.size + 1
        current.add(ColumnLoad("C$nextId", x, y, load, isNeighbor = isNeighbor))
        _columns.value = current
        analyze()
    }

    fun removeColumn(id: String) {
        _columns.value = _columns.value?.filter { it.id != id }
        analyze()
    }

    /**
     * [PHASE 1]: Import staking points from a CSV file.
     * Expected format: ID, X, Y, Load
     */
    fun importPointsFromCsv(context: Context, uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val importedColumns = mutableListOf<ColumnLoad>()
            
            var line: String? = reader.readLine() // Skip header if present
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split(",")
                if (parts.size >= 4) {
                    importedColumns.add(ColumnLoad(
                        id = parts[0].trim(),
                        x = parts[1].trim().toDouble() * 1000.0,
                        y = parts[2].trim().toDouble() * 1000.0,
                        axialLoad = parts[3].trim().toDouble()
                    ))
                }
            }
            reader.close()
            
            val current = _columns.value?.toMutableList() ?: mutableListOf()
            current.addAll(importedColumns)
            _columns.value = current
            analyze()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun analyze() {
        val cols = _columns.value ?: return
        val w = _plotWidth.value ?: 20.0
        val l = _plotLength.value ?: 30.0
        
        _recommendation.value = LayoutOptimizer.analyzeLayout(
            w, l, cols, 200.0, CalculatorEngine.DesignCode.EGYPTIAN
        )
    }

    fun clear() {
        _columns.value = emptyList()
        _recommendation.value = null
    }
}
