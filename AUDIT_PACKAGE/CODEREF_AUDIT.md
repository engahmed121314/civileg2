# CodeReference Coverage Audit

## Executive Summary

| Metric | Value |
|--------|-------|
| **Total Engine Files** | 45 (ECP: 21, ACI: 15, SBC: 15) |
| **Engines Using CodeReference** | ~8/45 (~18%) |
| **CodeReference Keys Defined** | 93 (ECP: 30, ACI: 30, SBC: 30) |
| **Keys Actually Used** | ~15/93 (~16%) |
| **Engines with Zero References** | ~37/45 (~82%) |

---

## CodeReference Keys Inventory

### ECP 203 (30 keys)
| Category | Keys | Used? |
|----------|------|-------|
| **Column** | COLUMN_AXIAL, COLUMN_REINFORCEMENT_MIN, COLUMN_REINFORCEMENT_MAX, COLUMN_TIES, COLUMN_COVER | ❌ No |
| **Beam** | BEAM_FLEXURE, BEAM_SHEAR, BEAM_REINFORCEMENT_MIN, BEAM_REINFORCEMENT_MAX, BEAM_DEVELOPMENT_LENGTH, BEAM_DOUBLY_REINFORCED | ✅ Yes (BEAM_FLEXURE, BEAM_SHEAR, BEAM_REINFORCEMENT_MIN, BEAM_REINFORCEMENT_MAX) |
| **Slab** | SLAB_ONE_WAY, SLAB_TWO_WAY, SLAB_DEFLECTION | ✅ Yes (SLAB_ONE_WAY) |
| **Footing** | FOOTING_BEARING, FOOTING_SHEAR_PUNCHING, FOOTING_REINFORCEMENT | ❌ No |
| **Seismic** | SEISMIC_BASE_SHEAR, SEISMIC_RESPONSE_SPECTRUM | ❌ No |

### ACI 318-19 (30 keys)
| Category | Keys | Used? |
|----------|------|-------|
| **Column** | COLUMN_AXIAL, COLUMN_REINFORCEMENT_MIN, COLUMN_REINFORCEMENT_MAX, COLUMN_TIES, COLUMN_COVER | ❌ No |
| **Beam** | BEAM_FLEXURE, BEAM_SHEAR, BEAM_REINFORCEMENT_MIN, BEAM_REINFORCEMENT_MAX, BEAM_DEVELOPMENT_LENGTH, BEAM_DOUBLY_REINFORCED | ✅ Yes (BEAM_FLEXURE, BEAM_SHEAR, BEAM_REINFORCEMENT_MIN, BEAM_REINFORCEMENT_MAX) |
| **Slab** | SLAB_ONE_WAY, SLAB_TWO_WAY, SLAB_DEFLECTION | ❌ No |
| **Footing** | FOOTING_BEARING, FOOTING_SHEAR_PUNCHING, FOOTING_REINFORCEMENT | ❌ No |
| **Seismic** | SEISMIC_BASE_SHEAR, SEISMIC_RESPONSE_SPECTRUM | ❌ No |

### SBC 304 (30 keys)
| Category | Keys | Used? |
|----------|------|-------|
| **Column** | COLUMN_AXIAL, COLUMN_REINFORCEMENT_MIN, COLUMN_REINFORCEMENT_MAX, COLUMN_TIES, COLUMN_COVER | ❌ No |
| **Beam** | BEAM_FLEXURE, BEAM_SHEAR, BEAM_REINFORCEMENT_MIN, BEAM_REINFORCEMENT_MAX, BEAM_DEVELOPMENT_LENGTH, BEAM_DOUBLY_REINFORCED | ✅ Yes (BEAM_FLEXURE, BEAM_SHEAR) |
| **Slab** | SLAB_ONE_WAY, SLAB_TWO_WAY, SLAB_DEFLECTION | ❌ No |
| **Footing** | FOOTING_BEARING, FOOTING_SHEAR_PUNCHING, FOOTING_REINFORCEMENT | ❌ No |
| **Seismic** | SEISMIC_BASE_SHEAR, SEISMIC_RESPONSE_SPECTRUM | ❌ No |

---

## Engine-by-Engine Usage

### ECP Engines (21 files)

