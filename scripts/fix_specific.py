#!/usr/bin/env python3
"""Apply all verified specific fixes to non-drawing files."""
import re, os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app"

def read_file(p):
    with open(p, 'r', encoding='utf-8') as f: return f.read()

def write_file(p, c):
    with open(p, 'w', encoding='utf-8') as f: f.write(c)

count = 0

# 1. SteelDesignScreen.kt
fp = os.path.join(BASE, "ui/compose/screens/SteelDesignScreen.kt")
c = read_file(fp)
o = c
# Fix duplicate imports and add SteelConnectionDesign
c = c.replace(
    'import com.civileg.app.domain.calculations.ecp.WeldDesignResult\nimport com.civileg.app.domain.calculations.ecp.BoltDesignResult\nimport com.civileg.app.domain.calculations.ecp.BlockShearResult\nimport com.civileg.app.domain.calculations.ecp.WeldDesignResult\nimport com.civileg.app.domain.calculations.ecp.BoltDesignResult\nimport com.civileg.app.domain.calculations.ecp.BlockShearResult.WeldDesignResult',
    'import com.civileg.app.domain.calculations.ecp.WeldDesignResult\nimport com.civileg.app.domain.calculations.ecp.BoltDesignResult\nimport com.civileg.app.domain.calculations.ecp.BlockShearResult\nimport com.civileg.app.domain.calculations.ecp.SteelConnectionDesign'
)
# Fix missing modifier in SteelInputField call
c = c.replace(
    'SteelInputField(appliedWeldForce, "القوة المؤثرة (kN)", { appliedWeldForce = it })',
    'SteelInputField(appliedWeldForce, "القوة المؤثرة (kN)", { appliedWeldForce = it }, Modifier.fillMaxWidth())'
)
if c != o: write_file(fp, c); count += 1; print("  SteelDesignScreen")

# 2. FrameDrawingCanvas.kt
fp = os.path.join(BASE, "ui/compose/screens/FrameDrawingCanvas.kt")
c = read_file(fp); o = c
# Fix ::toScreen
c = c.replace('::::toScreen', '::toScreen')
# Fix scale to Float
c = c.replace(
    'val scale = min(\n            drawW.value / xSpan,\n            drawH.value / ySpan\n        ) * 0.85',
    'val scale = min(\n            drawW.value / xSpan,\n            drawH.value / ySpan\n        ).toFloat() * 0.85f'
)
# Fix calculateGridSpacing pow/compareTo
c = c.replace(
    'val magnitude = 10.0.pow(floor(log10(rawSpacing)))',
    'val magnitude = 10.0.pow(floor(log10(rawSpacing.toDouble())).toInt()).toDouble()'
)
c = c.replace(
    'rawSpacing / magnitude < 2 -> 2 * magnitude\n        rawSpacing / magnitude < 5 -> 5 * magnitude\n        else -> 10 * magnitude',
    'rawSpacing / magnitude.toFloat() < 2f -> 2.0 * magnitude\n        rawSpacing / magnitude.toFloat() < 5f -> 5.0 * magnitude\n        else -> 10.0 * magnitude'
)
# Fix drawDeformedShape
c = c.replace(
    "val maxDisp = nodeResults.maxOfOrNull { max(abs(it.dx), abs(it.dy)) * scale } ?: 0f\n    if (maxDisp < 0.5f) return\n    val deformScale = 50f / maxDisp.coerceAtLeast(1f)",
    "val maxDisp = nodeResults.maxOfOrNull { max(abs(it.dx), abs(it.dy)) * scale.toDouble() } ?: 0.0\n    if (maxDisp < 0.5) return\n    val deformScale = 50.0 / maxDisp.coerceAtLeast(1.0)"
)
# Fix offsetX/offsetY
c = c.replace(
    'val offsetX = padding.value + (drawW.value - xSpan * scale) / 2 - xRange.start * scale\n        val offsetY = padding.value + (drawH.value - ySpan * scale) / 2 + yRange.endInclusive * scale',
    'val offsetX = (padding.value + (drawW.value - xSpan * scale) / 2 - xRange.start * scale).toFloat()\n        val offsetY = (padding.value + (drawH.value - ySpan * scale) / 2 + yRange.endInclusive * scale).toFloat()'
)
# Fix drawScaleBar
c = c.replace('val barLength_px = barLength_m * scale', 'val barLength_px = (barLength_m * scale).toFloat()')
c = c.replace('Offset(x, y - 5), end = Offset(x, y + 5)', 'Offset(x, y - 5f), end = Offset(x, y + 5f)')
c = c.replace('Offset(x + barLength_px, y - 5), end = Offset(x + barLength_px, y + 5)', 'Offset(x + barLength_px, y - 5f), end = Offset(x + barLength_px, y + 5f)')
c = c.replace('Offset(x + barLength_px / 2 - 10, y + 6)', 'Offset(x + barLength_px / 2f - 10f, y + 6f)')
if c != o: write_file(fp, c); count += 1; print("  FrameDrawingCanvas")

