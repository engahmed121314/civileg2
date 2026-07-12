package com.civileg.app.ui.compose.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import com.civileg.app.ui.compose.components.rememberWindowSizeClass
import com.civileg.app.ui.compose.components.WindowSizeClass

// ══════════════════════════════════════════════════════════════════════
// Color palette extensions for the Home Screen
// ══════════════════════════════════════════════════════════════════════
private val HeaderGradientStart   = Color(0xFF0D1B3E)
private val HeaderGradientMid     = Color(0xFF162B5B)
private val HeaderGradientEnd     = Color(0xFF2A1B5E) // deep purple
private val HeaderOverlay         = Color(0xFF0F9DFF).copy(alpha = 0.06f)

private val StatCardBlue          = Color(0xFF1565C0)
private val StatCardGreen         = Color(0xFF2E7D32)
private val StatCardOrange        = Color(0xFFE65100)

private val CardAccentBeam        = Color(0xFF1976D2)
private val CardAccentColumn      = Color(0xFF7B1FA2)
private val CardAccentSlab        = Color(0xFF00838F)
private val CardAccentFooting     = Color(0xFF4E342E)
private val CardAccentTank        = Color(0xFF0277BD)
private val CardAccentWall        = Color(0xFF558B2F)
private val CardAccentStair       = Color(0xFFAD1457)
private val CardAccentSteel       = Color(0xFF37474F)
private val CardAccentSeismic     = Color(0xFFBF360C)
private val CardAccentFrame       = Color(0xFF1A237E)

private val ToolCalcBg            = Color(0xFF1B5E20).copy(alpha = 0.12f)
private val ToolCalcAccent        = Color(0xFF2E7D32)
private val ToolConvBg            = Color(0xFF0D47A1).copy(alpha = 0.12f)
private val ToolConvAccent        = Color(0xFF1565C0)
private val ToolSteelBg           = Color(0xFF4E342E).copy(alpha = 0.12f)
private val ToolSteelAccent       = Color(0xFF5D4037)
private val ToolQtyBg             = Color(0xFFE65100).copy(alpha = 0.12f)
private val ToolQtyAccent         = Color(0xFFEF6C00)
private val ToolPdfBg             = Color(0xFFB71C1C).copy(alpha = 0.12f)
private val ToolPdfAccent         = Color(0xFFD32F2F)

private val FooterBackground      = Color(0xFFF8F9FA)
private val FooterDarkBg          = Color(0xFF161622)

// ══════════════════════════════════════════════════════════════════════
// Data models
// ══════════════════════════════════════════════════════════════════════

@Immutable
data class DesignModuleItem(
    val screen: AppScreen,
    val title: String,
    val subtitle: String,
    val supportedCodes: List<String>,
    val accentColor: Color,
    val icon: ImageVector
)

@Immutable
data class QuickTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val bgColor: Color,
    val accentColor: Color,
    val route: String
)

@Immutable
data class RecentProject(
    val name: String,
    val elementType: String,
    val date: String,
    val code: String,
    val icon: ImageVector
)

