---
Task ID: 1
Agent: Main Agent
Task: Deep inspection and professional fixes for Civileg2 Android app

Work Log:
- Deep inspection of 15+ source files identifying language, PDF, and calculation issues
- Unified SharedPreferences for language (LocaleHelper → app_settings) with legacy migration
- Made CalculatorEngine enums bilingual (DesignCode, SlabType, TankType, FootingType)
- Made CalculatorEngine suggestions bilingual (Stair, Tank, RetainingWall)
- Fixed Steel Section Modulus (Sx) formula - corrected parentheses in I-Section calculation
- Made ComprehensivePdfExporter language-aware (t(ar,en) helper, isEnglish flag)
- Made PdfGenerator language-aware (t() helper, language detection)
- Replaced 60+ hardcoded Arabic strings in SteelDesignScreen with stringResource()
- Added 42 new string resources (EN + AR) for steel design screens
- Fixed all @Composable invocation compile errors across 12 files
- Fixed PdfFontFactory import path for iText 8
- Fixed TankType enum reference (UNDERGROUND vs RECTANGULAR_UNDERGROUND)
- Successfully built APK v1.3.0

Stage Summary:
- APK v1.3.0 built successfully (32.2 MB)
- 9 files modified, 715 insertions, 429 deletions
- Language switching now unified (single SharedPreferences store)
- PDF reports now respect language setting
- Steel calculation Section Modulus formula corrected
- All Composable context errors resolved