# InputGuard Coverage Audit

## Rule 1.4 Compliance: "No Silent Defaults — Loud Failures Not Silent Zeros"

> **Every engine entry point MUST validate inputs with InputGuard. Zero/negative/NaN inputs must throw loud exceptions, not produce silent wrong results.**

---

## Coverage Matrix

| Engine / Function | Entry Point | InputGuard Status | Missing Guards |
|------------------|-------------|-------------------|----------------|
| **CalculatorEngine.designBeam** | ❌ STUB | ❌ NONE | fcu, fy, width, height, span, deadLoad, liveLoad, preferredDiameter |
| **CalculatorEngine.designColumn** | ❌ STUB | ❌ NONE | fcu, fy, width, depth, pu, mx, my, clearHeight |
| **CalculatorEngine.designSlab** | ⚠️ PARTIAL | ❌ NONE | fcu, fy, lx, ly, ts, deadLoad, liveLoad |
| **CalculatorEngine.calculateFooting** | ❌ STUB | ❌ NONE | fcu, fy, soil, colB, colT, p |
| **CalculatorEngine.designStaircase** | ⚠️ PARTIAL | ❌ NONE | fcu, fy, span, riser, tread, deadLoad, liveLoad |
| **CalculatorEngine.designTank** | ❌ STUB | ❌ NONE | fcu, fy, height, capacity |
| **CalculatorEngine.designRetainingWall** | ❌ STUB | ❌ NONE | fcu, fy, height, soilDensity, frictionAngle |
| **CalculatorEngine.calculateSeismicLoads** | ❌ STUB | ❌ NONE | zone, importance, soilType, height, totalWeight |
| **CalculatorEngine.designFlatSlab** | **MISSING** | **N/A** | N/A |
| **CalculatorEngine.designPileFoundation** | **MISSING** | **N/A** | N/A |
| **CalculatorEngine.designShearWall** | **MISSING** | **N/A** | N/A |
| **CalculatorEngine.designFrame** | **MISSING** | **N/A** | N/A |
| **CalculatorEngine.designSeismic** | **MISSING** | **N/A** | N/A |
| **CalculatorEngine.designSteelWarehouse** | ✅ COMPLETE | ❌ NONE | inputs validation missing |
| **CalculatorEngine.calculateSteelWarehousePro** | ✅ COMPLETE | ❌ NONE | inputs validation missing |
| **CalculatorEngine.calculateSteelMember** | ✅ COMPLETE | ❌ NONE | inputs validation missing |
| **CalculatorEngine.calculateStrapFooting** | ⚠️ PARTIAL | ❌ NONE | col1Load, col2Load, distance, soil, fcu, fy |
| **CalculatorEngine.calculateSteelWarehouse** | ✅ COMPLETE | ❌ NONE | inputs validation missing |

---

## Domain Engine Coverage (via CalculationFactory)

### ECP Engines (`app/src/main/java/com/civileg/app/domain/calculations/ecp/`)

| Engine | InputGuard at Entry | Methods Guarded |
|--------|---------------------|-----------------|
| ECPBeam.kt | ✅ `calculateFlexureReinforcement` | fcu, fy, width, effectiveDepth, totalDepth |
| ECPColumn.kt | ✅ `calculateFlexureReinforcement` | fcu, fy, width, depth |
| ECPSlab.kt | ✅ `designOneWaySlab` | fcu, fy, slabThickness, clearSpan |
| ECPSlab.kt | ✅ `designTwoWaySlab` | fcu, fy, slabThickness, shortSpan, longSpan |
| ECPFlatSlab.kt | ✅ `designFlatSlab` | fcu, fy, lx, ly, slabThickness, clearCover, liveLoad |
| ECPFooting.kt | ✅ `designIsolatedFooting` | fcu, fy, columnWidth, columnDepth, axialLoad, SBC |
| ECPPileFoundation.kt | ✅ `designPileFoundation` | pileDiameter, pileLength, safetyFactor, fcu, fy |
| ECPRetainingWall.kt | ✅ `designRetainingWall` | wallHeight, stemThickness, baseWidth, baseThickness, fcu, fy |
| ECPTank.kt | ✅ `designTank` | length, width, height, fcu, fy, waterDepth |
| ECPStaircase.kt | ✅ `designStaircase` | fcu, fy, span, totalRise, waistThickness |
| ECPShearWall.kt | ✅ `designWall` | fcu, fy, wallLength, wallThickness, wallHeight |
| ECPSeismic.kt | ✅ `designSeismic` | zone, soilType, totalWeight, height |
| ECPStrapFooting.kt | ⚠️ Wrapper only | Delegates to StrapFootingDesignEngine |

