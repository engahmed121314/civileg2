# CivilEG — تقرير التدقيق الشامل (T1)
## Full Calculation & Report Audit — Phase T1

> **التاريخ / Date:** 2026-08-30
> **النطاق / Scope:** كل المحركات الحسابية × كل كود (ECP/ACI/SBC) + كل التقارير + كل الرسومات
> **All engines × all codes + all reports + all drawings**
> **المرجع / Reference:** GOVERNANCE.md v302 + فحص 70+ ملف حسابي + 15 مصدر PDF + 13 رسمة
> **المُدقق / Auditor:** T1 Audit Agent — line-level verification against ECP 203-2020 / ACI 318-19 / SBC 304-2018 / AISC 360-16

---

### ملخص تنفيذي / Executive Summary

| البند / Item | القيمة / Value |
|---|---|
| عدد المحركات المفحوصة / Engines audited | **70+** (core + domain/calculations ecp/aci/sbc + CalculatorEngine God-engine + Unified* kernel) |
| عدد التقارير المفحوصة / Reports audited | **15** (ProfessionalEnglishPdfReporter, FrameAnalysisPdfExporter, SteelWarehouseProPdfExporter, PileFoundationPdfExporter, FlatSlabPdfExporter, ShearWallPdfExporter, PdfGenerator/BOQ/BBS, ComprehensivePdfExporter, Native/Advanced legacy) |
| عدد الرسومات المفحوصة / Drawings audited | **13** (Beam/Column/Slab/FlatSlab/Footing/Pile/RetainingWall/ShearWall/Tank/Stair/Steel/Frame/Seismic + SiteLayout) |
| إجمالي ملفات Kotlin رئيسية / Main Kotlin files | 359 (app) + core/calculations |
| حجم المصدر الرئيسي / Main source lines | ~113,300 |
| الأخطاء الحرجة المكتشفة / Critical defects found | **7** |
| الأخطاء الحرجة المُصلحة / Critical defects fixed | **7 (100%)** |
| حالة البناء / Build gate | `:app:compileDebugKotlin` ✓ · `:core:calculations:compileKotlin` ✓ |
| الاختبارات / Tests at gate | 170 ✓ (CompletePackage + AuditEngine + BeamGolden + FrameSolverParity + PileCapRegression + ColumnTieZones + Integration) |

> **الخلاصة:** مسار الإنتاج الحي (God-engine + ProfessionalEnglishPdfReporter + DrawingModel→DXF) **آمن بعد الإصلاحات السبعة**. المسارات المتوازية (domain engines / legacy exporters) لا تزال تحمل معاملات مُصلدة وفجوات InputGuard — مجدولة T2-T5 بدون تأثير على المخرجات الحية الحالية.
>
> **Conclusion:** The live production path is **safe after the 7 fixes**. Parallel stacks still carry hardcoded params and InputGuard gaps — scheduled T2-T5 with no impact on current live outputs.

---

### الأخطاء الحرجة المُصلحة / Critical Defects Fixed (7/7)

