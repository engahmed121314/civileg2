package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import com.civileg.app.domain.modeler.FrameModelValidator
import kotlin.math.*
import java.util.TreeSet

/**
 * يدعم:
 * - إطارات ثنائية الأبعاد (2D Frames)
 * - عقد بحرية ومقيدة (حرة، مفصلية، بكرة، تثبيت كامل، بكرة رأسية)
 * - أحمال عقدية (قوى + عزوم)
 * - أحمال على الأعضاء (منتظمة، مركزة، عزوم، متغيرة خطياً)
 * - مفاصل طرفية للعضو (releaseI/releaseJ) مع تكثيف ساكن (static condensation)
 * - حساب القوى الداخلية ورسم المخططات + مغلف توافيق الأحمال
 *
 * ── المرحلة الأولى (Phase 1) — إصلاحات حرجة C1..C5 + إصلاحات إشارات S1..S4 ──
 *
 * C1  حماية شاملة من NaN/Infinity في المدخلات والمخرجات (فحص مسبق + فحص لاحق).
 * C2  كشف المصفوفة الشاذة بسماحية محورية نسبية + فحص متبقيات الحل ||K·u-F||/||F||.
 * C3  كشف العقد المعزولة وعدم الاتصال بالارتكازات (BFS) — عبر FrameModelValidator.
 * C4  التحقق من مرجعية الأعضاء (عقد موجودة، لا حلقة ذاتية، طول صالح) — عبر FrameModelValidator.
 * C5  التحقق من الأحمال (قيم منتهية، مواقع داخل الطول) وخصائص المقاطع.
 *
 * S1  (Sign fix) تجميع الأحمال المكافئة: F += P_eq = +FEF_global — كانت معكوسة
 *     (مثال مُتحقق: كمرية بسيطة w=10,L=6 كانت تعطي M_mid=104.9 والصحيح 45).
 * S2  (Sign fix) مخطط العزوم: M(x) = -m_i + v_i·x - (الأحمال) — إشارة عزم النهاية
 *     كانت معكوسة (كمرية مثبتة الطرفين: M_end = -wL²/12 و M_mid = +wL²/24).
 * S3  (Formula fix) جدول FEF للعزم المركز أُعيد اشتقاقه والتحقق عددياً بطريقة
 *     العنصرين الدقيقة (FEM): q0 = [0, +6Mab/L³, +M·b(2a-b)/L², 0, -6Mab/L³,
 *     +M·a(2b-a)/L²] — الجدول القديم كان q0-style بينما بقية الجداول P_eq-style.
 *     الحمولات الطرفية تُثبَّت داخل العضو (clamp) فتنتقل للعقدة بدل إسقاطها.
 * S4  (Fallback fix) fallback مقطع الحديد بالبحث بالاسم (IPE 300 = فهرس 10) —
 *     ipeSections[13] كان يعطي IPE 400 فعلياً.
 *
 * ── المرحلة الثانية (Phase 2) — C6..C13 ──
 *
 * C6  (Bug fix) النماذج المثبتة بالكامل (صفر درجات حرية فعّالة) تُحل الآن:
 *     U=0، قوى النهايات = -FEF، ردود الأفعال = -F.
 * C7  (Bug fix) عينات المخططات تشمل مواقع الأحمال + a±ε + L/4, L/2, 3L/4.
 * C8  (Safety) فحص الاتزان العام ΣFx/ΣFy/ΣMz — المتبقيات تُخزن في النتيجة
 *     (equilibriumFx/Fy/Mz) ويُرفض الحل إذا اختلت (≥1e-3 نسبياً).
 * C9  (Feature) مغلف القوى عبر توافيق الأحمال (DL/LL/WL) من مخططات كل توفيق.
 * C10 (Feature) خيار الوزن الذاتي: خرسانة γ=25 kN/m³، حديد weight(kg/m)×g/1000.
 * C11 (Audit) توثيق الوحدات: eColumn/eSteel كلاهما kN/m² (200 GPa للحديد) —
 *     المحرك يحسب Ec = 4700·√fcu (MPa) لكل عضو.
 * C12 (Sign fix) القوة المحورية: N(x) = -n_i (شد موجب).
 * C13 (Consistency) اتساق قوى النهايات مع المخططات: V(0)=v_i، M(0)=-m_i، M(L)=+m_j.
 *
 * اتفاقية الإشارات الموثقة (y-up، عزوم CCW+):
 *   - FEF (المتجه المعاد من getFixedEndForces) = P_eq = الأحمال العقدية المكافئة
 *     = -q0 حيث q0 = قوى المؤازرة على العضو عند التثبيت الكامل (joint-on-member).
 *   - تجميع متجه الأحمال: F += FEF_global (P_eq).
 *   - قوى نهايات العضو: q_end = k·u_local - FEF_local = k·u_local + q0.
 *   - العزم الداخلي: M(x) = -m_i + v_i·x - (مساهمات الأحمال)؛ القص: V(x) = v_i - (...)؛
 *     القوة المحورية (C12): N(x) = -n_i (شد موجب).
 */
object FrameAnalysisEngine {

    private const val DOF_PER_NODE = 3  // dx, dy, rz

    /** أقل طول مقبول للعضو (م) */
    private const val MIN_MEMBER_LENGTH_M = 1e-6

    /** C2: سماحية المحور النسبية (كشف المصفوفة الشاذة/الميكانيزم) */
    private const val PIVOT_REL_TOL = 1e-12