private val enhancedDesignModules = listOf(
    DesignModuleItem(AppScreen.BeamDesign,     "الكمرات",           "تصميم الكمرات الخرسانية",   listOf("ECP", "ACI", "SBC"), CardAccentBeam,   Icons.Default.AccountBalance),
    DesignModuleItem(AppScreen.ColumnDesign,    "الأعمدة",           "تصميم الأعمدة الخرسانية",   listOf("ECP", "ACI", "SBC"), CardAccentColumn,  Icons.Default.ViewColumn),
    DesignModuleItem(AppScreen.SlabDesign,      "البلاطات",          "البلاطات المصمتة والهوردية", listOf("ECP", "ACI"),        CardAccentSlab,    Icons.Default.ViewWeek),
    DesignModuleItem(AppScreen.FootingDesign,   "القواعد",           "تصميم القواعد المنفصلة",    listOf("ECP", "ACI"),        CardAccentFooting, Icons.Default.Layers),
    DesignModuleItem(AppScreen.TankDesign,      "الخزانات",          "خزانات المياه (علوي/أرضي)", listOf("ECP", "ACI"),        CardAccentTank,    Icons.Default.WaterDrop),
    DesignModuleItem(AppScreen.RetainingWall,   "حوائط السند",       "حوائط السند واستقرار التربة", listOf("ECP", "ACI"),     CardAccentWall,    Icons.Default.SensorDoor),
    DesignModuleItem(AppScreen.StairDesign,     "السلالم",           "تصميم السلالم الخرسانية",   listOf("ECP", "ACI"),        CardAccentStair,   Icons.Default.Stairs),
    DesignModuleItem(AppScreen.SteelDesign,     "المنشآت المعدنية",  "تصميم الوصلات والأعضاء",     listOf("AISC", "ECP"),      CardAccentSteel,   Icons.Default.Build),
    DesignModuleItem(AppScreen.SeismicAnalysis, "الزلازل",           "قوى الزلازل والرياح",       listOf("ASCE", "SBC"),       CardAccentSeismic, Icons.Default.Warning),
    DesignModuleItem(AppScreen.FrameAnalysis,  "تحليل الإطارات",   "تحليل وتصميم الهياكل ثنائية الأبعاد", listOf("ECP", "ACI", "AISC"), CardAccentFrame, Icons.Default.AccountTree)
)

private val quickTools = listOf(
    QuickTool("حاسبة علمية",    "Scientific Calculator",  Icons.Default.Calculate,        ToolCalcBg,  ToolCalcAccent,  "calc"),
    QuickTool("محول الوحدات",   "Unit Converter",         Icons.Default.SwapHoriz,       ToolConvBg,  ToolConvAccent,  "converter"),
    QuickTool("جداول الحديد",   "Steel Rebar Tables",     Icons.Default.TableChart,      ToolSteelBg, ToolSteelAccent, "steel_tables"),
    QuickTool("كميات الأعمال",  "Quantity Surveying",     Icons.Default.Assignment,      ToolQtyBg,   ToolQtyAccent,   "boq"),
    QuickTool("التصدير PDF",    "PDF Export",             Icons.Default.PictureAsPdf,    ToolPdfBg,   ToolPdfAccent,   "pdf")
)

// Stub recent projects — in a real app this comes from a ViewModel / Room
private val stubRecentProjects = listOf<RecentProject>() // empty

