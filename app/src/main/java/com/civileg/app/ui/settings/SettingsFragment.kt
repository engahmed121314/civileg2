package com.civileg.app.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.civileg.app.databinding.FragmentSettingsBinding
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.utils.SettingsManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var settingsManager: SettingsManager
    
    private val PREFS_NAME = "app_settings"
    private val KEY_DARK_MODE = "dark_mode"
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupLanguageSelection()
        setupThemeSelection()
        setupPricing()
        setupAbout()
    }
    
    private fun setupPricing() {
        binding.etSteelPrice.setText(settingsManager.steelPrice.toString())
        binding.etConcretePrice.setText(settingsManager.concretePrice.toString())
        
        binding.btnSavePrices.setOnClickListener {
            val steel = binding.etSteelPrice.text.toString().toDoubleOrNull() ?: 45000.0
            val concrete = binding.etConcretePrice.text.toString().toDoubleOrNull() ?: 1500.0
            
            settingsManager.steelPrice = steel
            settingsManager.concretePrice = concrete
            
            Toast.makeText(context, "Prices updated successfully", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupLanguageSelection() {
        binding.btnLanguage.setOnClickListener {
            val languages = arrayOf("English", "العربية")
            val current = LocaleHelper.getLocale(requireContext())
            val selected = if (current == "en") 0 else 1
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Language")
                .setSingleChoiceItems(languages, selected) { dialog, which ->
                    val newLang = if (which == 0) "en" else "ar"
                    if (newLang != current) {
                        LocaleHelper.setLocale(requireContext(), newLang)
                        val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        requireContext().startActivity(intent)
                        requireActivity().finish()
                    }
                    dialog.dismiss()
                }
                .show()
        }
        
        val current = LocaleHelper.getLocale(requireContext())
        binding.tvCurrentLanguage.text = if (current == "en") "English" else "العربية"
    }
    
    private fun setupThemeSelection() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        
        binding.switchDarkMode.isChecked = isDarkMode
        
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }
    
    private fun setupAbout() {
        binding.btnAbout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Civil EG")
                .setMessage("Version 1.0.0\n\nCivil Engineering Assistant\nSupporting Egyptian, American, and Saudi codes.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
