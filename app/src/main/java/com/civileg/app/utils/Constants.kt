package com.civileg.app.utils

object Constants {
    
    // Material Properties
    const val DEFAULT_CONCRETE_STRENGTH = 25.0 // MPa
    const val DEFAULT_STEEL_STRENGTH = 360.0 // MPa (B400B) or 450 (B500B)
    const val CONCRETE_DENSITY = 25.0 // kN/m³
    const val STEEL_DENSITY = 78.5 // kN/m³
    
    // Safety Factors (Egyptian Code)
    const val FOS_CONCRETE = 1.5
    const val FOS_STEEL = 1.15
    const val FOS_OVERTURNING = 1.5
    const val FOS_SLIDING = 1.5
    
    // Cover Requirements (mm)
    const val COVER_SLAB = 20
    const val COVER_BEAM = 25
    const val COVER_COLUMN = 30
    const val COVER_FOOTING = 50
    const val COVER_WATER_RETAINING = 35
    
    // Deflection Limits (span/depth ratios)
    const val BASIC_SPAN_DEPTH_RATIO = 20.0 // For cantilever
    const val CONTINUOUS_SPAN_MODIFIER = 1.3
    
    // Maximum crack widths (mm)
    const val CRACK_WIDTH_NORMAL = 0.3
    const val CRACK_WIDTH_AGGRESSIVE = 0.2
    const val CRACK_WIDTH_WATER_RETAINING = 0.1
}