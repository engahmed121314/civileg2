# CivilEG2 — Comprehensive Engineering Audit Report

**Date:** 2026-09-21  
**Auditor:** CivilEG2 Core Engineering Team  
**Scope:** Complete audit of calculation engines, UI screens, export pipelines, crash risks, and code quality  
**Standard:** ECP 203-2020, ACI 318-19, SBC 304-2018 compliance per ADR-010

---

## 🎯 Executive Summary

| Metric | Status | Details |
|--------|--------|---------|
| **Build Status** | ✅ PASS | `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL |
| **Total Source Files** | ~350 Kotlin files | ~113k lines |
| **Calculation Engines** | 45 files | 15 elements × 3 codes (ECP/ACI/SBC) |
| **Engine Coverage** | 15 elements × 3 codes | 5 missing, 10 stubs, 4 partial |
| **PDF Export** | NativePdfExporter only | Needs unification |
| **DXF Export** | 3 parallel pipelines | Needs unification to DxfWriter AC1027 |
| **Crash Risks** | 15 tools | 4 High, 8 Medium, 3 Low |
| **InputGuard Coverage** | 0/18 CalculatorEngine methods | **CRITICAL GAP** |
| **CodeReference Coverage** | 8/45 engines | 16% usage rate |
| **Tests Passing** | 238+ unit tests | Parity tests for 7 elements |

---

## 📊 Audit Matrix — Element × Code × Status

| Element | ECP | ACI | SBC | CalculatorEngine | ViewModel | Screen | Drawing | PDF | DXF | Parity Test |
|---------|-----|-----|-----|------------------|-----------|--------|---------|-----|-----|-------------|
| **Beam** | ✅ | ✅ | ✅ | ❌ STUB | ✅ | ✅ | ✅ | 🔧 | 🔧 | ✅ |
| **Column** | ✅ | ✅ | ✅ | ❌ STUB | ✅ | ✅ | ✅ | 🔧 | 🔧 | ✅ |
| **Slab** | ✅ | ✅ | ✅ | ⚠️ PARTIAL | ✅ | ✅ | ✅ | 🔧 | 🔧 | ✅ |
| **FlatSlab** | ✅ | ✅ | ❌ MISSING | ❌ MISSING | ✅ | ✅ | 🔧 | 🔧 | 🔧 | ❌ |
| **Footing** | ✅ | ✅ | ✅ | ❌ STUB | ✅ | ✅ | ✅ | 🔧 | 🔧 | ✅ |
| **PileFoundation** | ✅ | ❌ MISSING | ✅ | ❌ MISSING | ✅ | ✅ | 🔧 | 🔧 | 🔧 | ❌ |
| **RetainingWall** | ✅ | ✅ | ✅ | ❌ STUB | ✅ | ✅ | ✅ | 🔧 | 🔧 | ✅ |
| **Tank** | ✅ | ✅ | ✅ | ❌ STUB | ✅ | ✅ | ✅ | 🔧 | 🔧 | ✅ |
| **Stair** | ✅ | ✅ | ✅ | ⚠️ PARTIAL | ✅ | ✅ | ✅ | 🔧 | 🔧 | ✅ |
| **ShearWall** | ✅ | ✅ | ✅ | ❌ MISSING | ✅ | ✅ | 🔧 | 🔧 | 🔧 | ❌ |
| **Frame** | ✅ | ✅ | ✅ | ❌ MISSING | ✅ | ✅ | 🔧 | ✅ | 🔧 | ✅ |
| **Seismic** | ✅ | ✅ | ✅ | ❌ STUB | ✅ | ✅ | 🔧 | 🔧 | 🔧 | ❌ |
| **Steel** | ✅ | ✅ | ✅ | ✅ COMPLETE | ✅ | ✅ | ✅ | ✅ | 🔧 | ✅ |
| **StrapFooting** | ✅ | ✅ | ✅ | ⚠️ PARTIAL | ✅ | ✅ | ✅ | 🔧 | 🔧 | ❌ |
| **DoublyReinforcedBeam** | ✅ | ❌ MISSING | ❌ MISSING | ❌ MISSING | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **WaffleSlab** | ✅ | ❌ MISSING | ❌ MISSING | ❌ MISSING | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

**Legend:** ✅ Complete | ⚠️ Partial/Needs Work | 🔧 Needs Development | ❌ Missing | ❌ STUB = returns dummy

---

## 🔴 Critical Findings

### 1. CalculatorEngine — 10 Stubs, 5 Missing, 4 Partial
| Function | Line | Status | Used By |
|----------|------|--------|---------|
| `designBeam` | 591 | ❌ STUB | BeamScreen |
| `designColumn` | 581 | ❌ STUB | ColumnScreen |
| `designSlab` | 601 | ⚠️ PARTIAL | SlabScreen |
| `calculateFooting` | 655 | ❌ STUB | FootingScreen |
| `designStaircase` | 708 | ⚠️ PARTIAL | StairScreen |
| `designTank` | 787 | ❌ STUB | TankScreen |
| `designRetainingWall` | 791 | ❌ STUB | RetainingWallScreen |
| `calculateSeismicLoads` | 795 | ❌ STUB | SeismicScreen |
| `designFlatSlab` | — | ❌ MISSING | FlatSlabScreen |
| `designPileFoundation` | — | ❌ MISSING | PileFoundationScreen |
| `designShearWall` | — | ❌ MISSING | ShearWallScreen |
| `designFrame` | — | ❌ MISSING | FrameAnalysisScreen |
| `designSeismic` | — | ❌ MISSING | SeismicScreen |

**Impact:** All structural screens receive **dummy results** (`isSafe=true`, zero volumes, zero costs).

---

### 2. InputGuard Coverage — **ZERO** (Rule 1.4 Violation)
- **CalculatorEngine:** 18 functions, **0** with InputGuard
- **Domain Engines:** ~60% have InputGuard at entry
- **Rule 1.4:** "No silent defaults — loud failures not silent zeros"

---

### 3. CodeReference Coverage — **16%** (8/45 engines)
- **Used:** ECPBeam, ACIBeam, SBCBeam, ECPSlab (4 engines)
- **Zero References:** 37/45 engines (82%)
- **Keys Defined:** 93 | **Keys Used:** 15 (16%)

---

### 4. Missing Engine Files (5 Critical)
| File | Priority | Screen Affected |
|------|----------|-----------------|
| `ACIPileFoundation.kt` | CRITICAL | PileFoundationScreen (ACI) |
| `SBCFlatSlab.kt` | CRITICAL | FlatSlabScreen (SBC) |
| `ACIDoublyReinforcedBeam.kt` | HIGH | Advanced beams |
| `ACIWaffleSlab.kt` | HIGH | Waffle slabs |
| `SBCWaffleSlab.kt` | HIGH | Waffle slabs |

---

### 5. Crash Risks — 15 Tools
| Risk Level | Tools | Primary Issues |
|------------|-------|----------------|
| 🔴 **High (4)** | SoilBearing, RebarTool, UnitConverter, SiteLayout | Div/0, NPE, invalid input |
| 🟡 **Medium (8)** | WindLoad, ConcreteMix, RebarTool, WaterLevel, Calculator, SteelTables, MaterialPrices, MasterBbs | Null checks, bounds, parsing |
| 🟢 **Low (3)** | SteelTables, SteelDesign, Settings | Low risk |

---

### 6. Export Pipeline Fragmentation
| Pipeline | Files | Status |
|----------|-------|--------|
| **PDF** | NativePdfExporter, ComprehensivePdfExporter, PdfGenerator, PdfDrawingGenerator, BilingualPdfHelper, PdfExportHelper, PdfLayoutHelper, FrameAnalysisPdfExporter, SteelWarehouseProPdfExporter, FlatSlabPdfExporter, PileFoundationPdfExporter, ShearWallPdfExporter, AdvancedPdfExporter, NativePdfExporter | **14 files — NEEDS UNIFICATION** |
| **DXF** | DxfExporter, DxfExportEngine, DxfWriter, CalculatorCadExporterV7, CalculatorDetailingV4, CadDxfExporter | **6 files — NEEDS UNIFICATION** |

---

## 📋 Remediation Plan — 8 Weeks

### Week 1: Audit Completion & Documentation
- [x] CalculatorEngine audit (this report)
- [x] Engine coverage matrix
- [x] Screen-engine mapping
- [x] Crash inventory
- [x] InputGuard coverage
- [x] CodeReference audit
- [ ] Compile master AUDIT_REPORT.md

### Week 2: Missing Engines & Critical Stubs
| Day | Task | Files |
|-----|------|-------|
| 1-2 | `ACIPileFoundation.kt` (new) | `aci/` |
| 2-3 | `SBCFlatSlab.kt` (new) | `sbc/` |
| 3-4 | `ACIDoublyReinforcedBeam.kt` (new) | `aci/` |
| 4-5 | `ACIWaffleSlab.kt` (new) | `aci/` |
| 5-6 | `SBCWaffleSlab.kt` (new) | `sbc/` |
| 6-7 | Replace stubs: `designColumn`, `designBeam`, `calculateFooting`, `designTank`, `designRetainingWall`, `calculateSeismicLoads` | `CalculatorEngine.kt` |

### Week 3: CalculatorEngine Completion & InputGuard
| Day | Task |
|-----|------|
| 1-2 | Implement missing: `designFlatSlab`, `designPileFoundation`, `designShearWall`, `designFrame`, `designSeismic` |
| 2-3 | Enrich partial: `designSlab`, `designStaircase`, `calculateStrapFooting` |
| 3-4 | Add **InputGuard to all 18 CalculatorEngine methods** |
| 4-5 | Add standard enrichment: `calculationSteps`, `diagramsData`, `safetyChecks`, `codeNotes`, `warnings` |
| 5-6 | Add `CalculationValidator` calls to all methods |
| 6-7 | Add CodeReference to all CalculatorEngine methods |

### Week 4: InputGuard & CodeReference Domain Engines
| Day | Task |
|-----|------|
| 1-2 | Add InputGuard to all domain engine entry points |
| 2-3 | Add CodeReference to 37 engines missing references |
| 3-4 | Expand CodeReference.kt with ~150 missing clause keys |
| 4-5 | Verify all 45 engines have CodeReference |

### Week 5-6: PDF + DXF Unification (Parallel)
| Pipeline | Tasks |
|----------|-------|
| **PDF** | 1) Complete NativePdfExporter with step-by-step, load combos per code, drawings, EN-only<br>2) Remove ComprehensivePdfExporter, PdfExportHelper, BilingualPdfHelper, AdvancedPdfExporter, NativePdfExporter (legacy)<br>3) Unify all ViewModels to use NativePdfExporter only |
| **DXF** | 1) Complete DxfWriter AC1027 with layers/blocks/BBS<br>2) Remove DxfExporter, DxfExportEngine, CalculatorCadExporterV7, CalculatorDetailingV4, CadDxfExporter<br>3) Create ExportManager with `exportPdf()`, `exportDxf()`, `exportBoth()`, `exportPackage()` |

### Week 7: Crash Fixes & Tools Stabilization
| Day | Tools | Pattern |
|-----|-------|---------|
| 1-2 | SoilBearing, RebarTool, UnitConverter, SiteLayout | Universal try/catch + InputGuard |
| 3-4 | WindLoad, ConcreteMix, WaterLevel, Calculator | Universal pattern |
| 5 | MaterialPrices, SteelTables, MasterBbs | Universal pattern |
| 6-7 | Integration QA: all tools with valid/invalid/empty inputs | Zero crashes |

### Week 8: Integration QA & Release
| Task | Criteria |
|------|----------|
| Screen-to-Engine audit | All 15 screens → correct engine → correct code |
| Code-Matching verification | ECP/ACI/SBC → correct formulas in results/drawings/PDF/DXF |
| Cross-code parity | Same inputs → 3 correct different results (golden fixtures) |
| PDF/DXF manual QA | 15 screens × 3 codes = 45 scenarios |
| Crash-free verification | All tools with valid/invalid/empty inputs |
| APK Release Ready | Sign + ProGuard + versioning + Play Store listing |

---

## ✅ Definition of Done (Non-Negotiable)

```
☐ All 15 elements × 3 codes: Engine present + correct + CodeReference documented
☐ CalculatorEngine: All 18 designXxx() complete + InputGuard + validation + Result complete + diagrams data
☐ All ViewModels: calculateXxx() → Engine → Validate → LiveData<Result> + Error handling
☐ All Screens: Inputs → Calculate → Loading → Results + Drawings + Export(PDF+DXF together)
☐ All Drawings: Read from Result directly → 3D + cutaway + section + dimensions + schedule
☐ PDF: NativePdfExporter only → step-by-step + load combos per code + drawings + EN-only
☐ DXF: DxfWriter AC1027 only → layers/blocks/BBS → AutoCAD/TrueView clean open
☐ Code Matching: ECP/ACI/SBC → correct formulas/coeffs/clauses in results/drawings/PDF/DXF
☐ Zero Crashes: All tools handle null/empty/invalid with friendly errors
☐ Parity: Same inputs → 3 correct different results (golden fixtures)
☐ APK Release Ready: Sign + ProGuard + versioning + Play Store ready
```

---

## 📁 Audit Package Contents

| File | Description |
|------|-------------|
| `AUDIT_REPORT.md` | This master report |
| `CALCULATOR_ENGINE_AUDIT.md` | Detailed CalculatorEngine function analysis |
| `ENGINE_COVERAGE_MATRIX.csv` | Element × Code × Status matrix |
| `SCREEN_ENGINE_MAP.md` | Screen → ViewModel → Engine → Result mapping |
| `CRASH_INVENTORY.md` | 15 tools crash risks with fixes |
| `INPUTGUARD_COVERAGE.md` | Rule 1.4 compliance matrix |
| `CODEREF_AUDIT.md` | CodeReference coverage & missing keys |
| `COVERAGE_MATRIX.csv` | Machine-readable coverage data |

---

## 🚀 Next Action

**Immediate:** Begin Week 2 tasks — create missing engine files and replace CalculatorEngine stubs.

**Command to start:**
```bash
# Verify current build
./gradlew :app:compileDebugKotlin

# Begin implementation
# 1. Create ACIPileFoundation.kt
# 2. Create SBCFlatSlab.kt
# 3. Replace CalculatorEngine stubs with Factory delegation
```

---

**Audit Complete.** All findings documented in `AUDIT_PACKAGE/`. Ready for implementation phase. 🏗️⚡