package com.civileg.app.domain.validators

object ColumnInputValidator {
    fun validate(
        width: Double, 
        depth: Double, 
        fcu: Double, 
        fy: Double,
        axialLoad: Double, 
        minCover: Double = 40.0
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        
        if (width < 200 || width > 2000) 
            issues.add(ValidationIssue.Warning("Column width should be 200-2000 mm"))
        
        if (depth < 200 || depth > 2000) 
            issues.add(ValidationIssue.Warning("Column depth should be 200-2000 mm"))

        if (fcu < 20 || fcu > 70) 
            issues.add(ValidationIssue.Error("Concrete strength out of ECP range (20-70 MPa)"))
        
        if (axialLoad < 0) 
            issues.add(ValidationIssue.Error("Axial load cannot be negative"))
            
        // Initial reinforcement ratio check
        val grossArea = width * depth
        val minSteel = 0.008 * grossArea
        val maxSteel = 0.08 * grossArea
        
        if (fy < 240 || fy > 500)
            issues.add(ValidationIssue.Warning("Steel yield strength usually between 240-500 MPa"))
        
        return issues
    }
}

sealed class ValidationIssue {
    data class Error(val message: String): ValidationIssue()
    data class Warning(val message: String): ValidationIssue()
}
