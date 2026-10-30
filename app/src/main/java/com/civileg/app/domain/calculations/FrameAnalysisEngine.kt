package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.SteelTables
import java.util.TreeSet
import kotlin.math.*

/**
 * محرك تحليل الهياكل الإنشائية بطريقة مصفوفة الجساءة (Stiffness Matrix Method)
 *
 * يدعم:
 * - إطارات ثنائية الأبعاد (2D Frames)
 * - عقد بحرية ومقيدة (حرة، مفصلية، بكرة، تثبيت كامل، بكرة رأسية)
 * - أحمال عقدية (قوى + عزوم)
 * - أحمال على الأعضاء (منتظمة، مركزة، عزوم، متغيرة خطياً)
 * - حساب القوى الداخلية ورسم المخططات
 *
 * ── المرحلة الأولى (Phase 1) — إصلاحات حرجة C1..C5 + إصلاحات إشارات ──
 *
 * C1  حماية شاملة من NaN/Infinity في كل المدخلات والمخرجات (فحص مسبق + فحص لاحق).
 * C2  كشف المصفوفة الشاذة (relative pivot tolerance) + فحص المتبقيات Residual Check.
 * C3  كشف العقد المعزولة + التحقق من اتصال كل مكوّن بالارتكازات (BFS).
 * C4  التحقق من مرجعية الأعضاء (عقد موجودة، لا حلقة ذاتية، طول صالح، معرفات فريدة).
 * C5  التحقق من الأحمال (قيم منتهية، مواقع داخل الطول، أعضاء/عقد موجودة) وخصائص المقاطع.
 *
 * S1  (Sign fix) متجه الأحلال المكافئة P_eq = +FEF (كان معكوساً) — كل نتائج أحمال
 *     الأعضاء كانت خاطئة الإشارة (مثال مُتحقق: كمرية بسيطة w=10,L=6 كانت تعطي
 *     M_mid=105 kN.m والصحيح 45 kN.m).
 * S2  (Sign fix) معادلة مخطط العزوم: M(x) = -Mi + Vi·x - (الأحمال) — إشارة عزم
 *     النهاية كانت معكوسة (الحالة المثبتة: كمرية مثبتة الطرفين تعطي الآن
 *     M_end = -wL²/12 و M_mid = +wL²/24 كما في جداول FEM القياسية).
 * S3  (Formula fix) معادلة FEF للعزم المركز تم اشتقاقها صحيحة من شروط الإزاحة
 *     v(0)=v(L)=v'(0)=v'(L)=0 والتحقق من الاتزان (المعادلة القديمة كانت تخالف
 *     الاتزان). الحمولات عند نهايات العضو تُقرَّب داخلياً (clamp) بدل إسقاطها.
 * S4  (Fallback fix) fallback مقطع الحديد: بحث بالاسم "IPE 300" بدل
 *     ipeSections[13] الذي كان يعطي IPE 400 فعلياً.
 *
 * ── المرحلة الثانية (Phase 2) — C6..C13 (تدقيق ما بعد التشغيل الفعلي للاختبارات) ──
 *
 * C6  (Bug fix) النماذج المثبتة بالكامل (صفر درجات حرية حرة) كانت تُرفض بخطأ
 *     "مقيّد بالكامل" — والصحيح هندسياً أنها حالة قابلة للحل: U=0، قوى النهايات
 *     = -FEF، ردود الأفعال = -F.
 * C7  (Bug fix) عينات المخططات كانت 21 نقطة منتظمة ثابتة — تفوّت قمم العزوم عند
 *     مواقع الأحمال المركزة. الآن: مواقع الأحمال + a±ε + L/4، L/2، 3L/4.
 * C8  (Safety) فحص الاتزان العام ΣFx/ΣFy/ΣMz بعد الحل — المتبقيات تُخزن في
 *     FrameAnalysisResult (equilibriumFx/Fy/Mz) ويُرفض الحل إذا اختلت (≥1e-3 نسبياً).
 * C9  (Feature) مغلف القوى عبر توافيق الأحمال (DL/LL/WL) — حل منفصل لكل توفيق
 *     ومغلف max/min (M,V,N) لكل عضو مع اسم التوفيق الحاكم.
 * C10 (Feature) خيار الوزن الذاتي للأعضاء (includeSelfWeight): خرسانة γ=25 kN/m³،
 *     حديد من وزن المقطع (kg/m × 9.81/1000) — يُضاف كحمل UDL بحالة DL.
 * C11 (Audit) توثيق وحدات eColumn (≈25 GPa وليس "25 MPa") — المحرك لا يستخدمه
 *     ويحسب Ec = 4700·√fcu لكل عضو.
 * C12 (Sign fix) مخطط القوة المحورية: N(x) = -n_i (شد موجب) — كان يرسم الشد
 *     بالسالب (n_i سالب في الشد). العرف الهندسي: الشد موجب.
 * C13 (Consistency) اختبارات اتساق قوى النهايات مع المخططات: V(0)=v_i، M(0)=-m_i.
 *
 * اتفاقية الإشارات الموثقة (y-up, عزوم CCW+):
 *   - FEF (المتجه المعاد من getFixedEndForces) = قوى النهاية الثابتة التي يبذلها
 *     العضو على العقد = الأحمال العقدية المكافئة P_eq = -q0 حيث q0 = قوى المؤازرة
 *     على العضو عند التثبيت الكامل.
 *   - تجميع متجه الأحمال: F += FEF_global   (P_eq)
 *   - قوى نهايات العضو: q_end = k·u_local + q0 = k·u_local - FEF_local
 *   - العزم الداخلي: M(x) = -m_i + v_i·x - (مساهمات الأحمال)، القص: V(x) = v_i - (...)
 */
object FrameAnalysisEngine {

    private const val DOF_PER_NODE = 3  // dx, dy, rz

    /** أقل طول مقبول للعضو (م) — أقل من ذلك يُرفض بدل تجاهله بصمت */
    private const val MIN_MEMBER_LENGTH_M = 1e-6

    /** سماحية المحور النسبي في الغاوس (كشف المصفوفة الشاذة/الميكانيزم) */
    private const val PIVOT_REL_TOL = 1e-12

    /** أقصى متبقٍ نسبي مقبول ||K·u - F||/||F|| */
    private const val RESIDUAL_REL_TOL = 1e-6

    /** هامش تثبيت موقع الحمولة داخل العضو [clamp] لتفادي إسقاط الأحمال الطرفية */
    private const val LOAD_POS_EPS = 1e-9

    /** C8: أقصى متبقٍ نسبي مقبول للاتزان العام ||ΣR + ΣF||/||ΣF|| */
    private const val EQUILIBRIUM_REL_TOL = 1e-3

    /** C10: وزن وحدة حجم الخرسانة المسلحة (kN/m³) — متسق مع CalculatorEngine */
    private const val CONCRETE_UNIT_WEIGHT_KN_M3 = 25.0

    /** C10: تسارع الجذبية (m/s²) لتحويل kg/m إلى kN/m */
    private const val G_ACCEL = 9.81

    // ========================================================================
    // Public data for UI (Equations & Solution Steps panel)
    // ========================================================================

    /**
     * خصائص جساءة عضو — تُعرض في لوحة "المعادلات وخطوات الحل".
     */
    data class MemberStiffnessProps(
        val memberId: Int,
        val E: Double,          // kN/m²  معامل المرونة الفعّال
        val A: Double,          // m²     مساحة المقطع
        val I: Double,          // m⁴     عزم القصور الفعّال (متشقق للخرسانة)
        val Ig: Double,         // m⁴     عزم القصور الكلي (خرسانة فقط، وإلا = I)
        val L: Double,          // m      الطول
        val angleDeg: Double,   // درجة   زاوية الميل مع الأفقي
        val sectionLabel: String,
        val materialType: FrameMaterialType
    )