// ══════════════════════════════════════════════════════════════════════
// Main Home Screen
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateTo: (String) -> Unit,
    onShowSettings: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // Adaptive layout: determine grid columns based on window size
    val windowSize = rememberWindowSizeClass()
    val gridColumns = when (windowSize) {
        WindowSizeClass.EXPANDED -> 4   // Large tablet: 4 columns
        WindowSizeClass.MEDIUM -> 3     // Small tablet: 3 columns
        WindowSizeClass.COMPACT -> 2    // Phone: 2 columns
    }
    val horizontalPadding = when (windowSize) {
        WindowSizeClass.EXPANDED -> 24.dp
        WindowSizeClass.MEDIUM -> 20.dp
        WindowSizeClass.COMPACT -> 14.dp
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Civil Engineer Pro",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Professional Suite",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onShowSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    if (isDark) Color(0xFF0F0F1A) else Color(0xFFF5F6FA)
                )
                .padding(horizontal = horizontalPadding)
        ) {
            // ══════════════════════════════════════════
            // A. HEADER SECTION
            // ══════════════════════════════════════════
            item(span = { GridItemSpan(gridColumns) }) {
                HeaderSection(isDark = isDark)
            }

            // ══════════════════════════════════════════
            // B. QUICK STATS ROW
            // ══════════════════════════════════════════
            item(span = { GridItemSpan(gridColumns) }) {
                QuickStatsRow(isDark = isDark)
            }

            // ══════════════════════════════════════════
            // C. MAIN DESIGN MODULES GRID
            // ══════════════════════════════════════════
            item(span = { GridItemSpan(gridColumns) }) {
                SectionHeader(
                    icon = Icons.Default.Engineering,
                    title = "التصميم الإنشائي",
                    subtitle = "9 وحدات تصميم متكاملة"
                )
            }

            items(enhancedDesignModules) { module ->
                EnhancedModuleCard(
                    module = module,
                    isDark = isDark,
                    onClick = { onNavigateTo(module.screen.route) }
                )
            }

            // ══════════════════════════════════════════
            // D. QUICK TOOLS SECTION (horizontal)
            // ══════════════════════════════════════════
            item(span = { GridItemSpan(gridColumns) }) {
                SectionHeader(
                    icon = Icons.Default.Build,
                    title = "أدوات سريعة",
                    subtitle = "أدوات مساعدة للمهندس المدني"
                )
            }

            item(span = { GridItemSpan(gridColumns) }) {
                QuickToolsRow(
                    isDark = isDark,
                    onToolClick = { onNavigateTo(it) }
                )
            }

            // ══════════════════════════════════════════
            // E. RECENT PROJECTS SECTION
            // ══════════════════════════════════════════
            item(span = { GridItemSpan(gridColumns) }) {
                SectionHeader(
                    icon = Icons.Default.History,
                    title = "آخر التصاميم",
                    subtitle = "تصاميمك المحفوظة مؤخراً"
                )
            }

            item(span = { GridItemSpan(gridColumns) }) {
                RecentProjectsSection(
                    projects = stubRecentProjects,
                    isDark = isDark
                )
            }

            // ══════════════════════════════════════════
            // F. FOOTER
            // ══════════════════════════════════════════
            item(span = { GridItemSpan(gridColumns) }) {
                FooterSection(isDark = isDark)
            }

            // Bottom spacer
            item(span = { GridItemSpan(gridColumns) }) {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// A. HEADER SECTION
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun HeaderSection(isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        HeaderGradientStart,
                        HeaderGradientMid,
                        HeaderGradientEnd
                    ),
                    start = Offset.Zero,
                    end = Offset(600f, 300f)
                )
            )
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = (-20).dp.roundToPx(), y = (-20).dp.roundToPx()) }
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(x = 30.dp.roundToPx(), y = 30.dp.roundToPx()) }
                .size(80.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.03f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            // Logo + version badge row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = Color.White.copy(alpha = 0.95f)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        "مهندس المدني",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        lineHeight = 32.sp
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "تصميم هندسي احترافي متعدد الأكواد",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Subtitle
            Text(
                "ابدأ مشروعك الإنشائي القادم بدقة واحترافية\nيدعم الكود المصري ECP 203 والأمريكي ACI 318 والسعودي SBC 304",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Code badges row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CodeBadge("ECP 203", Color(0xFF4FC3F7))
                CodeBadge("ACI 318", Color(0xFF81C784))
                CodeBadge("SBC 304", Color(0xFFFFB74D))
                CodeBadge("AISC", Color(0xFFEF9A9A))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Version badge
            Surface(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "v2.0 — إصدار احترافي",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBadge(code: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = code,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
// B. QUICK STATS ROW
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun QuickStatsRow(isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            value = "0",
            label = "تصميمات محفوظة",
            accentColor = StatCardBlue,
            icon = Icons.Default.Save,
            isDark = isDark,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            value = "3",
            label = "الأكواد المدعومة",
            accentColor = StatCardGreen,
            icon = Icons.Default.Gavel,
            isDark = isDark,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            value = "7+",
            label = "عناصر التصميم",
            accentColor = StatCardOrange,
            icon = Icons.Default.Grid3x3,
            isDark = isDark,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    accentColor: Color,
    icon: ImageVector,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "stat_animate"
    )

    Card(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E1E30) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Accent bar on the left
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = value,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = if (isDark) Color.White else Color(0xFF1A1A2E),
                        lineHeight = 24.sp
                    )
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575),
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// C. ENHANCED DESIGN MODULE CARD
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun EnhancedModuleCard(
    module: DesignModuleItem,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF1E1E30) else Color.White
    val subtleBg = module.accentColor.copy(alpha = 0.06f)

    Card(
        onClick = onClick,
        modifier = Modifier.height(170.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(subtleBg)
        ) {
            // Top-right decorative circle
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(x = 12.dp.roundToPx(), y = (-12).dp.roundToPx()) }
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(module.accentColor.copy(alpha = 0.08f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icon with accent background
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(module.accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = module.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = module.accentColor
                    )
                }

                // Title + subtitle
                Column {
                    Text(
                        text = module.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isDark) Color.White else Color(0xFF1A1A2E),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = module.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575),
                        maxLines = 2,
                        lineHeight = 15.sp,
                        fontSize = 11.sp
                    )
                }

                // Code badges
                if (module.supportedCodes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        module.supportedCodes.forEach { code ->
                            Surface(
                                color = module.accentColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = module.accentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// D. QUICK TOOLS ROW (horizontal scroll)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun QuickToolsRow(
    isDark: Boolean,
    onToolClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        quickTools.forEach { tool ->
            QuickToolCard(
                tool = tool,
                isDark = isDark,
                onClick = { onToolClick(tool.route) }
            )
        }
    }
}

@Composable
private fun QuickToolCard(
    tool: QuickTool,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF1E1E30) else Color.White

    Card(
        onClick = onClick,
        modifier = Modifier.width(130.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tool.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = tool.accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = tool.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isDark) Color.White else Color(0xFF1A1A2E),
                maxLines = 1,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Subtitle
            Text(
                text = tool.subtitle,
                fontSize = 9.sp,
                color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF9E9E9E),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// E. RECENT PROJECTS SECTION
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun RecentProjectsSection(
    projects: List<RecentProject>,
    isDark: Boolean
) {
    val cardBg = if (isDark) Color(0xFF1E1E30) else Color.White

    if (projects.isEmpty()) {
        // Empty state
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF4A4A6A) else Color(0xFFBDBDBD),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "لا توجد تصاميم محفوظة",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ابدأ تصميمك الأول وسيظهر هنا",
                    fontSize = 12.sp,
                    color = if (isDark) Color(0xFF6B6B8A) else Color(0xFFBDBDBD)
                )
            }
        }
    } else {
        // List of recent projects
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            projects.take(3).forEach { project ->
                RecentProjectItem(project = project, isDark = isDark)
            }
        }
    }
}

