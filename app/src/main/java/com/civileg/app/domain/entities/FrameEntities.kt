package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// Frame Geometry Entities
// ============================================================================

/**
 * عقدة في الهيكل الإنشائي (Node/Joint)
 * @param id معرف العقدة (فريد)
 * @param x الإحداثي الأفقي (متر)
 * @param y الإحداثي الرأسي (متر)
 * @param support نوع الارتكاز (إن وجد)
 */
@Parcelize
data class FrameNode(
    val id: Int,
    val x: Double,
    val y: Double,
    val support: SupportType = SupportType.Free
) : Parcelable

/**
 * أنواع الارتكازات
 */
@Parcelize
enum class SupportType(val displayNameAr: String, val displayNameEn: String) : Parcelable {
    Free("حر", "Free"),
    Pin("مفصلي", "Pin/Fixed Hinge"),
    Roller("بكرة", "Roller"),
    Fixed("تثبيت كامل", "Fixed"),
    VerticalRoller("بكرة رأسية", "Vertical Roller");

    fun isRestrained(dx: Boolean, dy: Boolean, rz: Boolean): Boolean = when (this) {
        Free -> false
        Pin -> (dx && dy && !rz)
        Roller -> (!dx && dy && !rz)
        Fixed -> (dx && dy && rz)
        VerticalRoller -> (dx && !dy && !rz)
    }

    val restrainedDOFs: List<Int>
        get() = when (this) {
            Free -> emptyList()
            Pin -> listOf(0, 1)       // dx, dy
            Roller -> listOf(1)        // dy only
            Fixed -> listOf(0, 1, 2)   // dx, dy, rotation
            VerticalRoller -> listOf(0) // dx only
        }
}

/**
 * عضو في الهيكل الإنشائي (Member/Element)
 * @param id معرف العضو
 * @param nodeI معرف العقدة البداية
 * @param nodeJ معرف العقدة النهاية
 * @param sectionType نوع المادة (خرسانة/حديد)
 * @param memberType نوع العضو (عمود/كمر/كمرية)
 * @param sectionProps خصائص المقطع (حسب نوع المادة)
 * @param name اسم اختياري للعضو
 */
@Parcelize
data class FrameMember(
    val id: Int,
    val nodeI: Int,
    val nodeJ: Int,
    val materialType: FrameMaterialType = FrameMaterialType.Concrete,
    val memberType: FrameMemberType = FrameMemberType.Beam,
    val concreteSection: @RawValue ConcreteSectionProps? = null,
    val steelSectionName: String? = null,
    val name: String = ""
) : Parcelable {
    /** طول العضو (يُحسب من الإحداثيات) */
    fun getLength(nodes: List<FrameNode>): Double {
        val ni = nodes.find { it.id == nodeI } ?: return 0.0
        val nj = nodes.find { it.id == nodeJ } ?: return 0.0
        val dx = nj.x - ni.x
        val dy = nj.y - ni.y
        return sqrt(dx * dx + dy * dy)
    }

    /** جيب زاوية الميل مع الأفقي (sin θ) */
    fun getSinTheta(nodes: List<FrameNode>): Double {
        val ni = nodes.find { it.id == nodeI } ?: return 0.0
        val nj = nodes.find { it.id == nodeJ } ?: return 0.0
        val dx = nj.x - ni.x
        val dy = nj.y - ni.y
        val L = sqrt(dx * dx + dy * dy)
        return if (L > 1e-12) dy / L else 0.0
    }

    /** جيب تمام زاوية الميل مع الأفقي (cos θ) */
    fun getCosTheta(nodes: List<FrameNode>): Double {
        val ni = nodes.find { it.id == nodeI } ?: return 1.0
        val nj = nodes.find { it.id == nodeJ } ?: return 1.0
        val dx = nj.x - ni.x
        val dy = nj.y - ni.y
        val L = sqrt(dx * dx + dy * dy)
        return if (L > 1e-12) dx / L else 1.0
    }
}

@Parcelize
enum class FrameMaterialType(val displayNameAr: String, val displayNameEn: String) : Parcelable {
    Concrete("خرسانة", "Concrete"),
    Steel("حديد", "Steel")
}

