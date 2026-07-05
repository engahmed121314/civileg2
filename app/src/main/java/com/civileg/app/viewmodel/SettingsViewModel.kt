package com.civileg.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.repository.ProjectRepository
import com.civileg.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppSettings(
    val defaultCode: DesignCode = DesignCode.ECP,
    val concretePrice: Double = 1500.0,
    val steelPrice: Double = 45000.0,
    val formworkPrice: Double = 300.0,
    val currency: String = "EGP",
    val unitSystem: String = "SI",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reportLanguage: String = "ar"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ProjectRepository
) : ViewModel() {
    
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    init {
        viewModelScope.launch {
            combine(
                repository.getDefaultDesignCode(),
                repository.getConcretePrice(),
                repository.getSteelPrice(),
                repository.getFormworkPrice(),
                repository.getCurrency(),
                repository.getUnitSystem(),
                repository.getThemeMode(),
                repository.getReportLanguage()
            ) { args: Array<*> ->
                val code = args[0] as String
                val concrete = args[1] as Double
                val steel = args[2] as Double
                val formwork = args[3] as Double
                val currency = args[4] as String
                val unitSystem = args[5] as String
                val theme = args[6] as String
                val reportLang = args[7] as String

                AppSettings(
                    defaultCode = try { DesignCode.valueOf(code) } catch (e: Exception) { DesignCode.ECP },
                    concretePrice = concrete,
                    steelPrice = steel,
                    formworkPrice = formwork,
                    currency = currency,
                    unitSystem = unitSystem,
                    themeMode = try { ThemeMode.valueOf(theme) } catch (e: Exception) { ThemeMode.SYSTEM },
                    reportLanguage = reportLang
                )
            }.collect { _settings.value = it }
        }
    }
    
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode.name)
        }
    }

    fun setReportLanguage(lang: String) {
        viewModelScope.launch {
            repository.setReportLanguage(lang)
        }
    }

    fun setDefaultCode(code: DesignCode) {
        viewModelScope.launch {
            repository.setDefaultDesignCode(code.name)
        }
    }
    
    fun setConcretePrice(price: Double) {
        viewModelScope.launch {
            repository.updatePrices(concrete = price, steel = null, formwork = null)
        }
    }
    
    fun setSteelPrice(price: Double) {
        viewModelScope.launch {
            repository.updatePrices(concrete = null, steel = price, formwork = null)
        }
    }
    
    fun setFormworkPrice(price: Double) {
        viewModelScope.launch {
            repository.updatePrices(concrete = null, steel = null, formwork = price)
        }
    }
    
    fun setCurrency(currency: String) {
        viewModelScope.launch {
            repository.setCurrency(currency)
        }
    }
    
    fun setUnitSystem(system: String) {
        viewModelScope.launch {
            repository.setUnitSystem(system)
        }
    }
}