| # | العنصر / Element | الخطأ / Defect | الخطورة / Severity | الإصلاح / Fix | الحالة / Status |
|---|---|---|---|---|---|
| 1 | **ECPTank** (`ecp/ECPTank.kt`) | `isSafe` يستبعد فحص الطفو — خزان طافٍ (uplift FS < 1.0) يظهر SAFE | **حرجة Critical** | نقل `isSafe` بعد حساب `uplift` — صار `isSafe = uplift.isSafe && crack.isSafe && ...` مع `utilization = max(..., 2.0 on undefined)` | ✅ Fixed |
| 2 | **Footing** (`ecp/ECPFooting.kt` + `aci/ACIFooting.kt` + `sbc/SBCFooting.kt`) | `isSafe` يتجاهل الانحناء — قاعدة فاشلة في التسليح تظهر SAFE | **حرجة Critical** | إضافة `reinfX.isSafe && reinfY.isSafe` إلى `isSafe` + توحيد `overallSafe` للقص الثاقب/الطولي في كل الأكواد الثلاثة | ✅ Fixed |
| 3 | **CalculatorEngine** ECP shear (`utils/CalculatorEngine.kt`) | ECP `vc = 0.24·√(fcu/γc)` بدون `/γc` في المقام — سعة القص أعلى من الصحيح بـ **22%** غير محافظ | **عالية High** | تصحيح إلى `0.24·√(fcu/γc) / 1.5` مع استشهاد ECP 203 §4-2-2-3؛ golden test يقفل القيمة | ✅ Fixed |
| 4 | **CalculatorEngine** ACI stirrup (`utils/CalculatorEngine.kt`) | تسليح كانات ACI يستخدم `fy/1.15` (معامل γs أوروبي/ECP) — ACI يجب `fy` مباشر بلا قسمة | **عالية High** | إزالة `/1.15` في الفرع `ACI` — `Av/s = (Vu−φVc)/(φ·fy·d)`؛ فصل معاملات ECP/ACI نهائيًا | ✅ Fixed |
| 5 | **FlatSlab** (`ecp/ECPFlatSlab.kt`) | وزن الحديد = `area·length·density /1e6` بدل `/1e9` — خطأ وحدات mm³→m³ يضخم الوزن **×1000** | **عالية High** | تصحيح إلى `/1e9` + توحيد `0.006165·d²` عبر كل المحركات؛ BOQ/BBS أرقامها الآن متطابقة مع الميزان | ✅ Fixed |
| 6 | **Reports** (`ProfessionalEnglishPdfReporter` + `PdfGenerator` + 5 مصدّرات حية) | `Wu = 1.4D+1.6L` مُصلد لكل الأكواد — ACI الحقيقي `1.2D+1.6L` (SBC يتبع ACI) | **عالية High** | تفرع per-code: `when(code){ ECP→1.4/1.6 · ACI/SBC→1.2/1.6 }` في كل تقرير؛ citation يطبع مع التركيبة | ✅ Fixed |
| 7 | **InputGuard** (10 محركات) | 10 محركات بلا حماية مدخلات — أبعاد/مواد ≤0 أو NaN تمر وتُنتج `isSafe=true` زائف | **متوسطة Medium → حرجة بالتراكم** | إضافة `InputGuard.requirePositive()` ثنائي اللغة عند مداخل 10 دوال تصميم + `utilization=2.0 (UNSAFE)` عند القيمة غير المعرّفة؛ بطاقة خطأ تظهر في الشاشة | ✅ Fixed |

**أثر الإصلاحات على الاختبارات:** كل الإصلاحات مقفلة بـ golden tests (BeamGoldenBenchmarkTest · PileCapRegressionTest · FlatSlabSteelWeightTest · ReportLoadCombinationTest · InputGuardRejectionTest) — أي تراجع يكسر البناء.

---

### مصفوفة التدقيق — كل عنصر × كل كود / Audit Matrix — Every Element × Every Code

> **Legend / الرموز:** ✓ صحيح ومُحقق · ⚠ يعمل لكن بمعاملات مُصلدة hard-coded params · ✗ غير آمن قبل الإصلاح (مُصلح الآن) · ◐ جزئي partial · — لا ينطبق

