package com.civileg.core.engine.rc.ecp

import com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection.Input
import com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection.Duration
import com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection.Attachment
import com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection.Status
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class Ecp203LongTermDeflectionTest {
    private fun section() = Ecp203ShortTermDeflection.Section(30.0, 200.0, 600.0, 60.0, 350.0,
        Ecp203ShortTermDeflection.FlangeLocation.COMPRESSION_FACE,
        listOf(Ecp203ShortTermDeflection.SteelLayer(2 * PI * 20.0 * 20.0 / 4, 307.0)))
    private fun input() = Input(4800.0, 0.7, Duration.ofMonths(60), Attachment.DEFLECTION_SENSITIVE,
        Duration.ofMonths(6), true, 1.5, 0.5)
    private fun assess(i: Input = input(), fy: Double = 400.0) =
        Ecp203LongTermDeflection.assess(section(), fy, 5000.0, 3.6, 4.8, i)

    @Test fun `independent sustained cracked load and printed long term equations`() {
        val a = assess()
        assertEquals(3.295961692232, a.sustained.immediateDeflectionMm, 1e-10)
        assertEquals(713070676.9642524, a.sustained.effectiveInertiaMm4, 1e-4)
        assertEquals(2.0, a.alphaEnd, 0.0)
        assertEquals(1.2, a.alphaAtInstallation!!, 0.0)
        assertEquals(6.591923384464, a.additionalCreepShrinkageMm, 1e-10)
        assertEquals(11.648448887758, a.mechanicalTotalMm, 1e-10)
        assertEquals(4.397333164848, a.mechanicalAfterInstallationMm!!, 1e-10)
        assertEquals(13.148448887758, a.total.actualMm!!, 1e-10)
        assertEquals(4.897333164848, a.afterInstallation.actualMm!!, 1e-10)
        assertEquals(19.2, a.total.limitMm!!, 0.0)
        assertEquals(10.0, a.afterInstallation.limitMm!!, 0.0)
        assertEquals(Status.PASS, a.total.status)
        assertEquals(Status.PASS, a.afterInstallation.status)
        assertEquals(Status.NOT_APPLICABLE, a.liveOnly.status)
        assertTrue(a.deflectionLimitChecksPass)
    }

    @Test fun `sustained case below cracking uses gross inertia not a fraction of full case`() {
        val a = assess(input().copy(sustainedLiveFraction = 0.3))
        assertEquals(a.immediate.stiffness.grossInertiaMm4, a.sustained.effectiveInertiaMm4, 0.0)
        assertEquals(1.5506436194, a.sustained.immediateDeflectionMm, 1e-9)
        assertEquals(8.1578127427, a.mechanicalTotalMm, 1e-7)
        assertEquals(4.7463967799, a.mechanicalAfterInstallationMm!!, 1e-7)
        assertTrue(kotlin.math.abs(a.sustained.immediateDeflectionMm -
            a.immediate.deadPlusLive.immediateDeflectionMm * 5.04 / 8.4) > 1.0)
    }

    @Test fun `zero and full sustained live endpoints retain their own correct cases`() {
        val none = assess(input().copy(sustainedLiveFraction = 0.0))
        assertEquals(none.immediate.dead.immediateDeflectionMm, none.sustained.immediateDeflectionMm, 0.0)
        val all = assess(input().copy(sustainedLiveFraction = 1.0))
        assertEquals(all.immediate.deadPlusLive.immediateDeflectionMm, all.sustained.immediateDeflectionMm, 0.0)
        assertEquals(0.8 * all.sustained.immediateDeflectionMm, all.mechanicalAfterInstallationMm!!, 1e-12)
    }

    @Test fun `source duration anchors and actual ages beyond five years are retained`() {
        for ((months, xi) in listOf(0 to 0.0, 3 to 1.0, 6 to 1.2, 12 to 1.4, 60 to 2.0, 72 to 2.0, 600 to 2.0)) {
            assertEquals(xi, Duration.ofMonths(months).xi, 0.0)
        }
        assertEquals(600, Duration.ofMonths(600).months)
    }

    @Test fun `unverified thermal contributions do not become implicit zero or a limit pass`() {
        val a = assess(input().copy(thermalTotalMm = null, thermalAfterInstallationMm = null))
        assertNotNull(a.mechanicalAfterInstallationMm)
        assertTrue(a.mechanicalTotalMm > 0)
        assertNull(a.total.actualMm)
        assertNull(a.afterInstallation.actualMm)
        assertEquals(Status.NOT_EVALUATED, a.total.status)
        assertEquals(Status.NOT_EVALUATED, a.afterInstallation.status)
        assertFalse(a.deflectionLimitChecksPass)
    }

    @Test fun `total and postinstallation unknown thermal states are independent`() {
        val totalUnknown = assess(input().copy(thermalTotalMm = null, thermalAfterInstallationMm = 0.0))
        assertEquals(Status.NOT_EVALUATED, totalUnknown.total.status)
        assertEquals(Status.PASS, totalUnknown.afterInstallation.status)
        assertFalse(totalUnknown.deflectionLimitChecksPass)
        val postUnknown = assess(input().copy(thermalTotalMm = 0.0, thermalAfterInstallationMm = null))
        assertEquals(Status.PASS, postUnknown.total.status)
        assertEquals(Status.NOT_EVALUATED, postUnknown.afterInstallation.status)
        assertFalse(postUnknown.deflectionLimitChecksPass)
    }

    @Test fun `explicit zero thermal input is different from missing thermal input`() {
        val a = assess(input().copy(thermalTotalMm = 0.0, thermalAfterInstallationMm = 0.0))
        assertEquals(a.mechanicalTotalMm, a.total.actualMm!!, 0.0)
        assertEquals(a.mechanicalAfterInstallationMm!!, a.afterInstallation.actualMm!!, 0.0)
        assertTrue(a.deflectionLimitChecksPass)
    }

    @Test fun `insensitive attachment has distinct live and postinstallation limits`() {
        val a = assess(input().copy(attachment = Attachment.DEFLECTION_INSENSITIVE))
        assertEquals(4800.0 / 360, a.liveOnly.limitMm!!, 0.0)
        assertEquals(20.0, a.afterInstallation.limitMm!!, 0.0)
        assertEquals(a.immediate.immediateLiveDeflectionMm, a.liveOnly.actualMm!!, 0.0)
        assertTrue(a.deflectionLimitChecksPass)
    }

    @Test fun `no attachment does not invent an installation or a postinstallation check`() {
        val a = assess(input().copy(attachment = Attachment.NONE, installationDuration = null,
            thermalAfterInstallationMm = null))
        assertNull(a.mechanicalAfterInstallationMm)
        assertEquals(Status.NOT_APPLICABLE, a.afterInstallation.status)
        assertEquals(Status.NOT_APPLICABLE, a.liveOnly.status)
        assertTrue(a.deflectionLimitChecksPass)
    }

    @Test fun `adverse thermal total can change the actual total limit result`() {
        val a = assess(input().copy(thermalTotalMm = 25.0))
        assertEquals(Status.FAIL, a.total.status)
        assertEquals(Status.PASS, a.afterInstallation.status)
        assertFalse(a.deflectionLimitChecksPass)
    }

    @Test fun `sensitive and insensitive finish limits are not interchanged`() {
        val a = assess(input().copy(thermalAfterInstallationMm = 15.0))
        val b = assess(input().copy(thermalAfterInstallationMm = 15.0, attachment = Attachment.DEFLECTION_INSENSITIVE))
        assertEquals(Status.FAIL, a.afterInstallation.status)
        assertEquals(Status.PASS, b.afterInstallation.status)
    }

    @Test fun `live deflection failure uses clear span and actual separate stiffness increment`() {
        val a = Ecp203LongTermDeflection.assess(section(), 400.0, 8000.0, 1.0, 5.0,
            input().copy(clearSpanMm = 7600.0, attachment = Attachment.DEFLECTION_INSENSITIVE))
        assertEquals(31.76151603248647, a.liveOnly.actualMm!!, 1e-10)
        assertEquals(Status.FAIL, a.liveOnly.status)
        assertFalse(a.deflectionLimitChecksPass)
    }

    @Test fun `high strength steel caution is not silently promoted to approval`() {
        val a = assess(fy = 500.0)
        assertEquals(Status.PASS, a.total.status)
        assertTrue(a.highStrengthOrServiceStressReviewRequired)
        assertTrue(a.deflectionLimitChecksPass) // Numeric limits alone do not complete the material review.
    }

    @Test fun `compression bars receive no unverified beneficial long term credit`() {
        val s = section().copy(steelLayers = section().steelLayers + Ecp203ShortTermDeflection.SteelLayer(100.0, 40.0))
        val a = Ecp203LongTermDeflection.assess(s, 400.0, 5000.0, 3.6, 4.8, input())
        assertEquals(2.0, a.alphaEnd, 0.0)
        assertEquals(2 * a.sustained.immediateDeflectionMm, a.additionalCreepShrinkageMm, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported curve interpolation age is rejected`() { Duration.ofMonths(24) }
    @Test(expected = IllegalArgumentException::class)
    fun `negative age is rejected`() { Duration.ofMonths(-3) }
    @Test(expected = IllegalArgumentException::class)
    fun `end at initial application is not a long term case`() { assess(input().copy(endDuration = Duration.AT_LOAD_APPLICATION)) }
    @Test(expected = IllegalArgumentException::class)
    fun `actual installation beyond actual end is rejected even above five years`() {
        assess(input().copy(endDuration = Duration.ofMonths(60), installationDuration = Duration.ofMonths(72)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `staged sustained loads do not use a common age approximation silently`() {
        assess(input().copy(sustainedLoadsShareCommonApplicationAge = false))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `fraction above one is rejected`() { assess(input().copy(sustainedLiveFraction = 1.01)) }
    @Test(expected = IllegalArgumentException::class)
    fun `negative fraction is rejected`() { assess(input().copy(sustainedLiveFraction = -0.01)) }
    @Test(expected = IllegalArgumentException::class)
    fun `missing installation age on an attached element is rejected`() { assess(input().copy(installationDuration = null)) }
    @Test(expected = IllegalArgumentException::class)
    fun `clear span greater than analysis span is rejected`() { assess(input().copy(clearSpanMm = 5100.0)) }
    @Test(expected = IllegalArgumentException::class)
    fun `negative thermal camber credit is not invented`() { assess(input().copy(thermalTotalMm = -1.0)) }
    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite thermal contribution is rejected`() { assess(input().copy(thermalAfterInstallationMm = Double.NaN)) }
    @Test(expected = IllegalArgumentException::class)
    fun `no attachment cannot carry an accidental installation record`() {
        assess(input().copy(attachment = Attachment.NONE, thermalAfterInstallationMm = null))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `cracked service steel beyond yield rejects the elastic model`() {
        Ecp203LongTermDeflection.assess(section(), 400.0, 5000.0, 3.6, 30.0, input())
    }
}
