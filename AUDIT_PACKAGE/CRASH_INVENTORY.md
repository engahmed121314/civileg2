# Crash Inventory — Tools & Utility Screens

## Executive Summary
| Total Tools Screens | Crash Risk High | Crash Risk Medium | Crash Risk Low |
|---------------------|-----------------|-------------------|----------------|
| 15 | 4 | 8 | 3 |

---

## High Crash Risk (🔴) — Immediate Fix Required

### 1. SoilBearingScreen / SoilBearingViewModel
**Crash Points:**
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `SoilBearingCalculator.calculate()` | `ArithmeticException` | Division by zero when `width = 0` or `length = 0` | `InputGuard.positive("width", width)` + `InputGuard.positive("length", length)` |
| `SoilBearingCalculator.calculate()` | `ArithmeticException` | Division by zero when `depth = 0` | `InputGuard.positive("depth", depth)` |
| `SoilBearingViewModel.calculate()` | `NullPointerException` | `soilType` spinner returns null | Null check + default value |
| `SoilBearingCalculator.terzaghiBearingCapacity()` | `ArithmeticException` | `tan(phi)` when `phi = 90°` | Clamp `phi` to `< 89°` |

**ViewModel Pattern Fix:**
```kotlin
fun calculateSoilBearing(inputs: SoilBearingInputs) {
    viewModelScope.launch {
        _isLoading.value = true
        try {
            InputGuard.positive("width", inputs.width)
            InputGuard.positive("length", inputs.length)
            InputGuard.positive("depth", inputs.depth)
            InputGuard.positive("load", inputs.load)
            InputGuard.inRange("phi", inputs.phi, 0.1, 85.0)
            
            val result = SoilBearingCalculator.calculate(inputs)
            val report = CalculationValidator.validateSoilBearing(result)
            _result.value = result
            _validationReport.value = report
            _error.value = null
        } catch (e: IllegalArgumentException) {
            _error.value = "مدخلات غير صالحة: ${e.message}"
        } catch (e: ArithmeticException) {
            _error.value = "خطأ حسابي: قيم غير صالحة تسبب القسمة على صفر"
        } catch (e: Exception) {
            _error.value = "خطأ غير متوقع: ${e.message}"
            Log.e("SoilBearing", "Crash", e)
        } finally {
            _isLoading.value = false
        }
    }
}
```

---

### 2. RebarToolScreen / RebarToolViewModel
**Crash Points:**
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `RebarCalculator.calculateArea()` | `NullPointerException` | `barDiameter` lookup returns null | Safe lookup with default |
| `RebarCalculator.calculateWeight()` | `ArithmeticException` | Division by zero when `spacing = 0` | `InputGuard.positive("spacing", spacing)` |
| `RebarToolViewModel.convert()` | `NumberFormatException` | Invalid string input | `toDoubleOrNull() ?: defaultValue` |

---

### 3. UnitConverterScreen
**Crash Points:**
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `UnitConverter.convert()` | `ArithmeticException` | Division by zero in conversion factors | Guard conversion factor ≠ 0 |
| `UnitConverter.convert()` | `IllegalArgumentException` | Unknown unit string | Validate unit enum first |
| `UnitConverter.convert()` | `ArithmeticException` | `Double.POSITIVE_INFINITY` result | Check `isFinite()` |

---

### 4. SiteLayoutScreen
**Crash Points:**
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `DxfExporter.exportSiteLayout()` | `IllegalArgumentException` | Empty columns list | Check `columns.isNotEmpty()` |
| `DxfExporter.exportSiteLayout()` | `ArithmeticException` | `plotWidth = 0` or `plotLength = 0` | `InputGuard.positive()` |
| `SiteLayoutViewModel.exportDxf()` | `NullPointerException` | `columns` LiveData null | Null check + empty list default |

---

## Medium Crash Risk (🟡) — Fix This Week

