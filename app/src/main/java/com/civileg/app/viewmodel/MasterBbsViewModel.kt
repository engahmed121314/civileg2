package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.*
import com.civileg.app.utils.*
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MasterBbsViewModel @Inject constructor(
    private val designDao: DesignDao
) : ViewModel() {

    private val _bbsEntries = MutableLiveData<List<BbsEntry>>(emptyList())
    val bbsEntries: LiveData<List<BbsEntry>> = _bbsEntries

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val gson = Gson()

    fun loadProjectBbs(projectId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                val designs = designDao.getDesignsForProjectList(projectId)
                val allEntries = mutableListOf<List<BbsEntry>>()
                
                designs.forEach { design ->
                    try {
                        val entries = when (design.type) {
                            DesignType.BEAM -> {
                                val res = gson.fromJson(design.results, CalculatorEngine.BeamResult::class.java)
                                BbsGenerator.generateBeamBbs(design.name, res)
                            }
                            DesignType.COLUMN -> {
                                val res = gson.fromJson(design.results, CalculatorEngine.ColumnResult::class.java)
                                BbsGenerator.generateColumnBbs(design.name, res)
                            }
                            DesignType.FOOTING -> {
                                val res = gson.fromJson(design.results, CalculatorEngine.FootingResult::class.java)
                                BbsGenerator.generateFootingBbs(design.name, res)
                            }
                            else -> emptyList()
                        }
                        if (entries.isNotEmpty()) allEntries.add(entries)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Track per-design errors but continue processing others
                    }
                }
                
                val combined = BbsGenerator.combineProjectBbs(allEntries)
                withContext(Dispatchers.Main) {
                    _bbsEntries.value = combined
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "BBS generation failed: ${e.message ?: "Unknown error"} / فشل إنشاء جدول التسليح"
                }
            } finally {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }
}
