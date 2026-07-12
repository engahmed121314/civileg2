إليك الوصف الكامل — انسخه كاملاً:

---

<div align="center">

# 🏗️ Civil Engineer Pro

### Your Complete Structural Engineering Companion

[![Android CI](https://github.com/engahmed121314/civileg2/actions/workflows/gradle.yml/badge.svg)](https://github.com/engahmed121314/civileg2/actions/workflows/gradle.yml)
[![API Level](https://img.shields.io/badge/minSDK-26-green.svg)](https://developer.android.com/about/versions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg)](https://kotlinlang.org)
[![Compose BOM](https://img.shields.io/badge/Compose-2024.12.01-5846BE.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**English** | [العربية](#العربية)

</div>

---

## English

### 📋 Overview

**Civil Engineer Pro** is a comprehensive Android application designed for structural engineers, civil engineering students, and construction professionals. It provides a complete suite of structural design and analysis tools covering reinforced concrete, steel structures, seismic analysis, and quantity surveying — all compliant with major international design codes.

The app supports **three design codes**: the Egyptian Code (ECP 203), the American Concrete Institute code (ACI 318), and the Saudi Building Code (SBC), making it versatile for engineers working across the Middle East and worldwide.

### ✨ Key Features

#### 🏢 Reinforced Concrete Design
- **Beam Design** — Simply supported, continuous, cantilever, and fixed beams with moment and shear diagrams, deflection checks, and detailed reinforcement calculations
- **Column Design** — Short and slender columns under axial load, uniaxial and biaxial bending with full P-M interaction diagrams
- **Slab Design** — One-way and two-way slabs (solid, ribbed/waffle, and flat slabs) with moment coefficients and deflection verification
- **Footing Design** — Isolated, combined, and strap footings with bearing pressure checks, settlement estimates, and reinforcement detailing
- **Retaining Wall Design** — Cantilever retaining walls with active/passive earth pressure, sliding/overturning checks, and stem design
- **Staircase Design** — Straight, L-shaped, and U-shaped stairs with waist slab calculations and reinforcement layout
- **Water Tank Design** — Rectangular and circular underground and elevated tanks with hydrostatic pressure analysis and crack-width checks

#### 🔩 Steel Structure Design
- **Steel Section Design** — AISC-based steel member design with tension, compression, bending, and combined loading checks
- **Steel Connection Design** — Base plate, splice, and beam-column connection design with bolt and weld capacity verification
- **Steel Warehouse** — Complete steel warehouse/frame building design with purlin, girt, and bracing calculations
- **Comprehensive Steel Tables** — Full AISC steel section database with properties lookup and capacity charts

#### 🌊 Seismic Analysis
- **Equivalent Static Method** — Seismic base shear calculation per ECP 201, ACI, and SBC codes
- **Response Spectrum Analysis** — Spectral acceleration, period calculation, and story drift checks
- **Seismic Response Charts** — Visual representation of seismic forces, story shears, and displacement profiles

#### 📐 Frame Analysis
- **2D Portal Frame Analysis** — Full 2D structural frame analysis with moment distribution method
- **Member Forces** — Bending moment, shear force, and axial force diagrams for all frame members
- **Interactive Drawing Canvas** — Real-time visualization of frame geometry, loading, and internal force diagrams

#### 📊 Additional Tools
- **Bill of Quantities (BOQ)** — Automatic quantity takeoff with cost estimation for concrete, steel, formwork, and more
- **Scientific Calculator** — Built-in engineering calculator with trigonometric, logarithmic, and unit conversion functions
- **Unit Converter** — Comprehensive unit conversion tool covering length, area, volume, force, stress, and moment
- **Rebar Inventory** — Track and manage reinforcement bar inventory with weight and cost calculations
- **PDF & Excel Export** — Generate professional design reports and calculation sheets in PDF and Excel formats
- **Design Archive** — Save, organize, and retrieve past design projects with full calculation history

### 🏛️ Supported Design Codes

| Code | Standard | Country |
|------|----------|---------|
| **ECP** | ECP 203, ECP 201 | Egypt |
| **ACI** | ACI 318-19 | USA / International |
| **SBC** | SBC 304, SBC 301 | Saudi Arabia |

### 🛠️ Technical Architecture

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin 2.1.0 |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Dependency Injection** | Hilt (Dagger) |
| **Database** | Room (SQLite) |
| **Navigation** | Jetpack Navigation Compose |
| **Build System** | Gradle 8.13 with Version Catalog |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **JDK** | Java 21 |

### 📁 Project Structure

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
│   │   └── screens/             # 17+ design & tool screens
│   └── (traditional views)      # Legacy View-based screens
├── viewmodel/                   # MVVM ViewModels
├── utils/                       # PDF/Excel export, steel tables, converters
└── pricing/                     # Material pricing & cost management
```

### 📊 Project Stats

- **240 Kotlin source files** across the project
- **75,000+ lines of code**
- **17+ design screens** with full input, calculation, and drawing output
- **3 international design codes** fully implemented
- **42 drawable resources** for professional UI

### 🚀 Building the Project

#### Prerequisites
- JDK 21
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

### ⚠️ Disclaimer

> This application is for **educational and preliminary design purposes only**. All calculations should be verified by a licensed structural engineer. The developer is not responsible for any errors or damages resulting from the use of this application.

### 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<div id="العربية">

## العربية

### 📋 نظرة عامة

**مهندس مدني برو** هو تطبيق أندرويد شامل مصمم للمهندسين الإنشائيين وطلاب الهندسة المدنية ومحترفي البناء. يوفر مجموعة كاملة من أدوات التصميم والتحليل الإنشائي تغطي الخرسانة المسلحة والمنشآت الفولاذية والتحليل الزلزالي وجداول الكميات — كل ذلك متوافق مع كودات التصميم الدولية الرئيسية.

يدعم التطبيق **ثلاثة كودات تصميم**: الكود المصري (ECP 203)، وكود معهد الخرسانة الأمريكي (ACI 318)، وكود البناء السعودي (SBC)، مما يجعله متعدد الاستخدامات للمهندسين العاملين في الشرق الأوسط وحول العالم.

### ✨ الميزات الرئيسية

#### 🏢 تصميم الخرسانة المسلحة
- **تصميم الكمرات** — كمرات بسيطة الإسناد ومستمرة وشاربية وثابتة مع مخططات العزوم والقص وفحص الانحناء وحسابات التسليح التفصيلية
- **تصميم الأعمدة** — أعمدة قصيرة ونحيفة تحت الحمل المحوري والانحناء أحادي وثنائي المحور مع مخططات التفاعل P-M الكاملة
- **تصميم البلاطات** — بلاطات باتجاه واحد واتجاهين (صلبة ومرقيقة/وافل وبلاطات مسطحة) بمعاملات العزوم وفحص الانحناء
- **تصميم الأساسات** — أساسات منفصلة ومركبة ومرتبطة مع فحص ضغط التربة وتقدير الهبوط وتفاصيل التسليح
- **تصميم حوائط السند** — حوائط سند قنطرية مع ضغط التربة النشط والسلبي وفحص الانزلاق والانقلاب وتصميم الساق
- **تصميم السلالم** — سلالم مستقيمة وعلى شكل L وشكل U مع حسابات لوح السلم وتوزيع التسليح
- **تصميم خزانات المياه** — خزانات أرضية ومرتفعة مستطيلة ودائرية مع تحليل ضغط الماء الساكن وفحص عرض الشروخ

#### 🔩 تصميم المنشآت الفولاذية
- **تصميم المقاطع الفولاذية** — تصميم أعضاء فولاذية حسب AISC مع فحص الشد والضغط والانحناء والأحمال المركبة
- **تصميم الوصلات الفولاذية** — تصميم لوحات القاعدة والوصلات والوصلات كمر-عمود مع فحص سعة البراغي واللحامات
- **مخازن فولاذية** — تصميم كامل لمباني المخازن الفولاذية مع حسابات الروافع والأطواق والتقوية
- **جداول فولاذية شاملة** — قاعدة بيانات كاملة لمقاطع AISC مع خصائصها ورسم السعات

#### 🌊 التحليل الزلزالي
- **طريقة القوى الثابتة المكافئة** — حساب قوة القص القاعدي الزلزالية حسب كودات ECP 201 و ACI و SBC
- **تحليل طيف الاستجابة** — تسارع طيفي وحساب الدورة الزمنية وفحص الانحراف بين الطوابق
- **رسوم بيانية زلزالية** — تمثيل بصري للقوى الزلزالية وقص الطوابق وتوزيع الإزاحات

#### 📐 تحليل الهياكل الإطارية
- **تحليل إطارات بوابية ثنائية الأبعاد** — تحليل هيكلي كامل ثنائي الأبعاد بطريقة توزيع العزوم
- **قوى الأعضاء** — مخططات عزم الانحناء وقوة القص والقوة المحورية لجميع أعضاء الهيكل
- **لوحة رسم تفاعلية** — تصور فوري لهندسة الهيكل والأحمال ومخططات القوى الداخلية

#### 📊 أدوات إضافية
- **جداول الكميات** — جرد تلقائي للكميات مع تقدير التكاليف للخرسانة والحديد والقالب وغيرها
- **آلة حاسبة علمية** — آلة حاسبة هندسية مدمجة بوظائف حساب المثلثات واللوغاريتمات وتحويل الوحدات
- **محول الوحدات** — أداة شاملة لتحويل الوحدات تغطي الطول والمساحة والحجم والقوة والإجهاد والعزم
- **جرد حديد التسليح** — تتبع وإدارة مخزون حديد التسليح مع حسابات الوزن والتكلفة
- **تصدير PDF و Excel** — إنشاء تقارير تصميم احترافية وجداول حساب بتنسيق PDF و Excel
- **أرشيف التصاميم** — حفظ وتنظيم واسترجاع مشاريع التصميم السابقة مع سجل الحسابات الكامل

### 🏛️ كودات التصميم المدعومة

| الكود | المعيار | الدولة |
|--------|---------|--------|
| **ECP** | ECP 203, ECP 201 | مصر |
| **ACI** | ACI 318-19 | أمريكا / دولي |
| **SBC** | SBC 304, SBC 301 | السعودية |

### 🛠️ البنية التقنية

| المكوّن | التقنية |
|---------|---------|
| **اللغة** | Kotlin 2.1.0 |
| **إطار واجهة المستخدم** | Jetpack Compose + Material 3 |
| **حقن التبعيات** | Hilt (Dagger) |
| **قاعدة البيانات** | Room (SQLite) |
| **التنقل** | Jetpack Navigation Compose |
| **نظام البناء** | Gradle 8.13 مع Version Catalog |
| **الحد الأدنى للمصدر** | 26 (Android 8.0) |
| **المصدر المستهدف** | 35 (Android 15) |
| **JDK** | Java 21 |

### 📊 إحصائيات المشروع

- **240 ملف Kotlin** عبر المشروع
- **أكثر من 75,000 سطر برمجي**
- **أكثر من 17 شاشة تصميم** مع مدخلات وحسابات ورسومات كاملة
- **3 كودات تصميم دولية** مطبقة بالكامل
- **42 مورد رسومي** لواجهة مستخدم احترافية

### 🚀 بناء المشروع

#### المتطلبات المسبقة
- JDK 21
- Android SDK مع API 35
- Gradle 8.13 (يتم التعامل معه عبر Gradle Wrapper)

#### أوامر البناء
```bash
# استنساخ المستودع
git clone https://github.com/engahmed121314/civileg2.git
cd civileg2

# بناء ملف APK للتصحيح
./gradlew assembleDebug

# بناء ملف APK للإصدار
./gradlew assembleRelease

# تشغيل الاختبارات
./gradlew test

# بناء نظيف
./gradlew clean assembleDebug
```

سيكون ملف APK الناتج في المسار `app/build/outputs/apk/debug/app-debug.apk`.

### ⚠️ إخلاء المسؤولية

> هذا التطبيق لأغراض **تعليمية والتصميم المبدئي فقط**. يجب التحقق من جميع الحسابات بواسطة مهندس إنشائي مرخص. المطور غير مسؤول عن أي أخطار أو أضرار ناتجة عن استخدام هذا التطبيق.

### 📄 الترخيص

هذا المشروع مرخص بموجب ترخيص MIT — راجع ملف [LICENSE](LICENSE) للتفاصيل.

---

</div>

<div align="center">
  <p>Made with ❤️ for Civil Engineers Worldwide</p>
  <p>صُنع بـ ❤️ لمهندسي المدني حول العالم</p>
</div>
