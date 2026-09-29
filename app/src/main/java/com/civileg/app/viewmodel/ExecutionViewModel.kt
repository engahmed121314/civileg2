package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
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

    fun getPourLogs(projectId: Long): LiveData<List<PourLog>> {
        return constructionDao.getPourLogsForProject(projectId)
    }

    fun addPourLog(log: PourLog) {
        viewModelScope.launch {
            constructionDao.insertPourLog(log)
        }
    }

    fun getInspections(projectId: Long): LiveData<List<SiteInspection>> {
        return constructionDao.getInspectionsForProject(projectId)
    }

    fun addInspection(inspection: SiteInspection) {
        viewModelScope.launch {
            constructionDao.insertSiteInspection(inspection)
        }
    }
}
