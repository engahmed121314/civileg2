# Screen → Engine → Result Mapping

## Complete Mapping of All Screens to Calculation Engines

---

### Structural Design Screens (Engineer Track)

| Screen | ViewModel | CalculatorEngine Method | CalculationFactory Method | Domain Engine (ECP) | Domain Engine (ACI) | Domain Engine (SBC) | Result Class | Drawing Component | PDF Exporter | DXF Exporter |
|--------|-----------|------------------------|--------------------------|---------------------|---------------------|---------------------|--------------|-------------------|--------------|--------------|
| **BeamScreen** | BeamViewModel | `designBeam()` ❌ STUB | `getBeamDesign()` | ECPBeam.kt | ACIBeam.kt | SBCBeam.kt | BeamResult | ProfessionalBeamDrawing | NativePdfExporter | DxfWriter |
| **ColumnScreen** | ColumnViewModel | `designColumn()` ❌ STUB | `getColumnDesign()` | ECPColumn.kt | ACIColumn.kt | SBCColumn.kt | ColumnResult | ProfessionalColumnDrawing | NativePdfExporter | DxfWriter |
| **SlabScreen** | SlabViewModel | `designSlab()` ⚠️ PARTIAL | `getSlabDesign()` | ECPSlab.kt | ACISlab.kt | SBCSlab.kt | SlabResult | ProfessionalSlabDrawing | NativePdfExporter | DxfWriter |
| **FlatSlabScreen** | FlatSlabViewModel | **MISSING** `designFlatSlab()` | `getFlatSlabDesign()` | ECPFlatSlab.kt | ACIFlatSlab.kt | **MISSING SBCFlatSlab** | FlatSlabResult | ProfessionalFlatSlabDrawing | NativePdfExporter | DxfWriter |
| **FootingScreen** | FootingViewModel | `calculateFooting()` ❌ STUB | `getFootingDesign()` | ECPFooting.kt | ACIFooting.kt | SBCFooting.kt | FootingResult | ProfessionalFootingDrawing | NativePdfExporter | DxfWriter |
| **PileFoundationScreen** | PileFoundationViewModel | **MISSING** `designPileFoundation()` | `getPileFoundationDesign()` | ECPPileFoundation.kt | **MISSING ACI** | SBCPileFoundation.kt | PileResult | ProfessionalPileDrawing | NativePdfExporter | DxfWriter |
| **RetainingWallScreen** | RetainingWallViewModel | `designRetainingWall()` ❌ STUB | `getRetainingWallDesign()` | ECPRetainingWall.kt | ACIRetainingWall.kt | SBCRetainingWall.kt | RetainingWallResult | ProfessionalRetainingWallDrawing | NativePdfExporter | DxfWriter |
| **TankScreen** | TankViewModel | `designTank()` ❌ STUB | `getTankDesign()` | ECPTank.kt | ACITank.kt | SBCTank.kt | TankResult | ProfessionalTankDrawing | NativePdfExporter | DxfWriter |
| **StairScreen** | StairViewModel | `designStaircase()` ⚠️ PARTIAL | `getStaircaseDesign()` | ECPStaircase.kt | ACIStaircase.kt | SBCStaircase.kt | StairResult | ProfessionalStairDrawing | NativePdfExporter | DxfWriter |
| **ShearWallScreen** | ShearWallViewModel | **MISSING** `designShearWall()` | `getShearWallDesign()` | ECPShearWall.kt | ACIShearWall.kt | SBCShearWall.kt | ShearWallResult | ProfessionalShearWallDrawing | NativePdfExporter | DxfWriter |
| **FrameAnalysisScreen** | FrameAnalysisViewModel | **MISSING** `designFrame()` | N/A (ConcreteFrameDesign) | ConcreteFrameDesign.kt | SteelFrameDesign.kt | SteelFrameDesign.kt | FrameResult | ProfessionalFrameDrawing | FrameAnalysisPdfExporter | DxfWriter |
| **SeismicScreen** | SeismicViewModel | `calculateSeismicLoads()` ❌ STUB | N/A | ECPSeismic.kt | ACISeismic.kt | SBCSeismic.kt | SeismicResult | ProfessionalSeismicDrawing | NativePdfExporter | DxfWriter |
| **SteelDesignScreen** | SteelViewModel | `calculateSteelMember()` ✅ / `designSteelWarehouse()` ✅ | `getSteelDesignEngine()` | SteelDesignEngine.kt | AISCSteelDesignEngine.kt | SBCSteelDesignEngine.kt | SteelMemberResult / SteelWarehouseResult | ProfessionalSteelDrawing | SteelWarehouseProPdfExporter | DxfWriter |

---

### Foundation & Specialized Screens

| Screen | ViewModel | CalculatorEngine Method | Factory Method | Status |
|--------|-----------|------------------------|----------------|--------|
| **StrapFootingScreen** | StrapFootingViewModel | `calculateStrapFooting()` ⚠️ PARTIAL | `getStrapFootingDesign()` | PARTIAL |
| **FlatSlabScreen** | FlatSlabViewModel | **MISSING** `designFlatSlab()` | `getFlatSlabDesign()` | MISSING |
| **PileFoundationScreen** | PileFoundationViewModel | **MISSING** `designPileFoundation()` | `getPileFoundationDesign()` | MISSING |

---

### Tools Screens (Normal User + Engineer Tools)

