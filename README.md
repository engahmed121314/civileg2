# 🏗️ CivilEG - تطبيق هندسة مدنية متقدم

## 📱 نظرة عامة

**CivilEG** هو تطبيق Android متقدم متخصص في تصميم وحساب جميع أنواع المنشآت الخرسانية بكفاءة واقتصادية عالية.

### الميزات الرئيسية:
- ✅ تصميم الكمرات (Beams)
- ✅ تصميم الأعمدة (Columns)
- ✅ تصميم البلاطات (Slabs)
- ✅ تصميم السلالم (Staircases)
- ✅ تصميم القواعد (Footings)
- ✅ حسابات اقتصادية متقدمة
- ✅ رسومات تفاعلية
- ✅ جدول كميات شامل
- ✅ جدولة زمنية واقعية

---

## 🎯 الأكواس المدعومة

| الكود | الوصف | المرحلة |
|------|-------|--------|
| 🇪🇬 **Egyptian** | الكود المصري ECP 203 | مدعوم بالكامل ✅ |
| 🇺🇸 **ACI** | الكود الأمريكي ACI 318 | مدعوم بالكامل ✅ |
| 🇸🇦 **Saudi** | الكود السعودي SBC | مدعوم بالكامل ✅ |

---

## 🚀 المميزات المتقدمة

### 1. حساب التكاليف الشاملة
```
التكلفة = الحديد + الخرسانة + الرمل + السن + الإسمنت + اليد العاملة
        + معاملات الهالك + التكاليف الإضافية
```

### 2. الجدولة الزمنية الواقعية
```
المدة = تحضير + حفر + قوالب + حديد + صب + معالجة + نزع
      + مع احتساب المتعلقات والمسار الحرج
```

### 3. المرونة القصوى
- نسب هالك قابلة للتخصيص
- أسعار مواد محدثة
- معدلات إنتاجية مختلفة
- ظروف موقع متعددة

---

## 📁 هيكل المشروع

```
civileg2/
├── app/
│   ├── src/main/
│   │   ├── java/com/civileg/app/
│   │   │   ├── ui/                    # واجهات المستخدم
│   │   │   │   ├── column/           # تصميم الأعمدة
│   │   │   │   ├── beam/             # تصميم الكمرات
│   │   │   │   ├── slab/             # تصميم البلاطات
│   │   │   │   └── stairs/           # تصميم السلالم
│   │   │   ├── utils/                # الأدوات والمساعدات
│   │   │   │   ├── CalculatorEngine.kt           # محرك الحسابات
│   │   │   │   ├── ComprehensiveCostManager.kt   # نظام التكاليف
│   │   │   │   ├── TimelineManager.kt            # الجدولة الزمنية
│   │   │   │   ├── RatioManager.kt               # إدارة النسب
│   │   │   │   ├── ValidationUtils.kt            # التحقق من البيانات
│   │   │   │   └── ...
│   │   │   ├── views/                # الرسومات التفاعلية
│   │   │   │   ├── BeamSectionView.kt
│   │   │   │   ├── ColumnSectionView.kt
│   │   │   │   ├── SlabDetailView.kt
│   │   │   │   └── ...
│   │   │   ├── viewmodel/            # ViewModel للبيانات
│   │   │   ├── db/                   # قاعدة البيانات
│   │   │   └── model/                # نماذج البيانات
│   │   └── res/                      # الموارد
│   ├── build.gradle.kts             # إعدادات البناء
│   └── ...
├── gradle/                          # إعدادات Gradle
├── IMPROVEMENT_PLAN.md              # خطة التحسينات
├── DETAILED_REVIEW_REPORT.md        # تقرير المراجعة
├── PHASE_1_IMPLEMENTATION.md        # توثيق المرحلة الأولى
├── FIXES_SUMMARY.md                 # ملخص الإصلاحات
├── SUMMARY.md                       # الملخص الشامل
└── README.md                        # هذا الملف
```

---

## 📊 البيانات والأنظمة

### نظام التكاليف الشاملة
**الملف:** `utils/ComprehensiveCostManager.kt`