### 5. WindLoadScreen / WindLoadViewModel
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `WindLoadCalculator.calculate()` | `NullPointerException` | `zone` spinner null | Null check + default |
| `WindLoadCalculator.calculate()` | `ArithmeticException` | `height = 0` | `InputGuard.positive()` |
| `WindLoadViewModel.calculate()` | `NullPointerException` | `terrainCategory` null | Default value |

---

### 6. ConcreteMixScreen / ConcreteMixViewModel
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `ConcreteMixDesigner.design()` | `NullPointerException` | `cementType` null | Null check + default |
| `ConcreteMixDesigner.design()` | `ArithmeticException` | `w/c ratio = 0` | `InputGuard.positive()` |
| `ConcreteMixViewModel.designAllGrades()` | `IndexOutOfBoundsException` | Empty results list | Check `isNotEmpty()` |

---

### 7. RebarToolScreen / RebarToolViewModel
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `RebarCalculator.calculateArea()` | `NullPointerException` | Bar diameter lookup fails | Safe lookup with fallback |
| `RebarCalculator.calculateWeight()` | `ArithmeticException` | `spacing = 0` | `InputGuard.positive()` |
| `RebarToolViewModel.convert()` | `NumberFormatException` | Invalid input string | `toDoubleOrNull() ?: default` |

---

### 8. UnitConverterScreen
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `UnitConverter.convert()` | `ArithmeticException` | Zero conversion factor | Guard factor ≠ 0 |
| `UnitConverter.convert()` | `IllegalArgumentException` | Unknown unit | Validate enum first |
| `UnitConverter.convert()` | Returns `Infinity` | Overflow in calculation | Check `isFinite()` |

---

### 9. WaterLevelScreen
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `WaterLevelCalculator.calculate()` | `ArithmeticException` | `pipeDiameter = 0` | `InputGuard.positive()` |
| `WaterLevelViewModel.calculate()` | `NullPointerException` | `material` spinner null | Null check + default |

---

### 10. CalculatorScreen
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `ExpressionParser.parse()` | `ArithmeticException` | Division by zero in expression | Try/catch + user message |
| `ExpressionParser.parse()` | `NumberFormatException` | Invalid number format | Input validation |
| `CalculatorViewModel.calculate()` | `StackOverflowError` | Recursive expression | Depth limit |

---

### 11. MaterialPricesScreen / SettingsViewModel
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `SettingsViewModel.setConcretePrice()` | `NumberFormatException` | Invalid price string | `toDoubleOrNull() ?: currentValue` |
| `SettingsViewModel.setSteelPrice()` | `NumberFormatException` | Invalid price string | `toDoubleOrNull() ?: currentValue` |

---

### 12. MaterialPricesScreen / SettingsViewModel (Currency)
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `SettingsViewModel.setCurrency()` | `IllegalArgumentException` | Invalid currency code | Validate against known codes |

---

### 13. SteelTablesScreen
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `SteelTables.getSection()` | `NoSuchElementException` | Section not found | Return `null` or default |
| `SteelTables.findSection()` | `NullPointerException` | Empty sections list | Check `isNotEmpty()` |

---

### 14. MasterBbsScreen / MasterBbsViewModel
| Location | Crash Type | Cause | Fix |
|----------|------------|-------|-----|
| `MasterBbsViewModel.generateBbs()` | `NullPointerException` | Empty designs list | Check `isNotEmpty()` |
| `BbsGenerator.generate()` | `ArithmeticException` | Zero bar diameter | `InputGuard.positive()` |

---

## Low Crash Risk (🟢) — Monitor Only

### 13. SteelTablesScreen
- Only read operations, no calculations
- Risk: Section lookup returns null → handle gracefully

### 14. SteelDesignScreen
- Uses `calculateSteelMember()` and `designSteelWarehouse()` which are complete
- Risk: Low

### 15. SettingsScreen / SettingsViewModel
- Only DataStore reads/writes
- Risk: Low (DataStore handles coroutines safely)

---

## Universal Fix Pattern — Apply to ALL ViewModels