    // ========================================================================
    // Main Solver
    // ========================================================================

    /**
     * حل الإطار بالكامل — wrapper يطبّق ميزات المرحلة الثانية C9/C10.
     *
     *  1. يضيف الوزن الذاتي للأعضاء إذا فُعّل (C10).
     *  2. يحسب مغلف القوى عبر توافيق الأحمال (C9).
     *  3. يتحقق من الاتزان العام (C8) داخل solveCore ويُكشف في النتيجة.
     *
     * @return FrameAnalysisResult يحتوي على إزاحات، قوى داخلية، ومخططات
     */
    fun solveFrame(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings
    ): FrameAnalysisResult {
        // PERFORMANCE METRIC: Measure solver execution time
        val startTimeNs = System.nanoTime()

        // ── C10: الوزن الذاتي يُضم كأحمال UDL بحالة "DL" ──
        val effectiveMemberLoads = memberLoads + selfWeightLoads(members, settings)

        // ── C9: حل منفصل لكل توفيق أحمال + بناء مغلف القوى ──
        val envelopes = computeLoadEnvelopes(nodes, members, nodalLoads, effectiveMemberLoads, settings, startTimeNs)
        if (envelopes != null) {
            // أعد الحل الأساسي بدون توفيقات للنتيجة الأساسية (للحفاظ على التوافق مع الواجهة)
            val baseResult = solveCore(nodes, members, nodalLoads, effectiveMemberLoads, settings, startTimeNs)
            if (!baseResult.isSolved) return baseResult
            return baseResult.copy(loadEnvelopes = envelopes)
        }

        // ── مسار واحد: حل الإطار مرة واحدة ──
        return solveCore(nodes, members, nodalLoads, effectiveMemberLoads, settings, startTimeNs)
    }

    /**
     * الحل الأساسي للإطار: validation → assembly → partitioning → solve → diagrams.
     *
     * يتضمن فحص الاتزان العام (C8) وتخزين المتبقيات في النتيجة. كما يعالج مسار
     * صفر DOF (C6) للحالات المثبتة بالكامل.
     */
    private fun solveCore(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        startTimeNs: Long
    ): FrameAnalysisResult {
        // ── InputGuard (ADR-010) — أخطاء المبرمج: نموذج فارغ يفشل بصوت عالٍ ──
        InputGuard.notEmpty("nodes", nodes)
        InputGuard.notEmpty("members", members)
        InputGuard.notNull("settings", settings)

        try {
            // ── C1..C5: تحقق شامل من النموذج قبل أي حساب (رسائل ثنائية) ──
            validateModel(nodes, members, nodalLoads, memberLoads, settings)?.let {
                return FrameAnalysisResult(errorMessage = it)
            }

            val numNodes = nodes.size
            val totalDOF = numNodes * DOF_PER_NODE

            // فهرس O(1) للعقد والأعضاء (يستبدل indexOfFirst المتداخل O(m·n))
            val nodeIndex = HashMap<Int, Int>(numNodes * 2)
            nodes.forEachIndexed { idx, n -> nodeIndex[n.id] = idx }
            val memberById = members.associateBy { it.id }

            // 1. Build global stiffness matrix
            val Kg = Array(totalDOF) { DoubleArray(totalDOF) }
            for (member in members) {
                assembleMemberStiffness(member, nodeIndex, nodes, settings, Kg)
            }

            // 2. Build load vector (nodal loads + equivalent loads from member loads)
            val F = DoubleArray(totalDOF)
            for (load in nodalLoads) {
                val nodeIdx = nodeIndex[load.nodeId] ?: continue // تم التحقق في validateModel
                F[nodeIdx * 3] += load.fx
                F[nodeIdx * 3 + 1] += load.fy
                F[nodeIdx * 3 + 2] += load.mz
            }
            // Equivalent nodal loads from member loads (fixed-end forces):
            //   P_eq = +FEF_global  (S1: كانت معكوسة الإشارة سابقاً)
            for (mLoad in memberLoads) {
                val member = memberById[mLoad.memberId] ?: continue // تم التحقق في validateModel
                val ni = nodeIndex[member.nodeI] ?: continue
                val nj = nodeIndex[member.nodeJ] ?: continue

                val fefLocal = getFixedEndForces(member, mLoad, nodes)

                // local: [fi_x, fi_y, mi_z, fj_x, fj_y, mj_z]  →  global
                val cosA = member.getCosTheta(nodes)
                val sinA = member.getSinTheta(nodes)
                val fefGlobal = DoubleArray(6)
                fefGlobal[0] = fefLocal[0] * cosA - fefLocal[1] * sinA
                fefGlobal[1] = fefLocal[0] * sinA + fefLocal[1] * cosA
                fefGlobal[2] = fefLocal[2]
                fefGlobal[3] = fefLocal[3] * cosA - fefLocal[4] * sinA
                fefGlobal[4] = fefLocal[3] * sinA + fefLocal[4] * cosA
                fefGlobal[5] = fefLocal[5]

                // S1 FIX: P_eq = +FEF_global (العضو يبذلها على العقد)
                F[ni * 3] += fefGlobal[0]; F[ni * 3 + 1] += fefGlobal[1]; F[ni * 3 + 2] += fefGlobal[2]
                F[nj * 3] += fefGlobal[3]; F[nj * 3 + 1] += fefGlobal[4]; F[nj * 3 + 2] += fefGlobal[5]
            }

            // 3. Identify restrained DOFs (BooleanArray — بحث O(1) بدل List.contains)
            val restrained = BooleanArray(totalDOF)
            var restrainedCount = 0
            for ((idx, node) in nodes.withIndex()) {
                for (dof in node.support.restrainedDOFs) {
                    restrained[idx * 3 + dof] = true
                    restrainedCount++
                }
            }

            // 4. Partition and solve
            val freeDOFs = IntArray(totalDOF - restrainedCount)
            var fPtr = 0
            for (d in 0 until totalDOF) if (!restrained[d]) freeDOFs[fPtr++] = d

            // ── C6: مسار صفر DOF (الإطار مقيّد بالكامل) ──
            // هندسياً: U = 0 → q_end = -FEF (لا إزاحات)، R = -F (ردود أفعال = -الأحمال)
            if (freeDOFs.isEmpty()) {
                val U_zero = DoubleArray(totalDOF) // كلها صفر
                return finalizeFullyRestrainedSolution(
                    nodes, members, memberLoads, settings, Kg, F, U_zero, startTimeNs
                )
            }

            val Kff = Array(freeDOFs.size) { i -> DoubleArray(freeDOFs.size) { j -> Kg[freeDOFs[i]][freeDOFs[j]] } }
            val Ff = DoubleArray(freeDOFs.size) { F[freeDOFs[it]] }

            val Uf = solveLinearSystem(Kff, Ff)
                ?: return FrameAnalysisResult(
                    errorMessage = "مصفوفة الجساءة شاذة (ميكانيزم/عدم استقرار) — تحقق من الارتكازات والوصلات أو العقد غير المتصلة / Singular stiffness matrix (mechanism) — check supports & connectivity"
                )

            if (Uf.any { it.isNaN() || it.isInfinite() }) {
                return FrameAnalysisResult(errorMessage = "نتائج غير منطقية (NaN/Inf) — تحقق من استقرار المنشأ / Non-finite solution — check stability")
            }

            // C2: فحص المتبقيات النسبي ||Kff·Uf - Ff||∞ / ||Ff||∞
            val residualError = relativeResidual(Kff, Uf, Ff)
            if (residualError != null && residualError > RESIDUAL_REL_TOL) {
                return FrameAnalysisResult(
                    errorMessage = "الحل العددي غير مستقر (متبقٍ نسبي = %.2e) — المنشأ قريب من الميكانيزم / Ill-conditioned system (rel. residual = %.2e)".format(residualError, residualError)
                )
            }

            // 5. Full displacement vector
            val U = DoubleArray(totalDOF)
            for (i in freeDOFs.indices) U[freeDOFs[i]] = Uf[i]

            // 6. Compute reactions:  R = K·U - F
            val reactions = DoubleArray(totalDOF)
            for (i in 0 until totalDOF) {
                var sum = 0.0
                for (j in 0 until totalDOF) sum += Kg[i][j] * U[j]
                reactions[i] = sum - F[i]
            }
            if (reactions.any { it.isNaN() || it.isInfinite() }) {
                return FrameAnalysisResult(errorMessage = "ردود الأفعال غير منتهية — تحقق من النموذج / Non-finite reactions")
            }

            // 7. Member end forces + diagrams
            val memberForces = mutableListOf<MemberEndForces>()
            val memberDiagrams = mutableListOf<MemberDiagram>()

            for (member in members) {
                val ni = nodeIndex[member.nodeI] ?: continue
                val nj = nodeIndex[member.nodeJ] ?: continue

                val L = member.getLength(nodes)
                val cosA = member.getCosTheta(nodes)
                val sinA = member.getSinTheta(nodes)

                // Member stiffness properties
                val E = getE(member, settings)
                val (A, I) = getSectionProperties(member, settings, L)
                val k = memberLocalStiffness(E, A, I, L)

                // Local end displacements
                val uGlobalI = doubleArrayOf(U[ni * 3], U[ni * 3 + 1], U[ni * 3 + 2])
                val uGlobalJ = doubleArrayOf(U[nj * 3], U[nj * 3 + 1], U[nj * 3 + 2])
                val uLocal = globalToLocalDisplacements(uGlobalI, uGlobalJ, cosA, sinA)

                // Local member forces = k·u_local
                val fLocal = DoubleArray(6) {
                    var sum = 0.0
                    for (j in 0..5) sum += k[it][j] * uLocal[j]
                    sum
                }

                // Total end forces: q_end = k·u - FEF  (بما أن FEF = -q0)
                var fefThisMember = DoubleArray(6)
                val memberLoadList = memberLoads.filter { it.memberId == member.id }
                for (mLoad in memberLoadList) {
                    val fef = getFixedEndForces(member, mLoad, nodes)
                    for (i in 0..5) fefThisMember[i] += fef[i]
                }
                val fTotal = DoubleArray(6) { fLocal[it] - fefThisMember[it] }

                if (fTotal.any { it.isNaN() || it.isInfinite() }) {
                    return FrameAnalysisResult(
                        errorMessage = "قوى غير منتهية للعضو #${member.id} — تحقق من خصائص المقطع / Non-finite forces for member #${member.id}"
                    )
                }

                // Convert to global
                val fiX = fTotal[0] * cosA - fTotal[1] * sinA
                val fiY = fTotal[0] * sinA + fTotal[1] * cosA
                val fjX = fTotal[3] * cosA - fTotal[4] * sinA
                val fjY = fTotal[3] * sinA + fTotal[4] * cosA

                memberForces.add(MemberEndForces(
                    memberId = member.id,
                    fi_x = fiX, fi_y = fiY, mi_z = fTotal[2],
                    fj_x = fjX, fj_y = fjY, mj_z = fTotal[5]
                ))

                // Generate diagrams
                val diagram = generateDiagrams(member, nodes, fTotal, memberLoadList, settings)
                memberDiagrams.add(diagram)
            }

            // 8. Node results
            val nodeResults = nodes.mapIndexed { idx, node ->
                val localRestrained = node.support.restrainedDOFs
                NodeResult(
                    nodeId = node.id,
                    dx = U[idx * 3],
                    dy = U[idx * 3 + 1],
                    rz = U[idx * 3 + 2],
                    reactionFx = if (0 in localRestrained) reactions[idx * 3] else 0.0,
                    reactionFy = if (1 in localRestrained) reactions[idx * 3 + 1] else 0.0,
                    reactionMz = if (2 in localRestrained) reactions[idx * 3 + 2] else 0.0
                )
            }

            val durationMs = (System.nanoTime() - startTimeNs) / 1e6
            println("Frame analysis solved in ${"%.2f".format(durationMs)} ms ($numNodes nodes, ${members.size} members, $totalDOF DOFs)")

            // ── C8: فحص الاتزان العام — ΣR + ΣF = 0 و Σ(M + x·Fy − y·Fx) = 0 ──
            val (eqFx, eqFy, eqMz) = computeGlobalEquilibrium(nodes, nodalLoads, memberLoads, memberForces, reactions, nodeResults)
            val appliedMag = sqrt(eqFx*eqFx + eqFy*eqFy + eqMz*eqMz)
            if (appliedMag > EQUILIBRIUM_REL_TOL * maxOf(1.0,
                    nodalLoads.sumOf { abs(it.fx) + abs(it.fy) } +
                    memberLoads.sumOf { abs(it.value) })) {
                return FrameAnalysisResult(
                    errorMessage = "الاتزان العام غير محقق (resFx=${"%.2e".format(eqFx)}, resFy=${"%.2e".format(eqFy)}, resMz=${"%.2e".format(eqMz)}) / Global equilibrium violated"
                )
            }

            return FrameAnalysisResult(
                nodeResults = nodeResults,
                memberEndForces = memberForces,
                memberDiagrams = memberDiagrams,
                isSolved = true,
                equilibriumFx = eqFx,
                equilibriumFy = eqFy,
                equilibriumMz = eqMz
            )
        } catch (e: Exception) {
            return FrameAnalysisResult(errorMessage = "خطأ في التحليل: ${e.message}")
        }
    }

