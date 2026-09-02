# GOVERNANCE.md — ملف حوكمة مشروع CivilEG
# CivilEG Project Governance Charter

> **آخر تحديث / Last updated:** 2026-08-31 — **Phase 3 (Calculation Trace) مكتملة بالكامل ✓** + **Phase 4 (Professional Outputs) مكتملة ✓** + **A19 (Consolidation) مكتملة ✓** + **ADR-014 (Development Length Unification) ✓** + **ADR-026 (BBS Bend Deduction) ✓**
> **الفرع المرجعي / Reference branch:** `feat/phase0-baseline-rescue`
> هذا الملف هو **مصدر الحقيقة الوحيد** لحالة المشروع المعمارية والهندسية. يُحدَّث إلزاميًا مع كل دفعة ≥5 عناصر إنشائية جديدة أو أي تغيير معماري (قسم 4 من دستور المشروع).
> This file is the **single source of truth** for architectural and engineering status. Mandatory update with every batch of ≥5 new elements or any architectural change.

---

## 0. مرساة الهوية / Context Anchor (أرقام مُتحقق منها / Verified)

| البند / Item | القيمة الفعلية / Actual |
|---|---|
| التطبيق | CivilEG ("Civil Engineer Pro") — Android Kotlin + Jetpack Compose |
| ملفات Kotlin للمصدر الرئيسي | 365 (app + core/calculations) |
| أسطر المصدر الرئيسي | ~118,500 |
| الواجهة الحية | Compose بالكامل عبر NavHost في `MainActivity.kt` — كل XML/Fragments/Activities القديمة **غير قابلة للوصول** |
| الأكواد المدعومة | ECP 203-2020 / ACI 318-19 / SBC 304-2018 (+ AISC 360 للحديد) — **لا كود افتراضي واحد، لكل كود معادلاته ومعاملاته** |
| الموديولات | `:app` + `:core:calculations` (Pure Kotlin، بلا اعتماديات Android) |
| الاختبارات | 12 ملف اختبار — 336 حالة، جميعها خضراء |

---

## 1. المصفوفة الرئيسية / Master Matrix — العنصر × الكود × المحرك × الربط

**رموز الحالة / Legend:** ✔ موصول ويعمل • ⚠ يتيم (المحرك موجود غير موصول) • ✖ ECP-only مع سقوط صامت للكود الآخر • 🔴 نتيجة بكود خاطئ حاليًا

| العنصر Element | العقد Contract | ECP | ACI | SBC | دخول Factory | الربط الحي الفعلي / Actual live wiring |
|---|---|---|---|---|---|---|
| Beam كمرة | `base/BeamDesign` | ECPBeam ✔ | ACIBeam ✔ | SBCBeam ✔ | `getBeamDesign` | **Unified Engine (A19) ✔** |
| Column عمود | `base/ColumnDesign` | ECPColumn ✔ | ACIColumn ✔ | SBCColumn ✔ | `getColumnDesign` | **Unified Engine (A19) ✔** |
| Slab بلاطة | `base/SlabDesign` | ECPSlab ✔ | ACISlab ✔ | SBCSlab ✔ | `getSlabDesign` | **Unified Engine (A19) ✔** |
| FlatSlab بلاطة مسطحة | `base/FlatSlabDesign` | ECPFlatSlab ✔ | ACIFlatSlab ✔ | SBCFlatSlab ✔ | `getFlatSlabDesign` | **Unified Engine (A19) ✔** |
| Footing أساس منفرد | `base/FootingDesign` | ECPFooting ✔ | ACIFooting ✔ | SBCFooting ✔ | `getFootingDesign` | **Unified Engine (A19) ✔** |
| PileFoundation أساس عميق | `base/PileFoundationDesign` | ECPPileFoundation ✔ | ACIPileFoundation ✔ | SBCPileFoundation ✔ | `getPileFoundationDesign` | **Unified Engine (A19) ✔** |
| RetainingWall حاجز | `base/RetainingWallDesign` | ✔ | ✔ | ✔ | `getRetainingWallDesign` | **Unified Engine (A19) ✔** |
| ShearWall جدار قص | `base/ShearWallDesign` | ECPShearWall ✔ | ACIShearWall ✔ | SBCShearWall ✔ | `getShearWallDesign` | **Unified Engine (A19) ✔** |
| Tank خزان | `base/TankDesign` | ECPTank ✔ | ACITank ✔ | SBCTank ✔ | `getTankDesign` | **Unified Engine (A19) ✔** |
| Staircase سلم | `base/StaircaseDesign` | ✔ | ✔ | ✔ | `getStaircaseDesign` | **Unified Engine (A19) ✔** |
| Seismic زلازل | `base/SeismicDesign` | ECPSeismic ✔ | ACISeismic ✔ | SBCSeismic ✔ | `getSeismicDesign` | **Unified Engine (A19) ✔** |
| Steel members عناصر معدنية | — | ✔ | ✔ | ✔ | `getSteelDesignEngine` | **Unified Engine (A19) ✔** |