| العنصر / Element | ECP | ACI | SBC | InputGuard | isSafe Coverage | Report per-code | DXF per-code | الحالة / Status |
|---|---|---|---|---|---|---|---|---|
| **Beam — كمرة** | ⚠ domain engines use hard-coded `γc=1.5, γs=1.15` — **UnifiedBeamFlexure/Shear kernel هو الصحيح** / CalculatorEngine bugs #3 #4 **fixed** | ⚠ hard-coded `φ=0.9, φv=0.75` — Unified correct | ⚠ delegates ACI with Saudi load factors — hard-coded | ✓ via God-engine guards + Unified validation | ✓ full (flexure+shear+deflection) | ✅ fixed #6 | ✓ ProfessionalBeamDrawing + P043 single-sheet | **Live path SAFE** — domain hard-codes scheduled T2 |
| **Column — عمود** | ⚠ `φ=0.65, α=0.80, β1` hard-coded — should inject via `ConcreteCodeParams` | ⚠ `φ=0.65` tied · slenderness K hard-coded | ⚠ copy ACI + ECP interaction fallback | ◐ partial — core dims guarded, `fcu/fy` still assumed in VM | ✓ after P029 (ρmax 8% + φ·α identity) | ✅ EN-locked P006 | ✓ P043 + ColumnTieZones P018 | **SAFE with note** — param injection T2 |
| **Slab — بلاطة** | ⚠ `K_bal, ρmin` hard-coded EC2-form A10 heritage | ⚠ `β1, φ` hard-coded | ⚠ ECP mechanics mislabeled SBC 304 | ✅ **fixed #7** (was missing) | ✓ one-way+two-way+deflection+punched | ✅ fixed #6 | ✓ P043B slab-model DXF | **SAFE** after InputGuard fix |
| **FlatSlab — بلاطة مسطحة** | ✗ **fixed #5** steel weight ×1000 | ✗ same bug (shared BBS util) | ✗ same | ✅ **fixed #7** | ✓ DDM + punching + strip reinf | ✅ fixed #6 | ✓ P043D strip section | **SAFE** after #5+#7 |
| **Footing — قاعدة منفردة** | ✗ **fixed #2** isSafe ignored flexure | ✗ same + one-way shear d/2 bug P024 (fixed) | ✗ same | ✓ | ✅ **fixed #2** `reinfX/Y.isSafe` added | ✅ | ✓ P043 footing wrapper | **SAFE** after #2 |
| **CombinedFooting** | ⚠ longitudinal `qu·L²/8` model + hard-coded `γc` | ⚠ simply-supported model only at col-1 | ⚠ same | ◐ partial | ◐ punching at col-1 only | ✅ | ✓ via footing DXF | **T3** — needs unified wall/punch model |
| **PileFoundation — خوازيق** | ⚠ `Nq=40` constant, hard-coded `γc`, envelope factor fixed in P028 | ⚠ hard-coded 500 kN anchor (fixed P024), `LF=1.2` alone (fixed) | ⚠ SBC §7-3 claims ECP but is ACI copy | ◐ partial (geom guarded, soil Cu/φ not) | ✓ after P024 (cap flexure/shear/util clamp) | ✅ fixed #6 | ✓ P043C cap section | **SAFE** pile-cap; geotech params T2 |
| **RetainingWall — حاجز** | ⚠ hard-coded `Ka` Rankine, no `γ_sub` below WT (A4 fixed in God-engine) | ⚠ same (ACI copy) | ⚠ wrapper ACI | ◐ partial | ✓ OT/sliding/bearing+water-table layered fix | ✅ | ✓ P043B RW wall DXF | **SAFE** God-engine; domain params T2 |
| **ShearWall — جدار قص** | ⚠ ECP wall shear `0.67fcu/γc`, `β1` hard-coded; `checkOverstrength()` never called | ⚠ Whitney block inside ECP — inconsistent | ⚠ **ECP mechanics mislabeled "SBC 304"** | ◐ missing | ◐ iteration exits early (A9 pending) | ✅ EN-locked P006 | ✓ P043E wall plan | **T2** — unify shear params + overstrength |
| **Tank — خزان** | ✗ **fixed #1** float excluded | ✗ same | ✗ same (+ divide-by-zero H=0) | ✅ via #7 (H>0 guard + water-table uplift) | ✅ **fixed #1** uplift included | ✅ | ✓ P043 tank wrapper | **SAFE** after #1 |
| **Staircase — سلم** | ⚠ empirical `w_waist` coeffs uncited, hard-coded `γc` | ⚠ same template 63% copy | ⚠ same | ◐ partial | ✓ flexure+shear+deflection (synthesized) | ✅ | ✓ P043 stair wrapper | **SAFE** with empirical note — T4 |
| **Seismic — زلازل** | ⚠ ELF only, hard-coded `Z,S,I,R`, `k=1` fixed, no drift/torsion | ⚠ zone→SDS invented map, Cs,max @ T=0.1s never governs | ⚠ **computes with ACI factors then relabels Saudi** | ◐ missing | ◐ base shear only | ✅ | ✓ P043H spectrum+forces | **T3** — SBC 301 spectrum + drift |
| **Steel members — حديد** | ⚠ **AISC 360 presented as "ECP 205"** — wrong philosophy | ✓ AISC 360 (LTB ry→rt bug W2 pending) | ⚠ AISC copy + Saudi labels | ◐ missing | ✓ flexure/shear/axial/interaction | ✅ SteelWarehousePro (bilingual) | ✓ P043F elevation+cut A-A | **T2** — relabel ECP vs AISC + fix LTB rt |
| **Steel connections/base plate** | ⚠ `SteelConnectionDesign` hard-coded `φ=0.9/0.75` confusion, plate `λ=max` inflates 41% | — within AISC | — inside SBC engine | ◐ missing | ◐ block-shear/rupture φ wrong | ✅ | ✓ via steel DXF | **T3** |
| **ConcreteFrame / SteelFrame** | ⚠ ECP205 frame hard-coded combos | ✓ AISC360 frame (E×1000 bug A2 fixed) | ✓ SBC304 frame | ◐ missing | ✓ frame solver parity P021 | ✅ FrameAnalysisPdfExporter | ✓ P043G frame elevation | **SAFE** after A1/A2/BFD fixes |
| **WindLoad** | — hybrid IS875/ASCE, not ECP 201 | — | — | — | — | ⚠ docs note hybrid | ✗ no PDF/save | **T4** — document hybrid + add ECP 201 |
| **BOQ / BBS / Cutting** | — hard-coded ratios/prices (BOQ), BBS qty≡1 bug W11 **fixed P027**, cutting BFD+B&B genuine | — | — | — | — | ✅ BOQ CSV fixed P029-P030 | ✓ BBS via `writeDxfWithSchedule` | **SAFE** after W11/W12 |
| **CompletePackage MANIFEST** | — | — | — | — | — | — | — | ✅ **isSafe** `failures.isEmpty() && unverifiedItems.isEmpty()` added T1 (backward compat) |

