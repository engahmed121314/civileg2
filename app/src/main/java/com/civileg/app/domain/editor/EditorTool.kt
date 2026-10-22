package com.civileg.app.domain.editor

import com.civileg.app.utils.LocaleHelper

enum class EditorTool(val displayNameAr: String, val displayNameEn: String, val iconName: String) {
    Select("تحديد/نقل", "Select/Move", "near_me"),
    AddNode("إضافة عقدة", "Add Joint", "add_circle"),
    DrawMember("رسم عضو", "Draw Member", "timeline"),
    AddSupport("ارتكاز", "Support", "foundation"),
    AddNodalLoad("حمولة نقطية", "Nodal Load", "arrow_downward"),
    AddMemberLoad("حمولة موزعة", "Member Load", "linear_scale"),
    Delete("حذف", "Delete", "delete"),
    Pan("تحريك", "Pan", "pan_tool");

    val displayName: String
        get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
}
