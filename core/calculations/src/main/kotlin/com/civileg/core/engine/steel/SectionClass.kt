package com.civileg.core.engine.steel

/**
 * تصنيف القطاعات الفولاذية حسب AISC 360-16 Table B4.1.
 *
 * // AISC 360-16 Table B4.1 — Classification of Sections for Local Buckling.
 * - COMPACT: λ ≤ λp — يمكن للقطاع أن يصل إلى العزم البلاستيكي Mp دون تجعد محلي.
 * - NON_COMPACT: λp < λ ≤ λr — العجز عن الوصول إلى Mp ولكن لا تجعد محلي قبل Mn.
 * - SLENDER: λ > λr — تجعد محلي يحدث قبل الوصول إلىMn، يتطلب خصائص مقطع فعالة.
 */
enum class SectionClass {
    COMPACT,
    NON_COMPACT,
    SLENDER
}
