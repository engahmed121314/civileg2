package com.civileg.app.domain.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Ensures every CodeReference key resolves for every design code.
 * A key present in one object but missing in another would silently surface
 * "Reference not found" in the UI, so all three key sets must match.
 */
class CodeReferenceTest {

    private fun fields(c: Class<*>): Set<String> =
        c.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map(Field::getName)
            .filter { it != "INSTANCE" && !it.startsWith("\$") }
            .toSet()

    @Test
    fun `all three codes expose the same key set`() {
        val ecp = fields(CodeReference.ECP::class.java)
        val aci = fields(CodeReference.ACI::class.java)
        val sbc = fields(CodeReference.SBC::class.java)

        assertTrue("ECP must define keys", ecp.isNotEmpty())
        assertEquals("ACI keys must match ECP", ecp, aci)
        assertEquals("SBC keys must match ECP", ecp, sbc)
    }

    @Test
    fun `every key resolves for every code`() {
        for (key in fields(CodeReference.ECP::class.java)) {
            for ((code, prefix) in listOf(
                DesignCode.ECP to "ECP",
                DesignCode.ACI to "ACI",
                DesignCode.SBC to "SBC"
            )) {
                val ref = CodeReference.getReference(code, key)
                assertTrue("$key/$code must resolve (got '$ref')", ref != "Reference not found")
                assertTrue("$key/$code must not be blank", ref.isNotBlank())
                // ACI/SBC steel refs use AISC/SBC-306 wording, so only require the
                // concrete-code prefix for non-steel keys.
                if (!key.startsWith("STEEL_")) {
                    assertTrue("$key/$code ref '$ref' should mention '$prefix'", ref.contains(prefix))
                }
            }
        }
    }

    @Test
    fun `unknown key returns not-found sentinel`() {
        assertEquals("Reference not found", CodeReference.getReference(DesignCode.ECP, "DOES_NOT_EXIST"))
    }
}