| Engine File | CodeReference Usage | Notes |
|-------------|---------------------|-------|
| `ECPBeam.kt` | ✅ **GOOD** | 7 references: BEAM_FLEXURE (3x), BEAM_SHEAR, BEAM_REINFORCEMENT_MIN (2x), BEAM_REINFORCEMENT_MAX |
| `ECPColumn.kt` | ❌ **NONE** | 0 references |
| `ECPSlab.kt` | ⚠️ **MINIMAL** | 1 reference: SLAB_ONE_WAY |
| `ECPFlatSlab.kt` | ❌ NONE | 0 references |
| `ECPFooting.kt` | ❌ NONE | 0 references |
| `ECPCombinedFooting.kt` | ❌ NONE | 0 references |
| `ECPStrapFooting.kt` | ❌ NONE | 0 references |
| `ECPStaircase.kt` | ❌ NONE | 0 references |
| `ECPTank.kt` | ❌ NONE | 0 references |
| `ECPRetainingWall.kt` | ❌ NONE | 0 references |
| `ECPShearWall.kt` | ❌ NONE | 0 references |
| `ECPSeismic.kt` | ❌ NONE | 0 references |
| `ECPPileFoundation.kt` | ❌ NONE | 0 references |
| `ECPHordiWaffleSlab.kt` | ❌ NONE | 0 references |
| `ECPHordiSlabWrapper.kt` | ❌ NONE | 0 references |
| `ECPDoublyReinforcedBeam.kt` | ❌ NONE | 0 references |
| `ECPAdvancedBeam.kt` | ❓ UNKNOWN | Need check |
| `ECPAdvancedColumn.kt` | ❓ UNKNOWN | Need check |
| `ECPAdvancedSlab.kt` | ❓ UNKNOWN | Need check |

### ACI Engines (15 files)

| Engine File | CodeReference Usage | Notes |
|-------------|---------------------|-------|
| `ACIBeam.kt` | ✅ **GOOD** | 6 references: BEAM_FLEXURE (3x), BEAM_SHEAR, BEAM_REINFORCEMENT_MIN, BEAM_REINFORCEMENT_MAX |
| `ACIColumn.kt` | ❌ NONE | 0 references |
| `ACISlab.kt` | ❌ NONE | 0 references |
| `ACIFlatSlab.kt` | ❌ NONE | 0 references |
| `ACIFooting.kt` | ❌ NONE | 0 references |
| `ACIStrapFooting.kt` | ❌ NONE | 0 references |
| `ACIStaircase.kt` | ❌ NONE | 0 references |
| `ACITank.kt` | ❌ NONE | 0 references |
| `ACIRetainingWall.kt` | ❌ NONE | 0 references |
| `ACIShearWall.kt` | ❌ NONE | 0 references |
| `ACISeismic.kt` | ❌ NONE | 0 references |
| `ACIPileFoundation.kt` | ❌ **MISSING FILE** | N/A |
| `ACIAdvancedBeam.kt` | ❓ UNKNOWN | Need check |
| `ACIAdvancedColumn.kt` | ❓ UNKNOWN | Need check |
| `ACIAdvancedSlab.kt` | ❓ UNKNOWN | Need check |

### SBC Engines (15 files)

| Engine File | CodeReference Usage | Notes |
|-------------|---------------------|-------|
| `SBCBeam.kt` | ✅ **GOOD** | 4 references: BEAM_FLEXURE (3x), BEAM_SHEAR |
| `SBCColumn.kt` | ❌ NONE | 0 references |
| `SBCSlab.kt` | ❌ NONE | 0 references |
| `SBCFlatSlab.kt` | ❌ NONE | 0 references |
| `SBCFooting.kt` | ❌ NONE | 0 references |
| `SBCStrapFooting.kt` | ❌ NONE | 0 references |
| `SBCStaircase.kt` | ❌ NONE | 0 references |
| `SBCTank.kt` | ❌ NONE | 0 references |
| `SBCRetainingWall.kt` | ❌ NONE | 0 references |
| `SBCShearWall.kt` | ❌ NONE | 0 references |
| `SBCSeismic.kt` | ❌ NONE | 0 references |
| `SBCPileFoundation.kt` | ❌ NONE | 0 references |
| `SBCAdvancedBeam.kt` | ❓ UNKNOWN | Need check |
| `SBCAdvancedColumn.kt` | ❓ UNKNOWN | Need check |
| `SBCAdvancedSlab.kt` | ❓ UNKNOWN | Need check |

