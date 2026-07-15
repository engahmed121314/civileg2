<div align="center">

# Civil Engineer Pro

### Your Complete Structural Engineering Companion

[![Android CI](https://github.com/engahmed121314/civileg2/actions/workflows/android.yml/badge.svg)](https://github.com/engahmed121314/civileg2/actions/workflows/android.yml)
[![API Level](https://img.shields.io/badge/minSDK-26-green.svg)](https://developer.android.com/about/versions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg)](https://kotlinlang.org)
[![Compose BOM](https://img.shields.io/badge/Compose-2024.12.01-5846BE.svg)](https://developer.android.com/jetpack/compose)

**English** | [العربية](#العربية)

</div>

---

## English

### Overview

**Civil Engineer Pro** is a comprehensive Android application for structural engineers, civil engineering students, and construction professionals. It provides structural design and analysis tools for reinforced concrete, steel structures, seismic analysis, and quantity surveying — compliant with major international design codes.

The app supports **three design codes**: the Egyptian Code (ECP 203/201), the American Concrete Institute code (ACI 318), and the Saudi Building Code (SBC 304/306).

### Key Features

#### Reinforced Concrete Design

- **Beam Design** — Simply supported, fixed, cantilever, and roller-hinged beams with moment and shear diagrams, deflection checks, and reinforcement calculations per selected support type and design code
- **Column Design** — Short and slender columns with effective length factor (K), axial load, uniaxial and biaxial bending (Bresler + load contour methods), full P-M interaction diagrams, 6 cross-section shapes (rectangular, circular, L-shaped, T-shaped, composite, tubular)
- **Slab Design** — One-way and two-way slabs including solid, ribbed/hollow-block, waffle, flat plate, flat slab with drop panels, post-tensioned, and precast — with moment coefficients, ACI Direct Design Method, and deflection verification (including hot climate adjustment for SBC)
- **Footing Design** — Isolated, combined, strip, raft, and pile cap footings with bearing pressure checks (net SBC with eccentricity), punching shear, one-way shear, and reinforcement detailing. *Note: Strap footing and settlement estimates are planned for a future release.*
- **Retaining Wall Design** — Cantilever retaining walls with Rankine active/passive earth pressure, surcharge loads, water table effects, sliding/overturning/bearing safety checks, and full stem/toe/heel reinforcement design with tapered stem support
- **Staircase Design** — Straight and dog-leg (quarter-turn) stairs with waist slab calculations on slope, comfort formula (2R+G), main and distribution reinforcement, shear stirrups, and deflection checks
- **Water Tank Design** — Rectangular and circular tanks (ground-level, elevated, and underground) with hydrostatic pressure analysis, hoop tension for circular tanks, crack-width checks (ECP: 0.2mm, ACI/SBC: stress-based), uplift check for buried tanks, and water-retaining minimum reinforcement

#### Steel Structure Design

- **Steel Member Design** — AISC 360-16 LRFD method for tension (gross yielding, net rupture, block shear), compression (flexural/torsional/local buckling), bending (LTB, weak axis), and combined loading (H1-1a/H1-1b interaction equations). Three code paths: ECP 205, AISC 360-16, and SBC 306
- **Base Plate Design** — Concentrically and eccentrically loaded base plates, pocket base plates, and anchor bolt design (grades 4.6–10.9, embedment lengths)
- **Bolt & Weld Design** — Bolt capacity (shear, bearing, tension, combined shear-tension, slip-critical, block shear) and fillet weld capacity with electrode types (E60XX–E90XX) per AISC/ECP
- **Steel Warehouse Design** — Complete portal frame warehouse design with main frame members, 4-view engineering drawings (front elevation, plan, side elevation, 3D isometric), bending moment diagrams, BMD with filled area, and PDF report generation

#### Seismic Analysis

- **Equivalent Static Method** — Seismic base shear calculation per ECP 201, ASCE 7-16, and SBC 301 with zone factors, soil parameters, and force distribution
- **Design Response Spectrum** — Code-based spectral acceleration curves (4-branch for ECP, T0/Ts-based for ASCE 7) with design period marker

#### Frame Analysis

- **2D Frame Analysis** — Full 2D stiffness matrix method solver supporting multiple node types (pin, roller, fixed), member types (beam, column, brace), and load types (UDL, point load, moment, linearly varying)
- **Force Diagrams** — Bending moment, shear force, and axial force diagrams for all frame members
- **Interactive Drawing Canvas** — Real-time visualization with pinch-to-zoom (0.5x–5x), pan, and tap-to-inspect

#### Additional Tools

- **Bill of Quantities (BOQ)** — Automatic quantity takeoff (concrete, steel, formwork, excavation) with cost estimation across 40+ material prices and 8 currencies (EGP, USD, SAR, AED, KWD, QAR, OMR, BHD)
- **Scientific Calculator** — Engineering calculator with trigonometric and logarithmic functions
- **Unit Converter** — Length, area, volume, weight, force, pressure/stress, and moment conversions
- **Rebar Inventory** — Track reinforcement bar inventory (15 diameters, 5 grades) with weight/cost calculations and cutting optimization plans
- **PDF Export** — Professional design reports with Arabic text support and engineering drawings
- **CSV Export** — Data export compatible with spreadsheet applications
- **Design Archive** — Save and organize past design projects with results and safety status

### Supported Design Codes

| Code | Standards | Country |
|------|-----------|---------|
| **ECP** | ECP 203 (concrete), ECP 201 (seismic), ECP 205 (steel) | Egypt |
| **ACI** | ACI 318-19 (concrete), ASCE 7-16 (seismic), AISC 360-16 (steel) | USA / International |
| **SBC** | SBC 304 (concrete), SBC 301 (seismic), SBC 306 (steel) | Saudi Arabia |

### Technical Architecture

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin 2.1.0 |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Dependency Injection** | Hilt (Dagger) |
| **Database** | Room (SQLite) — 11 entities, 11 DAOs |
| **Navigation** | Jetpack Navigation Compose |
| **Build System** | Gradle 8.13 with Version Catalog |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **JDK** | 17 |

### Project Structure

```
app/src/main/java/com/civileg/app/
├── di/                          # Hilt dependency injection modules
├── db/                          # Room database, DAOs, entities
├── domain/
│   ├── calculations/
│   │   ├── aci/                 # ACI 318 design implementations
│   │   ├── ecp/                 # ECP 203/201 design implementations
│   │   ├── sbc/                 # SBC design implementations
│   │   ├── base/                # Abstract base design classes
│   │   └── utils/               # Interaction diagrams, utilities
│   ├── entities/                # Domain entities and data classes
│   ├── validators/              # Input validation logic
│   └── usecases/                # Business logic use cases
├── ui/
│   ├── compose/
│   │   ├── components/          # Reusable Compose components
│   │   │   ├── drawings/        # Professional engineering drawings
│   │   │   ├── charts/          # Analysis charts & diagrams
│   │   │   └── interactive/     # Interactive section views
│   │   └── screens/             # 22 design & tool screens
│   └── (traditional views)      # Legacy View-based screens
├── viewmodel/                   # 16 MVVM ViewModels
├── utils/                       # PDF/CSV export, steel tables, converters
└── pricing/                     # Material pricing & cost management
```

### Project Stats

- **260+ Kotlin source files**
- **80,000+ lines of code**
- **22 design & tool screens** with full input, calculation, and drawing output
- **3 international design codes** with code-specific load factors and formulas
- **Strategy Pattern** for polymorphic design code dispatch via `CalculationFactory`

### Building the Project

#### Prerequisites
- JDK 17
- Android SDK with API 35
- Gradle 8.13 (handled by the Gradle Wrapper)

#### Build Commands
```bash
# Clone the repository
git clone https://github.com/engahmed121314/civileg2.git
cd civileg2

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Clean build
./gradlew clean assembleDebug
```

The output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Disclaimer

> This application is for **educational and preliminary design purposes only**. All calculations should be verified by a licensed structural engineer. The developer is not responsible for any errors or damages resulting from the use of this application.

---

<div id="العربية">

## العربية

### نظرة عامة

**مهندس مدني برو** هو تطبيق أندرويد شامل مصمم للمهندسين الإنشائيين وطلاب الهندسة المدنية ومحترفي البناء. يوفر أدوات تصميم وتحليل إنشائي للخرسانة المسلحة والمنشآت الفولاذية والتحليل الزلزالي وجداول الكميات — متوافق مع كودات التصميم الدولية الرئيسية.

يدعم التطبيق **ثلاثة كودات تصميم**: الكود المصري (ECP 203/201)، وكود معهد الخرسانة الأمريكي (ACI 318)، وكود البناء السعودي (SBC 304/306).

### الميزات الرئيسية

#### تصميم الخرسانة المسلحة

- **تصميم الكمرات** — كمرات بسيطة الإسناد وثابتة وشاربية وقلابة مع مخططات العزوم والقص ومعاملات الأحمال الخاصة بكل كود تصميم وفحص الانحناء وحسابات التسليح التفصيلية
- **تصميم الأعمدة** — أعمدة قصيرة ونحيفة مع معامل الطول الفعّال (K)، الحمل المحوري، الانحناء أحادي وثنائي المحور (طريقة Bresler وخطوط العزم المتساوية)، مخططات التفاعل P-M، 6 أشكال مقطعية (مستطيل، دائري، L، T، مركب، أنبوبي)
- **تصميم البلاطات** — بلاطات باتجاه واحد واتجاهين: صلبة ومرقيقة (هوردي) ووافل وبلاطة مسطحة وبلاطة مسطحة مع ألواح تسليح وبلاطات ما بعد الشد ومسبقة الصب — بمعاملات العزوم وطريقة ACI المباشرة وفحص الانحناء (مع معامل المناخ الحار لـ SBC)
- **تصميم الأساسات** — منفصلة ومركبة وشارية ولبشة وقبعات خازوق مع فحص ضغط التربة (صافي قدرة التربة مع اللامركزية) وقص الثقب وقص أحادي الاتجاه وتفاصيل التسليح
- **تصميم حوائط السند** — حوائط سند قنطرية مع ضغط تربة رانكين النشط والسلبي، أحمال الرصف، تأثير منسوب المياه الجوفية، فحص سلامة الانزلاق والانقلاب والضغط، وتصميم كامل لتسليح الساق والكعب والر球星 مع دعم الساق المتدرج
- **تصميم السلالم** — سلالم مستقيمة ورجل كلب مع حسابات لوح السلم على الميل، معادلة الراحة (2R+G)، تسليح رئيسي وتوزيعي، كانات قص، وفحص الانحناء
- **تصميم خزانات المياه** — خزانات مستطيلة ودائرية (أرضية ومرتفعة وتحت أرض) مع تحليل ضغط الماء الساكن، قوى الإحاطة للخزانات الدائرية، فحص عرض الشروخ (ECP: 0.2mm، ACI/SBC: قائم على إجهاد الحديد)، فحص الطفو للخزانات المدفونة

#### تصميم المنشآت الفولاذية

- **تصميم الأعضاء الفولاذية** — طريقة AISC 360-16 LRFD للشد (خضوع المقطع الكامل، تمزق المقطع الصافي، قص الكتلة) والضغط (انبعاج مرن/ليفي/محلي) والانحناء (LTB، المحور الضعيف) والأحمال المركبة (معادلات التفاعل H1-1a/H1-1b). ثلاثة مسارات: ECP 205، AISC 360-16، SBC 306
- **تصميم لوحات القاعدة** — لوحات قاعدة محملة مركزياً ولامركزياً، قواعد جيبية، وتصميم براغي التثبيت
- **تصميم البراغي واللحامات** — سعة البراغي (قص، تحمل، شد، مركب، انزلاق حرج، قص كتلة) وسعة اللحامات التفصيلية مع أنواع الأقطاب الكهربائية
- **تصميم المستودعات الفولاذية** — تصميم كامل لإطارات المستودعات مع أعضاء الإطار الرئيسي ورسومات هندسية بـ 4 مناظر (واجهة أمامية، مسقط أفقي، واجهة جانبية، ثلاثي الأبعاد) ومخططات عزوم الانحناء وتقارير PDF

#### التحليل الزلزالي

- **طريقة القوى الثابتة المكافئة** — حساب قوة القص القاعدي الزلزالية حسب ECP 201 و ASCE 7-16 و SBC 301
- **منحنى طيف التصميم** — منحنيات التسارع الطيفي حسب الكود مع علامة الدورة الزمنية التصميمية

#### تحليل الهياكل الإطارية

- **تحليل إطارات ثنائية الأبعاد** — محلل بطريقة مصفوفة الصلابة يدعم أنواع عقد متعددة (مفصل، بكرة، تثبيت) وأنواع أعضاء (كمرة، عمود، tale) وأنواع أحمال (موزع منتظم، مركّز، عزم، متغير خطياً)
- **مخططات القوى** — مخططات عزم الانحناء وقوة القص والقوة المحورية لجميع أعضاء الهيكل
- **لوحة رسم تفاعلية** — تصور فوري مع التقريب بالضغط (0.5x–5x) والسحب والنقر للفحص

#### أدوات إضافية

- **جداول الكميات** — جرد تلقائي للكميات (خرسانة، حديد، قالب، حفر) مع تقدير التكاليف عبر 40+ سعر مادة و8 عملات
- **آلة حاسبة علمية** — آلة حاسبة هندسية بدوال المثلثات واللوغاريتمات
- **محول الوحدات** — تحويلات الطول والمساحة والحجم والوزن والقوة والإجهاد/الضغط والعزم
- **جرد حديد التسليح** — تتبع مخزون حديد التسليح مع حسابات الوزن والتكلفة وخطط القص المثلى
- **تصدير PDF** — تقارير تصميم احترافية مع دعم النص العربي والرسومات الهندسية
- **تصدير CSV** — تصدير البيانات بتنسيق متوافق مع جداول البيانات
- **أرشيف التصاميم** — حفظ وتنظيم مشاريع التصميم السابقة مع النتائج وحالة السلامة

### كودات التصميم المدعومة

| الكود | المعايير | الدولة |
|--------|---------|--------|
| **ECP** | ECP 203 (خرسانة)، ECP 201 (زلازل)، ECP 205 (فولاذ) | مصر |
| **ACI** | ACI 318-19 (خرسانة)، ASCE 7-16 (زلازل)، AISC 360-16 (فولاذ) | أمريكا / دولي |
| **SBC** | SBC 304 (خرسانة)، SBC 301 (زلازل)، SBC 306 (فولاذ) | السعودية |

### البنية التقنية

| المكوّن | التقنية |
|---------|---------|
| **اللغة** | Kotlin 2.1.0 |
| **إطار واجهة المستخدم** | Jetpack Compose + Material 3 |
| **حقن التبعيات** | Hilt (Dagger) |
| **قاعدة البيانات** | Room (SQLite) — 11 كيان، 11 واجهة DAO |
| **التنقل** | Jetpack Navigation Compose |
| **نظام البناء** | Gradle 8.13 مع Version Catalog |
| **الحد الأدنى للمصدر** | 26 (Android 8.0) |
| **المصدر المستهدف** | 35 (Android 15) |
| **JDK** | 17 |

### إحصائيات المشروع

- **أكثر من 260 ملف Kotlin**
- **أكثر من 80,000 سطر برمجي**
- **22 شاشة تصميم وأدوات** مع مدخلات وحسابات ورسومات كاملة
- **3 كودات تصميم دولية** مع معاملات أحمال وصيغ خاصة بكل كود
- **نمط الاستراتيجية** للإرسال متعدد الأشكال عبر `CalculationFactory`

### بناء المشروع

#### المتطلبات المسبقة
- JDK 17
- Android SDK مع API 35
- Gradle 8.13 (يتم التعامل معه عبر Gradle Wrapper)

#### أوامر البناء
```bash
git clone https://github.com/engahmed121314/civileg2.git
cd civileg2

./gradlew assembleDebug
./gradlew assembleRelease
./gradlew test
./gradlew clean assembleDebug
```

### إخلاء المسؤولية

> هذا التطبيق لأغراض **تعليمية والتصميم المبدئي فقط**. يجب التحقق من جميع الحسابات بواسطة مهندس إنشائي مرخص. المطور غير مسؤول عن أي أخطاء أو أضرار ناتجة عن استخدام هذا التطبيق.

---

</div>

<div align="center">
  <p>Made with passion for Civil Engineers Worldwide</p>
  <p>صُنع بشغف لمهندسي المدني حول العالم</p>
</div>