    // ========================================================================
    // C6: Finalize fully-restrained solution (zero free DOFs)
    // ========================================================================

    /**
     * C6: معالجة النماذج المثبتة بالكامل — U=0 لكن قوى النهايات = -FEF،
     * ردود الأفعال = -F. هذا مسار صالح هندسياً.
     */
    private fun finalizeFullyRestrainedSolution(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        Kg: Array<DoubleArray>,
        F: DoubleArray,
        U: DoubleArray,
        startTimeNs: Long
    ): FrameAnalysisResult {
        val nodeIndex = nodes.withIndex().associate { it.value.id to it.index }
        val memberById = members.associateBy { it.id }
        val totalDOF = nodes.size * 3

        // C6: في مسار صفر DOF يكون U=0 → K·U=0 → R = -F
        val reactions = DoubleArray(totalDOF)
        for (i in 0 until totalDOF) {
            var sum = 0.0
            for (j in 0 until totalDOF) sum += Kg[i][j] * U[j]
            reactions[i] = sum - F[i] // = 0 - F[i] = -F[i]
        }

        // قوى النهايات: q_end = k·u_local - FEF = -FEF (لأن u=0)
        val memberForces = mutableListOf<MemberEndForces>()
        val memberDiagrams = mutableListOf<MemberDiagram>()
        for (member in members) {
            val ni = nodeIndex[member.nodeI] ?: continue
            val nj = nodeIndex[member.nodeJ] ?: continue
            val cosA = member.getCosTheta(nodes)
            val sinA = member.getSinTheta(nodes)

            // FEF هذا العضو (محلي)
            var fefThisMember = DoubleArray(6)
            val memberLoadList = memberLoads.filter { it.memberId == member.id }
            for (mLoad in memberLoadList) {
                val fef = getFixedEndForces(member, mLoad, nodes)
                for (i in 0..5) fefThisMember[i] += fef[i]
            }
            // q_end = -FEF (لأن u_local = 0)
            val fTotal = DoubleArray(6) { -fefThisMember[it] }

            val fiX = fTotal[0] * cosA - fTotal[1] * sinA
            val fiY = fTotal[0] * sinA + fTotal[1] * cosA
            val fjX = fTotal[3] * cosA - fTotal[4] * sinA
            val fjY = fTotal[3] * sinA + fTotal[4] * cosA

            memberForces.add(MemberEndForces(
                memberId = member.id,
                fi_x = fiX, fi_y = fiY, mi_z = fTotal[2],
                fj_x = fjX, fj_y = fjY, mj_z = fTotal[5]
            ))
            val diagram = generateDiagrams(member, nodes, fTotal, memberLoadList, settings)
            memberDiagrams.add(diagram)
        }

        val nodeResults = nodes.mapIndexed { idx, node ->
            val localRestrained = node.support.restrainedDOFs
            NodeResult(
                nodeId = node.id,
                dx = U[idx * 3], dy = U[idx * 3 + 1], rz = U[idx * 3 + 2],
                reactionFx = if (0 in localRestrained) reactions[idx * 3] else 0.0,
                reactionFy = if (1 in localRestrained) reactions[idx * 3 + 1] else 0.0,
                reactionMz = if (2 in localRestrained) reactions[idx * 3 + 2] else 0.0
            )
        }

        // C8: فحص الاتزان العام في مسار صفر DOF
        val (eqFx, eqFy, eqMz) = computeGlobalEquilibrium(
            nodes, emptyList(), memberLoads, memberForces, reactions, nodeResults
        )

        val durationMs = (System.nanoTime() - startTimeNs) / 1e6
        println("Frame analysis (fully restrained, C6) solved in ${"%.2f".format(durationMs)} ms")

        return FrameAnalysisResult(
            nodeResults = nodeResults,
            memberEndForces = memberForces,
            memberDiagrams = memberDiagrams,
            isSolved = true,
            equilibriumFx = eqFx,
            equilibriumFy = eqFy,
            equilibriumMz = eqMz
        )
    }