| Screen | ViewModel | CalculatorEngine Method | Factory Method | Status | Crash Risk |
|--------|-----------|------------------------|----------------|--------|------------|
| **WindLoadScreen** | WindLoadViewModel | N/A | WindLoadCalculator | Works | 🟡 Medium |
| **SoilBearingScreen** | SoilBearingViewModel | N/A | SoilBearingCalculator | Works | 🔴 High (div/0) |
| **ConcreteMixScreen** | ConcreteMixViewModel | N/A | ConcreteMixDesigner | Works | 🟡 Medium |
| **RebarToolScreen** | RebarToolViewModel | N/A | RebarCalculator | Works | 🟡 Medium |
| **UnitConverterScreen** | N/A | N/A | UnitConverter | Works | 🟡 Medium |
| **SteelTablesScreen** | N/A | N/A | SteelTables | Works | 🟡 Medium |
| **WaterLevelScreen** | N/A | N/A | N/A | Works | 🟡 Medium |
| **SiteLayoutScreen** | N/A | N/A | DxfExporter | Works | 🟡 Medium |
| **CalculatorScreen** | N/A | N/A | N/A | Works | 🟡 Medium |
| **MaterialPricesScreen** | SettingsViewModel | N/A | SettingsManager | Works | 🟢 Low |
| **ConcreteMixScreen** | ConcreteMixViewModel | N/A | ConcreteMixDesigner | Works | 🟡 Medium |

---

### Settings & Utility Screens

| Screen | ViewModel | Purpose | Status |
|--------|-----------|---------|--------|
| **SettingsScreen** | SettingsViewModel | Code, language, units, prices, currency | ✅ Works |
| **SettingsScreen** | SettingsViewModel | Theme (Light/Dark/System) | ✅ Works |
| **ArchiveScreen** | ProjectViewModel | Project list, designs archive | ✅ Works |
| **ProjectSummaryScreen** | ProjectViewModel | Project summary with designs | ✅ Works |
| **ExecutionLogScreen** | ExecutionViewModel | Construction logs | ✅ Works |
| **MasterBbsScreen** | MasterBbsViewModel | Master BBS from designs | ⚠️ Empty list |
| **InventoryScreen** | InventoryViewModel | Material inventory | ✅ Works |
| **BOQScreen** | BOQViewModel | Bill of Quantities | ✅ Works |
| **ExportCenterScreen** | ExportViewModel | Export management | ✅ Works |
| **RebarToolScreen** | RebarToolViewModel | Rebar utilities | ⚠️ Crash risk |
| **UnitConverterScreen** | N/A | Unit conversion | ⚠️ Crash risk |
| **SteelTablesScreen** | N/A | Steel section tables | ✅ Works |
| **WaterLevelScreen** | N/A | Water level calc | ⚠️ Crash risk |
| **SiteLayoutScreen** | N/A | Site layout DXF export | ⚠️ Crash risk |
| **CalculatorScreen** | N/A | Scientific calculator | ⚠️ Crash risk |

---

### Onboarding & Navigation

| Screen | Purpose | Status |
|--------|---------|--------|
| **SplashScreen** | App initialization | ✅ Works |
| **LanguageScreen** | Language selection (AR/EN) | ✅ Works |
| **UserTypeScreen** | Engineer / Normal User selection | ✅ Works |
| **HomeScreen** | Engineer dashboard | ✅ Works |
| **DesignHubScreen** | Design module navigation | ✅ Works |
| **ToolsHubScreen** | Tools navigation | ✅ Works |
| **MoreHubScreen** | More modules navigation | ✅ Works |
| **NormalUserHomeScreen** | Normal user tools dashboard | ✅ Works |
| **NormalUserScreens** | Quantity/Finishing/Concrete tools | ✅ Works |

---

## Critical Path: Structural Design Screens Requiring Immediate Attention

### Priority 1: Missing CalculatorEngine Methods (Blocks Screen Functionality)
1. `designFlatSlab()` → FlatSlabScreen
2. `designPileFoundation()` → PileFoundationScreen
3. `designShearWall()` → ShearWallScreen
4. `designFrame()` → FrameAnalysisScreen
5. `designSeismic()` → SeismicScreen

### Priority 2: Stub Methods Returning Dummies (Wrong Results)
1. `designColumn()` → ColumnScreen
2. `designBeam()` → BeamScreen
3. `calculateFooting()` → FootingScreen
4. `designTank()` → TankScreen
5. `designRetainingWall()` → RetainingWallScreen
6. `calculateSeismicLoads()` → SeismicScreen

### Priority 3: Partial Implementations (Need Enrichment)
1. `designSlab()` → SlabScreen
2. `designStaircase()` → StairScreen
3. `calculateStrapFooting()` → StrapFootingScreen

---

## Code Flow: Screen → ViewModel → CalculatorEngine → Factory → Domain Engine

```
BeamScreen.kt
    → BeamViewModel.calculateBeamPro()
        → CalculatorEngine.designBeam() [STUB - returns dummy]
            → CalculationFactory.getBeamDesign(code)
                → ECPBeam.kt / ACIBeam.kt / SBCBeam.kt
                    → Returns ReinforcementResult
            → Wraps in BeamResult [CURRENTLY DUMMY]
        → _result.value = BeamResult
    → BeamScreen observes _result
        → ProfessionalBeamDrawing reads BeamResult
        → NativePdfExporter exports PDF
        → DxfWriter exports DXF
```

**Current Problem:** CalculatorEngine methods return dummies instead of delegating to Factory + enriching.

**Required Fix:** CalculatorEngine methods must delegate to Factory → get real result → enrich with standard fields → return.