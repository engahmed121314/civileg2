package com.civileg.app.domain.entities

import com.civileg.core.engine.rc.ecp.*
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import org.junit.Assert.*
import org.junit.Test

/** The frozen JSON schema includes nested core models, not just its app entity roots. */
class HordiSnapshotSerializationContractTest {
    private fun reachableModels(): Set<Class<*>> {
        val found = linkedSetOf<Class<*>>()
        fun visit(type: Type) {
            when (type) {
                is ParameterizedType -> {
                    visit(type.rawType)
                    type.actualTypeArguments.forEach(::visit)
                }
                is WildcardType -> (type.upperBounds + type.lowerBounds).forEach(::visit)
                is Class<*> -> {
                    if (type.isArray) {
                        visit(type.componentType)
                    } else if (type.name.startsWith("com.civileg.") && found.add(type) && !type.isEnum) {
                        type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
                            .forEach { visit(it.genericType) }
                    }
                }
            }
        }
        listOf(HordiSlabInputs::class.java, SlabDesignResult::class.java,
            FrozenDesignRecord::class.java).forEach(::visit)
        return found
    }

    private fun coreModels() = reachableModels().filter {
        it.name.startsWith("com.civileg.core.")
    }.toSet()

    @Test fun `snapshot core schema includes every current nested input and enum type`() {
        assertEquals(setOf(
            Ecp203RibbedSection.Context::class.java,
            Ecp203RibbedSection.ImmediateDeflectionInput::class.java,
            Ecp203RibbedSection.Region::class.java,
            Ecp203BarLayout.Layer::class.java,
            Ecp203RectangularFlexure.SteelSurface::class.java,
            Ecp203WebShear.VerticalLinks::class.java,
            Ecp203LongTermDeflection.Input::class.java,
            Ecp203LongTermDeflection.Duration::class.java,
            Ecp203LongTermDeflection.Attachment::class.java
        ), coreModels())
    }

    @Test fun `every nested core JSON type has an explicit stable name keep rule`() {
        val file = sequenceOf(File("proguard-rules.pro"), File("app/proguard-rules.pro"),
            File("../app/proguard-rules.pro")).firstOrNull { it.isFile }
        assertNotNull("Cannot locate the app shrinker rules", file)
        val rules = file!!.readText()
        assertTrue(rules.contains("-keepattributes Signature"))
        coreModels().forEach { model ->
            val declaration = Regex("(?m)^\\s*-keep(?:,allowoptimization)?\\s+class\\s+" +
                Regex.escape(model.name) + "\\s*\\{\\s*\\*;\\s*}\\s*$")
            assertTrue("Frozen JSON model lacks a stable-name keep rule: ${model.name}",
                declaration.containsMatchIn(rules))
        }
    }
}