    // ========================================================================
    // C8: Global equilibrium check (ΣFx, ΣFy, ΣMz residuals)
    // ========================================================================

    /**
     * C8: متبقيات الاتزان العام (يجب أن تكون ≈ 0):
     *   resFx = ΣFx,reaction + ΣFx,applied
     *   resFy = ΣFy,reaction + ΣFy,applied
     *   resMz = ΣMz,reaction + Σ(Mz,applied + x·Fy − y·Fx)
     */
    private fun computeGlobalEquilibrium(
        nodes: List<FrameNode>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        memberForces: List<MemberEndForces>,
        reactions: DoubleArray,
        nodeResults: List<NodeResult>
    ): Triple<Double, Double, Double> {
        // مجموع ردود الأفعال
        var sumRx = 0.0
        var sumRy = 0.0
        var sumMz = 0.0
        for ((idx, node) in nodes.withIndex()) {
            val rx = nodeResults[idx].reactionFx
            val ry = nodeResults[idx].reactionFy
            val mz = nodeResults[idx].reactionMz
            sumRx += rx
            sumRy += ry
            sumMz += mz + node.x * ry - node.y * rx
        }
        // مجموع الأحمال العقدية
        var sumFx = 0.0
        var sumFy = 0.0
        var sumMzApplied = 0.0
        for (nl in nodalLoads) {
            val node = nodes.find { it.id == nl.nodeId } ?: continue
            sumFx += nl.fx
            sumFy += nl.fy
            sumMzApplied += nl.mz + node.x * nl.fy - node.y * nl.fx
        }
        // الأحمال على الأعضاء تساهم عبر قوى النهايات المساوية لـ -FEF،
        // لكن FEF يحفظ الاتزان ذاتياً (مجموع FEF = 0 لكل حمولة). لذا لا نضيفها مرتين.
        // المتبقيات:
        val resFx = sumRx + sumFx
        val resFy = sumRy + sumFy
        val resMz = sumMz + sumMzApplied
        return Triple(resFx, resFy, resMz)
    }

    // ========================================================================
    // C9 + C10: Self-weight loads + Load combinations envelope
    // ========================================================================

    /**
     * C10: أحمال الوزن الذاتي للأعضاء — تُعاد كقائمة MemberLoad (UDL هابط، حالة "DL")
     *
     * خرسانة: w = γ·b·h = 25 × (b·h بالمتر المربع) kN/m
     * حديد:   w = (kg/m) × g/1000 = (kg/m) × 9.81/1000 kN/m
     */
    private fun selfWeightLoads(
        members: List<FrameMember>,
        settings: FrameAnalysisSettings
    ): List<MemberLoad> {
        if (!settings.includeSelfWeight) return emptyList()
        val loads = mutableListOf<MemberLoad>()
        for (m in members) {
            val wPerM = when (m.materialType) {
                FrameMaterialType.Concrete -> {
                    val cs = m.concreteSection ?: continue
                    val b = cs.width / 1000.0  // m
                    val h = cs.depth / 1000.0  // m
                    CONCRETE_UNIT_WEIGHT_KN_M3 * b * h  // kN/m
                }
                FrameMaterialType.Steel -> {
                    val sec = SteelTables.getSectionByName(m.steelSectionName ?: "IPE 300")
                        ?: SteelTables.getSectionByName("IPE 300")
                        ?: continue
                    // weight = mass(kg/m) × g(m/s²) / 1000 = kN/m
                    sec.massPerMeter * G_ACCEL / 1000.0
                }
            }
            if (wPerM.isFinite() && wPerM > 0.0) {
                loads.add(MemberLoad(
                    memberId = m.id,
                    loadType = MemberLoadType.UDL,
                    value = wPerM,
                    position = 0.0,
                    loadCase = "DL"
                ))
            }
        }
        return loads
    }

    /**
     * C9: مغلف القوى الداخلية عبر جميع توافيق الأحمال.
     * يحل النموذج لكل توفيق (DL/LL/WL × معامل) ويحدد max/min(M, V, N) لكل عضو.
     * يعيد null إذا لم تكن توافيق مُعرّفة أو لم يكن هناك أحمال DL/LL/WL.
     */
    private fun computeLoadEnvelopes(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        startTimeNs: Long
    ): List<MemberForceEnvelope>? {
        val combos = settings.loadCombinations
        if (combos.isEmpty()) return null
        // هل لدينا أحمال موسومة بحالات؟ إن لم تكن موسومة صراحة، نُعامل الكل كـ DL.
        val hasLoadCases = memberLoads.any { it.loadCase.isNotBlank() && it.loadCase != "DL" } ||
            nodalLoads.any { it.loadCase.isNotBlank() && it.loadCase != "DL" }
        if (!hasLoadCases && combos.size <= 1) return null

        val envelopes = mutableMapOf<Int, MutableList<Pair<String, MemberEndForces>>>()
        for (combo in combos) {
            // صفّ الأحمال حسب التوفيق
            val scaledNodal = nodalLoads.map { nl ->
                val factor = when (nl.loadCase.uppercase()) {
                    "DL" -> combo.dlFactor
                    "LL" -> combo.llFactor
                    "WL" -> combo.wlFactor
                    else -> combo.dlFactor
                }
                nl.copy(fx = nl.fx * factor, fy = nl.fy * factor, mz = nl.mz * factor)
            }.filter { it.fx != 0.0 || it.fy != 0.0 || it.mz != 0.0 }

            val scaledMember = memberLoads.map { ml ->
                val factor = when (ml.loadCase.uppercase()) {
                    "DL" -> combo.dlFactor
                    "LL" -> combo.llFactor
                    "WL" -> combo.wlFactor
                    else -> combo.dlFactor
                }
                ml.copy(value = ml.value * factor)
            }.filter { it.value != 0.0 }

            if (scaledNodal.isEmpty() && scaledMember.isEmpty()) continue

            // حل الإطار لهذا التوفيق (recursive لكن بدون إعادة حساب المغلف)
            val comboSettings = settings.copy(loadCombinations = emptyList())
            val result = solveCore(nodes, members, scaledNodal, scaledMember, comboSettings, startTimeNs)
            if (!result.isSolved) continue

            for (mf in result.memberEndForces) {
                envelopes.getOrPut(mf.memberId) { mutableListOf() }.add(combo.name to mf)
            }
        }

        if (envelopes.isEmpty()) return emptyList()

        // بناء المغلف لكل عضو
        return members.map { m ->
            val list = envelopes[m.id] ?: emptyList()
            if (list.isEmpty()) {
                MemberForceEnvelope(m.id, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "—")
            } else {
                val allMoments = list.flatMap { listOf(it.second.mi_z, it.second.mj_z) }
                val maxMoment = allMoments.maxOrNull() ?: 0.0
                val minMoment = allMoments.minOrNull() ?: 0.0
                val maxShear = list.maxOfOrNull { it.second.maxShear } ?: 0.0
                val minShear = -(list.maxOfOrNull { it.second.maxShear } ?: 0.0)
                val maxAxial = list.maxOfOrNull { it.second.axialForce } ?: 0.0
                val minAxial = -(list.maxOfOrNull { it.second.axialForce } ?: 0.0)
                // التوفيق الحاكم = الأعلى |M|
                val governing = list.maxByOrNull {
                    maxOf(abs(it.second.mi_z), abs(it.second.mj_z))
                }?.first ?: "—"
                MemberForceEnvelope(
                    memberId = m.id,
                    maxMoment = maxMoment,
                    minMoment = minMoment,
                    maxShear = maxShear,
                    minShear = minShear,
                    maxAxial = maxAxial,
                    minAxial = minAxial,
                    governingCombination = governing
                )
            }
        }
    }

