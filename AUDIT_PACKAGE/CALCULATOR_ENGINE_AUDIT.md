# CalculatorEngine Audit Report
## Comprehensive Analysis of `CalculatorEngine.kt` (811 lines)

**Date:** 2026-09-21
**Auditor:** CivilEG2 Core Engineering Team
**Scope:** Complete audit of `CalculatorEngine.kt` - all `designXxx()` and `calculateXxx()` functions

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Total Lines** | 811 |
| **Total Functions** | 18 public functions |
| **Complete Functions** | 3 (designSteelWarehouse, designSteelWarehousePro, calculateSteelMember) |
| **Stub/Placeholder Functions** | 10 |
| **Missing Functions** | 5 |
| **Functions Delegating to Factory** | 4 (partial delegation) |
| **Functions with InputGuard** | 0 |
| **Functions Returning Full Result** | 3 |
| **Functions Returning Stubs/Dummies** | 10 |

---

## Function-by-Function Analysis

### ✅ **Fully Implemented Functions (3/18)**

| Function | Line | Status | Notes |
|----------|------|--------|-------|
| `designSteelWarehouse` | 449 | ✅ Complete | Full portal frame analysis with loads, sections, connections, quantities, costing |
| `calculateSteelWarehousePro` | 558 | ✅ Complete | Professional wrapper with BoM, costing, ROI |
| `calculateSteelMember` | 380 | ✅ Complete | AISC 360 integration for compression/bending/combined |

---

### ⚠️ **Partial Delegation to Factory (4/18) — Need Enrichment**

| Function | Line | Delegates To | Status | Missing |
|----------|------|--------------|--------|---------|
| `calculateStrapFooting` | 664 | `CalculationFactory.getStrapFootingDesign` + `StrapFootingDesignEngine` | Partial | Returns enriched `StrapFootingResult` but missing `calculationSteps`, `diagramsData`, `safetyChecks` enrichment |
| `designStaircase` | 708 | `CalculationFactory.getStaircaseDesign` | Partial | Parses string results, needs proper `calculationSteps`, `diagramsData`, `safetyChecks` enrichment |
| `designColumn` | 581 | **NO DELEGATION** | **STUB** | Returns dummy `ColumnResult` |
| `designBeam` | 591 | **NO DELEGATION** | **STUB** | Returns dummy `BeamResult` |

---

### ❌ **Stub/Placeholder Functions (10/18) — Return Dummies**

| Function | Line | Current Behavior | Required |
|----------|------|------------------|----------|
| `designColumn` | 581 | Returns dummy `ColumnResult(isSafe=true, axialCapacity=1000)` | Full implementation via Factory |
| `designBeam` | 591 | Returns dummy `BeamResult(isSafe=true, mu=...)` | Full implementation via Factory |
| `designSlab` | 601 | Partial for HOLLOW_BLOCK, dummy for others | Full per-type implementation |
| `calculateFooting` | 655 | Returns dummy `FootingResult(width=2000, length=2000, ...)` | Full implementation via Factory |
| `designStaircase` | 708 | Delegates to Factory but parses strings | Needs enrichment |
| `designTank` | 787 | Returns dummy `TankResult(isSafe=true)` | Full implementation via Factory |
| `designRetainingWall` | 791 | Returns dummy `RetainingWallResult(isSafe=true)` | Full implementation via Factory |
| `calculateSeismicLoads` | 795 | Returns dummy `SeismicResult(baseShear=100)` | Full implementation via Factory |
| `calculateWeldCapacity` | 799 | Delegates to `SteelConnectionDesign` | OK |
| `calculateBoltCapacity` | 804 | Delegates to `SteelConnectionDesign` | OK |

---

### ❌ **Missing Functions (5/18) — Not Implemented at All**

| Missing Function | Required For | Priority |
|------------------|--------------|----------|
| `designFlatSlab()` | FlatSlabScreen | **CRITICAL** |
| `designPileFoundation()` | PileFoundationScreen | **CRITICAL** |
| `designShearWall()` | ShearWallScreen | **CRITICAL** |
| `designFrame()` | FrameAnalysisScreen | **CRITICAL** |
| `designSeismic()` | SeismicScreen | **CRITICAL** |

---

