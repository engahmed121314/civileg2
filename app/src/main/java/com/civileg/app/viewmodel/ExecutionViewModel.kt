package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.ConstructionDao
import com.civileg.app.db.PourLog
import com.civileg.app.db.SiteInspection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExecutionViewModel @Inject constructor(
    private val constructionDao: ConstructionDao
) : ViewModel() {

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun getPourLogs(projectId: Long): LiveData<List<PourLog>> {
        return constructionDao.getPourLogsForProject(projectId)
    }

    fun addPourLog(log: PourLog) {
        viewModelScope.launch {
            try {
                constructionDao.insertPourLog(log)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add pour log / فشل إضافة سجل الصب: ${e.message}"
            }
        }
    }

    fun getInspections(projectId: Long): LiveData<List<SiteInspection>> {
        return constructionDao.getInspectionsForProject(projectId)
    }

    fun addInspection(inspection: SiteInspection) {
        viewModelScope.launch {
            try {
                constructionDao.insertSiteInspection(inspection)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add inspection / فشل إضافة التفتيش: ${e.message}"
            }
        }
    }
}