---

## Summary Statistics

| Metric | Value |
|--------|-------|
| **Total Engine Files** | 45 |
| **Files with CodeReference** | 8 (ECPBeam, ACIBeam, SBCBeam, ECPSlab) |
| **Files with Zero References** | 37 |
| **Total CodeReference Keys** | 93 |
| **Keys Actually Used** | 15 |
| **Usage Rate** | 16% |
| **Beam Engines (all 3 codes)** | ✅ Good coverage |
| **All Other Engines** | ❌ No coverage |

---

## Missing Coverage by Element Type

| Element | ECP | ACI | SBC | Priority |
|---------|-----|-----|-----|----------|
| **Beam** | ✅ Full | ✅ Full | ✅ Full | Done |
| **Column** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **Slab** | ⚠️ Partial | ❌ None | ❌ None | **HIGH** |
| **FlatSlab** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **Footing** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **Pile** | ❌ None | ❌ N/A | ❌ None | **HIGH** |
| **RetainingWall** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **Tank** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **Stair** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **ShearWall** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **Seismic** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **Footing** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **StrapFooting** | ❌ None | ❌ None | ❌ None | **MEDIUM** |
| **Stair** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **ShearWall** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **Seismic** | ❌ None | ❌ None | ❌ None | **HIGH** |
| **Frame** | ❓ Unknown | ❓ Unknown | ❓ Unknown | **HIGH** |
| **Steel** | ❓ Unknown | ❓ Unknown | ❓ Unknown | **MEDIUM** |

---

## Missing CodeReference Keys (Need to Add)

Based on the engines, these clause references are needed but not defined:

### ECP 203 - Additional Keys Needed
| Element | Missing Clauses |
|---------|-----------------|
| **Column** | COLUMN_SLENDERNESS, COLUMN_BIAXIAL, COLUMN_INTERACTION, COLUMN_CONFINEMENT, COLUMN_TIES_DETAIL |
| **Slab** | SLAB_PUNCHING, SLAB_MIN_THICKNESS, SLAB_DISTRIBUTION, SLAB_OPENINGS |
| **FlatSlab** | FLATSLAB_DDM, FLATSLAB_EFM, FLATSLAB_DROP_PANEL, FLATSLAB_COLUMN_STRIP, FLATSLAB_MIDDLE_STRIP |
| **Footing** | FOOTING_ECCENTRICITY, FOOTING_BIAXIAL, FOOTING_PUNCHING, FOOTING_SHEAR_ONE_WAY, FOOTING_SHEAR_TWO_WAY, FOOTING_COMBINED, FOOTING_STRAP, FOOTING_RAFT |
| **Pile** | PILE_SINGLE_CAPACITY, PILE_GROUP_EFFICIENCY, PILE_SETTLEMENT, PILE_LATERAL, PILE_CAP_DESIGN |
| **RetainingWall** | RW_RANKINE, RW_COULOMB, RW_OT_FS, RW_SLIDING_FS, RW_BEARING_FS, RW_STEM_DESIGN, RW_TOE_DESIGN, RW_HEEL_DESIGN |
| **Tank** | TANK_HYDROSTATIC, TANK_HOOP_STRESS, TANK_VERTICAL_STEEL, TANK_CRACK_WIDTH, TANK_UPLIFT, TANK_BASE_DESIGN |
| **Stair** | STAIR_WAIST_SLAB, STAIR_LANDING, STAIR_CRANK_BARS, STAIR_DISTRIBUTION, STAIR_DEFLECTION, STAIR_COMFORT |
| **ShearWall** | SW_BOUNDARY_ELEMENT, SW_COUPLING_BEAM, SW_OVERSTRENGTH, SW_DRIFT, SW_SHEAR_FRICTION |
| **Seismic** | SEISMIC_MODAL, SEISMIC_SPECTRUM, SEISMIC_DRIFT, SEISMIC_TORSION, SEISMIC_P_DELTA |
| **Frame** | FRAME_PORTAL, FRAME_CONTINUOUS, FRAME_DRIFT, FRAME_P_DELTA, FRAME_SEISMIC_COMBO |
| **StrapFooting** | STRAP_FOOTING_REACTION, STRAP_BEAM_DESIGN |
| **DoublyReinforced** | DOUBLY_REINFORCED_COMPRESSION, DOUBLY_REINFORCED_STRAIN_COMPATIBILITY |
| **WaffleSlab** | WAFFLE_RIB_DESIGN, WAFFLE_TOPPING, WAFFLE_VOID |
| **HordiSlab** | HORDI_RIB_DESIGN, HORDI_TOPPING |

