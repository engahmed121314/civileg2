# Contributing to Civil Engineer Pro | دليل المساهمة في المهندس المدني برو

[English](#english) | [العربية](#العربية)

---

<div id="english">

## English

Thank you for your interest in contributing to **Civil Engineer Pro**! We welcome community contributions to help improve civil engineering tools for engineers, students, and practitioners worldwide.

### Code of Conduct

By participating in this project, you agree to treat everyone with respect, adhere to constructive communication, and prioritize user safety and calculation accuracy above all else.

### How Can You Contribute?

- 🐛 **Report Bugs**: Notice calculation inaccuracies or app crashes? [Open a Bug Report](.github/ISSUE_TEMPLATE/bug_report.md).
- 💡 **Suggest Features**: Have an idea for a new design code or element? [Submit a Feature Request](.github/ISSUE_TEMPLATE/feature_request.md).
- 📝 **Improve Documentation**: Enhance equations explanations, add code references, or improve translations.
- 💻 **Submit Code**: Add new calculation modules, write tests, improve UI/UX, or optimize performance.

---

### Development Setup

#### Prerequisites
- **JDK 17** (Temurin recommended)
- **Android Studio Meerkat** or newer
- **Android SDK** with Platform API 36
- **Git**

#### Steps
```bash
# 1. Fork and clone
git clone https://github.com/engahmed121314/civileg2.git
cd civileg2

# 2. Open project in Android Studio
# Allow Gradle Sync to download dependencies

# 3. Build debug APK
./gradlew assembleDebug

# 4. Run tests
./gradlew testDebugUnitTest
```

---

### Project Architecture & Conventions

- **Language**: Kotlin 2.1+ following official Kotlin style guides
- **Architecture**: MVVM with Clean Architecture layers
  - `domain/calculations/`: Calculation engines segregated by code (`ecp/`, `aci/`, `sbc/`) implementing base interfaces in `base/`.
  - `domain/entities/`: Immutable data transfer objects.
  - `ui/compose/`: Declarative UI using Jetpack Compose + Material 3.
  - `viewmodel/`: State management exposing immutable `StateFlow`.
  - `db/`: Room database with DAOs and TypeConverters.
- **Dependency Injection**: Hilt (Dagger).
- **Internationalization**: All strings MUST exist in both `values/strings.xml` (English) and `values-ar/strings.xml` (Arabic).
- **Engineering Calculations**: Must include reference citations (e.g., *ECP 203 Clause 4-2-1*, *ACI 318-19 Table 22.2.2.4.3*, *SBC 304 Clause 10.3*).

---

### Pull Request (PR) Workflow

1. Fork the repo and create a dedicated branch: `git checkout -b feat/add-strap-footing`
2. Follow commit conventions: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`
3. Ensure all tests pass: `./gradlew testDebugUnitTest`
4. Verify hand calculations for any algorithmic updates.
5. Submit PR targeting `master` branch using our PR template.

---

### Security Guidelines

Do **NOT** post security vulnerabilities in public issues or PRs. Please refer to [SECURITY.md](SECURITY.md) to report security issues privately to **engahmed121314@gmail.com**.

</div>

---

<div id="العربية" dir="rtl">

## العربية

شكراً لاهتمامك بالمساهمة في مشروع **المهندس المدني برو**! نرحب بمساهمات المجتمع التقني والهندسي لتطوير أفضل أدوات التصميم الإنشائي للمهندسين والطلاب حول العالم.

### كيف يمكنك المساهمة؟

- 🐛 **الإبلاغ عن أخطاء**: واجهت مشكلة في الحسابات أو توقف للتطبيق؟ [افتح بلاغ خطأ](.github/ISSUE_TEMPLATE/bug_report.md).
- 💡 **اقتراح ميزات جديدة**: لديك فكرة لكود تصميم جديد أو عنصر إنشائي؟ [قدّم طلب ميزة](.github/ISSUE_TEMPLATE/feature_request.md).
- 📝 **تحسين التوثيق**: توضيح معادلات التصميم، إضافة مراجع بنود الأكواد، أو تحسين الترجمات.
- 💻 **كتابة كود برمجي**: إضافة كودات جديدة، كتابة اختبارات دقيقة، أو تحسين الواجهات والأداء.

---

### بيئة التطوير

- **JDK 17**
- **Android Studio Meerkat** أو أحدث
- **Android SDK API 36**

```bash
git clone https://github.com/engahmed121314/civileg2.git
cd civileg2
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

---

### معايير الكود والإرشادات

1. **ثنائية اللغة**: جميع النصوص والعناصر يجب أن تتوفر باللغتين العربية والإنجليزية.
2. **التحقق الهندسي**: أي تعديل في خوارزميات التصميم يجب تدعيمه بحسابات يدوية واختبارات وحدة (Unit Tests).
3. **الأمان**: عدم كشف أو تضمين مفاتيح خاصة أو استدعاءات خارجية غير آمنة.
4. **المعمارية**: الالتزام بنمط Clean Architecture + MVVM + Hilt.

</div>
