---
Task ID: 1
Agent: Main Agent
Task: مراجعة شاملة لتطبيق civileg2 - إصلاح الأخطاء وإكمال الكود الناقص

Work Log:
- قراءة وفحص ECPFooting.kt: تبين أنه مكتمل بكل الوظائف (617 سطر)
- إصلاح خطأ في ECPFooting.kt سطر 168: نقطة مفقودة قبل format()
- فحص جميع الكيانات (Entities): ColumnEntities, SlabEntities, CommonEntities, BeamEntities, ReinforcementResult, LoadCombination - كلها مكتملة
- فحص ECPAdvancedColumn.kt:
  - إصلاح getMaxSpacing من 200 إلى 300 حسب ECP 203 البند 4-2-6
  - إعادة كتابة حساب قدرة العزم باستخدام K-method مع فحص K_bal
- فحص ECPAdvancedSlab.kt:
  - إكمال designPrecastSlab من placeholder إلى تصميم كامل (K-method, قص, تماسك)
- فحص وإصلاح SBCFooting.kt:
  - إعادة كتابة checkPunchingShear باستخدام f'c و φ=0.75 ومحيط صحيح
  - إصلاح getPunchingShearCapacity بنفس الطريقة
- فحص وإصلاح ECPAdvancedBeam.kt:
  - إعادة كتابة checkDeflection بوحدات صحيحة (Icr, Ec=4400√fcu, δ_LT)
  - إعادة كتابة checkCrackWidth بمعادلة ECP 203 (wk = 3.3 × εm × acr)
  - إعادة كتابة checkDevelopmentLength بطريقة ECP 203 البند 5-2
- فحص وإصلاح ACIFooting.kt:
  - إصلاح محيط الاختراق: b0 = 2(c1+c2) + 4d (كان خاطئاً)
  - إضافة تحويل f'c = 0.8×fcu في جميع معادلات القص
  - إصلاح معامل β في معادلة القص
- فحص وإصلاح ACIColumn.kt:
  - إزالة قسمة الحمل على loadCombination.factor (الحمل تصميمي بالفعل)
- فحص وإصلاح ACIBeam.kt:
  - إزالة قسمة العزم والقص على loadCombination.factor
  - تحويل sqrt(fcu) إلى sqrt(f'c) في جميع معادلات القص
- فحص وإصلاح SBCColumn.kt:
  - إزالة قسمة الحمل على loadCombination.factor
- إعادة كتابة InteractionDiagram.kt بشكل كامل:
  - تنفيذ خوارزمية التوازن الحقيقية (Pn, Mn لحسب عمق المحور المحايد c)
  - توليد منحنى التفاعل الكامل (10+ نقاط من الضغط الخالص للشد الخالص)
  - حساب نقطة التوازن (Balanced Point)
- فحص SteelDesignEngine.kt: مكتمل (كمرات، أعمدة، LTB، انبعاج محلي)
- فحص CalculationFactory.kt: مكتمل (ECP/ACI/SBC لجميع العناصر)
- فحص ECPSeismic.kt: مكتمل (طيف الاستجابة، توزيع القوى)
- تأكيد عدم وجود ملفات مكررة (PdfExporters, UnitConverters, DB classes)

Stage Summary:
- ECPFooting.kt: مكتمل ✅ (إصلاح خطأ بسيط)
- ECPAdvancedColumn.kt: تم الإصلاح ✅
- ECPAdvancedSlab.kt: تم الإكمال ✅
- SBCFooting.kt: تم الإصلاح ✅
- ECPAdvancedBeam.kt: تم الإصلاح ✅
- ACIFooting.kt: تم الإصلاح ✅
- ACIColumn.kt: تم الإصلاح ✅
- ACIBeam.kt: تم الإصلاح ✅
- SBCColumn.kt: تم الإصلاح ✅
- InteractionDiagram.kt: تمت إعادة الكتابة ✅
- SteelDesignEngine.kt: مكتمل ✅
- لا ملفات مكررة ✅