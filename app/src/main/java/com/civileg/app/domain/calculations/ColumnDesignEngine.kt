package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import com.civileg.core.calculations.entities.*
import kotlin.math.*

object ColumnDesignEngine {

    fun designColumn(
        b: Double, t: Double, H: Double,
        beamDepthIn: Double, beamDepthOut: Double,
        cover: Double,
        Pu: Double, MextIn: Double, MextOut: Double,
        fcu: Double, fy: Double,
        isBraced: Boolean,
        topCond: Int, botCond: Int,
        preferredDia: Int,
        code: DesignCode
    ): ColumnDesignResult {
        val steps = mutableListOf<AppCalculationStep>()
        val ag = b * t
        
        val K_in = 0.75
        val K_out = 0.75
        val Ho_in = H - beamDepthIn
        val Ho_out = H - beamDepthOut
        
        val lambdaIn = (K_in * Ho_in) / t
        val lambdaOut = (K_out * Ho_out) / b
        val lambdaMax = max(lambdaIn, lambdaOut)
        
        val columnClassification = if (lambdaMax > 15.0) "Long" else "Short"
        
        val MaddIn = 0.0
        val MaddOut = 0.0
        val MdesIn = MextIn + MaddIn
        val MdesOut = MextOut + MaddOut
        
        val axialCapacity = 0.67 * fcu / 1.5 * ag / 1000.0
        val utilizationRatio = Pu / axialCapacity.coerceAtLeast(1.0)
        
        val AsMin = 0.008 * ag
        val AsMax = 0.04 * ag
        val numBars = ceil(AsMin / (PI * preferredDia.toDouble().pow(2.0) / 4.0)).toInt().coerceAtLeast(4)
        val AsProvided = numBars * (PI * preferredDia.toDouble().pow(2.0) / 4.0)

        return ColumnDesignResult(
            columnWidth = b,
            columnDepth = t,
            totalHeight = H,
            clearHeightIn = Ho_in,
            clearHeightOut = Ho_out,
            fcu = fcu,
            fy = fy,
            Pu = Pu,
            MextIn = MextIn,
            MextOut = MextOut,
            isBraced = isBraced,
            topCond = topCond,
            botCond = botCond,
            beamDepthIn = beamDepthIn,
            beamDepthOut = beamDepthOut,
            cover = cover,
            designCode = code,
            KfactorIn = K_in,
            KfactorOut = K_out,
            lambdaIn = lambdaIn,
            lambdaOut = lambdaOut,
            lambdaMax = lambdaMax,
            lambdaLimitShort = 15.0,
            lambdaLimitLong = 30.0,
            columnClassification = columnClassification,
            deflectionIn = 0.0,
            deflectionOut = 0.0,
            MaddIn = MaddIn,
            MaddOut = MaddOut,
            MdesIn = MdesIn,
            MdesOut = MdesOut,
            eccentricityIn = 0.0,
            eccentricityOut = 0.0,
            Phi = 0.65,
            alphaFactor = 0.8,
            Pu0 = axialCapacity,
            axialCapacity = axialCapacity,
            utilizationRatio = utilizationRatio,
            AsRequired = AsMin,
            AsMin = AsMin,
            AsMax = AsMax,
            AsProvided = AsProvided,
            rhoActual = AsProvided / ag * 100.0,
            rhoMin = 0.8,
            rhoMax = 4.0,
            finalBars = "$numBars\u03A6$preferredDia",
            finalBarCount = numBars,
            finalBarDia = preferredDia,
            tieSpacingMax = 200.0,
            tieSpacingDense = 100.0,
            tieSpacingNormal = 200.0,
            condensationZoneLength = 500.0,
            isSafe = utilizationRatio <= 1.0,
            calculationSteps = steps
        )
    }
}