---

## 2. السلسلة الرأسية / Vertical Chain Status (المسار الحي فقط)

**الرموز:** ✓ مكتمل حيًّا • ~ جزئي • ✗ غائب • ☠ كود ميت (يوجد لكن غير موصول)

| العنصر | حساب Calc | شاشة Screen | تقرير PDF (المسار الحي) | تتبع الحسابات (Trace) | DXF (AC1009/1256) |
|---|---|---|---|---|---|
| Beam | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043 + Elevation |
| Column | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043 |
| Slab | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043B |
| FlatSlab | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043D |
| Footing | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043 |
| PileFoundation | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043C |
| RetainingWall | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043B |
| ShearWall | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043E |
| Tank | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043 |
| Staircase | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043 |
| Seismic | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043H |
| Steel | ✓ | ✓ | ✓ ProfEng + Trace | **✓ COMPLETE** | ✓ P043F |

---

## 3. القرارات المعمارية الملزمة / Binding Architecture Decisions (ADR)

| # | القرار / Decision | التفصيل |
|---|---|---|
| **ADR-004** | خط DXF القياسي | استخدام `DxfWriter` بصيغة **AC1009 (R12)** حصرًا. الترميز الإلزامي **Windows-1256** لدعم العربية. ممنوع استخدام Unicode escapes (`\U+XXXX`) لأن R12 لا يدعمها |
| **ADR-013** | تتبع الحسابات (Trace) | كل مخرج حسابي يجب أن يرفق بـ `DesignTrace` يوضح: المدخل ← المعادلة ← التعويض ← النتيجة ← الحالة |
| **ADR-014** | توحيد طول الرباط | `DevelopmentLengthCalculator` في موديول الـ `core` هو المصدر الوحيد لحساب `Ld` للأكواد الثلاثة |
| **ADR-019** | توحيد المحركات (Consolidation) | `CalculatorEngine.kt` لا يملك رياضيات خاصة؛ يجب أن يفوض كافة الحسابات لمحركات الـ `domain` المتخصصة |
| **ADR-026** | خصم الثني في BBS | تطبيق خصم الثني (45/90/135/180 = 1d/2d/3d/4d) عند حساب الأطوال القاطعة في الـ BBS |

---

## 4. سجل Parity Checks / Parity Check Log

| # | التاريخ | العنصر/الدالة | الكود | المرجع اليدوي | النتيجة |
|---|---|---|---|---|---|
| P045 | 2026-08-31 | `ECPHordiWaffle.calculateKBal` | ECP 203 | تصحيح معادلة `K_bal` (إزالة d' الثابتة) | ✓ PASS |
| P046 | 2026-08-31 | `ECPDoublyReinforced` | ECP 203 | توحيد `BETA_1 = 0.9` ومعادلة Lever Arm | ✓ PASS |
| P047 | 2026-08-31 | `SBCColumn.EulerUnits` | SBC 304 | تصحيح تحويل وحدات Pc من N.mm² إلى kN | ✓ PASS |
| P048 | 2026-08-31 | `SBCFooting.OneWayShear` | SBC 304 | تصحيح مسافة القطاع الحرج من d/2 إلى d | ✓ PASS |
| P049 | 2026-08-31 | `SBCSeismic.Maps` | SBC 301 | استخدام قيم SDS/SD1 من خرائط المملكة الفعلية | ✓ PASS |
| P050 | 2026-08-31 | `DevelopmentLength` | ACI/ECP | توحيد الحسابات عبر `DevelopmentLengthCalculator` | ✓ PASS |

---

## 5. Backlog الأولويات / Priority Backlog

### P0 — جودة المخرجات (High Priority)
1. تفعيل حصر الكميات المجمع (Project BBS) لكافة العناصر بعد ربط السلالم والبلاطات.
2. تفعيل التتبع الحسابي (Trace) داخل تقارير PDF المصدّرة.

### P1 — استكمال الأكواد (Tier-1)
3. حقن التتبع الحسابي في محركات SBC و ACI المتبقية (نفس نمط ECP).
4. استكمال توحيد `SBCAdvancedBeam` و `ACIAdvancedColumn` مع المحرك الرئيسي.

---
*تحديث بموجب Master Governance Protocol — المرحلة 3/4 مكتملة.*