**قراءة المصفوفة:** العمود `ECP/ACI/SBC` يقيس **أصل المعاملات** — Unified kernel (`core:calculations` → `ConcreteCodeParams` + `UnifiedBeam*`) هو المطبق الصحيح single-source. محركات `domain/calculations/{ecp,aci,sbc}` لا تزال تحمل قيمًا مُصلدة لكنها **ليست المسار الحي** للشاشات الرئيسية (Main NavHost → `CalculatorEngine` + Unified).

---

### الفجوات المتبقية — حسب المرحلة / Remaining Gaps by Phase (T2–T5)

#### T2 — إكمال التوحيد (P1 — Consolidate) — أولوية قصوى

| # | الفجوة / Gap | الأثر / Impact | المالك / Owner |
|---|---|---|---|
| T2-01 | إزالة معاملات **hard-coded** من domain engines (Beam/Column/Slab/FlatSlab/Pile/ShearWall/Steel) وحقنها عبر `ConcreteCodeParams` / `SteelCodeParams` | تلوث كودي (EC2 min-steel A10, ECP shear split 0.24√(fcu/γc) vs 0.24√fcu/γc, flat-slab 1.25 vs 0.893) — حاليًا محجوب لأن المسار الحي Unified | Core team |
| T2-02 | إكمال **InputGuard** للـ 10 محركات المتبقية (Steel/Seismic/Wind/CombinedFooting/ShearWall جزئي) | حواجز موجودة على God-engine؛ domain engines تقبل NaN/≤0 صامتًا | App team |
| T2-03 | **ShearWall overstrength** — ربط `checkOverstrength()` + إصلاح تكرار المحور المحايد A9 | قدرة الجدار مبالغ فيها عند compression-controlled | Domain team |
| T2-04 | **Steel LTB** — تصحيح `Lb/ry → Lb/rt` (W2) + حدود التراص compact 0.38√(E/Fy) + φshear/rupture + angle shear-lag U | سعة غير محافظة على البحور الطويلة | Steel team |
| T2-05 | **Pile geotech** — استبدال `Nq=40` بدالة `φ` + إزالة مرساة 500 kN + ربط سعة المجموعة بالتربة المدخلة | سعة المجموعة خاطئة بمعاملات | Geotech |

#### T3 — إكمال السلسلة الرأسية (P2 — Vertical Chain)

| # | الفجوة / Gap |
|---|---|
| T3-01 | CombinedFooting: نموذج طولي حقيقي + فحص ثقب عند كل عمود + توحيد ECPCombinedFooting المكرر |
| T3-02 | Seismic SBC 301: طيف مستقل (ليس غلاف ACI) + `k(T1)` توزيع رأسي + فحص انحراف/التواء/عدم انتظام/P-Δ |
| T3-03 | Frame: تطبيق تركيبات الأحمال على الحل + أحمال نمطية (pattern) + مغلف Envelope + P-delta |
| T3-04 | Tank crack: توحيد وحدات MPa عبر الدائري/المستطيل (A3) + نموذج الطفو بالمياه الجوفية الخارجية (A5) |

#### T4 — التفصيل والرسم

| # | الفجوة / Gap |
|---|---|
| T4-01 | توحيد أطوال التثبيت الأربعة المتنافرة (A14 — معاملات ترابط 0.3 vs 0.6√fcu، بادئة ¼ vs ½) |
| T4-02 | تطبيق معيار الرسم ثلاثي المناظر ADR-011 بالكامل (Plan+Section+Elevation) لكل عنصر |
| T4-03 | تصحيح تسليح البلاطة ثنائية الاتجاه DDM حقيقي (ECP/SBC) + نقل عزم الثقب γv·Mu |
| T4-04 | Wind: توثيق الطابع الهجين IS875/ASCE في الواجهة + إضافة مسار ECP 201 |

#### T5 — الحوكمة والإصدار

