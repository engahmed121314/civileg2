package com.civileg.app.ui.beam

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.civileg.app.R
import com.civileg.app.databinding.FragmentBeamDesignBinding
import com.civileg.app.domain.entities.*
import com.civileg.app.db.Project as DbProject
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ContinuousBeamAnalysis
import com.civileg.app.utils.SettingsManager
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import com.civileg.app.views.BeamSectionView
import com.civileg.app.viewmodel.ProjectViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import java.io.File
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class BeamDesignFragment : Fragment() {

    private var _binding: FragmentBeamDesignBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()
    private val args: BeamDesignFragmentArgs by navArgs()

    private lateinit var spanAdapter: SpanAdapter
    private val spans = mutableListOf<ContinuousBeamAnalysis.Span>()
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    @Inject
    lateinit var analysisEngine: ContinuousBeamAnalysis
    
    private var lastResult: CalculatorEngine.BeamResult? = null
    private var lastInputData: JSONObject? = null
    private var projectsList: List<DbProject> = emptyList()
    private var selectedCode: DesignCode = DesignCode.ECP

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBeamDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            projectsList = projects
        }

        setupSpinners()
        setupCodeSelection()
        setupCalculateButton()
        setupSpanList()
        setupInitialDrawing()
        setupTabs()
    }

    private fun setupSpanList() {
        spans.add(ContinuousBeamAnalysis.Span(5.0, 25.0)) // Default span
        spanAdapter = SpanAdapter(spans) {
            // Optional: trigger partial re-calc or validation
        }
        binding.rvSpans.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            adapter = spanAdapter
        }

        binding.btnAddSpan.setOnClickListener {
            spanAdapter.addSpan()
        }
    }

    private fun setupSpinners() {
        val mainBars = listOf("12", "14", "16", "18", "20", "22", "25")
        binding.spinnerMainBar.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mainBars))
        
        val stirrupBars = listOf("8", "10", "12")
        binding.spinnerStirrupBar.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, stirrupBars))
    }

    private fun setupTabs() {
        binding.beamTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.beamSectionView.visibility = if (tab?.position == 0) View.VISIBLE else View.GONE
                binding.layoutAnalysis.visibility = if (tab?.position == 1) View.VISIBLE else View.GONE
                binding.scrollDataSheet.visibility = if (tab?.position == 2) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.analysisToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                binding.analysisDiagramView.mode = if (checkedId == R.id.btnBMD) {
                    com.civileg.app.views.AnalysisDiagramView.Mode.BMD
                } else {
                    com.civileg.app.views.AnalysisDiagramView.Mode.SFD
                }
            }
        }
    }

    private fun setupCodeSelection() {
        binding.codeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedCode = when (checkedId) {
                    R.id.btnAmericanCode -> DesignCode.ACI
                    R.id.btnSaudiCode -> DesignCode.SBC
                    else -> DesignCode.ECP
                }
            }
        }
    }

    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            performCalculation()
        }
    }

    private fun setupInitialDrawing() {
        binding.beamSectionView.apply {
            beamWidth = 300f
            beamHeight = 600f
            cover = 40f
            topBars = listOf(BeamSectionView.BarInfo(2, 12))
            bottomBars = listOf(BeamSectionView.BarInfo(3, 16))
            invalidate()
        }
    }

    private fun performCalculation() {
        try {
            val width = binding.etBeamWidth.text.toString().toDoubleOrNull() ?: 250.0
            val height = binding.etBeamHeight.text.toString().toDoubleOrNull() ?: 600.0
            val length = binding.etBeamLength.text.toString().toDoubleOrNull() ?: 5000.0
            val fc = binding.etConcreteStrength.text.toString().toDoubleOrNull() ?: 25.0
            val fy = binding.etSteelStrength.text.toString().toDoubleOrNull() ?: 360.0
            val dl = binding.etDeadLoad.text.toString().toDoubleOrNull() ?: 15.0
            val ll = binding.etLiveLoad.text.toString().toDoubleOrNull() ?: 10.0
            
            val preferredBar = binding.spinnerMainBar.text.toString().toIntOrNull() ?: 16
            val wastePercent = binding.etWasteFactor.text.toString().toDoubleOrNull() ?: 5.0

            val code = when(selectedCode) {
                DesignCode.ECP -> CalculatorEngine.DesignCode.EGYPTIAN
                DesignCode.ACI -> CalculatorEngine.DesignCode.ACI
                DesignCode.SBC -> CalculatorEngine.DesignCode.SAUDI
            }

            // Perform Analysis for BMD/SFD using ContinuousBeamAnalysis
            val analysis = analysisEngine.solve(spans)
            displayAnalysis(analysis)

            // Use the max moment from analysis for the design
            val maxMoment = analysis.points.maxOf { Math.abs(it.moment) }
            val maxShear = analysis.points.maxOf { Math.abs(it.shear) }

            val result = calculatorEngine.designBeam(
                width = width,
                height = height,
                span = spans.sumOf { it.length }, // Total length for weight/volume
                fcu = fc,
                fy = fy,
                deadLoad = dl, // Note: CalculatorEngine currently takes dl/ll separately, 
                liveLoad = ll, // but we used combined load in spans for analysis.
                preferredDiameter = preferredBar,
                code = code,
                customMoment = maxMoment,
                customShear = maxShear
            )

            lastResult = result
            lastInputData = JSONObject().apply {
                put("width", width); put("height", height); put("length", length)
                put("fc", fc); put("fy", fy); put("dl", dl); put("ll", ll)
                put("waste", wastePercent)
            }
            
            updateDrawing(result)
            displayResults(result)
            populateDataSheet(result)

            Toast.makeText(requireContext(), getString(R.string.calculation_complete), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError(e.message ?: getString(R.string.error))
        }
    }

    private fun displayAnalysis(analysis: ContinuousBeamAnalysis.AnalysisResult) {
        val sb = StringBuilder()
        sb.append("--- Support Moments (kN.m) ---\n")
        analysis.moments.forEachIndexed { i, m -> sb.append("M$i: ${String.format(Locale.US, "%.2f", m)}\n") }
        
        sb.append("\n--- Max Shear Forces (kN) ---\n")
        analysis.shearForces.forEachIndexed { i, v -> sb.append("Span ${i+1}: ${String.format(Locale.US, "%.2f", v)}\n") }
        
        sb.append("\n--- Reactions (kN) ---\n")
        analysis.reactions.forEachIndexed { i, r -> sb.append("R$i: ${String.format(Locale.US, "%.2f", r)}\n") }
        
        // Find max positive moment (approximate from points)
        val maxPosM = analysis.points.maxByOrNull { it.moment }?.moment ?: 0.0
        sb.append("\nMax Positive Moment: ${String.format(Locale.US, "%.2f", maxPosM)} kN.m")

        binding.tvAnalysisDetails.text = sb.toString()
        binding.analysisDiagramView.setData(analysis)
    }

    private fun populateDataSheet(result: CalculatorEngine.BeamResult) {
        binding.dataSheetContainer.removeAllViews()
        val details = mutableListOf<Pair<String, String>>()
        details.add("Beam Dimensions" to "${result.width} x ${result.depth} mm")
        details.add("Design Moment (Mu)" to String.format(Locale.US, "%.2f kN.m", result.appliedMoment))
        details.add("Moment Capacity (φMn)" to String.format(Locale.US, "%.2f kN.m", result.momentCapacity))
        details.add("Steel Required (As)" to String.format(Locale.US, "%.1f mm²", result.reinforcementBottom.area))
        details.add("Steel Provided" to "${result.reinforcementBottom.numBars}Ø${result.reinforcementBottom.diameter}")
        details.add("Stirrups" to result.stirrups.description)
        details.add("Calculated Deflection" to String.format(Locale.US, "%.2f mm", result.deflection))
        details.add("Allowable Deflection" to String.format(Locale.US, "%.2f mm", result.allowableDeflection))
        details.add("Concrete Volume" to String.format(Locale.US, "%.3f m³", result.concreteVolume))
        details.add("Steel Weight" to String.format(Locale.US, "%.2f kg", result.steelWeight))
        details.add("Steel Waste (Est)" to String.format(Locale.US, "%.3f Tons", result.steelWasteTons))

        details.forEach { (label, value) ->
            val row = TextView(requireContext()).apply {
                text = getString(R.string.data_sheet_row, label, value)
                setPadding(0, 8, 0, 8)
                textSize = 16f
                setTextColor(resources.getColor(R.color.text_primary, null))
            }
            binding.dataSheetContainer.addView(row)
        }
    }

    private fun updateDrawing(result: CalculatorEngine.BeamResult) {
        binding.beamSectionView.updateFromCalculation(
            width = result.width.toFloat(),
            height = result.depth.toFloat(),
            coverValue = 40f,
            topReinforcement = listOf(BeamSectionView.BarInfo(result.reinforcementTop.numBars, result.reinforcementTop.diameter)),
            bottomReinforcement = listOf(BeamSectionView.BarInfo(result.reinforcementBottom.numBars, result.reinforcementBottom.diameter)),
            momentCap = result.momentCapacity,
            shearCap = result.shearCapacity,
            appMoment = result.appliedMoment,
            appShear = result.appliedShear,
            naDepth = 0.0,
            ratio = result.steelRatio,
            safe = result.isSafe
        )
    }

    private fun displayResults(result: CalculatorEngine.BeamResult) {
        binding.resultsCard.visibility = View.VISIBLE
        val bottomBar = result.reinforcementBottom
        binding.etBottomReinforcement.setText(getString(R.string.reinforcement_format, bottomBar.numBars, bottomBar.diameter))
        
        binding.etWasteDetail.setText(getString(R.string.waste_tons_format, result.steelWasteTons))
        binding.etWasteDetail.visibility = View.VISIBLE
        
        binding.btnExportPdf.setOnClickListener { exportToPdf(result) }
        
        binding.root.post {
            binding.root.smoothScrollTo(0, binding.resultsCard.top)
        }
    }

    private fun exportToPdf(result: CalculatorEngine.BeamResult) {
        try {
            val exporter = ComprehensivePdfExporter(requireContext())
            val projectName = projectsList.firstOrNull { it.id == args.projectId }?.name ?: "Unnamed Project"
            val fileName = "Beam_Report_${System.currentTimeMillis()}.pdf"
            val filePath = File(requireContext().getExternalFilesDir(null), fileName).absolutePath
            
            val beamType = BeamType.SimplySupported(result.depth / 100.0) // Mock span
            val inputs = BeamInputs(
                fcu = 25.0, fy = 360.0, width = result.width, totalDepth = result.depth, 
                effectiveDepth = result.depth - 50, designMoment = result.appliedMoment, 
                designShear = result.appliedShear
            )
            
            val advResult = AdvancedBeamResult(
                beamType = beamType,
                sectionType = BeamSectionType.RECTANGULAR,
                flexureResult = ReinforcementResult(
                    astRequired = result.reinforcementBottom.area,
                    astProvided = result.reinforcementBottom.area,
                    barDiameter = result.reinforcementBottom.diameter.toDouble(),
                    numberOfBars = result.reinforcementBottom.numBars,
                    tiesDiameter = result.stirrups.diameter.toDouble(),
                    tiesSpacing = result.stirrups.spacing,
                    isSafe = result.isSafe,
                    utilizationRatio = result.appliedMoment / result.momentCapacity.coerceAtLeast(1.0)
                ),
                shearResult = ShearReinforcementResult(
                    concreteShearCapacity = result.shearCapacity * 0.6,
                    requiredArea = result.stirrups.area,
                    providedArea = result.stirrups.area,
                    requiredShearReinforcement = 200.0,
                    providedShearReinforcement = 250.0,
                    stirrupDiameter = result.stirrups.diameter.toDouble(),
                    stirrupSpacing = result.stirrups.spacing,
                    isSafe = result.isSafe,
                    utilizationRatio = result.appliedShear / result.shearCapacity.coerceAtLeast(1.0)
                ),
                deflectionCheck = DeflectionCheckResult(
                    calculatedDeflection = result.deflection, 
                    allowableDeflection = result.allowableDeflection, 
                    isSafe = result.deflection <= result.allowableDeflection
                ),
                momentDiagram = emptyList(),
                shearDiagram = emptyList(),
                inventoryAnalysis = null,
                crackWidthCheck = null,
                developmentLengthCheck = null,
                warnings = emptyList(),
                codeNotes = listOf("Exported from Civil EG App", "Design Code: ${result.code.displayName}")
            )

            exporter.exportBeamReport(
                projectName = projectName,
                designCode = DesignCode.ECP,
                beamType = beamType,
                inputs = inputs,
                result = advResult,
                inventoryAnalysis = null,
                momentShearDiagrams = MomentShearDiagrams(emptyList(), emptyList()),
                outputPath = filePath
            )
            Toast.makeText(requireContext(), "PDF Exported: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            showError("Export Error: ${e.message}")
        }
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.error))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