    // ========================================================================
    // Model Validation (C1..C5) — يُعيد رسالة خطأ أو null إذا كان النموذج سليماً
    // ========================================================================

    private fun validateModel(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings
    ): String? {
        if (nodes.size < 2) {
            return "يجب إدخال عقدتين على الأقل / At least two nodes are required"
        }

        // ── C1: إحداثيات منتهية + معرفات عقد فريدة ──
        val nodeIds = HashSet<Int>(nodes.size * 2)
        for (n in nodes) {
            if (!n.x.isFinite() || !n.y.isFinite()) {
                return "إحداثيات العقدة #${n.id} غير صالحة (NaN/∞) / Node #${n.id} has non-finite coordinates"
            }
            if (!nodeIds.add(n.id)) {
                return "معرف العقدة #${n.id} مكرر — يجب أن تكون المعرفات فريدة / Duplicate node ID #${n.id}"
            }
        }

        // ── C4 + C5: التحقق من الأعضاء وخصائصها ──
        val memberIds = HashSet<Int>(members.size * 2)
        val memberCountByNode = HashMap<Int, Int>(nodes.size * 2)
        for (m in members) {
            if (!memberIds.add(m.id)) {
                return "معرف العضو #${m.id} مكرر — يجب أن تكون المعرفات فريدة / Duplicate member ID #${m.id}"
            }
            if (m.nodeI !in nodeIds) {
                return "العضو #${m.id} يشير إلى عقدة بداية غير موجودة (#${m.nodeI}) / Member #${m.id} references missing start node #${m.nodeI}"
            }
            if (m.nodeJ !in nodeIds) {
                return "العضو #${m.id} يشير إلى عقدة نهاية غير موجودة (#${m.nodeJ}) / Member #${m.id} references missing end node #${m.nodeJ}"
            }
            if (m.nodeI == m.nodeJ) {
                return "العضو #${m.id} يبدأ وينتهي عند نفس العقدة (طول صفري) / Member #${m.id} is a self-loop (zero length)"
            }
            val L = m.getLength(nodes)
            if (!L.isFinite() || L < MIN_MEMBER_LENGTH_M) {
                return "طول العضو #${m.id} غير صالح (${"%.4f".format(L)} m) — تحقق من إحداثيات العقد / Member #${m.id} has invalid length"
            }
            when (m.materialType) {
                FrameMaterialType.Concrete -> {
                    val cs = m.concreteSection
                        ?: return "العضو الخرساني #${m.id} بدون خصائص مقطع / Concrete member #${m.id} has no section"
                    if (!cs.width.isFinite() || !cs.depth.isFinite() || cs.width <= 0.0 || cs.depth <= 0.0) {
                        return "أبعاد مقطع العضو #${m.id} غير صالحة (b=${cs.width}, h=${cs.depth} mm) / Member #${m.id} has invalid section dimensions"
                    }
                    if (!cs.fcu.isFinite() || cs.fcu <= 0.0) {
                        return "مقاومة الخرسانة fcu للعضو #${m.id} غير صالحة (${cs.fcu} MPa) / Member #${m.id} has invalid fcu"
                    }
                }
                FrameMaterialType.Steel -> {
                    if (!settings.eSteel.isFinite() || settings.eSteel <= 0.0) {
                        return "معامل مرونة الحديد E غير صالح (${settings.eSteel} kN/m²) / Invalid steel modulus E"
                    }
                    val secName = m.steelSectionName?.trim()
                    if (!secName.isNullOrBlank() && SteelTables.getSectionByName(secName) == null) {
                        return "مقطع الحديد '$secName' للعضو #${m.id} غير معروف — تحقق من الاسم / Unknown steel section '$secName' for member #${m.id}"
                    }
                }
            }
            memberCountByNode[m.nodeI] = (memberCountByNode[m.nodeI] ?: 0) + 1
            memberCountByNode[m.nodeJ] = (memberCountByNode[m.nodeJ] ?: 0) + 1
        }

        // ── C3: العقد المعزولة (بلا أعضاء وغير مثبتة بالكامل → صفوف صفرية → شذوذ) ──
        val isolated = nodes.filter { (memberCountByNode[it.id] ?: 0) == 0 && it.support != SupportType.Fixed }
        if (isolated.isNotEmpty()) {
            val ids = isolated.joinToString(", ") { "#${it.id}" }
            return "عقدة معزولة غير مرتكزة: $ids — اربطها بعضو أو اجعل ارتكازها تثبيتاً كاملاً / Isolated unsupported node(s): $ids"
        }

        // ── C3: كل مكوّن متصل يجب أن يلمس ارتكازاً واحداً على الأقل (BFS) ──
        val adjacency = HashMap<Int, MutableList<Int>>(members.size * 4)
        for (m in members) {
            adjacency.getOrPut(m.nodeI) { mutableListOf() }.add(m.nodeJ)
            adjacency.getOrPut(m.nodeJ) { mutableListOf() }.add(m.nodeI)
        }
        val nodeById = nodes.associateBy { it.id }
        val visited = HashSet<Int>(nodes.size * 2)
        for (start in nodes) {
            if (start.id in visited) continue
            val queue = ArrayDeque<Int>()
            queue.add(start.id); visited.add(start.id)
            val component = mutableListOf(start.id)
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                for (nb in adjacency[cur].orEmpty()) {
                    if (nb !in visited) { visited.add(nb); queue.add(nb); component.add(nb) }
                }
            }
            val hasRestraint = component.any { nid -> nodeById[nid]?.support?.restrainedDOFs?.isNotEmpty() == true }
            if (!hasRestraint) {
                val ids = component.joinToString(", ") { "#$it" }
                return "مجموعة عقد غير متصلة بأي ارتكاز ($ids) — أضف ارتكازاً أو صِل الأعضاء / Component ($ids) is not connected to any support"
            }
        }

