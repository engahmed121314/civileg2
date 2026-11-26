package com.civileg.app.domain.calculations.utils

import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*
import java.io.Serializable

/** Numeric vertical-link checks for the stated editions. These checks do not
 * establish seismic/torsion, hook anchorage, critical-section location or
 * actual cover applicability. Failure requires redesign, never input clamping.
 */
object BeamShearCodeReview {
    data class Check(val name: String,val value: Double,val limit: Double,val unit: String,
        val passed: Boolean,val clause: String,val redesign: String): Serializable
    data class Review(val basis: String,val capacityKn: Double,val checks: List<Check>,
        val unresolved: List<String>): Serializable {
        val numericChecksPass: Boolean get()=checks.all { it.passed }
        val codeAcceptanceComplete: Boolean get()=numericChecksPass && unresolved.isEmpty()
    }
    fun review(code: DesignCode,fcu: Double,fy: Double,width: Double,depth: Double,
        effectiveDepth: Double,demandKn: Double,diameterMm: Double,legs: Int,spacingMm: Double): Review {
        require(listOf(fcu,fy,width,depth,effectiveDepth,diameterMm,spacingMm).all { it.isFinite() && it>0 })
        require(demandKn.isFinite() && demandKn>=0 && legs>=2 && effectiveDepth<depth)
        val checks=mutableListOf<Check>()
        fun maximum(name:String,value:Double,limit:Double,unit:String,clause:String,fix:String) {
            checks.add(Check(name,value,limit,unit,value<=limit+1e-9,clause,fix))
        }
        fun minimum(name:String,value:Double,limit:Double,unit:String,clause:String,fix:String) {
            checks.add(Check(name,value,limit,unit,value+1e-9>=limit,clause,fix))
        }
        val av=legs*PI*diameterMm*diameterMm/4.0
        val avPerMm=av/spacingMm
        val basis=when(code){DesignCode.ECP->"ECP203-2020";DesignCode.ACI->"ACI318M-14";DesignCode.SBC->"SBC304-CR-2018"}
        val capacity:Double
        if(code==DesignCode.ECP) {
            val vc=0.12*sqrt(fcu/1.5)*width*effectiveDepth/1000
            val vs=avPerMm*minOf(fy,420.0)/1.15*effectiveDepth/1000
            val vmax=minOf(0.7*sqrt(fcu/1.5),4.4)*width*effectiveDepth/1000
            capacity=minOf(vc+vs,vmax)
            maximum("Maximum section shear",demandKn,vmax,"kN","4-27","Increase web width/effective depth or concrete strength; closer links alone cannot fix this limit")
            minimum("Web steel per unit length",avPerMm,maxOf(0.4/minOf(fy,420.0),0.0015)*width,"mm2/mm","4-40","Increase link area/legs or reduce spacing")
            minimum("Link diameter",diameterMm,8.0,"mm","4-2-2-6(a)","Adopt links at least8mm in diameter")
            minimum("Number of link legs",legs.toDouble(),if(width>=400 || width>depth)4.0 else 2.0,"legs","4-2-2-6(b)","Provide at least4legs and verify transverse leg spacing at most250mm")
            maximum("Vertical-link longitudinal spacing",spacingMm,200.0,"mm","4-2-2-6(f)","Reduce longitudinal link spacing to at most200mm")
        } else {
            // Current app input is cube strength. Conversion is explicitly
            // listed as an unresolved material-basis fact, never code approval.
            val fc=0.8*fcu
            val vc=0.17*sqrt(fc)*width*effectiveDepth/1000
            val vs=avPerMm*minOf(fy,420.0)*effectiveDepth/1000
            val vmax=0.75*(vc+0.66*sqrt(fc)*width*effectiveDepth/1000)
            capacity=minOf(0.75*(vc+vs),vmax)
            maximum("Maximum section shear",demandKn,vmax,"kN","22.5.1/22.5.10","Increase web width/effective depth or concrete strength; do not rely on more links alone")
            val requiredVs=maxOf(0.0,demandKn/0.75-vc)
            val maxSpacing=if(maxOf(requiredVs,vs)<=0.33*sqrt(fc)*width*effectiveDepth/1000)minOf(effectiveDepth/2,600.0) else minOf(effectiveDepth/4,300.0)
            maximum("Vertical-link longitudinal spacing",spacingMm,maxSpacing,"mm","Table9.7.6.2.2","Reduce adopted spacing to computed maximum")
            if(demandKn>0.5*0.75*vc) minimum("Web steel per unit length",avPerMm,maxOf(0.062*sqrt(fc),0.35)*width/minOf(fy,420.0),"mm2/mm","Table9.6.3.3","Increase link area/legs or reduce spacing")
        }
        maximum("Adopted-link shear resistance",demandKn,capacity,"kN",if(code==DesignCode.ECP)"4-30/4-34" else "22.5.10.5.3","Increase links or section within maximum-shear limit, then recalculate")
        val unresolved=buildList {
            if(code!=DesignCode.ECP) add("Explicit cylinder concrete strength required; existing0.8cube conversion is not verified project material data")
            if(code==DesignCode.ECP) add("Link steel type not entered; minimum ratio conservatively uses plain-steel floor0.0015, not high-grade deformed-steel relaxation")
            add("Confirm ordinary nonprestressed gravity beam applicability, no torsion/seismic design requirement")
            add("Actual effective depth, link anchorage/closure, transverse leg arrangement and critical-section/load position require case data")
        }
        return Review(basis,capacity,checks.toList(),unresolved)
    }
}
