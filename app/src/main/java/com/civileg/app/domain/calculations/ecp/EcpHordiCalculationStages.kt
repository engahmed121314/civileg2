package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.SlabCalculationStage
import com.civileg.core.engine.rc.ecp.Ecp203RibbedSection
import java.util.Locale
import kotlin.math.PI

/** Presentation only. Values come from the same assessment returned to the UI. */
internal object EcpHordiCalculationStages {
    fun build(fcu: Double, fy: Double, ribWidth: Double, ribSpacing: Double,
        totalDepth: Double, topping: Double, moment: Double, shear: Double,
        context: Ecp203RibbedSection.Context, a: Ecp203RibbedSection.Assessment
    ): List<SlabCalculationStage> = buildList {
        fun f(value: Double) = String.format(Locale.US, "%.6g", value)
        fun stage(key: String, heading: String, equation: String, substitution: String,
            result: String, citation: String) {
            add(SlabCalculationStage(key, heading, equation, substitution, result, citation))
        }
        stage("steel_area", "Actual tension reinforcement", "As = sum(n_i * pi * phi_i^2 / 4)",
            context.tensionLayers.joinToString(" + ") { "${it.count} * pi * ${f(it.diameterMm)}^2 / 4" },
            "As = ${f(a.layout.providedSteelMm2)} mm2/rib", "ECP203-2020, actual selected layers; printed7-6/7-7")
        stage("effective_depth", "Effective depth of actual layers", "d = h - sum(As_i * y_i) / As",
            "${f(totalDepth)} - (" + context.tensionLayers.indices.joinToString(" + ") { i ->
                val layer = context.tensionLayers[i]
                "${f(layer.count * PI * layer.diameterMm * layer.diameterMm / 4.0)} * ${f(a.layout.layerCentersFromTensionFaceMm[i])}"
            } + ") / ${f(a.layout.providedSteelMm2)}",
            "d = ${f(requireNotNull(a.layout.effectiveDepthMm))} mm", "ECP203-2020, actual layer centroid; printed7-6/7-7")
        stage("material_strength", "Design material strengths", "fcd = 0.67*fcu/1.50; fyd = fy/1.15",
            "fcd = 0.67*${f(fcu)}/1.50\nfyd = ${f(fy)}/1.15",
            "fcd = ${f(0.67*fcu/1.50)} MPa\nfyd = ${f(fy/1.15)} MPa", "ECP203-2020, printed4-1..4-4")
        stage("flexure", "Layer compatibility and flexure", "sum(As_i*sigma_i) = C(c); sigma_i <= fy/1.15",
            "c = ${f(a.flexure.neutralAxisDepthMm)} mm\nAs_i = [" + context.tensionLayers.joinToString(", ") { f(it.count*PI*it.diameterMm*it.diameterMm/4.0) } +
                "] mm2\nsigma_i = [${a.flexure.layerStressMpa.joinToString(", ") { f(it) }}] MPa",
            "As,required = ${f(requireNotNull(a.requiredSteelMm2))} mm2\nMRd = ${f(a.momentResistanceKnM)} kN.m/rib\nMEd = ${f(moment)} kN.m/rib",
            "ECP203-2020, printed4-1..4-9; 0.95*d arm and singly reinforced ceilings retained")
        stage("shear", "Web shear demand", "q = VEd*1000/(bw*d)",
            "${f(shear)}*1000/(${f(ribWidth)}*${f(requireNotNull(a.layout.effectiveDepthMm))})",
            "q = ${f(a.shear.designShearStressMpa)} MPa\nVRd = ${f(a.shear.shearResistanceKn)} kN/rib", "ECP203-2020, Eq4-24; printed4-17..4-22")
        a.immediateDeflection?.let { sls ->
            val input = requireNotNull(context.immediateDeflectionInput)
            val k = sls.stiffness
            stage("service_loads", "Characteristic service loads", "qD = gk*s/1000; qD+L = (gk+qk)*s/1000",
                "qD = ${f(input.characteristicDeadLoadKnM2)}*${f(ribSpacing)}/1000\nqD+L = (${f(input.characteristicDeadLoadKnM2)}+${f(context.characteristicLiveLoadKnM2)})*${f(ribSpacing)}/1000",
                "qD = ${f(sls.dead.characteristicLineLoadKnPerM)} kN/m/rib\nqD+L = ${f(sls.deadPlusLive.characteristicLineLoadKnPerM)} kN/m/rib",
                "Characteristic loads, self weight included; not the supplied ULS forces")
            stage("elastic_modulus", "Elastic modulus and modular ratio", "Ec = 4400*sqrt(fcu); n = Es/Ec",
                "Ec = 4400*sqrt(${f(fcu)})\nn = 200000/${f(k.elasticModulusMpa)}",
                "Ec = ${f(k.elasticModulusMpa)} MPa\nn = ${f(k.modularRatio)}", "ECP203-2020, Eq2-1; printed2-23")
            stage("section_stiffness", "Gross and cracked section stiffness", "Ig = sum(b*h^3/12 + A*dy^2)\nIcr = transformed cracked-section inertia <= Ig",
                "bw = ${f(ribWidth)}; B = ${f(a.effectiveFlangeWidthMm)}; tf = ${f(topping)}; h = ${f(totalDepth)} mm\nCracked neutral axis = ${f(k.crackedNeutralAxisMm)} mm",
                "Ig = ${f(k.grossInertiaMm4)} mm4\nIcr = ${f(k.crackedInertiaMm4)} mm4", "ECP203-2020, Eq4-70 definitions; printed4-59/60")
            val yt = totalDepth-k.grossCentroidFromCompressionFaceMm
            stage("cracking_moment", "Cracking moment", "Mcr = 0.6*sqrt(fcu)*Ig/yt/10^6",
                "0.6*sqrt(${f(fcu)})*${f(k.grossInertiaMm4)}/${f(yt)}/10^6",
                "Mcr = ${f(k.crackingMomentKnM)} kN.m", "ECP203-2020, Eq4-71; printed4-60")
            fun stiffness(key: String, heading: String, load: com.civileg.core.engine.rc.ecp.Ecp203ShortTermDeflection.LoadCase) {
                val uncracked = load.maximumMomentKnM <= k.crackingMomentKnM
                stage(key, heading, if (uncracked) "Ma <= Mcr: Ie = Ig" else "Ie = (Mcr/Ma)^3*Ig + [1-(Mcr/Ma)^3]*Icr <= Ig",
                    if (uncracked) "${f(load.maximumMomentKnM)} <= ${f(k.crackingMomentKnM)} kN.m" else
                        "(${f(k.crackingMomentKnM)}/${f(load.maximumMomentKnM)})^3*${f(k.grossInertiaMm4)}\n+ [1-(${f(k.crackingMomentKnM)}/${f(load.maximumMomentKnM)})^3]*${f(k.crackedInertiaMm4)}",
                    "Ie = ${f(load.effectiveInertiaMm4)} mm4", "ECP203-2020, Eq4-70; printed4-59/60")
            }
            stiffness("stiffness_dead", "Effective stiffness under dead load", sls.dead)
            stiffness("stiffness_total", "Effective stiffness under dead + live load", sls.deadPlusLive)
            fun deflection(key: String, heading: String, symbol: String, load: com.civileg.core.engine.rc.ecp.Ecp203ShortTermDeflection.LoadCase) {
                stage(key, heading, "$symbol = 5*q*L^4/(384*Ec*Ie)",
                    "5*${f(load.characteristicLineLoadKnPerM)}*${f(input.analysisSpanMm)}^4\n/ (384*${f(k.elasticModulusMpa)}*${f(load.effectiveInertiaMm4)})",
                    "$symbol = ${f(load.immediateDeflectionMm)} mm", "Elastic simply supported UDL; ECP203-2020, printed4-59/60")
            }
            deflection("immediate_dead", "Immediate dead-load deflection", "deltaD", sls.dead)
            deflection("immediate_total", "Immediate dead + live deflection", "delta(D+L)", sls.deadPlusLive)
            stage("immediate_live", "Separate live-load increment", "deltaLive = delta(D+L) - deltaD",
                "${f(sls.deadPlusLive.immediateDeflectionMm)} - ${f(sls.dead.immediateDeflectionMm)}",
                "deltaLive = ${f(sls.immediateLiveDeflectionMm)} mm", "ECP203-2020, Eq4-77; printed4-62. Separate load-case stiffness used.")
            a.longTermDeflection?.let { lt ->
                val longInput = requireNotNull(input.longTermInput)
                stage("sustained_load", "Sustained characteristic load", "qS = qD + psi*qL",
                    "${f(sls.dead.characteristicLineLoadKnPerM)} + ${f(longInput.sustainedLiveFraction)}*${f(context.characteristicLiveLoadKnM2*ribSpacing/1000.0)}",
                    "qS = ${f(lt.sustained.characteristicLineLoadKnPerM)} kN/m/rib", "ECP203-2020, common-age sustained-load model; printed4-61..63")
                stiffness("stiffness_sustained", "Effective stiffness under sustained load", lt.sustained)
                deflection("immediate_sustained", "Immediate sustained-load deflection", "deltaS", lt.sustained)
                stage("creep_factor", "Duration factors", "alpha = xi/(1+50*muPrime); muPrime credit = 0",
                    "alphaEnd = ${f(longInput.endDuration.xi)}/(1+50*0)\nEnd age = ${longInput.endDuration.months} months" +
                        (longInput.installationDuration?.let { "\nalphaInstall = ${f(it.xi)}/(1+50*0); age = ${it.months} months" } ?: ""),
                    "alphaEnd = ${f(lt.alphaEnd)}" + (lt.alphaAtInstallation?.let { "\nalphaInstall = ${f(it)}" } ?: ""), "ECP203-2020, Eq4-73; printed4-61. No compression-steel reduction credit.")
                stage("creep_increment", "Additional creep and shrinkage", "deltaAdditional = alphaEnd*deltaS",
                    "${f(lt.alphaEnd)}*${f(lt.sustained.immediateDeflectionMm)}", "deltaAdditional = ${f(lt.additionalCreepShrinkageMm)} mm", "ECP203-2020, Eq4-73; printed4-61")
                stage("mechanical_total", "Mechanical total deflection", "deltaMechanical = delta(D+L) + alphaEnd*deltaS",
                    "${f(sls.deadPlusLive.immediateDeflectionMm)} + ${f(lt.alphaEnd)}*${f(lt.sustained.immediateDeflectionMm)}",
                    "deltaMechanical = ${f(lt.mechanicalTotalMm)} mm", "ECP203-2020, printed4-61/62; thermal contribution separate")
                lt.mechanicalAfterInstallationMm?.let { after ->
                    stage("post_installation", "Mechanical deflection after installation", "deltaAfter = deltaS*(alphaEnd-alphaInstall) + delta(D+L) - deltaS",
                        "${f(lt.sustained.immediateDeflectionMm)}*(${f(lt.alphaEnd)}-${f(requireNotNull(lt.alphaAtInstallation))})\n+ ${f(sls.deadPlusLive.immediateDeflectionMm)} - ${f(lt.sustained.immediateDeflectionMm)}",
                        "deltaAfter = ${f(after)} mm", "ECP203-2020, Eq4-80; printed4-63. Thermal contribution separate.")
                }
                fun limit(key: String, heading: String, check: com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection.Check,
                    mechanical: Double?, thermal: Double?, divisor: Int?) {
                    val actual = check.actualMm?.let { f(it)+" mm" } ?: if (check.status ==
                        com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection.Status.NOT_APPLICABLE) "NOT APPLICABLE" else "NOT VERIFIED"
                    val limit = check.limitMm?.let { f(it)+" mm" } ?: "NOT APPLICABLE"
                    stage(key, heading, if (divisor == null) "Attachment-specific limit is not applicable" else "delta <= Lclear/$divisor",
                        (if (divisor == null) "No applicable attachment check" else "${f(longInput.clearSpanMm)}/$divisor = $limit") +
                            (mechanical?.let { "\nMechanical = ${f(it)} mm" } ?: "") +
                            (if (key == "limit_live") "" else if (divisor == null) "\nThermal = NOT APPLICABLE" else
                                "\nThermal = ${thermal?.let { f(it)+" mm" } ?: "NOT VERIFIED (blank is not zero)"}"),
                        "Deflection = $actual\nLimit = $limit\nStatus = ${check.status}", "ECP203-2020, Eq${check.sourceEquation}; printed4-62/63. Not whole-member approval.")
                }
                limit("limit_total", "Thermal-inclusive total limit", lt.total, lt.mechanicalTotalMm, longInput.thermalTotalMm, 250)
                limit("limit_live", "Live-load increment limit", lt.liveOnly, sls.immediateLiveDeflectionMm, null,
                    if (lt.liveOnly.limitMm != null) 360 else null)
                limit("limit_after", "Thermal-inclusive installation limit", lt.afterInstallation,
                    lt.mechanicalAfterInstallationMm, longInput.thermalAfterInstallationMm,
                    when (longInput.attachment) {
                        com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection.Attachment.NONE -> null
                        com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection.Attachment.DEFLECTION_SENSITIVE -> 480
                        com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection.Attachment.DEFLECTION_INSENSITIVE -> 240
                    })
            }
        }
    }
}
