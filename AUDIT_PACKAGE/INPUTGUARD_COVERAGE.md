# InputGuard Coverage Audit

## Rule 1.4 Compliance: "No Silent Defaults — Loud Failures Not Silent Zeros"

> **Every engine entry point MUST validate inputs with InputGuard. Zero/negative/NaN inputs must throw loud exceptions, not produce silent wrong results.**

---

## Coverage Matrix

> **The `CalculatorEngine` rows below were re-verified from source on 2026-10-18** and match the
> **Verified status** table. The **domain-engine** rows that follow still reflect the original
> audit and have not been re-scanned.

| Engine / Function | Entry Point | InputGuard Status | Missing Guards |
|------------------|-------------|-------------------|----------------|
| **CalculatorEngine.designBeam** | ✅ | ✅ 7 guards | — see Verified status table |
| **CalculatorEngine.designColumn** | ✅ | ✅ 8 guards | fcu, fy, width, depth, pu, mx, my, clearHeight |
| **CalculatorEngine.designSlab** | ✅ | ✅ 5 guards | — see Verified status table |
| **CalculatorEngine.calculateFooting** | ✅ | ✅ 6 guards | — see Verified status table |
| **CalculatorEngine.designStaircase** | ✅ | ✅ 5 guards | — see Verified status table |
| **CalculatorEngine.designTank** | ✅ | ✅ 4 guards | — see Verified status table |
| **CalculatorEngine.designRetainingWall** | ✅ | ✅ 5 guards | — see Verified status table |
| **CalculatorEngine.calculateSeismicLoads** | ✅ COMPLETE | ✅ 6 guards | height, totalWeight, zone, soilType, importance, reductionFactor |
| **CalculatorEngine.designFlatSlab** | ✅ | ✅ 5 guards | — see Verified status table |
| **CalculatorEngine.designPileFoundation** | ✅ | ✅ 4 guards | — see Verified status table |
| **CalculatorEngine.designShearWall** | ✅ | ✅ 5 guards | — see Verified status table |
| **CalculatorEngine.designFrame** | **DOES NOT EXIST** | n/a | stale row |
| **CalculatorEngine.designSeismic** | **DOES NOT EXIST** | n/a | stale row |
| **CalculatorEngine.designSteelWarehouse** | ✅ COMPLETE | ✅ 9 guards | span, eaveHeight, baySpacing, length, ridgeHeight, slope, deadLoad, liveLoad, purlinSpacing |
| **CalculatorEngine.calculateSteelWarehousePro** | ✅ COMPLETE | ✅ inherited | calls `designSteelWarehouse` on line 1 of its body → all fields it reads are validated before first read |
| **CalculatorEngine.calculateSteelMember** | ✅ COMPLETE | ✅ 4 guards | notNull(section, memberType, inputs, code) |
| **CalculatorEngine.calculateStrapFooting** | ✅ COMPLETE | ✅ 11 guards | col1Load, col2Load, distance, col1W/col1D/col2W/col2D, soil, fcu, fy, strapWidth |
| **CalculatorEngine.calculateSteelWarehouse** | **DOES NOT EXIST** | n/a | no such function — only `designSteelWarehouse` + `calculateSteelWarehousePro` (stale row) |

### Verified status (scanned from source, 2026-10-18)

Guard counts are counted mechanically from `CalculatorEngine.kt`; line numbers are of the
`fun` declaration. **✔ = entry validates its numeric inputs**; **⚠ = partial**.