---

### ACI Engines (`app/src/main/java/com/civileg/app/domain/calculations/aci/`)

| Engine | InputGuard at Entry | Methods Guarded |
|--------|---------------------|-----------------|
| ACIBeam.kt | ✅ `calculateFlexureReinforcement` | fcu, fy, width, effectiveDepth, totalDepth |
| ACIColumn.kt | ✅ `calculateFlexureReinforcement` | fcu, fy, width, depth |
| ACISlab.kt | ✅ `designOneWaySlab` | fcu, fy, slabThickness, clearSpan |
| ACISlab.kt | ✅ `designTwoWaySlab` | fcu, fy, slabThickness, shortSpan, longSpan |
| ACIFlatSlab.kt | ✅ `designFlatSlab` | fcu, fy, lx, ly, slabThickness, clearCover, liveLoad |
| ACIFooting.kt | ✅ `designIsolatedFooting` | fcu, fy, columnWidth, columnDepth, axialLoad, SBC |
| ACIPileFoundation.kt | ❌ **MISSING FILE** | N/A |
| ACIRetainingWall.kt | ✅ `designRetainingWall` | fcu, fy, wallHeight, stemThickness, baseWidth |
| ACITank.kt | ✅ `designTank` | length, width, height, fcu, fy, waterDepth |
| ACIStaircase.kt | ✅ `designStaircase` | fcu, fy, span, totalRise, waistThickness |
| ACIShearWall.kt | ✅ `designWall` | fcu, fy, wallLength, wallThickness, wallHeight |
| ACISeismic.kt | ✅ `designSeismic` | zone, soilType, totalWeight, height |
| ACIStrapFooting.kt | ⚠️ Wrapper only | Delegates to StrapFootingDesignEngine |

---

### SBC Engines (`app/src/main/java/com/civileg/app/domain/calculations/sbc/`)

| Engine | InputGuard at Entry | Methods Guarded |
|--------|---------------------|-----------------|
| SBCBeam.kt | ✅ `calculateFlexureReinforcement` | fcu, fy, width, effectiveDepth, totalDepth |
| SBCColumn.kt | ✅ `calculateFlexureReinforcement` | fcu, fy, width, depth |
| SBCSlab.kt | ✅ `designOneWaySlab` | fcu, fy, slabThickness, clearSpan |
| SBCSlab.kt | ✅ `designTwoWaySlab` | fcu, fy, slabThickness, shortSpan, longSpan |
| SBCFlatSlab.kt | ✅ `designFlatSlab` | fcu, fy, lx, ly, slabThickness, clearCover, liveLoad |
| SBCFooting.kt | ✅ `designIsolatedFooting` | fcu, fy, columnWidth, columnDepth, axialLoad, SBC |
| SBCPileFoundation.kt | ✅ `designPileFoundation` | pileDiameter, pileLength, safetyFactor, fcu, fy |
| SBCRetainingWall.kt | ✅ `designRetainingWall` | fcu, fy, wallHeight, stemThickness, baseWidth |
| SBCTank.kt | ✅ `designTank` | length, width, height, fcu, fy, waterDepth |
| SBCStaircase.kt | ✅ `designStaircase` | fcu, fy, span, totalRise, waistThickness |
| SBCShearWall.kt | ✅ `designWall` | fcu, fy, wallLength, wallThickness, wallHeight |
| SBCSeismic.kt | ✅ `designSeismic` | zone, soilType, totalWeight, height |
| SBCStrapFooting.kt | ⚠️ Wrapper only | Delegates to StrapFootingDesignEngine |

---

## Missing InputGuard — Critical Gaps

