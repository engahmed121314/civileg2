package com.civileg.app.utils.bim

import com.civileg.app.utils.detailing.StructuralDrawing

class IfcValidator {

    fun validate(stepContent: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check for NaN/Inf in geometry values
        if (stepContent.contains("NaN") || stepContent.contains("Inf")) {
            errors.add("STEP file contains NaN or Inf values — geometry validation failed")
        }

        // Check HEADER section present
        if (!stepContent.contains("# HEADER SECTION")) {
            errors.add("Missing HEADER section in STEP file")
        }

        // Check DATA section present
        if (!stepContent.contains("# DATA root")) {
            errors.add("Missing DATA root section in STEP file")
        }

        // Check IfcProject entity
        if (!stepContent.contains("IFCPROJECT")) {
            errors.add("Missing IfcProject entity in STEP file")
        }

        // Check required attributes
        if (!stepContent.contains("GlobalId")) {
            errors.add("Missing GlobalId attribute in IFC entities")
        }

        // Check Name attribute
        if (!stepContent.contains("Name")) {
            errors.add("Missing Name attribute in IFC entities")
        }

        // Check structural elements
        val elementType = extractElementType(stepContent)
        when (elementType) {
            "BEAM" -> {
                if (!stepContent.contains("OverallHeight") || !stepContent.contains("OverallWidth")) {
                    errors.add("IfcBeam missing required profile attributes (OverallHeight, OverallWidth)")
                }
            }
            "COLUMN" -> {
                if (!stepContent.contains("OverallHeight") || !stepContent.contains("OverallWidth")) {
                    errors.add("IfcColumn missing required profile attributes (OverallHeight, OverallWidth)")
                }
            }
            "SLAB" -> {
                if (!stepContent.contains("OverallHeight") || !stepContent.contains("Length") || !stepContent.contains("Width")) {
                    errors.add("IfcSlab missing required attributes (OverallHeight, Length, Width)")
                }
            }
        }

        // Check that at least one element was written
        if (stepContent.contains("END-ISO-10303-21;") && stepContent.trim().length() < 50) {
            errors.add("STEP file contains no structural elements")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    private fun extractElementType(stepContent: String): String {
        return when {
            stepContent.contains("BEAM_ENTITY") -> "BEAM"
            stepContent.contains("COLUMN_ENTITY") -> "COLUMN"
            stepContent.contains("SLAB_ENTITY") -> "SLAB"
            else -> "UNKNOWN"
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )

    fun validateAndThrow(stepContent: String) {
        val result = validate(stepContent)
        if (!result.isValid) {
            throw IllegalArgumentException(
                "IFC Validator failures:\n" +
                    result.errors.joinToString("\n ")
            )
        }
    }
}