package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.posttensioned.ACI_PT_PARAMS
import com.civileg.app.domain.calculations.base.posttensioned.PTDesignEngine
import com.civileg.app.domain.calculations.base.posttensioned.PTLosses
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedDesign
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedInput
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedResult
import com.civileg.app.domain.calculations.base.posttensioned.PrestressDesign
import com.civileg.app.domain.calculations.base.posttensioned.StressCheckResult
import com.civileg.app.domain.calculations.base.posttensioned.UltimateStrengthResult
import com.civileg.app.domain.calculations.base.posttensioned.AnchorageZoneResult
import com.civileg.app.domain.calculations.base.posttensioned.PTDeflectionResult

/**
 * تصميم العناصر مسبقة الإجهاد وفق الكود الأمريكي ACI 318-25.
 */
class ACIPostTensioned : PostTensionedDesign {

    private val engine = PTDesignEngine(ACI_PT_PARAMS)

    override fun designPostTensioned(input: PostTensionedInput): PostTensionedResult =
        engine.design(input)

    override fun calculateLosses(input: PostTensionedInput): PTLosses =
        engine.calculateLosses(input)

    override fun calculateRequiredPrestress(input: PostTensionedInput): PrestressDesign =
        engine.calculateRequiredPrestress(input)

    override fun checkStresses(input: PostTensionedInput, losses: PTLosses): StressCheckResult =
        engine.checkStresses(input, losses)

    override fun checkUltimateStrength(input: PostTensionedInput, losses: PTLosses): UltimateStrengthResult =
        engine.checkUltimateStrength(input, losses)

    override fun designAnchorageZone(input: PostTensionedInput, jackingForce: Double): AnchorageZoneResult =
        engine.designAnchorageZone(input, jackingForce)

    override fun getMinimumPrestress(input: PostTensionedInput): Double =
        engine.minimumPrestress(input)

    override fun calculateDeflection(input: PostTensionedInput, losses: PTLosses): PTDeflectionResult =
        engine.calculateDeflection(input, losses)
}