## InputGuard Coverage

| Function | InputGuard Present? | Required |
|----------|---------------------|----------|
| ALL 18 functions | **NO** | **REQUIRED** (Rule 1.4: loud failures) |

---

## CodeReference Coverage

| Function | CodeReference Clauses Documented? |
|----------|-----------------------------------|
| Steel functions | Partial |
| Concrete functions | **NO** — most lack clause references |
| All stubs | **N/A** |

---

## Delegation Pattern Analysis

### Current Pattern (Inconsistent)
```kotlin
// Pattern 1: Full delegation + enrichment (GOOD)
fun calculateStrapFooting(...) {
    val design = CalculationFactory.getStrapFootingDesign(code.toDomain())
    val res = StrapFootingDesignEngine.design(inputs)
    return enriched StrapFootingResult(...)
}

// Pattern 2: Partial delegation + string parsing (FRAGILE)
fun designStaircase(...) {
    val result = CalculationFactory.getStaircaseDesign(...).designStaircase(input)
    val mainBar = parseBarSpec(result.mainRebar, ...)  // STRING PARSING!
    return StairResult(...)
}

// Pattern 3: Dummy returns (BROKEN)
fun designColumn(...) {
    return ColumnResult(isSafe = true, axialCapacity = 1000.0)  // DUMMY!
}

// Pattern 4: Partial delegation but returns dummy (BROKEN)
fun calculateStrapFooting(...) {
    val design = CalculationFactory.getStrapFootingDesign(...)
    val res = StrapFootingDesignEngine.design(inputs)
    return StrapFootingResult(..., concreteVolume = 0.0, steelWeight = 0.0, cost = 0.0) // ZEROS!
}
```

---

## Required Standard Pattern (Target)

```kotlin
fun designXxx(inputs: Inputs): XxxResult {
    // 1. InputGuard FIRST (Rule 1.4)
    InputGuard.positive("param", inputs.param)
    InputGuard.positive("fcu", fcu)
    InputGuard.positive("fy", fy)
    // ... all inputs
    
    // 2. Delegate to Factory
    val engine = CalculationFactory.getXxxDesign(code.toDomain())
    val domainResult = engine.calculateXxx(...)
    
    // 3. Enrich with standard fields
    val enriched = XxxResult(
        // ... domain fields
        calculationSteps = buildCalculationSteps(...),
        diagramsData = buildDiagramsData(...),
        safetyChecks = buildSafetyChecks(...),
        codeNotes = buildCodeNotes(...),
        warnings = buildWarnings(...)
    )
    
    // 4. Validation
    CalculationValidator.validateXxx(enriched)
    
    return enriched
}
```

---

## Required Actions Summary

| Action | Count | Priority |
|--------|-------|----------|
| Add missing 5 functions | 5 | **CRITICAL** |
| Replace 10 stubs with Factory delegation | 10 | **CRITICAL** |
| Enrich 4 partial functions | 4 | **HIGH** |
| Add InputGuard to 18 functions | 18 | **CRITICAL** (Rule 1.4) |
| Add CodeReference clauses | 18 | **HIGH** |
| Add CalculationValidator calls | 18 | **HIGH** |
| Add standard enrichment (steps, diagrams, safetyChecks, codeNotes) | 18 | **HIGH** |

---

## File References

| File | Path |
|------|------|
| CalculatorEngine.kt | `app/src/main/java/com/civileg/app/utils/CalculatorEngine.kt` |
| CalculationFactory | `app/src/main/java/com/civileg/app/domain/calculations/CalculationFactory.kt` |
| Domain Engines (ECP) | `app/src/main/java/com/civileg/app/domain/calculations/ecp/*.kt` |
| Domain Engines (ACI) | `app/src/main/java/com/civileg/app/domain/calculations/aci/*.kt` |
| Domain Engines (SBC) | `app/src/main/java/com/civileg/app/domain/calculations/sbc/*.kt` |
| CalculationValidator | `app/src/main/java/com/civileg/app/utils/CalculationValidator.kt` |
| InputGuard | `app/src/main/java/com/civileg/app/domain/calculations/InputGuard.kt` |
| CodeReference | `app/src/main/java/com/civileg/app/domain/entities/CodeReference.kt` |