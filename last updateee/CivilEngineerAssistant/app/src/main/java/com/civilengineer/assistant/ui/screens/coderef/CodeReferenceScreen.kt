package com.civilengineer.assistant.ui.screens.coderef

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.civilengineer.assistant.ui.components.*
import com.civilengineer.assistant.ui.theme.*

/**
 * شاشة مرجع الأكواد
 * توفر للمهندس المعلومات المرجعية الأساسية من الأكواد الثلاثة
 */
@Composable
fun CodeReferenceScreen(navController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("ECP المصري", "SBC السعودي", "ACI الأمريكي")

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "مرجع الأكواد",
                subtitle = "Code Reference",
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab, containerColor = PrimaryBlue) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, color = if (selectedTab == index) Color.White else Color.White.copy(0.6f), fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> EgyptianCodeReference()
                    1 -> SaudiCodeReference()
                    2 -> AmericanCodeReference()
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun EgyptianCodeReference() {
    CodeSection("الكود المصري لتصميم وتنفيذ المنشآت الخرسانية - ECP 203-2020", listOf(
        CodeRefItem("معاملات الأمان", listOf(
            "γc = 1.50 (خرسانة)", "γs = 1.15 (حديد)",
            "معامل حمل ميت = 1.40", "معامل حمل حي = 1.60"
        )),
        CodeRefItem("الغطاء الخرساني الأدنى (mm)", listOf(
            "أعمدة = 25mm", "كمرات = 25mm", "بلاطات = 15mm (داخلي)",
            "قواعد = 50mm", "عناصر ملامسة للتربة = 50mm"
        )),
        CodeRefItem("نسب تسليح الأعمدة", listOf(
            "الحد الأدنى μ_min = 0.8%", "الحد الأقصى μ_max = 4% (6% عند الوصلات)",
            "أقل بعد = 200mm", "تباعد كانات ≤ 15φ أو أقل بعد أو 200mm"
        )),
        CodeRefItem("نسب تسليح الكمرات", listOf(
            "ρ_min = max(0.25√fcu/fy, 1.3/fy)",
            "ρ_max = 0.67β₁(fcu/1.5γc)/(fy/1.15γs) × 600/(600+fy/γs)",
            "تسليح قص: qcu = 0.24√(fcu/γc)"
        )),
        CodeRefItem("نسب تسليح البلاطات", listOf(
            "حديد أملس: As_min = 0.25% × b × ts",
            "حديد مشرشر: As_min = 0.18% × b × ts",
            "أقل سمك بلاطة مصمتة = 80mm"
        )),
        CodeRefItem("أقصى إجهاد قص", listOf(
            "qu_max = 0.7√(fcu/γc) ≤ 4.4 N/mm²",
            "اختراق القص: قp ≤ 0.316√(fcu/γc)"
        )),
        CodeRefItem("سمك البلاطات المبدئي", listOf(
            "مصمتة اتجاه واحد: L/25 (مستمرة) - L/20 (بسيطة)",
            "مصمتة اتجاهين: L/32 (مستمرة)",
            "هوردي: L/20", "فلات سلاب: L/32",
            "كابولي: L/10"
        )),
    ))
}

@Composable
private fun SaudiCodeReference() {
    CodeSection("الكود السعودي للخرسانة - SBC 304", listOf(
        CodeRefItem("معاملات الأمان", listOf(
            "γc = 1.50 (خرسانة)", "γs = 1.15 (حديد)",
            "معامل حمل ميت = 1.40", "معامل حمل حي = 1.60"
        )),
        CodeRefItem("الغطاء الخرساني", listOf(
            "أعمدة = 40mm", "كمرات = 30mm", "بلاطات = 20mm",
            "قواعد = 75mm", "ملامس للتربة = 50mm"
        )),
        CodeRefItem("نسب التسليح", listOf(
            "أعمدة: 1% ≤ ρ ≤ 4%",
            "كمرات: ρ_min = max(0.25√fcu/fy, 1.4/fy)",
            "بلاطات: As_min = 0.20% (fy≤420) أو 0.18% (fy>420)"
        )),
        CodeRefItem("متطلبات الزلازل (SBC 301)", listOf(
            "المعادلة: V = Cs × W",
            "Cs = SDS/(R/I) ≥ 0.044×SDS×I",
            "T = 0.075H^0.75 (إطارات خرسانية)"
        )),
        CodeRefItem("تجميعات الأحمال", listOf(
            "U1 = 1.4D + 1.6L",
            "U2 = 1.2D + 1.0L + 1.0E",
            "U3 = 0.9D + 1.0E"
        )),
    ))
}

@Composable
private fun AmericanCodeReference() {
    CodeSection("الكود الأمريكي - ACI 318-19", listOf(
        CodeRefItem("معاملات تخفيض المقاومة φ", listOf(
            "انحناء = 0.90", "قص = 0.75", "ضغط (كانات) = 0.65",
            "ضغط (حلزوني) = 0.75", "تحمل = 0.65"
        )),
        CodeRefItem("تجميعات الأحمال (ASCE 7)", listOf(
            "U1 = 1.4D", "U2 = 1.2D + 1.6L",
            "U3 = 1.2D + 1.0L + 1.0E", "U4 = 0.9D + 1.0E",
            "U5 = 1.2D + 1.0W + 1.0L"
        )),
        CodeRefItem("الغطاء الخرساني", listOf(
            "ملامس للتربة = 75mm", "معرض للطقس (قطر كبير) = 50mm",
            "معرض للطقس (قطر صغير) = 38mm", "داخلي = 20mm"
        )),
        CodeRefItem("نسب التسليح", listOf(
            "أعمدة: 1% ≤ ρ ≤ 8%",
            "كمرات: ρ_min = max(0.25√f'c/fy, 1.4/fy)",
            "بلاطات: As_min = 0.18% (Grade 60)"
        )),
        CodeRefItem("القص (ACI 318-19)", listOf(
            "Vc = 0.17√f'c × bw × d",
            "Vs_max = 0.66√f'c × bw × d",
            "s_max = d/2 ≤ 600mm"
        )),
        CodeRefItem("الزلازل (ASCE 7-22)", listOf(
            "V = Cs × W",
            "Cs = min(SDS/(R/Ie), SD1/(T×R/Ie))",
            "Cs_min = max(0.044×SDS×Ie, 0.01)"
        )),
    ))
}

data class CodeRefItem(val title: String, val items: List<String>)

@Composable
private fun CodeSection(title: String, sections: List<CodeRefItem>) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryBlueDark)
    Spacer(modifier = Modifier.height(8.dp))

    sections.forEach { section ->
        CollapsibleSection(title = section.title, icon = Icons.Default.Article, initiallyExpanded = false) {
            section.items.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("  •  ", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
