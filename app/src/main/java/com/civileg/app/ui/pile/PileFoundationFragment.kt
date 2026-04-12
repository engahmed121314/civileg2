package com.civileg.app.ui.pile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.civileg.app.R
import com.civileg.app.databinding.FragmentPileFoundationBinding
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.PdfGenerator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*
import kotlin.math.*

class PileFoundationFragment : Fragment() {
    
    private var _binding: FragmentPileFoundationBinding? = null
    private val binding get() = _binding!!

    private var lastResult: PileResult? = null
    private var lastInput: PileInput? = null

    data class PileInput(
        val diameter: Double,
        val length: Double,
        val soilType: String,
        val spt: Double,
        val numPiles: Int
    )

    data class PileResult(
        val endBearing: Double,
        val skinFriction: Double,
        val ultimateCapacity: Double,
        val allowableCapacity: Double,
        val settlement: Double,
        val groupCapacity: Double
    )
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPileFoundationBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupSoilTypeSpinner()
        setupCalculateButton()
        setupExportButton()
    }
    
    private fun setupSoilTypeSpinner() {
        val isArabic = com.civileg.app.utils.LocaleHelper.getLocale(requireContext()) == "ar"
        val soilTypes = if (isArabic) {
            arrayOf("طين (Clay)", "رمل (Sand)", "صخر (Rock)", "تربة مختلطة")
        } else {
            arrayOf("Clay", "Sand", "Rock", "Mixed Soil")
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, soilTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSoilType.adapter = adapter
    }
    
    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            calculatePileCapacity()
        }
    }
    
    private fun calculatePileCapacity() {
        try {
            val pileDiameter = binding.etPileDiameter.text.toString().toDoubleOrNull() ?: throw Exception(getString(R.string.input_parameters))
            val pileLength = binding.etPileLength.text.toString().toDoubleOrNull() ?: throw Exception(getString(R.string.input_parameters))
            val soilType = binding.spinnerSoilType.selectedItem.toString()
            val sptValue = binding.etSptValue.text.toString().toDoubleOrNull() ?: 10.0
            val numPiles = binding.etNumPiles.text.toString().toIntOrNull() ?: 1

            // Pile cross-section area
            val area = PI * pileDiameter * pileDiameter / 4 // mm²
            
            // End bearing capacity (simplified Meyerhof method)
            val qEnd = when {
                soilType.lowercase().contains("clay") || soilType.contains("طين") -> 9 * sptValue * 100 // kN/m²
                soilType.lowercase().contains("sand") || soilType.contains("رمل") -> 40 * sptValue * 100 // kN/m²
                soilType.lowercase().contains("rock") || soilType.contains("صخر") -> 3000.0 * 100 // kN/m²
                else -> 100 * sptValue * 100
            }
            
            // Skin friction (simplified)
            val perimeter = PI * pileDiameter / 1000.0 // m
            val fs = when {
                soilType.lowercase().contains("clay") || soilType.contains("طين") -> 0.5 * sptValue // kN/m²
                soilType.lowercase().contains("sand") || soilType.contains("رمل") -> 2.0 * sptValue // kN/m²
                else -> sptValue
            }
            
            val qSkin = fs * perimeter * (pileLength) // kN
            
            // Total capacity
            val qTotal = (qEnd * area / 1e6) + qSkin // kN
            
            // Allowable capacity with FS = 2.5
            val qAllowable = qTotal / 2.5
            
            // Settlement estimation (simplified)
            val settlement = qAllowable * (pileLength * 1000.0) / (2 * 30000.0 * area / 1e6) // mm
            
            // Group effect if multiple piles
            val groupEfficiency = if (numPiles > 1) 0.85 else 1.0
            val groupCap = qAllowable * groupEfficiency * numPiles

            lastInput = PileInput(pileDiameter, pileLength, soilType, sptValue, numPiles)
            lastResult = PileResult(
                endBearing = qEnd * area / 1e6,
                skinFriction = qSkin,
                ultimateCapacity = qTotal,
                allowableCapacity = qAllowable * groupEfficiency,
                settlement = settlement,
                groupCapacity = groupCap
            )

            showResults(lastResult!!, numPiles)
        } catch (e: Exception) {
            showError("${getString(R.string.status_unsafe)}: ${e.message}")
        }
    }
    
    private fun showResults(result: PileResult, numPiles: Int) {
        binding.cardResults.visibility = View.VISIBLE
        val isArabic = com.civileg.app.utils.LocaleHelper.getLocale(requireContext()) == "ar"
        
        binding.tvEndBearing.text = if (isArabic) String.format(Locale.getDefault(), "التحميل الارتكازي: %.2f كيلو نيوتن", result.endBearing) else String.format(Locale.getDefault(), "End Bearing: %.2f kN", result.endBearing)
        binding.tvSkinFriction.text = if (isArabic) String.format(Locale.getDefault(), "احتكاك السطح: %.2f كيلو نيوتن", result.skinFriction) else String.format(Locale.getDefault(), "Skin Friction: %.2f kN", result.skinFriction)
        binding.tvUltimateCapacity.text = if (isArabic) String.format(Locale.getDefault(), "السعة القصوى: %.2f كيلو نيوتن", result.ultimateCapacity) else String.format(Locale.getDefault(), "Ultimate Capacity: %.2f kN", result.ultimateCapacity)
        binding.tvAllowableCapacity.text = if (isArabic) String.format(Locale.getDefault(), "السعة المسموحة: %.2f كيلو نيوتن", result.allowableCapacity) else String.format(Locale.getDefault(), "Allowable Capacity: %.2f kN", result.allowableCapacity)
        binding.tvSettlement.text = if (isArabic) String.format(Locale.getDefault(), "الهبوط المتوقع: %.2f مم", result.settlement) else String.format(Locale.getDefault(), "Estimated Settlement: %.2f mm", result.settlement)
        
        if (numPiles > 1) {
            binding.layoutGroup.visibility = View.VISIBLE
            binding.tvNumPiles.text = if (isArabic) "عدد الخوازيق: $numPiles" else "Number of Piles: $numPiles"
            binding.tvGroupCapacity.text = if (isArabic) String.format(Locale.getDefault(), "إجمالي سعة المجموعة: %.2f كيلو نيوتن", result.groupCapacity) else String.format(Locale.getDefault(), "Total Group Capacity: %.2f kN", result.groupCapacity)
        } else {
            binding.layoutGroup.visibility = View.GONE
        }
    }

    private fun setupExportButton() {
        binding.btnExportPdf.setOnClickListener {
            val res = lastResult ?: return@setOnClickListener
            val input = lastInput ?: return@setOnClickListener

            val inputs = mapOf(
                "Pile Diameter" to "${input.diameter} mm",
                "Pile Length" to "${input.length} m",
                "Soil Type" to input.soilType,
                "SPT Value" to "${input.spt}",
                "Number of Piles" to "${input.numPiles}"
            )
            val results = mapOf(
                "End Bearing" to String.format(Locale.getDefault(), "%.2f kN", res.endBearing),
                "Skin Friction" to String.format(Locale.getDefault(), "%.2f kN", res.skinFriction),
                "Allowable Capacity" to String.format(Locale.getDefault(), "%.2f kN", res.allowableCapacity),
                "Est. Settlement" to String.format(Locale.getDefault(), "%.2f mm", res.settlement)
            )

            val file = PdfGenerator.generateDesignReport(
                requireContext(), getString(R.string.pile_foundation), "PILE",
                inputs, results, true
            )
            ExportUtils.openPdf(requireContext(), file)
        }
    }
    
    private fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.status_unsafe))
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