        // ── C5: التحقق من الأحمال ──
        for (nl in nodalLoads) {
            if (nl.nodeId !in nodeIds) {
                return "حمولة عقدية على عقدة غير موجودة #${nl.nodeId} / Nodal load on missing node #${nl.nodeId}"
            }
            if (!nl.fx.isFinite() || !nl.fy.isFinite() || !nl.mz.isFinite()) {
                return "قيم حمولة عقدية غير صالحة على العقدة #${nl.nodeId} (NaN/∞) / Non-finite nodal load at node #${nl.nodeId}"
            }
        }
        val memberLenById = members.associate { it.id to it.getLength(nodes) }
        for (ml in memberLoads) {
            if (ml.memberId !in memberIds) {
                return "حمولة على عضو غير موجود #${ml.memberId} / Member load on missing member #${ml.memberId}"
            }
            if (!ml.value.isFinite() || !ml.position.isFinite()) {
                return "قيمة/موقع حمولة غير صالح على العضو #${ml.memberId} (NaN/∞) / Non-finite member load on member #${ml.memberId}"
            }
            if (ml.loadType == MemberLoadType.PointLoad || ml.loadType == MemberLoadType.Moment) {
                val L = memberLenById[ml.memberId] ?: 0.0
                if (ml.position < -1e-9 || ml.position > L + 1e-9) {
                    return "موقع الحمولة على العضو #${ml.memberId} خارج الطول (a=${"%.3f".format(ml.position)} m، L=${"%.3f".format(L)} m) / Load position out of range on member #${ml.memberId}"
                }
            }
        }
        return null
    }

    // ========================================================================
    // Stiffness Matrix Assembly
    // ========================================================================

    private fun assembleMemberStiffness(
        member: FrameMember,
        nodeIndex: Map<Int, Int>,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings,
        Kg: Array<DoubleArray>
    ) {
        val ni = nodeIndex[member.nodeI] ?: return
        val nj = nodeIndex[member.nodeJ] ?: return

        val L = member.getLength(nodes)
        if (L < MIN_MEMBER_LENGTH_M) return // دفاع عمقي — تم التحقق في validateModel
        val cosA = member.getCosTheta(nodes)
        val sinA = member.getSinTheta(nodes)
        val E = getE(member, settings)
        val (A, I) = getSectionProperties(member, settings, L)

        val kl = memberLocalStiffness(E, A, I, L)
        val T = transformationMatrix(cosA, sinA)
        val Tt = transpose(T, 6, 6)
        val klT = multiply(Tt, kl, 6, 6, 6)
        val KgMember = multiply(klT, T, 6, 6, 6)

        // Assemble into global
        val dofs = intArrayOf(ni * 3, ni * 3 + 1, ni * 3 + 2, nj * 3, nj * 3 + 1, nj * 3 + 2)
        for (i in 0..5) {
            for (j in 0..5) {
                Kg[dofs[i]][dofs[j]] += KgMember[i][j]
            }
        }
    }

    /**
     * مصفوفة الجساءة المحلية للعضو (6x6)
     * DOFs: [u_i, v_i, θ_i, u_j, v_j, θ_j]
     */
    private fun memberLocalStiffness(E: Double, A: Double, I: Double, L: Double): Array<DoubleArray> {
        val k = Array(6) { DoubleArray(6) }
        val c = E * A / L
        val a = 12.0 * E * I / (L * L * L)
        val b = 6.0 * E * I / (L * L)
        val d = 4.0 * E * I / L
        val e = 2.0 * E * I / L

        k[0][0] = c;  k[0][3] = -c
        k[1][1] = a;  k[1][2] = b;   k[1][4] = -a;  k[1][5] = b
        k[2][1] = b;  k[2][2] = d;   k[2][4] = -b;  k[2][5] = e
        k[3][0] = -c; k[3][3] = c
        k[4][1] = -a; k[4][2] = -b;  k[4][4] = a;   k[4][5] = -b
        k[5][1] = b;  k[5][2] = e;   k[5][4] = -b;  k[5][5] = d
        return k
    }

    /**
     * مصفوفة التحويل من المحلي للعام (6x6)
     */
    private fun transformationMatrix(c: Double, s: Double): Array<DoubleArray> {
        return arrayOf(
            doubleArrayOf(c, s, 0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(-s, c, 0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, c, s, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, -s, c, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        )
    }

    // ========================================================================
    // Fixed-End Forces
    // ========================================================================

    /**
     * قوى النهاية الثابتة للحمولات على الأعضاء — باتفاقية P_eq (قوى يبذلها العضو
     * على العقد عند تثبيت الطرفين) = -q0 حيث q0 قوى المؤازرة على العضو.
     *
     * القيمة الموجبة للقوى تعني حمولة هابطة (اتجاه -y المحلي) وللعزوم CCW+.
     *
     * S3: فرع العزم المركز أُعيد اشتقاقه من شروط الإزاحة والاتزان. الحمولات
     * الطرفية تُثبَّت داخل العضو (clamp) فتنتقل طبيعياً للعقدة بدل إسقاطها.
     */
    private fun getFixedEndForces(
        member: FrameMember,
        load: MemberLoad,
        nodes: List<FrameNode>
    ): DoubleArray {
        val L = member.getLength(nodes)
        val w = load.value

        return when (load.loadType) {
            MemberLoadType.UDL -> {
                // q0 = [0, +wL/2, +wL²/12, 0, +wL/2, -wL²/12]  →  P_eq = -q0
                doubleArrayOf(0.0, -w * L / 2.0, -w * L * L / 12.0, 0.0, -w * L / 2.0, w * L * L / 12.0)
            }
            MemberLoadType.PointLoad -> {
                // S3: تثبيت الموقع داخل العضو حتى تنتقل الحمولة الطرفية للعقدة
                val a = load.position.coerceIn(LOAD_POS_EPS, L - LOAD_POS_EPS)
                val b = L - a
                val P = w
                doubleArrayOf(
                    0.0,
                    -P * b * b * (L + 2 * a) / (L * L * L),
                    -P * a * b * b / (L * L),
                    0.0,
                    -P * a * a * (L + 2 * b) / (L * L * L),
                    P * a * a * b / (L * L)
                )
            }
            MemberLoadType.Moment -> {
                // S3 (مُعاد اشتقاقه): عزم M (CCW+) عند مسافة a من العقدة I
                //   q0 = [0, +2M·b(2L-3a)/L³, +M·b(L-3a)/L², 0, -2M·b(2L-3a)/L³, +M(2L²-6aL+3a²)/L²]
                //   P_eq = -q0
                // تحقق: a→L يعطي [0,0,0,0,0,+M] (عزم عُقدي عند J) ✓
                val a = load.position.coerceIn(LOAD_POS_EPS, L - LOAD_POS_EPS)
                val b = L - a
                val M = w
                doubleArrayOf(
                    0.0,
                    -2.0 * M * b * (2.0 * L - 3.0 * a) / (L * L * L),
                    -M * b * (L - 3.0 * a) / (L * L),
                    0.0,
                    2.0 * M * b * (2.0 * L - 3.0 * a) / (L * L * L),
                    -M * (2.0 * L * L - 6.0 * a * L + 3.0 * a * a) / (L * L)
                )
            }
            MemberLoadType.LinearVarying -> {
                // حمولة مثلثية (0 عند I، w عند J، هابطة):
                //   q0 = [0, +3wL/20, +wL²/30, 0, +7wL/20, -wL²/20]  →  P_eq = -q0
                doubleArrayOf(
                    0.0,
                    -w * L * 3.0 / 20.0,
                    -w * L * L / 30.0,
                    0.0,
                    -w * L * 7.0 / 20.0,
                    w * L * L / 20.0
                )
            }
        }
    }

    /** واجهة عامة لعرض FEF في لوحة المعادلات (P_eq المحلي) */
    fun memberLoadEquivalentForces(
        member: FrameMember,
        load: MemberLoad,
        nodes: List<FrameNode>
    ): DoubleArray = getFixedEndForces(member, load, nodes)

    // ========================================================================
    // Diagram Generation
    // ========================================================================

    /**
     * توليد مخططات القوى الداخلية (عزم، قص، محوري) لعضو واحد
     *
     * S2 FIX: M(x) = -m_i + v_i·x - (مساهمات الأحمال)
     *         (كانت الإشارة +m_i — تعطي عزوم نهايات معكوسة)
     */
    private fun generateDiagrams(
        member: FrameMember,
        nodes: List<FrameNode>,
        fLocalTotal: DoubleArray, // Total local end forces (q_end = k·u - FEF)
        memberLoadList: List<MemberLoad>,
        settings: FrameAnalysisSettings
    ): MemberDiagram {
        val L = member.getLength(nodes)

        // ── C7: بناء مجموعة مواقع العينات ──
        // مواقع الأحمال المركزة + a±ε + L/4, L/2, 3L/4 + طرفا العضو.
        val sampleSet = TreeSet<Double>()
        sampleSet.add(0.0)
        sampleSet.add(L)
        // نقاط منتظمة (21 نقطة كثافة مناسبة)
        for (i in 1 until 21) sampleSet.add(L * i / 20.0)
        // مواقع الأحمال + جوارها
        for (mLoad in memberLoadList) {
            if (mLoad.loadType == MemberLoadType.PointLoad || mLoad.loadType == MemberLoadType.Moment) {
                val a = mLoad.position.coerceIn(0.0, L)
                sampleSet.add(a)
                sampleSet.add((a - LOAD_POS_EPS * 10).coerceIn(0.0, L))   // a⁻ε
                sampleSet.add((a + LOAD_POS_EPS * 10).coerceIn(0.0, L))   // a⁺ε
            }
        }
        // نقاط ربع وثمن
        sampleSet.add(L / 4.0)
        sampleSet.add(L / 2.0)
        sampleSet.add(3.0 * L / 4.0)
        val samples = sampleSet.toList()

        val momentPoints = ArrayList<DiagramPoint>(samples.size)
        val shearPoints = ArrayList<DiagramPoint>(samples.size)
        val axialPoints = ArrayList<DiagramPoint>(samples.size)

        // End forces in local system
        val vI = fLocalTotal[1]  // shear at I (local y)
        val mI = fLocalTotal[2]  // moment at I (CCW joint-on-member)
        val nI = fLocalTotal[0]  // axial at I

        for (x in samples) {
            var V = 0.0
            var M = 0.0
            // C12: N(x) = -n_i (شد موجب — n_i سالب في الشد)
            val N = -nI

            // S2: الإشارة الصحيحة لعزم النهاية
            V = vI
            M = -mI + vI * x

            // Add member load contributions (نفس اتفاقية P_eq: قوى موجبة = هابطة)
            for (mLoad in memberLoadList) {
                when (mLoad.loadType) {
                    MemberLoadType.UDL -> {
                        V -= mLoad.value * x
                        M -= mLoad.value * x * x / 2.0
                    }
                    MemberLoadType.PointLoad -> {
                        val a = mLoad.position.coerceIn(LOAD_POS_EPS, L - LOAD_POS_EPS)
                        if (x >= a) {
                            V -= mLoad.value
                            M -= mLoad.value * (x - a)
                        }
                    }
                    MemberLoadType.Moment -> {
                        val a = mLoad.position.coerceIn(LOAD_POS_EPS, L - LOAD_POS_EPS)
                        if (x >= a) {
                            M -= mLoad.value
                        }
                    }
                    MemberLoadType.LinearVarying -> {
                        // Triangular load: 0 at I, w at J
                        val wAtX = mLoad.value * x / L
                        V -= wAtX * x / 2.0
                        M -= mLoad.value * x * x * x / (6.0 * L)
                    }
                }
            }

            momentPoints.add(DiagramPoint(x, M))
            shearPoints.add(DiagramPoint(x, V))
            axialPoints.add(DiagramPoint(x, N))
        }

        return MemberDiagram(
            memberId = member.id,
            momentDiagram = momentPoints,
            shearDiagram = shearPoints,
            axialDiagram = axialPoints,
            maxMoment = momentPoints.maxOfOrNull { abs(it.value) } ?: 0.0,
            maxShear = shearPoints.maxOfOrNull { abs(it.value) } ?: 0.0,
            maxAxial = axialPoints.maxOfOrNull { abs(it.value) } ?: 0.0
        )
    }

    // ========================================================================
    // Section Properties Helpers (public for UI equations panel)
    // ========================================================================

    /**
     * خصائص جساءة عضو لعرضها في لوحة المعادلات (أو null إن كانت غير صالحة).
     */
    fun getMemberStiffnessProps(
        member: FrameMember,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings
    ): MemberStiffnessProps? {
        val L = member.getLength(nodes)
        if (!L.isFinite() || L < MIN_MEMBER_LENGTH_M) return null
        val E = getE(member, settings)
        val (A, I) = getSectionProperties(member, settings, L)
        if (!E.isFinite() || E <= 0.0 || !A.isFinite() || A <= 0.0 || !I.isFinite() || I <= 0.0) return null
        val ig = when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val cs = member.concreteSection ?: return null
                val b = cs.width / 1000.0
                val h = cs.depth / 1000.0
                b * h * h * h / 12.0
            }
            FrameMaterialType.Steel -> I
        }
        val angleDeg = Math.toDegrees(atan2(member.getSinTheta(nodes), member.getCosTheta(nodes)))
        val label = when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val cs = member.concreteSection
                if (cs != null) "b×h = ${cs.width.toInt()}×${cs.depth.toInt()} mm" else "—"
            }
            FrameMaterialType.Steel -> member.steelSectionName?.takeIf { it.isNotBlank() } ?: "IPE 300"
        }
        return MemberStiffnessProps(
            memberId = member.id, E = E, A = A, I = I, Ig = ig, L = L,
            angleDeg = angleDeg, sectionLabel = label, materialType = member.materialType
        )
    }

    private fun getE(member: FrameMember, settings: FrameAnalysisSettings): Double {
        return when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val fcu = member.concreteSection?.fcu ?: 25.0
                // E_c = 4700 * sqrt(fcu) MPa = 4700 * sqrt(fcu) * 1e3 kN/m²
                4700.0 * sqrt(fcu) * 1e3
            }
            FrameMaterialType.Steel -> settings.eSteel // kN/m²
        }
    }

    private fun getSectionProperties(
        member: FrameMember,
        settings: FrameAnalysisSettings,
        L: Double
    ): Pair<Double, Double> {
        // Returns (A in m², I in m⁴) with cracked inertia per ACI 318 §6.6 / ECP 203
        return when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val cs = member.concreteSection ?: ConcreteSectionProps(250.0, 500.0)
                val b = cs.width / 1000.0  // mm -> m
                val h = cs.depth / 1000.0
                val Ig = b * h * h * h / 12.0
                // Cracked inertia: 0.35Ig for beams, 0.70Ig for columns (ACI 318-19 §6.6.3.1.1)
                val crackedFactor = when (member.memberType) {
                    FrameMemberType.Beam -> 0.35
                    FrameMemberType.Column -> 0.70
                    else -> 0.50
                }
                Pair(b * h, Ig * crackedFactor)
            }
            FrameMaterialType.Steel -> {
                // S4 FIX: fallback بالاسم بدل ipeSections[13] (الذي كان IPE 400 فعلياً)
                val sec = SteelTables.getSectionByName(member.steelSectionName ?: "IPE 300")
                    ?: SteelTables.getSectionByName("IPE 300")
                    ?: SteelTables.ipeSections.first()
                val A = sec.area * 1e-4      // cm² -> m²
                val I = sec.iy * 1e-8        // cm⁴ -> m⁴ (iy = المحور القوي بالاتفاقية الأوروبية)
                Pair(A, I)
            }
        }
    }

    // ========================================================================
    // Linear Algebra Utilities
    // ========================================================================

    /**
     * حل نظام معادلات خطية Ax = b بطريقة Gauss Elimination مع Partial Pivoting
     *
     * C2: سماحية محور نسبية (pivot < tol × scale ⇒ شاذ) بدل حد مطلق ثابت،
     * لأن مدخلات المصفوفة تتراوح بين ~1e3 و~1e8 kN/m² حسب المقاطع.
     */
    fun solveLinearSystem(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = A.size
        if (n == 0) return null

        // C2: مقياس المصفوفة للسماحية النسبية
        var scale = 0.0
        for (i in 0 until n) {
            for (j in 0 until n) {
                val v = abs(A[i][j])
                if (v > scale) scale = v
                if (!A[i][j].isFinite()) return null
            }
        }
        if (scale <= 0.0) return null
        val pivotTol = scale * PIVOT_REL_TOL

        // Augmented matrix
        val aug = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) A[i][j] else b[i] } }

        // Forward elimination with partial pivoting
        for (col in 0 until n) {
            // Find pivot
            var maxVal = abs(aug[col][col])
            var maxRow = col
            for (row in col + 1 until n) {
                if (abs(aug[row][col]) > maxVal) {
                    maxVal = abs(aug[row][col])
                    maxRow = row
                }
            }
            if (maxVal < pivotTol) return null // Singular / mechanism
            if (maxRow != col) {
                val tmp = aug[col]; aug[col] = aug[maxRow]; aug[maxRow] = tmp
            }
            // Eliminate
            for (row in col + 1 until n) {
                val factor = aug[row][col] / aug[col][col]
                for (j in col..n) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }

        // Back substitution
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = aug[i][n]
            for (j in i + 1 until n) sum -= aug[i][j] * x[j]
            x[i] = sum / aug[i][i]
        }
        if (x.any { it.isNaN() || it.isInfinite() }) return null
        return x
    }

    /**
     * C2: المتبقٍ النسبي ||A·x - b||∞ / ||b||∞ — يعيد null إذا كان b صفرياً
     */
    private fun relativeResidual(A: Array<DoubleArray>, x: DoubleArray, b: DoubleArray): Double? {
        val n = b.size
        var bNorm = 0.0
        for (v in b) if (abs(v) > bNorm) bNorm = abs(v)
        if (bNorm < 1e-12) return null // لا أحمال — لا معنى للفحص
        var maxRes = 0.0
        for (i in 0 until n) {
            var ax = 0.0
            for (j in 0 until n) ax += A[i][j] * x[j]
            val res = abs(ax - b[i])
            if (res > maxRes) maxRes = res
        }
        return maxRes / bNorm
    }

    private fun transpose(m: Array<DoubleArray>, rows: Int, cols: Int): Array<DoubleArray> {
        return Array(cols) { i -> DoubleArray(rows) { j -> m[j][i] } }
    }

    private fun multiply(A: Array<DoubleArray>, B: Array<DoubleArray>, m: Int, n: Int, p: Int): Array<DoubleArray> {
        val C = Array(m) { DoubleArray(p) }
        for (i in 0 until m) {
            for (j in 0 until p) {
                var sum = 0.0
                for (k in 0 until n) sum += A[i][k] * B[k][j]
                C[i][j] = sum
            }
        }
        return C
    }

    private fun globalToLocalDisplacements(
        uI: DoubleArray, uJ: DoubleArray, c: Double, s: Double
    ): DoubleArray {
        return doubleArrayOf(
            c * uI[0] + s * uI[1],
            -s * uI[0] + c * uI[1],
            uI[2],
            c * uJ[0] + s * uJ[1],
            -s * uJ[0] + c * uJ[1],
            uJ[2]
        )
    }

    // ========================================================================
    // Preset Frame Templates
    // ========================================================================

    /**
     * قالب إطار بسيط (عمودين + كمر واحد)
     */
    fun createSimplePortalFrame(
        span: Double = 6.0,
        height: Double = 4.0,
        udl: Double = 20.0
    ): Triple<List<FrameNode>, List<FrameMember>, List<MemberLoad>> {
        // ── InputGuard (ADR-010) ──
        InputGuard.positive("span", span)
        InputGuard.positive("height", height)
        InputGuard.nonNegative("udl", udl)
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, span, 0.0, SupportType.Fixed),
            FrameNode(3, span, height, SupportType.Free),
            FrameNode(4, 0.0, height, SupportType.Free)
        )
        val concreteSec = ConcreteSectionProps(300.0, 600.0)
        val members = listOf(
            FrameMember(1, 1, 4, FrameMaterialType.Concrete, FrameMemberType.Column, concreteSec, name = "عمود أيسر"),
            FrameMember(2, 2, 3, FrameMaterialType.Concrete, FrameMemberType.Column, concreteSec, name = "عمود أيمن"),
            FrameMember(3, 4, 3, FrameMaterialType.Concrete, FrameMemberType.Beam, ConcreteSectionProps(250.0, 500.0), name = "كمر سقف")
        )
        val loads = listOf(
            MemberLoad(3, MemberLoadType.UDL, udl, loadCase = "DL")
        )
        return Triple(nodes, members, loads)
    }

    /**
     * قالب إطار متعدد الطوابق
     */
    fun createMultiStoryFrame(
        spans: List<Double> = listOf(5.0, 5.0),
        storyHeights: List<Double> = listOf(3.5, 3.0),
        udlPerFloor: Double = 15.0
    ): Triple<List<FrameNode>, List<FrameMember>, List<MemberLoad>> {
        // ── InputGuard (ADR-010) ──
        InputGuard.notEmpty("spans", spans)
        InputGuard.notEmpty("storyHeights", storyHeights)
        InputGuard.nonNegative("udlPerFloor", udlPerFloor)
        val numSpans = spans.size
        val numStories = storyHeights.size
        val nodes = mutableListOf<FrameNode>()
        var nodeId = 1

        // Ground nodes
        for (i in 0..numSpans) {
            val x = spans.take(i).sum()
            val node = FrameNode(nodeId++, x, 0.0, SupportType.Fixed)
            nodes.add(node)
        }

        // Upper floor nodes
        for (s in 0 until numStories) {
            val y = storyHeights.take(s + 1).sum()
            for (i in 0..numSpans) {
                val x = spans.take(i).sum()
                nodes.add(FrameNode(nodeId++, x, y, SupportType.Free))
            }
        }

        // Members
        val members = mutableListOf<FrameMember>()
        var memberId = 1
        val colSec = ConcreteSectionProps(300.0, 600.0)
        val beamSec = ConcreteSectionProps(250.0, 500.0)

        // Columns
        for (s in 0 until numStories) {
            for (i in 0..numSpans) {
                val lowerNode = s * (numSpans + 1) + i + 1
                val upperNode = (s + 1) * (numSpans + 1) + i + 1
                members.add(FrameMember(
                    memberId++, lowerNode, upperNode,
                    FrameMaterialType.Concrete, FrameMemberType.Column, colSec,
                    name = "عمود $i طابق ${s + 1}"
                ))
            }
        }

        // Beams
        for (s in 0 until numStories) {
            for (i in 0 until numSpans) {
                val leftNode = (s + 1) * (numSpans + 1) + i + 1
                val rightNode = leftNode + 1
                members.add(FrameMember(
                    memberId++, leftNode, rightNode,
                    FrameMaterialType.Concrete, FrameMemberType.Beam, beamSec,
                    name = "كمر $i طابق ${s + 1}"
                ))
            }
        }

        // Loads (UDL on beams)
        val loads = mutableListOf<MemberLoad>()
        // Beams are added after columns: columns first, then beams
        // Number of columns = (numSpans + 1) * numStories
        // Beams start at index = (numSpans + 1) * numStories
        val numColumns = (numSpans + 1) * numStories
        for (s in 0 until numStories) {
            for (i in 0 until numSpans) {
                val beamIdx = numColumns + s * numSpans + i
                if (beamIdx < members.size) {
                    loads.add(MemberLoad(members[beamIdx].id, MemberLoadType.UDL, udlPerFloor, loadCase = "DL"))
                }
            }
        }

        return Triple(nodes, members, loads)
    }
}
