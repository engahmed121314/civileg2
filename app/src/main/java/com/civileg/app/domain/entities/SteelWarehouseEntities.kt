package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SteelWarehouseInputs(
    val span: Double,              // m
    val length: Double,            // m
    val eaveHeight: Double,        // m
    val ridgeHeight: Double,       // m
    val baySpacing: Double,        // m
    val numberOfStories: Int = 1,
    val slope: Double = 0.1,       // tan(theta)
    val code: DesignCode = DesignCode.ECP,
    val liveLoad: Double = 0.5,    // kN/m2
    val windSpeed: Double = 33.0,  // m/s
    val snowLoad: Double = 0.0,    // kN/m2
    val claddingWeight: Double = 0.15, // kN/m2
    val usePurlins: Boolean = true,
    val purlinSpacing: Double = 1.5, // m
    val useBracing: Boolean = true,
    
    // Manual Overrides
    val overrideColumnSection: SteelSectionType? = null,
    val overrideRafterSection: SteelSectionType? = null,
    val overridePurlinSection: SteelSectionType? = null
) : Parcelable

@Parcelize
data class SteelWarehouseAnalysisResult(
    val mainFrame: MainFrameResult,
    val secondaryMembers: SecondaryMembersResult,
    val connections: List<SteelConnectionDetail>,
    val totalWeight: Double,       // Tons
    val totalCladdingArea: Double, // m2
    val weightPerM2: Double,       // kg/m2
    val resultsByCode: String,
    val safetyStatus: Boolean,
    val recommendations: List<String>,
    val materialTakeoff: Map<String, Double>, // Section Name to Quantity/Weight
    val loadDiagram: LoadDiagramData? = null
) : Parcelable

@Parcelize
data class LoadDiagramData(
    val verticalLoads: List<Pair<Double, Double>>, // Position (x), Load Value (kN/m)
    val horizontalLoads: List<Pair<Double, Double>>, // Height (y), Load Value (kN/m)
    val momentDiagram: List<Pair<Double, Double>>,
    val shearDiagram: List<Pair<Double, Double>>
) : Parcelable

@Parcelize
data class MainFrameResult(
    val columnSection: SteelSectionType,
    val rafterSection: SteelSectionType,
    val maxMoment: Double,         // kN.m
    val maxShear: Double,          // kN
    val maxAxial: Double,          // kN
    val maxDeflection: Double,     // mm
    val allowableDeflection: Double, // mm
    val isSafe: Boolean,
    val utilizationMoment: Double = 0.0,
    val utilizationShear: Double = 0.0,
    val utilizationAxial: Double = 0.0
) : Parcelable

@Parcelize
data class SecondaryMembersResult(
    val purlinSection: SteelSectionType,
    val girtSection: SteelSectionType,
    val bracingSection: SteelSectionType,
    val purlinCount: Int,
    val isSafe: Boolean
) : Parcelable

@Parcelize
data class SteelConnectionDetail(
    val name: String,
    val type: ConnectionType,
    val capacity: Double,
    val demand: Double,
    val isSafe: Boolean
) : Parcelable