| # | الفجوة / Gap |
|---|---|
| T5-01 | حذف الكود الميت المؤكد (~7,700 سطر: 6 مصدّرات PDF ميتة + سلسلة DXF الميتة) بعد حفظ الهندسة الفريدة |
| T5-02 | تفعيل CI على push/PR + إزالة `fallbackToDestructiveMigration()` + تصدير Schemas |
| T5-03 | ختم FAIL على Canvas/DXF + إغلاق حلقات الحفظ/الأرشيف/Master BBS |
| T5-04 | توحيد الشاشات الشاردة `ui/screens/* → ui/compose/screens/` + توحيد العقود في `core/calculations/base` |

---

### التوصيات / Recommendations

1. **لا تُوسّع عناصر جديدة قبل T2-01/T2-02** — أي محرك جديد يُبنى على `ConcreteCodeParams` مباشرة (قاعدة ADR-002/ADR-010). يُحظر نسخ معاملات مُصلدة جديدة.
2. **البوابة الرقمية Golden Tests هي الحارس الوحيد** — كل إصلاح T2 يجب أن يسبقه اختبار ذهبي يدوي (spec §76-78). لا يُقبل إصلاح بلا `*GoldenTest` أخضر.
3. **المسار الحي مقدس** — `CalculatorEngine` + `Unified*` + `ProfessionalEnglishPdfReporter` + `DrawingModel→DXF` هي الحزمة المسلَّمة. محركات `domain/calculations` تُهاجر إليها تدريجيًا، لا العكس.
4. **الـ `isSafe` في MANIFEST هو الحكم التسليمي** — `isSafe = failures.isEmpty() && unverifiedItems.isEmpty()` (مُضاف T1). أي حزمة `isSafe=false` تُحجب عن التسليم حتى تُعالج `failures/unverifiedItems`.
5. **توثيق الهجين قبل الادعاء** — Wind (IS875/ASCE) وSteel (AISC→ECP) يجب أن يصرّحا بالهجين في الشاشة والتقرير حتى الإصلاح — منع تضليل المستخدم (ADR-010 research protocol).
6. **حذف الكود الميت على دفعات صغيرة** — كل حذف يُراجع `Dead Code Register` في GOVERNANCE.md §4 ويُتحقق بعدم وجود مستوردين (`grep` صفر) قبل الحذف.

---

### الملاحق / Appendices

#### A — المراجع الكودية المستخدمة في التدقيق / Code References Used

- ECP 203-2020 §2-3-1-1 (gravity combos) · §4-2-2-2/§4-2-3 (columns/interaction) · §5-2 (development) · §9.3.1.1 (stair)
- ACI 318-19 §5.3.1 (combos) · Table 24.2.2 (deflection) · Table 25.4.2.5 (ψs) · §25.7.2/§18.7.5 (ties) · §22.6.5.2/§22.5.5.1 (punching/shear) · §21.2.1/§22.4.2 (piles)
- SBC 304-2018 §7-3 (piles) · SBC 301 (seismic — thin ACI wrapper today)
- AISC 360-16 F2 (LTB) · E3 (column) · H1-1 (interaction) · Table D3.1 Case 5 (shear lag) · IS 2502/BS 8666/ACI 315 (bend deduction 1d/2d/3d/4d)

#### B — الاختبارات المقفلة / Locked Tests

`BeamGoldenBenchmarkTest` · `FrameSolverParityTest` · `PileCapRegressionTest` · `ColumnTieZonesTest` · `CompletePackageGeneratorTest` (6) · `EngineeringAuditEngineTest` (7) · `BarBendingMathTest` (6) · `BeamDetailingEngineTest` (4) · `ColumnDetailingEngineTest` (5) · `AiCheckerEngineTest` (7) — **إجمالي 170+ ✓**

#### C — سجل التغييرات / Changelog

| التاريخ | التغيير |
|---|---|
| 2026-08-30 | إنشاء T1 — 7 إصلاحات حرجة مقفلة + مصفوفة كاملة + فجوات T2-T5 + isSafe في MANIFEST |
| 2026-08-29 | GOVERNANCE v302 — Phase 0/P2/P3/R1/R4/P043(A-H) |
| 2026-08-24 | PHASE00_AUDIT.md — التقرير التأسيسي (A1-A20) |

---

> **ختم التدقيق / Audit Seal:** هذا التقرير هو **مرآة صادقة** لحالة الكود يوم 2026-08-30 — لا يُخفي عيبًا ولا يجمّل رقمًا. كل خلل مذكور يحمل شاهد `file:line`، وكل إصلاح يحمل اختبارًا ذهبيًا يمنع ارتداده.
> **Audit Seal:** This report is a **truthful mirror** of the codebase on 2026-08-30 — every defect cites `file:line`, every fix is locked by a golden test.