```kotlin
// حساب تكاليف الكمرة
val bill = ComprehensiveCostManager.calculateBeamBillOfQuantities(
    width = 400.0,
    height = 600.0,
    length = 5.0,
    concreteGrade = "C25",
    steelOption = steelOption,
    materialPrices = prices
)

// الحصول على التفاصيل
bill.getCostBreakdown()      // تفصيل التكاليف
bill.getSteelBreakdown()     // تفاصيل الحديد
bill.getConcreteBreakdown()  // تفاصيل الخرسانة
```

### نظام الجدولة الزمنية
**الملف:** `utils/TimelineManager.kt`

```kotlin
// توليد جدول زمني للكمرة
val tasks = TimelineManager.generateBeamTimeline(
    beamCount = 5,
    steelWeightKg = 2500.0,
    concreteVolume = 12.0,
    formworkArea = 120.0
)

// حساب التكاليف الزمنية
val timeCost = TimelineManager.calculateTimelineCost(tasks)
```

### نظام إدارة النسب
**الملف:** `utils/RatioManager.kt`

```kotlin
// استخدام إعداد مسبق
val config = RatioManager.getConfigurationByName("Efficient")

// إنشاء تكوين مخصص
val custom = RatioManager.createCustomConfiguration(
    name = "My Project",
    wastageFactors = WastageFactors(...),
    mixRatioKey = "1:1.5:3 (C25)",
    codeKey = "EGYPTIAN",
    environmentKey = "MARINE",
    curingKey = "STANDARD"
)
```

---

## 🛠️ التثبيت والتطوير

### المتطلبات:
- Android Studio 2021.3.1 أو أحدث
- JDK 11 أو أحدث
- Android SDK 21 أو أحدث

### خطوات التثبيت:
```bash
# 1. استنساخ المشروع
git clone https://github.com/civileg/civileg2.git
cd civileg2

# 2. فتح المشروع في Android Studio
# File → Open → اختر المجلد

# 3. بناء المشروع
./gradlew clean build

# 4. تشغيل على جهاز أو محاكي
./gradlew installDebug
```

---

## 📚 التوثيق والمراجع

### ملفات التوثيق:
| الملف | الوصف | الفائدة |
|------|-------|--------|
| `IMPROVEMENT_PLAN.md` | خطة التحسينات | فهم الرؤية العامة |
| `DETAILED_REVIEW_REPORT.md` | تقرير المراجعة | معرفة الأخطاء والحلول |
| `PHASE_1_IMPLEMENTATION.md` | توثيق المرحلة الأولى | تعليمات استخدام مفصلة |
| `FIXES_SUMMARY.md` | ملخص الإصلاحات | مرجع سريع |
| `SUMMARY.md` | ملخص شامل | نظرة عامة |

---

## 🔍 الأخطاء المصححة

### الأخطاء الحرجة (Critical):
- ✅ قيم hard-coded للغطاء الخرساني
- ✅ قيم hard-coded لأبعاد الرسم

### الأخطاء الرئيسية (Major):
- ✅ رسم أولي لا يتجاوب
- ✅ معالجة أخطاء ضعيفة

### الأخطاء البسيطة (Minor):
- ✅ بيانات ناقصة عند الحفظ

---

## 🎯 خطوات التطوير المستقبلي

### المرحلة الثانية (أسبوع 5-6):
```
□ إنشاء Fragments للواجهات الجديدة
□ تحديث ViewModel بـ LiveData
□ إضافة رسوم بيانية وجداول
□ تحسين عرض البيانات
```

### المرحلة الثالثة (أسبوع 7-8):
```
□ تقارير PDF متقدمة
□ تصدير البيانات (Excel, PDF)
□ مقارنة بين التصاميم
□ توصيات ذكية
```

### المرحلة الرابعة (أسبوع 9-10):
```
□ التعلم الآلي للتنبؤات
□ تحسين قواعد النسب
□ مقاييس الأداء
□ تحسينات الواجهة
```

---

## 💻 أمثلة الاستخدام

### مثال 1: حساب تصميم عمود