# 3. HomeScreen.kt
fp = os.path.join(BASE, "ui/compose/screens/HomeScreen.kt")
c = read_file(fp); o = c
c = c.replace('Icons.Default.Structure', 'Icons.Default.AccountBalance')
# Replace offset modifier with padding
c = c.replace('.offset(x = (-20).dp, y = (-20).dp)', '.padding(end = 20.dp, top = 20.dp)')
c = c.replace('.offset(x = 30.dp, y = 30.dp)', '.padding(start = 30.dp, bottom = 30.dp)')
c = c.replace('.offset(x = 12.dp, y = (-12).dp)', '.padding(end = 12.dp, top = 12.dp)')
# Add missing import
if 'import androidx.compose.ui.layout.offset' in c:
    c = c.replace('import androidx.compose.ui.layout.offset\n', '')
if 'import androidx.compose.ui.layout.offset' not in c and '.offset(' not in c:
    pass  # no need for offset import
if c != o: write_file(fp, c); count += 1; print("  HomeScreen")

# 4. SeismicScreen.kt
fp = os.path.join(BASE, "ui/compose/screens/SeismicScreen.kt")
c = read_file(fp); o = c
if 'import androidx.compose.runtime.livedata.observeAsState' not in c:
    c = c.replace('import androidx.compose.runtime.*', 'import androidx.compose.runtime.*\nimport androidx.compose.runtime.livedata.observeAsState')
# Add missing imports for drawing
if 'StrokeCap' in c and 'import androidx.compose.ui.graphics.StrokeCap' not in c:
    c = c.replace('import androidx.compose.ui.graphics.Color', 
                   'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.PathEffect\nimport androidx.compose.ui.graphics.StrokeCap\nimport androidx.compose.ui.graphics.StrokeJoin')
if c != o: write_file(fp, c); count += 1; print("  SeismicScreen")

# 5. StairScreen.kt
fp = os.path.join(BASE, "ui/compose/screens/StairScreen.kt")
c = read_file(fp); o = c
# Fix totalSpanMM to be Float
c = c.replace('val totalSpanMM = res.span * 1000f', 'val totalSpanMM = res.span.toFloat() * 1000f')
# Fix toDegrees
c = c.replace('-toDegrees(slopeAngle).toFloat()', '-Math.toDegrees(slopeAngle).toFloat()')
# Fix if expression
c = c.replace('if (hasLanding && landingThickness > 0.0).toFloat()\n            landingThickness else slabThickness',
              'if (hasLanding && landingThickness > 0.0f)\n            landingThickness else slabThickness')
if c != o: write_file(fp, c); count += 1; print("  StairScreen")

# 6. ArchiveScreen.kt
fp = os.path.join(BASE, "ui/compose/screens/ArchiveScreen.kt")
c = read_file(fp); o = c
# Change to use db.Project
c = c.replace('import com.civileg.app.domain.entities.Project', 'import com.civileg.app.db.Project')
c = c.replace('viewModel.allArchiveProjects', 'viewModel.allProjects')
c = c.replace('viewModel.deleteArchiveProject(project)', 'viewModel.delete(project)')
if c != o: write_file(fp, c); count += 1; print("  ArchiveScreen")

# 7. BeamScreen.kt
fp = os.path.join(BASE, "ui/compose/screens/BeamScreen.kt")
c = read_file(fp); o = c
c = c.replace('res.span ?: 5.0', 'span.toDoubleOrNull() ?: 5.0')
c = c.replace('res.developmentLength ?: 0.0', '0.0')
if c != o: write_file(fp, c); count += 1; print("  BeamScreen")

# 8. ColumnScreen.kt
fp = os.path.join(BASE, "ui/compose/screens/ColumnScreen.kt")
c = read_file(fp); o = c
c = c.replace('(result.clearHeight ?: 3000.0)', '3000.0')
c = c.replace('result.isCircular', 'result.columnType.equals("CIRCULAR", ignoreCase = true)')
if c != o: write_file(fp, c); count += 1; print("  ColumnScreen")

# 9. ProfessionalBottomNavBar.kt
fp = os.path.join(BASE, "ui/compose/components/ProfessionalBottomNavBar.kt")
c = read_file(fp); o = c
c = c.replace('Icons.Default.Structure', 'Icons.Default.AccountBalance')
if 'import androidx.compose.material3.Icon' not in c:
    lines = c.split('\n')
    for i, l in enumerate(lines):
        if l.strip().startswith('import ') and i > 0:
            lines.insert(i, 'import androidx.compose.material3.Icon')
            break
    c = '\n'.join(lines)
if c != o: write_file(fp, c); count += 1; print("  ProfessionalBottomNavBar")

# 10. SBC files
fp = os.path.join(BASE, "domain/calculations/sbc/SBCAdvancedSlab.kt")
c = read_file(fp); o = c
if 'import kotlin.math.pow' not in c:
    lines = c.split('\n')
    for i, l in enumerate(lines):
        if l.strip().startswith('import ') and i > 0:
            lines.insert(i, 'import kotlin.math.pow')
            break
    c = '\n'.join(lines)
if c != o: write_file(fp, c); count += 1; print("  SBCAdvancedSlab")

print(f"\nTotal specific fixes: {count}")