# Security Policy | سياسة الأمان

[English](#english) | [العربية](#العربية)

---

<div id="english">

## English

Civil Engineer Pro takes software security and intellectual property protection very seriously. Because engineering calculations guide real-world structures, both calculation integrity and codebase protection are paramount.

### Supported Versions

| Version | Supported | Security Patches |
|---------|:---------:|:----------------:|
| 1.7.x   |    ✅     |       Yes        |
| < 1.7.0 |    ❌     |        No        |

---

### Reporting a Vulnerability

If you discover a security vulnerability or algorithmic integrity issue, please **DO NOT** create a public issue.

Send a detailed private report to:
📧 **engahmed121314@gmail.com**

#### Report Checklist:
1. Module affected (e.g., Auth, Licensing, Calculation engine, Database).
2. Step-by-step reproduction guide.
3. Proof-of-concept payload or execution trace (if applicable).
4. Potential business or structural impact.

We commit to acknowledging reports within **48 hours** and issuing remediations within **5 to 10 business days**.

---

### Security Measures & Anti-Piracy Architecture

Civil Engineer Pro incorporates multi-layered security controls to protect the application and its proprietary calculation algorithms:

1. **Google Play Integrity API**:
   - Device licensing validation and verification that the binary runs on genuine Google Play certified devices.
   - Detects sideloading, app clones, emulators configured with tampering tooling, and patched APKs.

2. **R8 / ProGuard Obfuscation & Tree-Shaking**:
   - Core proprietary design engines (`domain/calculations/`) have internal algorithms, variable names, and helper functions aggressively obfuscated.
   - Debug logging, assert statements, and console outputs are stripped in release builds (`-assumenosideeffects`).

3. **Data Security & Privacy at Rest**:
   - SQLite/Room database stores user designs strictly locally on the sandbox filesystem.
   - No external leakage of project plans, dimensions, client names, or costs.
   - Sensitive settings encrypted using standard Android Keystore / AES primitives.

4. **Network & Communications**:
   - Offline-first execution: No structural calculations require network calls.
   - AdMob & Google Play Billing run via encrypted HTTPS endpoints with strict TLS pinning defaults.

</div>

---

<div id="العربية" dir="rtl">

## العربية

يولي تطبيق **المهندس المدني برو** أمان البرمجيات وحماية الملكية الفكرية أهمية قصوى، نظراً لحساسية الحسابات الإنشائية وأهمية حماية الشيفرة المصدرية من السرقة والتلاعب.

### الإصدارات المدعومة

نحن نوفر تحديثات الأمان والحماية لأحدث إصدار مستقر (الإصدارات 1.7 وما بعدها).

---

### الإبلاغ عن الثغرات الأمنية

في حال اكتشاف ثغرة أمنية أو خطأ في تكامل الحسابات، يرجى **عدم** نشرها في تذاكر الدعم العامة (Public Issues).

يرجى إرسال تقرير خاص ومباشر إلى:
📧 **engahmed121314@gmail.com**

يتضمن التقرير وصف الثغرة، خطوات إعادة إنتاجها، وأثرها المحتمل. سنقوم بالرد خلال 48 ساعة والعمل على إصدار التحديث اللازم.

---

### تدابير الحماية ومكافحة سرقة التطبيق

تم تزويد التطبيق بنظام دفاعي متعدد الطبقات:

1. **حماية Google Play Integrity**:
   - التحقق من تشغيل التطبيق على أجهزة أصلية ومرخصة من متجر Google Play.
   - كشف النسخ المقرصنة والمعدلة وتطبيقات الـ Mod أو المحاكيات المشبوهة.

2. **تشويش وتشفير الكود (R8 / ProGuard)**:
   - خوارزميات التصميم والمعادلات الهندسية مشفرة بأسماء ومسارات عشوائية في نسخ الإصدار (Release).
   - إزالة جميع بيانات التصحيح وأوامر السجلات (Logs) تلقائياً لمنع الهندسة العكسية.

3. **حفظ البيانات وخصوصيتها**:
   - تعمل جميع الحسابات أوفلاين بنسبة 100% دون إرسال أي أبعاد أو مشاريع إلى خوادم خارجية.
   - حماية ملفات المشاريع داخل النطاق الآمن لنظام أندرويد (App Sandbox).

</div>
