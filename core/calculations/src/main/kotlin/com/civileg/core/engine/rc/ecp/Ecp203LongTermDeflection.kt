package com.civileg.core.engine.rc.ecp

/**
 * Common-age sustained-load deflection, ECP203-2020 printed4-61..4-63,
 * Eqs4-73/74/76/77/78/79/80. Prismatic simply supported UDL only.
 * Staggered load ages, camber and a thermal analysis are not supplied here.
 * Compression reinforcement receives no long-term reduction credit, conservatively;
 * this does not assert that actual compression reinforcement is absent.
 * A limit check is not crack-width, detailing or whole-member approval.
 */
object Ecp203LongTermDeflection {
    /** Exact stated anchors, or an actual age >=60 months where the source gives xi=2. */
    data class Duration(val months: Int) {
        init { require(months in listOf(0, 3, 6, 12) || months >= 60) }
        val xi: Double get() = when {
            months == 0 -> 0.0
            months == 3 -> 1.0
            months == 6 -> 1.2
            months == 12 -> 1.4
            months >= 60 -> 2.0
            else -> throw IllegalArgumentException("Duration is not a supported source anchor.")
        }
        companion object {
            fun ofMonths(months: Int) = Duration(months)
            val AT_LOAD_APPLICATION = ofMonths(0)
        }
    }
    enum class Attachment { NONE, DEFLECTION_INSENSITIVE, DEFLECTION_SENSITIVE }
    enum class Status { PASS, FAIL, NOT_EVALUATED, NOT_APPLICABLE }

    data class Input(
        val clearSpanMm: Double,
        val sustainedLiveFraction: Double,
        val endDuration: Duration,
        val attachment: Attachment,
        /** Zero means just after the initial sustained-load instantaneous response. */
        val installationDuration: Duration?,
        val sustainedLoadsShareCommonApplicationAge: Boolean,
        /** Adverse thermal contribution from a separate analysis. Null is unknown, not zero. */
        val thermalTotalMm: Double? = null,
        val thermalAfterInstallationMm: Double? = null
    )

    data class Check(val actualMm: Double?, val limitMm: Double?, val status: Status,
        val sourceEquation: String)

    data class Assessment(
        val immediate: Ecp203ShortTermDeflection.Assessment,
        val sustained: Ecp203ShortTermDeflection.LoadCase,
        val alphaEnd: Double,
        val alphaAtInstallation: Double?,
        val additionalCreepShrinkageMm: Double,
        val mechanicalTotalMm: Double,
        val mechanicalAfterInstallationMm: Double?,
        val total: Check,
        val liveOnly: Check,
        val afterInstallation: Check,
        val highStrengthOrServiceStressReviewRequired: Boolean,
        val deflectionLimitChecksPass: Boolean
    )