### ACI 318 - Additional Keys Needed
| Element | Missing Clauses |
|---------|-----------------|
| **Column** | 10.5, 10.6, 10.7, 22.4, 22.5, 22.6 |
| **Slab** | 8.4, 8.5, 8.6, 8.7, 8.8, 8.10, 8.11, 8.12 |
| **FlatSlab** | 8.11, 8.12, 8.13, 8.14 |
| **Footing** | 13.3, 13.4, 13.5, 13.6, 15.4, 15.5, 15.6 |
| **Pile** | 21.1, 21.2, 21.3, 21.4, 21.5, 21.6, 21.7, 21.8, 21.9, 21.10, 21.11, 21.12 |
| **RetainingWall** | 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7, 18.8, 18.9, 18.10 |
| **Tank** | 350.1, 350.2, 350.3, 350.4, 350.5, 350.6 |
| **Stair** | 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9, 9.10 |
| **ShearWall** | 18.2, 18.3, 18.4, 18.5, 18.6, 18.7, 18.8, 18.9, 18.10, 18.11, 18.12, 18.13, 18.14 |
| **Seismic** | 18.1, 18.2, 18.3, 18.4, 18.5, 18.5.1, 18.5.2, 18.5.3, 18.5.4, 18.6, 18.7, 18.8, 18.9, 18.10, 18.11, 18.12, 18.13, 18.14 |
| **Frame** | 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7, 18.8, 18.9, 18.10 |

### SBC 304 - Additional Keys Needed
| Element | Missing Clauses |
|---------|-----------------|
| **Column** | Section 10.1-10.10 |
| **Slab** | Section 8.1-8.10 |
| **FlatSlab** | Section 8.11-8.15 |
| **Footing** | Section 15.1-15.10 |
| **Pile** | Section 16.1-16.10 |
| **RetainingWall** | Section 18.1-18.10 |
| **Tank** | Section 17.1-17.10 |
| **Stair** | Section 9.1-9.10 |
| **ShearWall** | Section 18.1-18.15 |
| **Seismic** | Section 21.1-21.10 |
| **Frame** | Section 18.1-18.10 |

---

## Action Plan

### Phase 1: Add Missing Keys to CodeReference.kt (Week 1)
- [ ] Add all missing ECP keys (~50 keys)
- [ ] Add all missing ACI keys (~50 keys) 
- [ ] Add all missing SBC keys (~50 keys)

### Phase 2: Apply CodeReference to All Engines (Week 2)
- [ ] ECP: Column, Slab, FlatSlab, Footing, Pile, RetainingWall, Tank, Stair, ShearWall, Seismic, Frame, StrapFooting, Stair, DoublyReinforced, Waffle, Hordi
- [ ] ACI: Column, Slab, FlatSlab, Footing, Pile, RetainingWall, Tank, Stair, ShearWall, Seismic, Frame, StrapFooting, Stair
- [ ] SBC: Column, Slab, FlatSlab, Footing, Pile, RetainingWall, Tank, Stair, ShearWall, Seismic, Frame, StrapFooting, Stair

### Phase 3: Add to CalculatorEngine & ViewModels (Week 3)
- [ ] CalculatorEngine methods add CodeReference to results
- [ ] ViewModels expose CodeReference in UI
- [ ] PDF reports include CodeReference in step-by-step

### Phase 4: CodeReference Verification (Week 4)
- [ ] Verify every engine has at least 1 CodeReference
- [ ] Verify clause numbers match actual code sections
- [ ] Update CodeReference.kt with verified clauses

---

## Verification Commands

```bash
# Check CodeReference usage across all engines
grep -r "CodeReference\." app/src/main/java/com/civileg/app/domain/calculations/

# Count files with zero references
for f in app/src/main/java/com/civileg/app/domain/calculations/**/*.kt; do
    if ! grep -q "CodeReference" "$f"; then
        echo "NO REF: $f"
    fi
done

# Check CodeReference key coverage
grep -c "const val" app/src/main/java/com/civileg/app/domain/entities/CodeReference.kt
```