@Parcelize
enum class FrameMemberType(val displayNameAr: String, val displayNameEn: String) : Parcelable {
    Beam("كمر", "Beam"),
    Column("عمود", "Column"),
    Brace("كمرية", "Brace")
}

/**
 * خصائص المقطع الخرساني
 */
@Parcelize
data class ConcreteSectionProps(
    val width: Double,         // mm - عرض المقطع (b)
    val depth: Double,         // mm - عمق المقطع (h)
    val fcu: Double = 25.0,    // MPa - مقاومة الضغط للخرسانة
    val fy: Double = 400.0,    // MPa - مقاومة التسليح
    val cover: Double = 50.0   // mm - غطاء الخرسانة
) : Parcelable {
    val effectiveDepth: Double get() = depth - cover - 10.0 // تقريباً (خصم الغطاء + نصف قطر السيخ)
    val area: Double get() = width * depth                    // mm²
    val inertia: Double get() = width * depth.pow(3) / 12.0   // mm⁴ (عزم القصور الذاتي)
}

// ============================================================================
// Load Entities
// ============================================================================

/**
 * حمولة على عقدة
 */
@Parcelize
data class NodalLoad(
    val nodeId: Int,
    val fx: Double = 0.0,      // kN - قوة أفقية
    val fy: Double = 0.0,      // kN - قوة رأسية (موجب لأعلى)
    val mz: Double = 0.0,      // kN.m - عزم دوران
    val loadCase: String = "DL" // DL, LL, WL
) : Parcelable

/**
 * حمولة على عضو
 */
@Parcelize
data class MemberLoad(
    val memberId: Int,
    val loadType: MemberLoadType = MemberLoadType.UDL,
    val value: Double = 0.0,     // kN/m أو kN حسب النوع
    val position: Double = 0.0,  // موقع الحمولة من بداية العضو (متر)
    val loadCase: String = "DL"  // DL, LL, WL
) : Parcelable

@Parcelize
enum class MemberLoadType(val displayNameAr: String, val displayNameEn: String) : Parcelable {
    UDL("توزيع منتظم", "Uniform Distributed Load"),
    PointLoad("حمولة مركزة", "Point Load"),
    Moment("عزم", "Applied Moment"),
    LinearVarying("متغير خطياً", "Linearly Varying Load")
}

// ============================================================================
// Results Entities
// ============================================================================

/**
 * نتيجة تحليل العقدة (إزاحات + ردود الأفعال)
 */
@Parcelize
data class NodeResult(
    val nodeId: Int,
    val dx: Double = 0.0,        // m - الإزاحة الأفقية
    val dy: Double = 0.0,        // m - الإزاحة الرأسية
    val rz: Double = 0.0,        // rad - الدوران
    val reactionFx: Double = 0.0, // kN - رد فعل أفقي
    val reactionFy: Double = 0.0, // kN - رد فعل رأسي
    val reactionMz: Double = 0.0  // kN.m - رد فعل عزم
) : Parcelable

/**
 * نتيجة تحليل العضو (قوى داخلية عند النهايات)
 */
@Parcelize
data class MemberEndForces(
    val memberId: Int,
    // قوى عند بداية العضو (Node I)
    val fi_x: Double = 0.0,     // kN - محورية عند I
    val fi_y: Double = 0.0,     // kN - قص عند I
    val mi_z: Double = 0.0,     // kN.m - عزم عند I
    // قوى عند نهاية العضو (Node J)
    val fj_x: Double = 0.0,     // kN - محورية عند J
    val fj_y: Double = 0.0,     // kN - قص عند J
    val mj_z: Double = 0.0      // kN.m - عزم عند J
) : Parcelable {
    val maxMoment: Double get() = maxOf(kotlin.math.abs(mi_z), kotlin.math.abs(mj_z))
    val maxShear: Double get() = maxOf(kotlin.math.abs(fi_y), kotlin.math.abs(fj_y))
    val axialForce: Double get() = kotlin.math.abs(fi_x)
}

/**
 * نقطة على مخطط القوى الداخلية (للرسم)
 */