### CalculatorEngine (18 functions, 0 guarded)
| Function | Priority | Reason |
|----------|----------|--------|
| designBeam | CRITICAL | STUB - used by BeamScreen |
| designColumn | CRITICAL | STUB - used by ColumnScreen |
| designSlab | HIGH | PARTIAL - used by SlabScreen |
| calculateFooting | CRITICAL | STUB - used by FootingScreen |
| designStaircase | HIGH | PARTIAL - used by StairScreen |
| designTank | CRITICAL | STUB - used by TankScreen |
| designRetainingWall | CRITICAL | STUB - used by RetainingWallScreen |
| calculateSeismicLoads | CRITICAL | STUB - used by SeismicScreen |
| designFlatSlab | CRITICAL | MISSING - used by FlatSlabScreen |
| designPileFoundation | CRITICAL | MISSING - used by PileFoundationScreen |
| designShearWall | CRITICAL | MISSING - used by ShearWallScreen |
| designFrame | CRITICAL | MISSING - used by FrameAnalysisScreen |
| designSeismic | CRITICAL | MISSING - used by SeismicScreen |
| designSteelWarehouse | HIGH | Used by SteelDesignScreen |
| calculateSteelWarehousePro | HIGH | Used by SteelDesignScreen |
| calculateSteelMember | HIGH | Used by SteelDesignScreen |
| calculateStrapFooting | HIGH | PARTIAL - used by StrapFootingScreen |
| calculateSteelWarehouse | HIGH | Used by SteelDesignScreen |

### Missing Domain Engine Files
| File | Priority | Screen Affected |
|------|----------|-----------------|
| `app/src/main/java/com/civileg/app/domain/calculations/aci/ACIPileFoundation.kt` | CRITICAL | PileFoundationScreen (ACI) |
| `app/src/main/java/com/civileg/app/domain/calculations/sbc/SBCFlatSlab.kt` | CRITICAL | FlatSlabScreen (SBC) |
| `app/src/main/java/com/civileg/app/domain/calculations/aci/ACIDoublyReinforcedBeam.kt` | HIGH | Advanced beams |
| `app/src/main/java/com/civileg/app/domain/calculations/sbc/SBCDoublyReinforcedBeam.kt` | HIGH | Advanced beams |
| `app/src/main/java/com/civileg/app/domain/calculations/aci/ACIWaffleSlab.kt` | HIGH | Waffle slabs |
| `app/src/main/java/com/civileg/app/domain/calculations/sbc/SBCWaffleSlab.kt` | HIGH | Waffle slabs |

---

## InputGuard API — Required Implementation

```kotlin
// app/src/main/java/com/civileg/app/domain/calculations/InputGuard.kt
package com.civileg.app.domain.calculations

object InputGuard {
    // Positive numbers (> 0) — throws IllegalArgumentException with bilingual message
    fun positive(name: String, value: Double): Double {
        require(value > 0) { "Input '$name' must be positive, got $value / يجب أن يكون '$name' موجباً، القيمة: $value" }
        return value
    }
    
    fun positive(name: String, value: Int): Int {
        require(value > 0) { "Input '$name' must be positive, got $value / يجب أن يكون '$name' موجباً، القيمة: $value" }
        return value
    }
    
    fun positive(name: String, value: Float): Float {
        require(value > 0f) { "Input '$name' must be positive, got $value / يجب أن يكون '$name' موجباً، القيمة: $value" }
        return value
    }
    
    // Non-negative (>= 0)
    fun nonNegative(name: String, value: Double): Double {
        require(value >= 0) { "Input '$name' must be non-negative, got $value / يجب أن يكون '$name' غير سالب، القيمة: $value" }
        return value
    }
    
    fun nonNegative(name: String, value: Int): Int {
        require(value >= 0) { "Input '$name' must be non-negative, got $value / يجب أن يكون '$name' غير سالب، القيمة: $value" }
        return value
    }
    
    // Range validation
    fun inRange(name: String, value: Double, min: Double, max: Double): Double {
        require(value >= min && value <= max) { "Input '$name' must be in [$min, $max], got $value / يجب أن يكون '$name' في المدى [$min, $max]، القيمة: $value" }
        return value
    }
    
    fun inRange(name: String, value: Int, min: Int, max: Int): Int {
        require(value >= min && value <= max) { "Input '$name' must be in [$min, $max], got $value / يجب أن يكون '$name' في المدى [$min, $max]، القيمة: $value" }
        return value
    }
    
    // Null checks
    fun <T> notNull(name: String, value: T?): T {
        require(value != null) { "Input '$name' must not be null / لا يمكن أن يكون '$name' معدوماً" }
        return value
    }
    
    // String validation
    fun notBlank(name: String, value: String): String {
        require(value.isNotBlank()) { "Input '$name' must not be blank / لا يمكن أن يكون '$name' فارغاً" }
        return value
    }
    
    // Collection validation
    fun <T> notEmpty(name: String, collection: Collection<T>): Collection<T> {
        require(collection.isNotEmpty()) { "Input '$name' must not be empty / لا يمكن أن تكون '$name' فارغة" }
        return collection
    }
    
    // At least one positive
    fun atLeastOne(vararg pairs: Pair<String, Double>): List<Double> {
        val positives = pairs.filter { it.second > 0 }.map { it.second }
        require(positives.isNotEmpty()) { "At least one of [${pairs.map { it.first }.joinToString()}] must be positive / يجب أن يكون واحداً على الأقل من [${pairs.map { it.first }.joinToString()}] موجباً" }
        return positives
    }
    
    // Custom validation
    fun require(condition: Boolean, message: String) {
        require(condition) { message }
    }
}
```