| Function | Line | InputGuard calls | Status |
|----------|------|------------------|--------|
| `calculateSteelMember` | 375 | 4 | ✔ notNull(section, memberType, inputs, code) |
| `designSteelWarehouse` | 636 | 9 | ✔ span, eaveHeight, baySpacing, length, ridgeHeight, slope, deadLoad, liveLoad, purlinSpacing |
| `calculateSteelWarehousePro` | 727 | 0 (inherited) | ✔ delegates to `designSteelWarehouse` first — documented in KDoc |
| `designColumn` | 755 | 8 | ✔ fcu, fy, width, depth, pu, mx, my, clearHeight |
| `designBeam` | 819 | 7 | ✔ |
| `designSlab` | 938 | 5 | ✔ |
| `calculateFooting` | 1067 | 6 | ✔ |
| `calculateStrapFooting` | 1178 | 11 | ✔ col1Load, col2Load, distance, col1W/col1D/col2W/col2D, soil, fcu, fy, strapWidth (`preferredDiameter` unused by the function, deliberately unguarded) |
| `designStaircase` | 1248 | 5 | ✔ |
| `designTank` | 1335 | 4 | ✔ |
| `designRetainingWall` | 1390 | 5 | ✔ |
| `calculateSeismicLoads` | 1450 | 6 | ✔ height, totalWeight, zone (0.00–0.40), soilType (notBlank), importance, reductionFactor |
| `designShearWall` | 1544 | 5 | ✔ |
| `designPileFoundation` | 1560 | 4 | ✔ |
| `designFlatSlab` | 1575 | 5 | ✔ |
| `designFrame` | — | — | **does not exist** (stale row) |
| `designSeismic` | — | — | **does not exist** (stale row) |

**Remaining gaps in `CalculatorEngine`:** none — every entry point listed above now validates the
numeric inputs it reads (`designFrame` / `designSeismic` / `calculateSteelWarehouse` do not exist).
Regression tests: `InputGuardTest`, `SteelWarehouseInputGuardTest`, `CalculatorEngineInputGuardTest`.

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

### CalculatorEngine — **CLOSED** (verified 2026-10-18)

The original assessment ("18 functions, 0 guarded") is obsolete. All 15 `CalculatorEngine` entry
points that actually exist now validate the numeric inputs they read — guard counts per function
are in the **Verified status** table above. Three of the original 18 names (`designFrame`,
`designSeismic`, `calculateSteelWarehouse`) refer to functions that do not exist.

| Group | Status |
|-------|--------|
| `designBeam`, `designColumn`, `designSlab`, `calculateFooting`, `designStaircase`, `designTank`, `designRetainingWall`, `designFlatSlab`, `designPileFoundation`, `designShearWall` | ✅ guarded |
| `calculateSeismicLoads`, `calculateStrapFooting`, `designSteelWarehouse`, `calculateSteelWarehousePro`, `calculateSteelMember` | ✅ guarded (completed 2026-10-18) |
| `designFrame`, `designSeismic`, `calculateSteelWarehouse` | N/A — functions do not exist |

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
- [x] Add InputGuard to `designBeam()` — 8 params (7 guards verified)
- [x] Add InputGuard to `designColumn()` — 8 params (8 guards verified)
- [x] Add InputGuard to `designSlab()` — 7 params (5 guards verified)
- [x] Add InputGuard to `calculateFooting()` — 6 params (6 guards verified)
- [x] Add InputGuard to `designStaircase()` — 8 params (5 guards verified)
- [x] Add InputGuard to `designTank()` — 5 params (4 guards verified)
- [x] Add InputGuard to `designRetainingWall()` — 6 params (5 guards verified)
- [x] Add InputGuard to `calculateSeismicLoads()` — 5 params (6 guards verified; zone/soilType/importance/reductionFactor added 2026-10-18)
- [x] Add InputGuard to `designSteelWarehouse()` — 10+ params (9 guards verified; added 2026-10-18)
- [x] Add InputGuard to `calculateSteelWarehousePro()` — 10+ params (inherits from `designSteelWarehouse`, documented in KDoc)
- [x] Add InputGuard to `calculateSteelMember()` — 4 params (4 guards verified)
- [x] Add InputGuard to `calculateStrapFooting()` — 9 params (11 guards verified; added 2026-10-18)
- [x] ~~Add InputGuard to `calculateSteelWarehouse()`~~ — **function does not exist** (N/A)
- [x] Add InputGuard to NEW `designFlatSlab()` — 12 params (5 guards verified)
- [x] Add InputGuard to NEW `designPileFoundation()` — 10+ params (4 guards verified)
- [x] Add InputGuard to NEW `designShearWall()` — 7 params (5 guards verified)
- [x] ~~Add InputGuard to NEW `designFrame()`~~ — **function does not exist** (N/A)
- [x] ~~Add InputGuard to NEW `designSeismic()`~~ — **function does not exist** (N/A)

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