@Parcelize
data class DiagramPoint(
    val x: Double,   // الموقع على العضو (من 0 إلى L)
    val value: Double // قيمة القوة (عزم/قص/محوري)
) : Parcelable

/**
 * مخطط القوى الداخلية لعضو واحد
 */
@Parcelize
data class MemberDiagram(
    val memberId: Int,
    val momentDiagram: List<DiagramPoint> = emptyList(),     // kN.m
    val shearDiagram: List<DiagramPoint> = emptyList(),      // kN
    val axialDiagram: List<DiagramPoint> = emptyList(),      // kN
    val maxMoment: Double = 0.0,
    val maxShear: Double = 0.0,
    val maxAxial: Double = 0.0
) : Parcelable

/**
 * نتيجة التصميم لعضو خرساني
 */
@Parcelize
data class ConcreteMemberDesignResult(
    val memberId: Int,
    val memberName: String,
    val memberType: FrameMemberType,
    val section: ConcreteSectionProps,
    val maxMoment: Double,
    val maxShear: Double,
    val axialForce: Double,
    // تصميم الانحناء
    val asRequired: Double = 0.0,        // mm²
    val asProvided: Double = 0.0,        // mm²
    val barDia: Double = 0.0,            // mm
    val numBarsTop: Int = 0,
    val numBarsBot: Int = 0,
    val asTop: Double = 0.0,             // mm²
    val asBot: Double = 0.0,             // mm²
    // تصميم القص
    val stirrupDia: Double = 0.0,        // mm
    val stirrupSpacing: Double = 0.0,    // mm
    val vuCapacity: Double = 0.0,        // kN
    //checks
    val momentUtilization: Double = 0.0,
    val shearUtilization: Double = 0.0,
    val isSafe: Boolean = true,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList()
) : Parcelable

/**
 * نتيجة التصميم لعضو معدني
 */
@Parcelize
data class SteelMemberDesignResult(
    val memberId: Int,
    val memberName: String,
    val memberType: FrameMemberType,
    val selectedSection: String,
    val sectionIx: Double,          // cm⁴
    val sectionSx: Double,          // cm³
    val sectionZx: Double,          // cm³
    val sectionArea: Double,        // cm²
    val sectionWeight: Double,      // kg/m
    val maxMoment: Double,
    val maxShear: Double,
    val axialForce: Double,
    val flexuralCapacity: Double,   // kN.m
    val shearCapacity: Double,      // kN
    val axialCapacity: Double,      // kN
    val flexuralUtilization: Double,
    val shearUtilization: Double,
    val axialUtilization: Double,
    val combinedUtilization: Double,
    val isSafe: Boolean = true,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList()
) : Parcelable

/**
 * نتيجة التحليل الكاملة
 */
@Parcelize
data class FrameAnalysisResult(
    val nodeResults: List<NodeResult> = emptyList(),
    val memberEndForces: List<MemberEndForces> = emptyList(),
    val memberDiagrams: List<MemberDiagram> = emptyList(),
    val concreteDesignResults: List<ConcreteMemberDesignResult> = emptyList(),
    val steelDesignResults: List<SteelMemberDesignResult> = emptyList(),
    val isSolved: Boolean = false,
    val errorMessage: String? = null
) : Parcelable {
    val hasResults: Boolean get() = isSolved && errorMessage == null
}

/**
 * إعدادات التحليل
 */
@Parcelize
data class FrameAnalysisSettings(
    val designCode: DesignCode = DesignCode.ECP,
    val eColumn: Double = 25e6,      // kN/m² (25 MPa default) - معامل المرونة للخرسانة
    val eSteel: Double = 200e6,      // kN/m² (200 GPa) - معامل المرونة للحديد
    val loadCombinations: List<LoadCombinationFrame> = listOf(
        LoadCombinationFrame("1.4DL + 1.6LL", 1.4, 1.6, 0.0),
        LoadCombinationFrame("1.2DL + 1.6LL + 0.5WL", 1.2, 1.6, 0.5)
    )
) : Parcelable

@Parcelize
data class LoadCombinationFrame(
    val name: String,
    val dlFactor: Double,
    val llFactor: Double,
    val wlFactor: Double
) : Parcelable