```kotlin
// Standard ViewModel Calculation Template
fun calculateXxx(inputs: XxxInputs) {
    viewModelScope.launch {
        _isLoading.value = true
        _error.value = null
        _validationReport.value = null
        
        try {
            // 1. INPUT VALIDATION (Rule 1.4: Loud failures)
            InputGuard.positive("param1", inputs.param1)
            InputGuard.positive("param2", inputs.param2)
            InputGuard.nonNegative("param3", inputs.param3)
            InputGuard.inRange("angle", inputs.angle, 0.1, 89.9)
            InputGuard.notNull("requiredObject", inputs.requiredObject)
            
            // 2. CALL ENGINE
            val result = calculatorEngine.designXxx(...)
            
            // 3. VALIDATE RESULT
            val report = CalculationValidator.validateXxx(result)
            
            // 4. UPDATE STATE
            _result.value = result
            _validationReport.value = report
            _error.value = null
            
        } catch (e: IllegalArgumentException) {
            // Input validation failures - user fixable
            _error.value = "مدخلات غير صالحة: ${e.message}"
            _result.value = null
            
        } catch (e: ArithmeticException) {
            // Math errors - division by zero, overflow
            _error.value = "خطأ حسابي: ${e.message}"
            _result.value = null
            
        } catch (e: IllegalStateException) {
            // Business logic errors
            _error.value = "خطأ في العملية: ${e.message}"
            _result.value = null
            
        } catch (e: Exception) {
            // Unexpected errors - log for debugging
            _error.value = "خطأ غير متوقع: ${e.message}"
            _result.value = null
            Log.e("XxxViewModel", "Calculation crash", e)
            
        } finally {
            _isLoading.value = false
        }
    }
}
```

---

## InputGuard API Reference (Required for All Fixes)

```kotlin
// InputGuard.kt - Available Methods
object InputGuard {
    // Positive numbers (> 0)
    fun positive(name: String, value: Double): Double
    fun positive(name: String, value: Int): Int
    fun positive(name: String, value: Float): Float
    
    // Non-negative (>= 0)
    fun nonNegative(name: String, value: Double): Double
    fun nonNegative(name: String, value: Int): Int
    
    // Range validation
    fun inRange(name: String, value: Double, min: Double, max: Double): Double
    fun inRange(name: String, value: Int, min: Int, max: Int): Int
    
    // Null checks
    fun notNull<T>(name: String, value: T?): T
    
    // String validation
    fun notBlank(name: String, value: String): String
    
    // Collection validation
    fun notEmpty(name: String, collection: Collection<*>): Collection<*>
    
    // At least one of multiple values
    fun atLeastOne(vararg pairs: Pair<String, Double>): List<Double>
    
    // Custom validation with custom message
    fun require(condition: Boolean, message: String)
}
```

---

## Implementation Priority

| Week | Tools to Fix | Pattern |
|------|--------------|---------|
| **Week 7, Days 1-2** | SoilBearing, RebarTool, UnitConverter, SiteLayout | High Risk - Apply universal pattern |
| **Week 7, Days 3-4** | WindLoad, ConcreteMix, WaterLevel, Calculator | Medium Risk - Apply universal pattern |
| **Week 7, Day 5** | MaterialPrices, SteelTables, MasterBbs | Low Risk - Apply universal pattern |
| **Week 7, Day 6-7** | Integration testing all tools with valid/invalid/empty inputs | QA |

---

## Validation Checklist Per Tool

- [ ] All numeric inputs have `InputGuard.positive()` or `nonNegative()`
- [ ] All required objects have `InputGuard.notNull()`
- [ ] All ranges have `InputGuard.inRange()`
- [ ] All string inputs have `InputGuard.notBlank()`
- [ ] All lists have `InputGuard.notEmpty()`
- [ ] Try/catch with specific exception types
- [ ] User-friendly Arabic/English error messages
- [ ] `_isLoading` always reset in `finally` block
- [ ] `_error` cleared on success
- [ ] `CalculationValidator` called after engine
- [ ] Log.e() for unexpected exceptions with context