    fun assess(section: Ecp203ShortTermDeflection.Section, characteristicSteelYieldMpa: Double,
        analysisSpanMm: Double, deadLineLoadKnPerM: Double, liveLineLoadKnPerM: Double,
        input: Input): Assessment {
        require(input.sustainedLoadsShareCommonApplicationAge) {
            "This method requires a common application age for all sustained loads; staged loads need a separate analysis."
        }
        require(input.clearSpanMm.isFinite() && input.clearSpanMm > 0.0 &&
            analysisSpanMm.isFinite() && input.clearSpanMm <= analysisSpanMm + 1e-6)
        require(input.sustainedLiveFraction.isFinite() && input.sustainedLiveFraction in 0.0..1.0)
        require(characteristicSteelYieldMpa.isFinite() && characteristicSteelYieldMpa > 0.0)
        require(input.endDuration != Duration.AT_LOAD_APPLICATION) {
            "Choose a supported nonzero duration for the long-term assessment."
        }
        require(listOfNotNull(input.thermalTotalMm, input.thermalAfterInstallationMm)
            .all { it.isFinite() && it >= 0.0 })
        if (input.attachment == Attachment.NONE) {
            require(input.installationDuration == null && input.thermalAfterInstallationMm == null)
        } else {
            requireNotNull(input.installationDuration) { "Attachment checks need its installation duration."
            }
            require(input.installationDuration.months <= input.endDuration.months) {
                "Installation cannot occur after the assessment duration."
            }
        }
        val frozen = section.copy(steelLayers = section.steelLayers.toList())
        val immediate = Ecp203ShortTermDeflection.assessSimplySupportedUdl(frozen, analysisSpanMm,
            deadLineLoadKnPerM, liveLineLoadKnPerM)
        // D+psiL has its own moment and stiffness. Never scale the full-load
        // deflection by a load fraction across the cracking transition.
        val sustained = Ecp203ShortTermDeflection.assessSimplySupportedUdl(frozen, analysisSpanMm,
            deadLineLoadKnPerM, liveLineLoadKnPerM * input.sustainedLiveFraction).deadPlusLive
        val full = immediate.deadPlusLive
        val cracked = full.maximumMomentKnM > immediate.stiffness.crackingMomentKnM
        require(!cracked || full.fullyCrackedElasticTensionSteelStressMpa <= characteristicSteelYieldMpa + 1e-9) {
            "Cracked elastic service steel exceeds characteristic yield; this deflection model is outside its range."
        }
        // muPrime=0 means explicitly no beneficial compression-steel credit.
        val alphaEnd = input.endDuration.xi
        val alphaInstall = input.installationDuration?.xi
        val additional = alphaEnd * sustained.immediateDeflectionMm
        val mechanicalTotal = full.immediateDeflectionMm + additional
        val mechanicalAfter = alphaInstall?.let {
            sustained.immediateDeflectionMm * (alphaEnd - it) +
                full.immediateDeflectionMm - sustained.immediateDeflectionMm
        }
        require(additional.isFinite() && mechanicalTotal.isFinite() &&
            (mechanicalAfter == null || mechanicalAfter.isFinite() && mechanicalAfter >= -1e-9))
        fun check(actual: Double?, limit: Double?, equation: String): Check {
            require(actual == null || actual.isFinite() && actual >= 0.0)
            return Check(actual, limit, when {
                limit == null -> Status.NOT_APPLICABLE
                actual == null -> Status.NOT_EVALUATED
                actual <= limit + 1e-9 -> Status.PASS
                else -> Status.FAIL
            }, equation)
        }
        val total = check(input.thermalTotalMm?.let { mechanicalTotal + it }, input.clearSpanMm / 250.0, "4-74")
        val live = if (input.attachment == Attachment.DEFLECTION_INSENSITIVE)
            check(immediate.immediateLiveDeflectionMm, input.clearSpanMm / 360.0, "4-76/77")
        else check(null, null, "4-76/77")
        val postLimit = when (input.attachment) {
            Attachment.NONE -> null
            Attachment.DEFLECTION_SENSITIVE -> input.clearSpanMm / 480.0
            Attachment.DEFLECTION_INSENSITIVE -> input.clearSpanMm / 240.0
        }
        val post = check(mechanicalAfter?.let { mechanical ->
            input.thermalAfterInstallationMm?.let { mechanical + it }
        }, postLimit, if (input.attachment == Attachment.DEFLECTION_SENSITIVE) "4-78/80" else "4-79/80")
        val review = characteristicSteelYieldMpa > 420.0 ||
            cracked && full.fullyCrackedElasticTensionSteelStressMpa > 200.0
        val passed = listOf(total, live, post).all {
            it.status == Status.PASS || it.status == Status.NOT_APPLICABLE
        }
        return Assessment(immediate, sustained, alphaEnd, alphaInstall, additional, mechanicalTotal,
            mechanicalAfter?.coerceAtLeast(0.0), total, live, post, review, passed)
    }
}