---

## Implementation Checklist

### Phase 1: CalculatorEngine (Week 1)
- [ ] Add InputGuard to `designBeam()` — 8 params
- [ ] Add InputGuard to `designColumn()` — 8 params
- [ ] Add InputGuard to `designSlab()` — 7 params
- [ ] Add InputGuard to `calculateFooting()` — 6 params
- [ ] Add InputGuard to `designStaircase()` — 8 params
- [ ] Add InputGuard to `designTank()` — 5 params
- [ ] Add InputGuard to `designRetainingWall()` — 6 params
- [ ] Add InputGuard to `calculateSeismicLoads()` — 5 params
- [ ] Add InputGuard to `designSteelWarehouse()` — 10+ params
- [ ] Add InputGuard to `calculateSteelWarehousePro()` — 10+ params
- [ ] Add InputGuard to `calculateSteelMember()` — 4 params
- [ ] Add InputGuard to `calculateStrapFooting()` — 9 params
- [ ] Add InputGuard to `calculateSteelWarehouse()` — 10+ params
- [ ] Add InputGuard to NEW `designFlatSlab()` — 12 params
- [ ] Add InputGuard to NEW `designPileFoundation()` — 10+ params
- [ ] Add InputGuard to NEW `designShearWall()` — 7 params
- [ ] Add InputGuard to NEW `designFrame()` — 8 params
- [ ] Add InputGuard to NEW `designSeismic()` — 6 params

### Phase 2: Domain Engines (Week 2)
- [ ] Create `ACIPileFoundation.kt` with InputGuard
- [ ] Create `SBCFlatSlab.kt` with InputGuard
- [ ] Create `ACIDoublyReinforcedBeam.kt` with InputGuard
- [ ] Create `SBCDoublyReinforcedBeam.kt` with InputGuard
- [ ] Create `ACIWaffleSlab.kt` with InputGuard
- [ ] Create `SBCWaffleSlab.kt` with InputGuard
- [ ] Verify all ECP/ACI/SBC engines have InputGuard at entry

### Phase 3: CalculatorEngine Delegation (Week 3)
- [ ] Replace all STUBs with Factory delegation
- [ ] Add standard enrichment (calculationSteps, diagramsData, safetyChecks, codeNotes, warnings)
- [ ] Add CalculationValidator calls
- [ ] Add CodeReference clauses

---

## Verification Commands

```bash
# Check InputGuard usage in CalculatorEngine
grep -n "InputGuard" app/src/main/java/com/civileg/app/utils/CalculatorEngine.kt

# Check InputGuard usage in all domain engines
grep -r "InputGuard" app/src/main/java/com/civileg/app/domain/calculations/

# Verify no STUBs remain in CalculatorEngine
grep -n "isSafe = true.*code = code.*return.*Result" app/src/main/java/com/civileg/app/utils/CalculatorEngine.kt

# Run tests
./gradlew testDebugUnitTest --tests "*InputGuard*"
```