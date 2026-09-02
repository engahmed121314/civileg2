package com.civileg.core.engineering

import com.civileg.core.calculations.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test

class CodeVersionRegistryTest {

    @Test
    fun allFamiliesHaveADefaultEdition() {
        DesignCode.entries.forEach { code ->
            val def = CodeVersionRegistry.defaultFor(code)
            assertEquals(code, def.code)
            assertTrue(def.isDefault)
        }
    }

    @Test
    fun resolveEcpReturnsEcpParams() {
        val params = CodeVersionRegistry.resolve(DesignCode.ECP)
        assertTrue(params is Ecp203Params)
        assertEquals(CodeFamily.ECP_203, params.family)
    }

    @Test
    fun resolveAciReturnsAciParams() {
        val params = CodeVersionRegistry.resolve(DesignCode.ACI)
        assertTrue(params is Aci318Params)
    }

    @Test
    fun sbcResolvesToAciParameterSet() {
        // SBC 304 is ACI-based → resolves to the ACI parameter set.
        val params = CodeVersionRegistry.resolve(DesignCode.SBC)
        assertTrue(params is Aci318Params)
    }

    @Test
    fun extensionPropertiesExposeVersions() {
        assertEquals(1, DesignCode.ECP.versions.size)
        assertEquals("ECP:203-2020", DesignCode.ECP.defaultVersion.key)
    }

    @Test
    fun forDesignCodeBuildsEngineWithSelectedParams() {
        val engine = CodeRuleEngine.forDesignCode(
            DesignCode.ECP,
            ConcreteMaterial(30.0),
            SteelMaterial(360.0, 520.0)
        )
        assertTrue(engine.params is Ecp203Params)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownEditionThrows() {
        CodeVersionRegistry.resolve(DesignCode.ECP, "203-2007")
    }
}