    /** C2: أقصى متبقٍ نسبي مقبول ||K·u - F||/||F|| */
    private const val RESIDUAL_REL_TOL = 1e-6

    /** S3: هامش تثبيت موقع الحمولة داخل العضو [clamp] لتفادي إسقاط الأحمال الطرفية */
    private const val LOAD_POS_EPS = 1e-9

    /** C8: أقصى متبقٍ نسبي مقبول للاتزان العام */
    private const val EQUILIBRIUM_REL_TOL = 1e-3

    /** C10: وزن وحدة حجم الخرسانة المسلحة (kN/m³) */
    private const val CONCRETE_UNIT_WEIGHT_KN_M3 = 25.0

    /** C10: تسارع الجذبية (m/s²) لتحويل kg/m إلى kN/m */
    private const val G_ACCEL = 9.81

    // ========================================================================
    // Main Solver
    // ========================================================================

    /**
     * حل الإطار بالكامل — غلاف يطبّق ميزات المرحلة الثانية (C9/C10):
     *  1. يضيف الوزن الذاتي للأعضاء إذا فُعّل (C10).
     *  2. يحل النموذج مرة لكل توفيق أحمال ويبني مغلف القوى (C9).
     *  3. يتحقق من الاتزان العام (C8) داخل solveCore.
     *
     * @return FrameAnalysisResult يحتوي على إزاحات، قوى داخلية، مخططات، ومغلف توافيق
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

        // ── C9: مغلف القوى عبر توافيق الأحمال (حل منفصل لكل توفيق) ──
        val envelopes = computeLoadEnvelopes(nodes, members, nodalLoads, effectiveMemberLoads, settings, startTimeNs)
        if (envelopes != null) {
            val base = solveCore(nodes, members, nodalLoads, effectiveMemberLoads, settings, startTimeNs)
            if (!base.isSolved) return base
            return base.copy(loadEnvelopes = envelopes)
        }
        return solveCore(nodes, members, nodalLoads, effectiveMemberLoads, settings, startTimeNs)
    }

    /**
     * الحل الأساسي: تحقق → تجميع → تقسيم → حل → قوى/مخططات → اتزان (C8).
     * يشمل مسار صفر الدرجات (C6) والتحقق من المخرجات (C1) والمتبقيات (C2).
     */
    private fun solveCore(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        startTimeNs: Long
    ): FrameAnalysisResult {

        // ── InputGuard (ADR-010) ──
        InputGuard.notEmpty("nodes", nodes)
        InputGuard.notEmpty("members", members)
        InputGuard.notNull("settings", settings)
        try {
            val validationIssues = FrameModelValidator.validate(
                nodes = nodes,
                members = members,
                nodalLoads = nodalLoads,
                memberLoads = memberLoads
            )
            if (validationIssues.isNotEmpty()) {
                val shown = validationIssues.take(5).joinToString("\n") { it.message }
                val suffix = if (validationIssues.size > 5) {
                    "\n+${validationIssues.size - 5} ..."
                } else {
                    ""
                }
                return FrameAnalysisResult(errorMessage = shown + suffix)
            }

            val numNodes = nodes.size
            val totalDOF = numNodes * DOF_PER_NODE

            // 1. Build global stiffness matrix
            val Kg = Array(totalDOF) { DoubleArray(totalDOF) }
            for (member in members) {
                assembleMemberStiffness(member, nodes, settings, Kg)
            }

            // 2. Build load vector (with member load equivalent nodal loads)
            val F = DoubleArray(totalDOF)
            for (load in nodalLoads) {
                val nodeIdx = nodes.indexOfFirst { it.id == load.nodeId }
                if (nodeIdx >= 0) {
                    F[nodeIdx * 3] += load.fx
                    F[nodeIdx * 3 + 1] += load.fy
                    F[nodeIdx * 3 + 2] += load.mz
                }
            }
            // Add fixed-end forces from member loads
            for (mLoad in memberLoads) {
                val mIdx = members.indexOfFirst { it.id == mLoad.memberId }
                if (mIdx < 0) continue
                val member = members[mIdx]
                val fefLocal = getFixedEndForces(member, mLoad, nodes, settings)
                
                // CRITICAL FIX: FEF must be transformed from LOCAL to GLOBAL before assembly into F
                val cosA = member.getCosTheta(nodes)
                val sinA = member.getSinTheta(nodes)
                
                // local: [fi_x, fi_y, mi_z, fj_x, fj_y, mj_z]
                // global: [Fi_X, Fi_Y, Mi_Z, Fj_X, Fj_Y, Mj_Z]
                val fefGlobal = DoubleArray(6)
                fefGlobal[0] = fefLocal[0] * cosA - fefLocal[1] * sinA
                fefGlobal[1] = fefLocal[0] * sinA + fefLocal[1] * cosA
                fefGlobal[2] = fefLocal[2]
                fefGlobal[3] = fefLocal[3] * cosA - fefLocal[4] * sinA
                fefGlobal[4] = fefLocal[3] * sinA + fefLocal[4] * cosA
                fefGlobal[5] = fefLocal[5]

                val ni = nodes.indexOfFirst { it.id == member.nodeI }
                val nj = nodes.indexOfFirst { it.id == member.nodeJ }
                if (ni >= 0 && nj >= 0) {
                    // S1: تجميع الأحمال المكافئة: F += P_eq = +FEF_global
                    // (كانت معكوسة سابقاً — سبب خطأ M_mid=104.9 بدل 45)
                    F[ni * 3] += fefGlobal[0]; F[ni * 3 + 1] += fefGlobal[1]; F[ni * 3 + 2] += fefGlobal[2]
                    F[nj * 3] += fefGlobal[3]; F[nj * 3 + 1] += fefGlobal[4]; F[nj * 3 + 2] += fefGlobal[5]
                }
            }

            // 3. Identify restrained DOFs
            val restrainedDOFs = mutableListOf<Int>()
            for ((idx, node) in nodes.withIndex()) {
                for (dof in node.support.restrainedDOFs) {
                    restrainedDOFs.add(idx * 3 + dof)
                }
            }

            // 4. Partition and solve
            val freeDOFs = (0 until totalDOF).filter { it !in restrainedDOFs }
            // ── C6: مسار صفر درجات حرية (النموذج مقيّد بالكامل) ──
            // هندسياً حالة قابلة للحل: U=0 → قوى النهايات = -FEF، ردود الأفعال = -F.
            if (freeDOFs.isEmpty()) {
                return finalizeFullyRestrainedSolution(
                    nodes, members, nodalLoads, memberLoads, settings,
                    Kg, F, DoubleArray(totalDOF), startTimeNs
                )
            }

            // Member-end releases can leave a node rotation with zero stiffness.
            // Such DOFs are inactive (the connected member is hinged there); they
            // must not enter the stiffness system or it becomes singular.
            val inactiveDOFs = freeDOFs.filter { abs(Kg[it][it]) < 1e-9 }
            if (inactiveDOFs.any { abs(F[it]) > 1e-9 }) {
                return FrameAnalysisResult(errorMessage = "يوجد حمل على درجة حرية غير فعالة - تحقق من المفاصل")
            }
            val activeFreeDOFs = freeDOFs.filter { it !in inactiveDOFs }
            // ── C6: كل الدرجات الحرة غير فعّالة (مفاصل) → نفس مسار صفر DOF ──
            if (activeFreeDOFs.isEmpty()) {
                return finalizeFullyRestrainedSolution(
                    nodes, members, nodalLoads, memberLoads, settings,
                    Kg, F, DoubleArray(totalDOF), startTimeNs
                )
            }

            val Kff = Array(activeFreeDOFs.size) { i -> DoubleArray(activeFreeDOFs.size) { j -> Kg[activeFreeDOFs[i]][activeFreeDOFs[j]] } }
            val Ff = DoubleArray(activeFreeDOFs.size) { F[activeFreeDOFs[it]] }

            val Uf = solveLinearSystem(Kff, Ff) ?: return FrameAnalysisResult(errorMessage = "فشل حل نظام المعادلات - تحقق من الارتكازات")

            if (Uf.any { it.isNaN() || it.isInfinite() }) {
                return FrameAnalysisResult(errorMessage = "نتائج غير منطقية (NaN/Inf) - تحقق من استقرار المنشأ")
            }

            // C2: فحص المتبقيات النسبي ||Kff·Uf - Ff||∞ / ||Ff||∞
            val residualError = relativeResidual(Kff, Uf, Ff)
            if (residualError != null && residualError > RESIDUAL_REL_TOL) {
                return FrameAnalysisResult(
                    errorMessage = "الحل العددي غير مستقر (متبقٍ نسبي = %.2e) - المنشأ قريب من الميكانيزم".format(residualError)
                )
            }

            // 5. Full displacement vector
            val U = DoubleArray(totalDOF)
            for (i in activeFreeDOFs.indices) U[activeFreeDOFs[i]] = Uf[i]

            // 6. Compute reactions
            val reactions = DoubleArray(totalDOF)
            for (i in 0 until totalDOF) {
                var sum = 0.0
                for (j in 0 until totalDOF) sum += Kg[i][j] * U[j]
                reactions[i] = sum - F[i]
            }

            // C1: ردود الأفعال يجب أن تكون منتهية
            if (reactions.any { it.isNaN() || it.isInfinite() }) {
                return FrameAnalysisResult(errorMessage = "ردود الأفعال غير منتهية - تحقق من النموذج")
            }

            // 7. Member end forces
            val memberForces = mutableListOf<MemberEndForces>()
            val memberDiagrams = mutableListOf<MemberDiagram>()

            for (member in members) {
                val ni = nodes.indexOfFirst { it.id == member.nodeI }
                val nj = nodes.indexOfFirst { it.id == member.nodeJ }
                if (ni < 0 || nj < 0) continue

                val L = member.getLength(nodes)
                val cosA = member.getCosTheta(nodes)
                val sinA = member.getSinTheta(nodes)

                // Member stiffness properties
                val E = getE(member, settings)
                val (A, I) = getSectionProperties(member, settings)
                val k = effectiveMemberStiffness(member, memberLocalStiffness(E, A, I, L))

                // Local end displacements
                val uGlobalI = doubleArrayOf(U[ni * 3], U[ni * 3 + 1], U[ni * 3 + 2])
                val uGlobalJ = doubleArrayOf(U[nj * 3], U[nj * 3 + 1], U[nj * 3 + 2])
                val uLocal = globalToLocalDisplacements(uGlobalI, uGlobalJ, cosA, sinA)

                // Local member forces = k * u_local
                val fLocal = DoubleArray(6) {
                    var sum = 0.0
                    for (j in 0..5) sum += k[it][j] * uLocal[j]
                    sum
                }

                // Add fixed-end forces for member loads on this member
                val fefThisMember = DoubleArray(6)
                val memberLoadList = memberLoads.filter { it.memberId == member.id }
                for (mLoad in memberLoadList) {
                    val fef = getFixedEndForces(member, mLoad, nodes, settings)
                    for (i in 0..5) fefThisMember[i] += fef[i]
                }

                // q_end = k·u_local + q0  (FEF = P_eq = -q0 → -FEF = +q0)
                val fTotal = DoubleArray(6) { fLocal[it] - fefThisMember[it] }

                // C1: قوى العضو يجب أن تكون منتهية
                if (fTotal.any { it.isNaN() || it.isInfinite() }) {
                    return FrameAnalysisResult(errorMessage = "قوى غير منتهية للعضو #${member.id} - تحقق من خصائص المقطع")
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

            // ── C8: فحص الاتزان العام (ΣR + ΣF,applied = 0) ──
            val (eqFx, eqFy, eqMz) = computeGlobalEquilibrium(nodes, members, nodalLoads, memberLoads, settings, nodeResults)
            val appliedMag = nodalLoads.sumOf { abs(it.fx) + abs(it.fy) + abs(it.mz) } +
                memberLoads.sumOf { abs(it.value) }
            if (maxOf(abs(eqFx), abs(eqFy), abs(eqMz)) > EQUILIBRIUM_REL_TOL * max(1.0, appliedMag)) {
                return FrameAnalysisResult(
                    errorMessage = "الاتزان العام غير محقق (resFx=%.2e, resFy=%.2e, resMz=%.2e)".format(eqFx, eqFy, eqMz)
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
    // Stiffness Matrix Assembly
    // ========================================================================

    private fun assembleMemberStiffness(
        member: FrameMember,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings,
        Kg: Array<DoubleArray>
    ) {
        val ni = nodes.indexOfFirst { it.id == member.nodeI }
        val nj = nodes.indexOfFirst { it.id == member.nodeJ }
        if (ni < 0 || nj < 0) return

        val L = member.getLength(nodes)
        if (L < 1e-12) return
        val cosA = member.getCosTheta(nodes)
        val sinA = member.getSinTheta(nodes)
        val E = getE(member, settings)
        val (A, I) = getSectionProperties(member, settings)

        val kl = effectiveMemberStiffness(member, memberLocalStiffness(E, A, I, L))
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

    private fun releasedDofs(member: FrameMember): IntArray {
        val released = mutableListOf<Int>()
        if (member.releaseI) released.add(2)
        if (member.releaseJ) released.add(5)
        return released.toIntArray()
    }

    private fun effectiveMemberStiffness(
        member: FrameMember,
        k: Array<DoubleArray>
    ): Array<DoubleArray> {
        val released = releasedDofs(member)
        if (released.isEmpty()) return k
        return condenseMember(member, k, DoubleArray(6)).first
    }

    private fun condenseFixedEndForces(
        member: FrameMember,
        settings: FrameAnalysisSettings,
        nodes: List<FrameNode>,
        fef: DoubleArray
    ): DoubleArray {
        if (!member.releaseI && !member.releaseJ) return fef
        val L = member.getLength(nodes)
        if (L < 1e-12) return fef
        val k = memberLocalStiffness(
            getE(member, settings),
            getSectionProperties(member, settings).first,
            getSectionProperties(member, settings).second,
            L
        )
        return condenseMember(member, k, fef).second
    }

    /**
     * Static condensation for member-end moment releases.
     * Released rotational DOFs are eliminated from both the local stiffness
     * matrix and the fixed-end force vector so their end moment is exactly zero.
     */
    private fun condenseMember(
        member: FrameMember,
        k: Array<DoubleArray>,
        fef: DoubleArray
    ): Pair<Array<DoubleArray>, DoubleArray> {
        val released = releasedDofs(member)
        if (released.isEmpty()) return k to fef

        val retained = (0 until 6).filter { it !in released }
        val rc = retained.size
        val cc = released.size

        val kcc = Array(cc) { i -> DoubleArray(cc) { j -> k[released[i]][released[j]] } }
        val krc = Array(rc) { i -> DoubleArray(cc) { j -> k[retained[i]][released[j]] } }
        val kcr = Array(cc) { i -> DoubleArray(rc) { j -> k[released[i]][retained[j]] } }
        val krr = Array(rc) { i -> DoubleArray(rc) { j -> k[retained[i]][retained[j]] } }

        val invKcc = invertSmall(kcc) ?: return k to fef
        val krcInv = multiply(krc, invKcc, rc, cc, cc)
        val kStar = Array(rc) { i ->
            DoubleArray(rc) { j ->
                krr[i][j] - (0 until cc).sumOf { m -> krcInv[i][m] * kcr[m][j] }
            }
        }
        val fefRetained = DoubleArray(rc) { retained[it].let { dof -> fef[dof] } }
        val fefReleased = DoubleArray(cc) { released[it].let { dof -> fef[dof] } }
        val fefStar = DoubleArray(rc) { i ->
            fefRetained[i] - (0 until cc).sumOf { m -> krcInv[i][m] * fefReleased[m] }
        }

        val kEff = Array(6) { DoubleArray(6) }
        for (i in 0 until rc) {
            for (j in 0 until rc) kEff[retained[i]][retained[j]] = kStar[i][j]
        }
        val fefEff = DoubleArray(6)
        for (i in 0 until rc) fefEff[retained[i]] = fefStar[i]
        return kEff to fefEff
    }

    private fun invertSmall(a: Array<DoubleArray>): Array<DoubleArray>? {
        val n = a.size
        if (n == 0) return emptyArray()
        val inv = Array(n) { DoubleArray(n) }
        for (col in 0 until n) {
            val b = DoubleArray(n) { if (it == col) 1.0 else 0.0 }
            val x = solveLinearSystem(a, b) ?: return null
            for (row in 0 until n) inv[row][col] = x[row]
        }
        return inv
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
     * حساب قوى النهاية الثابتة (Fixed-End Forces) للحمولات على الأعضاء
     */
    private fun getFixedEndForces(
        member: FrameMember,
        load: MemberLoad,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings
    ): DoubleArray {
        val L = member.getLength(nodes)
        val w = load.value
        // S3: تثبيت موقع الحمولة داخل العضو (clamp) — الحمولات الطرفية تنتقل للعقدة
        val a = load.position.coerceIn(LOAD_POS_EPS, max(LOAD_POS_EPS, L - LOAD_POS_EPS))

        val raw = when (load.loadType) {
            MemberLoadType.UDL -> {
                // FEF for UDL w (kN/m) over full span L
                // Local: [0, -wL/2, -wL²/12, 0, -wL/2, wL²/12]
                doubleArrayOf(0.0, -w * L / 2.0, -w * L * L / 12.0, 0.0, -w * L / 2.0, w * L * L / 12.0)
            }
            MemberLoadType.PointLoad -> {
                // S3: الحمولة الطرفية (a→0 أو a→L) تنتقل للعقدة بدل إسقاطها
                // P_eq = -q0 (q0 = [0, +Pb²(L+2a)/L³, +Pab²/L², 0, +Pa²(L+2b)/L³, -Pa²b/L²])
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
                // S3 (مُصحح والتحقق عددياً بطريقة العنصرين الدقيقة FEM):
                //   q0 = [0, +6Mab/L³, +M·b(2a-b)/L², 0, -6Mab/L³, +M·a(2b-a)/L²]
                //   P_eq = -q0. تحقق: a→0 يعطي P_eq = [0,0,+M,0,0,0] (عزم عُقدي عند I)
                val b = L - a
                val M = w
                doubleArrayOf(
                    0.0,
                    -6.0 * M * a * b / (L * L * L),
                    -M * b * (2.0 * a - b) / (L * L),
                    0.0,
                    +6.0 * M * a * b / (L * L * L),
                    -M * a * (2.0 * b - a) / (L * L)
                )
            }
            MemberLoadType.LinearVarying -> {
                // FEF for triangular load (0 at I, w at J)
                // Member reactions (on member): RI = 3wL/20, RJ = 7wL/20, MI = wL²/30 (CCW), MJ = -wL²/20 (CW)
                // Nodal forces (on nodes): FI_Y = -3wL/20, MI_Z = -wL²/30, FJ_Y = -7wL/20, MJ_Z = wL²/20
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
        return condenseFixedEndForces(member, settings, nodes, raw)
    }

    // ========================================================================
    // Diagram Generation
    // ========================================================================

    /**
     * توليد مخططات القوى الداخلية (عزم، قص، محوري) لعضو واحد
     */
    /**
     * توليد مخططات القوى الداخلية (عزم، قص، محوري) لعضو واحد
     *
     * S2: M(x) = -m_i + v_i·x - (مساهمات الأحمال)
     * C12: N(x) = -n_i (شد موجب)
     * C7: عينات عند مواقع الأحمال + a±ε + شبكة منتظمة + L/4, L/2, 3L/4.
     */
    private fun generateDiagrams(
        member: FrameMember,
        nodes: List<FrameNode>,
        fLocalTotal: DoubleArray, // قوى النهاية المحلية (q_end = k·u + q0)
        memberLoadList: List<MemberLoad>,
        settings: FrameAnalysisSettings
    ): MemberDiagram {
        val L = member.getLength(nodes)
        if (L < MIN_MEMBER_LENGTH_M) {
            return MemberDiagram(memberId = member.id)
        }

        // ── C7: مجموعة مواقع العينات (مرتبة منقّاة عبر TreeSet) ──
        val sampleSet = TreeSet<Double>()
        sampleSet.add(0.0)
        sampleSet.add(L)
        for (i in 1 until 20) sampleSet.add(L * i / 20.0)
        sampleSet.add(L / 4.0)
        sampleSet.add(L / 2.0)
        sampleSet.add(3.0 * L / 4.0)
        for (mLoad in memberLoadList) {
            if (mLoad.loadType == MemberLoadType.PointLoad || mLoad.loadType == MemberLoadType.Moment) {
                val a = mLoad.position.coerceIn(0.0, L)
                sampleSet.add(a)
                if (a > LOAD_POS_EPS) sampleSet.add((a - LOAD_POS_EPS * 10).coerceIn(0.0, L))
                if (a < L - LOAD_POS_EPS) sampleSet.add((a + LOAD_POS_EPS * 10).coerceIn(0.0, L))
            }
        }
        val samples = sampleSet.toList()

        val momentPoints = ArrayList<DiagramPoint>(samples.size)
        val shearPoints = ArrayList<DiagramPoint>(samples.size)
        val axialPoints = ArrayList<DiagramPoint>(samples.size)

        val vI = fLocalTotal[1]  // قص عند I
        val mI = fLocalTotal[2]  // عزم عند I (CCW joint-on-member)
        val nI = fLocalTotal[0]  // محوري عند I

        for (x in samples) {
            var V = vI
            var M = -mI + vI * x          // S2
            val N = -nI                    // C12: شد موجب

            for (mLoad in memberLoadList) {
                when (mLoad.loadType) {
                    MemberLoadType.UDL -> {
                        V -= mLoad.value * x
                        M -= mLoad.value * x * x / 2.0
                    }
                    MemberLoadType.PointLoad -> {
                        val a = mLoad.position.coerceIn(LOAD_POS_EPS, max(LOAD_POS_EPS, L - LOAD_POS_EPS))
                        if (x >= a) {
                            V -= mLoad.value
                            M -= mLoad.value * (x - a)
                        }
                    }
                    MemberLoadType.Moment -> {
                        val a = mLoad.position.coerceIn(LOAD_POS_EPS, max(LOAD_POS_EPS, L - LOAD_POS_EPS))
                        if (x >= a) {
                            M -= mLoad.value
                        }
                    }
                    MemberLoadType.LinearVarying -> {
                        // حمولة مثلثية: 0 عند I و w عند J
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
    // C6: Fully-restrained solution (zero active DOFs)
    // ========================================================================

    /**
     * C6: النماذج المثبتة بالكامل — U=0 لكن قوى النهايات = -FEF (= +q0)،
     * وردود الأفعال = -F. حالة صالحة هندسياً كانت تُرفض سابقاً بخطأ.
     */
    private fun finalizeFullyRestrainedSolution(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        Kg: Array<DoubleArray>,
        F: DoubleArray,
        U: DoubleArray,
        startTimeNs: Long
    ): FrameAnalysisResult {
        val totalDOF = nodes.size * 3

        // C6: U=0 → K·U=0 → R = -F
        val reactions = DoubleArray(totalDOF)
        for (i in 0 until totalDOF) {
            var sum = 0.0
            for (j in 0 until totalDOF) sum += Kg[i][j] * U[j]
            reactions[i] = sum - F[i]   // = -F[i]
        }

        val memberForces = mutableListOf<MemberEndForces>()
        val memberDiagrams = mutableListOf<MemberDiagram>()
        for (member in members) {
            val ni = nodes.indexOfFirst { it.id == member.nodeI }
            val nj = nodes.indexOfFirst { it.id == member.nodeJ }
            if (ni < 0 || nj < 0) continue
            val cosA = member.getCosTheta(nodes)
            val sinA = member.getSinTheta(nodes)

            // q_end = k·0 + q0 = -FEF
            var fefThisMember = DoubleArray(6)
            val memberLoadList = memberLoads.filter { it.memberId == member.id }
            for (mLoad in memberLoadList) {
                val fef = getFixedEndForces(member, mLoad, nodes, settings)
                for (q in 0..5) fefThisMember[q] += fef[q]
            }
            val fTotal = DoubleArray(6) { -fefThisMember[it] }
            if (fTotal.any { it.isNaN() || it.isInfinite() }) {
                return FrameAnalysisResult(errorMessage = "قوى غير منتهية للعضو #${member.id} - تحقق من خصائص المقطع")
            }

            val fiX = fTotal[0] * cosA - fTotal[1] * sinA
            val fiY = fTotal[0] * sinA + fTotal[1] * cosA
            val fjX = fTotal[3] * cosA - fTotal[4] * sinA
            val fjY = fTotal[3] * sinA + fTotal[4] * cosA
            memberForces.add(MemberEndForces(
                memberId = member.id,
                fi_x = fiX, fi_y = fiY, mi_z = fTotal[2],
                fj_x = fjX, fj_y = fjY, mj_z = fTotal[5]
            ))
            memberDiagrams.add(generateDiagrams(member, nodes, fTotal, memberLoadList, settings))
        }

        val nodeResults = nodes.mapIndexed { idx, node ->
            val r = node.support.restrainedDOFs
            NodeResult(
                nodeId = node.id,
                dx = U[idx * 3], dy = U[idx * 3 + 1], rz = U[idx * 3 + 2],
                reactionFx = if (0 in r) reactions[idx * 3] else 0.0,
                reactionFy = if (1 in r) reactions[idx * 3 + 1] else 0.0,
                reactionMz = if (2 in r) reactions[idx * 3 + 2] else 0.0
            )
        }

        // C8: فحص الاتزان العام في مسار C6
        val (eqFx, eqFy, eqMz) = computeGlobalEquilibrium(nodes, members, nodalLoads, memberLoads, settings, nodeResults)
        val appliedMag = nodalLoads.sumOf { abs(it.fx) + abs(it.fy) + abs(it.mz) } +
            memberLoads.sumOf { abs(it.value) }
        if (maxOf(abs(eqFx), abs(eqFy), abs(eqMz)) > EQUILIBRIUM_REL_TOL * max(1.0, appliedMag)) {
            return FrameAnalysisResult(
                errorMessage = "الاتزان العام غير محقق (resFx=%.2e, resFy=%.2e, resMz=%.2e)".format(eqFx, eqFy, eqMz)
            )
        }

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
    // C8: Global equilibrium residuals
    // ========================================================================

    /**
     * C8: متبقيات الاتزان العام (يجب أن تكون ≈ 0):
     *   resFx = ΣR_fx + ΣF_x,applied
     *   resFy = ΣR_fy + ΣF_y,applied
     *   resMz = Σ(R_mz + x·R_fy − y·R_fx) + Σ(F_mz + x·F_y − y·F_x)
     * الأحمال المطبقة = العقدية + مكافئات أحمال الأعضاء (نفس fefGlobal المُجمّعة في F).
     */
    private fun computeGlobalEquilibrium(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        nodeResults: List<NodeResult>
    ): Triple<Double, Double, Double> {
        // (1) ردود الأفعال (تُخزَّن فقط عند الدرجات المقيّدة — الباقي ≈ 0)
        var rFx = 0.0; var rFy = 0.0; var rMz = 0.0
        for ((idx, node) in nodes.withIndex()) {
            val rx = nodeResults[idx].reactionFx
            val ry = nodeResults[idx].reactionFy
            val rz = nodeResults[idx].reactionMz
            rFx += rx; rFy += ry
            rMz += rz + node.x * ry - node.y * rx
        }
        // (2) الأحمال العقدية المباشرة
        var nFx = 0.0; var nFy = 0.0; var nMz = 0.0
        val nodeById = nodes.associateBy { it.id }
        for (nl in nodalLoads) {
            val n = nodeById[nl.nodeId] ?: continue
            nFx += nl.fx; nFy += nl.fy
            nMz += nl.mz + n.x * nl.fy - n.y * nl.fx
        }
        // (3) مكافئات أحمال الأعضاء (fefGlobal — نفس ما يُجمَّع في F)
        var mFx = 0.0; var mFy = 0.0; var mMz = 0.0
        for (mLoad in memberLoads) {
            val mIdx = members.indexOfFirst { it.id == mLoad.memberId }
            if (mIdx < 0) continue
            val member = members[mIdx]
            val ni = nodes.indexOfFirst { it.id == member.nodeI }
            val nj = nodes.indexOfFirst { it.id == member.nodeJ }
            if (ni < 0 || nj < 0) continue
            val fefLocal = getFixedEndForces(member, mLoad, nodes, settings)
            val cosA = member.getCosTheta(nodes)
            val sinA = member.getSinTheta(nodes)
            val fefGlobal = DoubleArray(6)
            fefGlobal[0] = fefLocal[0] * cosA - fefLocal[1] * sinA
            fefGlobal[1] = fefLocal[0] * sinA + fefLocal[1] * cosA
            fefGlobal[2] = fefLocal[2]
            fefGlobal[3] = fefLocal[3] * cosA - fefLocal[4] * sinA
            fefGlobal[4] = fefLocal[3] * sinA + fefLocal[4] * cosA
            fefGlobal[5] = fefLocal[5]
            val nodeI = nodes[ni]
            val nodeJ = nodes[nj]
            mFx += fefGlobal[0] + fefGlobal[3]
            mFy += fefGlobal[1] + fefGlobal[4]
            mMz += fefGlobal[2] + fefGlobal[5] +
                nodeI.x * fefGlobal[1] - nodeI.y * fefGlobal[0] +
                nodeJ.x * fefGlobal[4] - nodeJ.y * fefGlobal[3]
        }
        return Triple(rFx + nFx + mFx, rFy + nFy + mFy, rMz + nMz + mMz)
    }

    // ========================================================================
    // C10 (self-weight) + C9 (load-combination envelope)
    // ========================================================================

    /**
     * C10: أحمال الوزن الذاتي — تُعاد كقائمة MemberLoad (UDL هابط، حالة "DL").
     * خرسانة: w = γ·b·h = 25 kN/m³. حديد: w = weight(kg/m) × g / 1000.
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
                    CONCRETE_UNIT_WEIGHT_KN_M3 * (cs.width / 1000.0) * (cs.depth / 1000.0)  // kN/m
                }
                FrameMaterialType.Steel -> {
                    val sec = com.civileg.app.utils.SteelTables.getSectionByName(m.steelSectionName ?: "IPE 300")
                        ?: com.civileg.app.utils.SteelTables.getSectionByName("IPE 300")
                        ?: continue
                    sec.weight * G_ACCEL / 1000.0  // kg/m × m/s² / 1000 = kN/m
                }
            }
            if (wPerM.isFinite() && wPerM > 0.0) {
                loads.add(MemberLoad(m.id, MemberLoadType.UDL, wPerM, 0.0, loadCase = "DL"))
            }
        }
        return loads
    }

    /**
     * C9: مغلف القوى عبر توافيق الأحمال — حل منفصل لكل توفيق ثم أخذ
     * max/min الموقّعة من مخططات كل توفيق. يعيد null إذا لم تُعرَّف توافيق.
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

        val perMember = HashMap<Int, MutableList<Pair<String, MemberDiagram>>>()
        for (combo in combos) {
            fun factorOf(case: String): Double = when (case.trim().uppercase()) {
                "DL" -> combo.dlFactor
                "LL" -> combo.llFactor
                "WL" -> combo.wlFactor
                else -> combo.dlFactor
            }
            val scaledNodal = nodalLoads
                .map { it.copy(fx = it.fx * factorOf(it.loadCase), fy = it.fy * factorOf(it.loadCase), mz = it.mz * factorOf(it.loadCase)) }
                .filter { it.fx != 0.0 || it.fy != 0.0 || it.mz != 0.0 }
            val scaledMember = memberLoads
                .map { it.copy(value = it.value * factorOf(it.loadCase)) }
                .filter { it.value != 0.0 }
            if (scaledNodal.isEmpty() && scaledMember.isEmpty()) continue

            val comboSettings = settings.copy(loadCombinations = emptyList())
            val result = solveCore(nodes, members, scaledNodal, scaledMember, comboSettings, startTimeNs)
            if (!result.isSolved) continue
            for (d in result.memberDiagrams) {
                perMember.getOrPut(d.memberId) { mutableListOf() }.add(combo.name to d)
            }
        }
        if (perMember.isEmpty()) return emptyList()

        return members.mapNotNull { m ->
            val list = perMember[m.id] ?: return@mapNotNull null
            var maxM = Double.NEGATIVE_INFINITY; var minM = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY; var minV = Double.POSITIVE_INFINITY
            var maxN = Double.NEGATIVE_INFINITY; var minN = Double.POSITIVE_INFINITY
            for ((_, d) in list) {
                for (p in d.momentDiagram) { if (p.value > maxM) maxM = p.value; if (p.value < minM) minM = p.value }
                for (p in d.shearDiagram)  { if (p.value > maxV) maxV = p.value; if (p.value < minV) minV = p.value }
                for (p in d.axialDiagram)  { if (p.value > maxN) maxN = p.value; if (p.value < minN) minN = p.value }
            }
            val governing = list.maxByOrNull { it.second.maxMoment }?.first ?: "—"
            MemberForceEnvelope(
                memberId = m.id,
                maxMoment = if (maxM.isInfinite()) 0.0 else maxM,
                minMoment = if (minM.isInfinite()) 0.0 else minM,
                maxShear = if (maxV.isInfinite()) 0.0 else maxV,
                minShear = if (minV.isInfinite()) 0.0 else minV,
                maxAxial = if (maxN.isInfinite()) 0.0 else maxN,
                minAxial = if (minN.isInfinite()) 0.0 else minN,
                governingCombination = governing
            )
        }
    }

    // ========================================================================
    // C2: relative residual
    // ========================================================================

    /** C2: المتبقٍ النسبي ||A·x - b||∞ / ||b||∞ — يعيد null إذا كان b صفرياً */
    private fun relativeResidual(A: Array<DoubleArray>, x: DoubleArray, b: DoubleArray): Double? {
        val n = b.size
        var bNorm = 0.0
        for (v in b) if (abs(v) > bNorm) bNorm = abs(v)
        if (bNorm < 1e-12) return null
        var maxRes = 0.0
        for (i in 0 until n) {
            var ax = 0.0
            for (j in 0 until n) ax += A[i][j] * x[j]
            val res = abs(ax - b[i])
            if (res > maxRes) maxRes = res
        }
        return maxRes / bNorm
    }

    // ========================================================================
    // Public data for UI (Equations & Solution Steps panel)
    // ========================================================================

    /** خصائص جساءة عضو — لعرضها في لوحة المعادلات. */
    data class MemberStiffnessProps(
        val memberId: Int,
        val E: Double,          // kN/m²
        val A: Double,          // m²
        val I: Double,          // m⁴ (متشقق للخرسانة)
        val Ig: Double,         // m⁴ (كلي — خرسانة فقط)
        val L: Double,          // m
        val angleDeg: Double,
        val sectionLabel: String,
        val materialType: FrameMaterialType
    )

    /** C11: خصائص جساءة عضو لعرضها في لوحة المعادلات (أو null إن كانت غير صالحة). */
    fun getMemberStiffnessProps(
        member: FrameMember,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings
    ): MemberStiffnessProps? {
        val L = member.getLength(nodes)
        if (!L.isFinite() || L < MIN_MEMBER_LENGTH_M) return null
        val E = getE(member, settings)
        val (A, I) = getSectionProperties(member, settings)
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

    /** واجهة عامة لعرض FEF في لوحة المعادلات (P_eq المحلي) */
    fun memberLoadEquivalentForces(
        member: FrameMember,
        load: MemberLoad,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings
    ): DoubleArray = getFixedEndForces(member, load, nodes, settings)


    // Section Properties Helper
    // ========================================================================

    private fun getE(member: FrameMember, settings: FrameAnalysisSettings): Double {
        return when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val fcu = member.concreteSection?.fcu ?: 25.0
                // E_c = 4700 * sqrt(fcu) MPa = 4700 * sqrt(fcu) * 1e3 kN/m²
                4700.0 * sqrt(fcu) * 1e3
            }
            FrameMaterialType.Steel -> settings.eSteel  // C11: kN/m² كما هي (200 GPa) — كان ×1e3 خطأً وحداتياً
        }
    }

    private fun getSectionProperties(
        member: FrameMember,
        settings: FrameAnalysisSettings
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
                // S4: fallback بالبحث بالاسم — ipeSections[13] كان يعطي IPE 400 فعلياً (IPE 300 = فهرس 10)
                val sec = com.civileg.app.utils.SteelTables.getSectionByName(member.steelSectionName ?: "IPE 300")
                    ?: com.civileg.app.utils.SteelTables.getSectionByName("IPE 300")
                    ?: com.civileg.app.utils.SteelTables.ipeSections.first()
                val A = sec.area * 1e-4      // cm² -> m²
                val I = sec.iy * 1e-8         // cm⁴ -> m⁴ (European IPE: Iy = strong axis)
                Pair(A, I)
            }
        }
    }

    // ========================================================================
    // Linear Algebra Utilities
    // ========================================================================

    /**
     * حل نظام معادلات خطية Ax = b بطريقة Gauss Elimination مع Pivoting
     */
    fun solveLinearSystem(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = A.size
        if (n == 0) return null

        // C2: مقياس المصفوفة للسماحية النسبية (مدخلات بين ~1e3 و~1e8 حسب المقاطع)
        var scale = 0.0
        for (r in 0 until n) {
            for (c in 0 until n) {
                val v = abs(A[r][c])
                if (v > scale) scale = v
                if (!A[r][c].isFinite()) return null
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
            if (maxVal < pivotTol) return null // C2: singular/mechanism (relative tolerance)
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