@Composable
private fun RecentProjectItem(
    project: RecentProject,
    isDark: Boolean
) {
    val cardBg = if (isDark) Color(0xFF1E1E30) else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Element icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = project.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Name + type
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isDark) Color.White else Color(0xFF1A1A2E),
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.elementType,
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        color = if (isDark) Color(0xFF4A4A6A) else Color(0xFFBDBDBD),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = project.date,
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
                    )
                }
            }

            // Code badge
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = project.code,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// F. FOOTER
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun FooterSection(isDark: Boolean) {
    val footerBg = if (isDark) FooterDarkBg else FooterBackground
    val textColor = if (isDark) Color(0xFF6B6B8A) else Color(0xFF9E9E9E)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = footerBg,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Divider
            HorizontalDivider(
                color = if (isDark) Color(0xFF2A2A3E) else Color(0xFFE0E0E0),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App branding
            Text(
                text = "CivilEG v2.0",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = if (isDark) Color(0xFF4FC3F7) else Color(0xFF1565C0)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "محفظة المهندس المدني الاحترافية",
                fontSize = 11.sp,
                color = textColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Code badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CodeBadge("ECP 203", Color(0xFF4FC3F7))
                Text("•", color = textColor, fontSize = 12.sp)
                CodeBadge("ACI 318", Color(0xFF81C784))
                Text("•", color = textColor, fontSize = 12.sp)
                CodeBadge("SBC 304", Color(0xFFFFB74D))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Developer credit
            Text(
                text = "تطوير فريق CivilEG — هندسة برمجية متخصصة",
                fontSize = 10.sp,
                color = textColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// SHARED — Section Header
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}