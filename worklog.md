---
Task ID: 1-9
Agent: Main Agent
Task: مراجعة شاملة لإصلاح أخطاء الكود في تطبيق civileg2

Work Log:
- تحقق من ECPFooting.kt: مكتمل بالفعل (من الجلسة السابقة)
- إصلاح ECPSlab.kt: K-method كان يستخدم fc بدلاً من fcu، وK/0.9 بدلاً من K/1.25، وقسمة Mu على loadCombination.factor خطأ
- إصلاح ECPAdvancedSlab.kt: Hordi/Waffle K-method، shear double γc، punching hardcoded values، deflection placeholder→real calculations، post-tension losses placeholder→real
- إصلاح ECPAdvancedColumn.kt: biaxial alpha formula، fy hardcoded→parameter، punching check perimeter formula
- إصلاح SBCFooting.kt: calculateFootingReinforcement كان placeholder→real K-method calculations، إضافة eccentricity handling
- إصلاح SteelDesignEngine.kt: Lr calculation wrong→AISC F2-6 formula، إضافة checkColumnCombined (AISC H1 interaction)
- إصلاح AppDatabase.kt: إزالة ProjectEntity غير الموجود
- إصلاح ECPColumn.kt: إزالة معامل φ المزدوج (ECP يستخدم γ فقط)، fix Pu division by factor، ties spacing 15→16×db, max spacing 200→300mm
- إصلاح ECPBeam.kt: K/0.9→K/1.25، K uses fcu not fc، Vu division by factor removed
- إصلاح ACIBeam.kt: fcu→fc'=0.8*fcu، λ cancellation in development length
- إصلاح ACIColumn.kt: fcu→fc'=0.8*fcu، إزالة معامل 0.8 الزائد
- إصلاح ACISeismic.kt: T0 formula، soil factors، overturning moment cumulative
- إصلاح ECPSeismic.kt: building height documentation، default warnings

Stage Summary:
- 12+ ملف تم إصلاحها
- 8 أخطاء حرجة تم حلها (φ مزدوج، K-method خاطئ، fcu vs fc'، λ cancellation)
- 9 أخطاء عالية تم حلها (shear double γc، hardcoded values، T0 formula)
- جميع حسابات الخرسانة الآن متوافقة مع ECP 203-2020
- جميع حسابات ACI الآن تستخدم fc' بدلاً من fcu