```kotlin
// المدخلات
val result = CalculatorEngine.calculateColumnEgyptian(
    type = ColumnSectionView.ColumnType.RECTANGULAR,
    w = 400f,           // العرض (مم)
    h = 500f,           // الارتفاع (مم)
    d = 0f,             // القطر (للدائري)
    l = 3000f,          // الارتفاع الكلي (مم)
    c = 30f,            // الغطاء الخرساني (مم)
    fc = 25.0,          // مقاومة الخرسانة (MPa)
    fy = 360.0,         // مقاومة الحديد (MPa)
    p = 500.0,          // القوة المحورية (kN)
    mx = 0.0,           // العزم بالاتجاه X
    my = 0.0            // العزم بالاتجاه Y
)

// جدول الكميات
val bill = ComprehensiveCostManager.calculateColumnBillOfQuantities(
    width = 400.0,
    height = 500.0,
    length = 3.0,
    concreteGrade = "C25",
    steelOption = result.mainSteel,
    materialPrices = prices
)

// الجدول الزمني
val timeline = TimelineManager.generateColumnTimeline(
    columnCount = 4,
    steelWeightKg = result.steelWeightKg,
    concreteVolume = result.concreteVol,
    columnHeight = 3.0
)

// النتيجة النهائية
println("Steel Area: ${result.requiredSteelArea} mm²")
println("Concrete Volume: ${result.concreteVol} m³")
println("Total Cost: ${bill.totalCost} EGP")
println("Duration: ${timeline.totalDuration} days")
```

### مثال 2: إنشاء تكوين مخصص

```kotlin
val customConfig = RatioManager.createCustomConfiguration(
    name = "Marine Construction",
    wastageFactors = RatioManager.WastageFactors(
        steelWastage = 1.12,
        concreteWastage = 1.08,
        sandWastage = 1.10,
        stoneChippingsWastage = 1.10,
        cementWastage = 1.08,
        laborWastage = 1.20
    ),
    mixRatioKey = "1:1.5:3 (C25)",
    codeKey = "EGYPTIAN",
    environmentKey = "MARINE",
    curingKey = "STANDARD"
)

// التحقق من الصحة
val errors = RatioManager.validateConfiguration(customConfig)
if (errors.isEmpty()) {
    // استخدام التكوين
}
```

---

## 📊 الإحصائيات

| المقياس | العدد |
|--------|-------|
| ملفات Kotlin | 50+ |
| أسطر الكود | 10,000+ |
| ملفات التوثيق | 8 |
| أمثلة الاستخدام | 15+ |
| حالات الاختبار | 20+ |
| الأخطاء المصححة | 5 |

---

## 🔐 الأمان والجودة

- ✅ التحقق الكامل من المدخلات
- ✅ معالجة شاملة للأخطاء
- ✅ لا توجد null pointer exceptions
- ✅ حماية البيانات الحساسة
- ✅ توثيق كامل للكود

---

## 📞 الدعم والمساعدة

للمزيد من المعلومات:
- اقرأ ملفات التوثيق في المشروع
- راجع الأمثلة في الكود
- تحقق من التعليقات والشرح

---

## 📄 الترخيص

هذا المشروع مرخص تحت رخصة MIT

---

## 👨‍💻 الفريق

تم تطوير هذا التطبيق بواسطة فريق متخصص في الهندسة المدنية والبرمجة.

---

## 🎉 الإنجازات الحالية

✅ تصميم شامل لجميع أنواع المنشآت
✅ حسابات اقتصادية متقدمة
✅ جدولة زمنية واقعية
✅ نسب قابلة للتخصيص
✅ توثيق شامل
✅ أكواد متعددة مدعومة
✅ رسومات تفاعلية
✅ تقارير شاملة

---

## 🚀 النسخة الحالية

- **الإصدار:** 1.0
- **تاريخ الإطلاق:** 12 مارس 2026
- **الحالة:** ✅ جاهز للإنتاج

---

## 📝 ملاحظات مهمة

- جميع الحسابات وفق المعايير الهندسية الدولية
- معاملات الأمان مطبقة حسب الأكواس المختلفة
- النسب قابلة للتخصيص حسب ظروف الموقع
- التوثيق محدث بشكل مستمر

---

**آخر تحديث:** 12 مارس 2026
**الحالة:** ✅ نشط وجاهز للتطوير
**الدعم:** متاح بالكامل


