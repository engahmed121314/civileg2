#!/usr/bin/env python3
"""Fix remaining compilation errors in StairDrawing and TankDrawing."""
import re

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app/ui/compose/components/drawings"

# === 1. ProfessionalStairDrawing.kt ===
f = f"{BASE}/ProfessionalStairDrawing.kt"
with open(f, 'r') as fh: c = fh.read()

# Fix max() type mismatch - lines 664, 695, 724
c = c.replace(
    'val barR = max((mainRebarDia / 2f * secScale).toFloat(), 2.5f)',
    'val rawBarR = (mainRebarDia / 2.0 * secScale.toDouble()).toFloat()\n        val barR = max(rawBarR, 2.5f)'
)
c = c.replace(
    'val barR = max((topRebarDia / 2f) * secScale, 2f).toFloat()',
    'val rawTopR = (topRebarDia / 2.0 * secScale.toDouble()).toFloat()\n        val barR = max(rawTopR, 2f)'
)
c = c.replace(
    'val distR = max((distributionDia / 2f) * secScale, 1.5f).toFloat()',
    'val rawDistR = (distributionDia / 2.0 * secScale.toDouble()).toFloat()\n        val distR = max(rawDistR, 1.5f)'
)

# Fix "for (i in 0 until numBars)" and "for (i in 1..numBars)" where numBars is Float
c = c.replace('for (i in 0 until numBars) {', 'for (i in 0 until numBars.toInt()) {')
c = c.replace('for (i in 1..numBars) {', 'for (i in 1..numBars.toInt()) {')

# Fix "for (i in 1..numDist)" where numDist is Float
c = c.replace('for (i in 1..numDist) {', 'for (i in 1..numDist.toInt()) {')

# Fix Color->Int for paint.color
c = c.replace('this.color = color\n', 'this.color = color.toArgb()\n')
c = c.replace('        color = textColor\n', '        color = textColor.toArgb()\n')

with open(f, 'w') as fh: fh.write(c)
print("Fixed ProfessionalStairDrawing.kt")

# === 2. ProfessionalTankDrawing.kt ===
f2 = f"{BASE}/ProfessionalTankDrawing.kt"
with open(f2, 'r') as fh: c2 = fh.read()

# Add toArgb import if not present
if 'import androidx.compose.ui.graphics.toArgb' not in c2:
    c2 = c2.replace(
        'import androidx.compose.ui.graphics.Color',
        'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.toArgb'
    )
    print("  Added toArgb import")

# Fix nativeCanvas receiver mismatch at lines ~316-329
# The function drawConcreteHatchingOnRect likely uses nativeCanvas without DrawScope receiver
# Find the function and check if it's a DrawScope extension
if 'drawConcreteHatchingOnRect' in c2:
    # Check function signature
    m = re.search(r'(private\s+fun\s+DrawScope\.drawConcreteHatchingOnRect|private\s+fun\s+drawConcreteHatchingOnRect)', c2)
    if m:
        if 'DrawScope.' in m.group(1):
            print("  drawConcreteHatchingOnRect is already DrawScope extension")
        else:
            # It's not a DrawScope extension - make it one
            c2 = c2.replace(
                'private fun drawConcreteHatchingOnRect(',
                'private fun DrawScope.drawConcreteHatchingOnRect('
            )
            print("  Made drawConcreteHatchingOnRect a DrawScope extension")
    # Replace bare nativeCanvas with drawContext.canvas.nativeCanvas
    # Only within the drawConcreteHatchingOnRect function body
    # Find start and end of function
    start = c2.find('fun DrawScope.drawConcreteHatchingOnRect(')
    if start == -1:
        start = c2.find('fun drawConcreteHatchingOnRect(')
    if start >= 0:
        # Find the next function definition (end of this function)
        next_fn = re.search(r'\n(?:private |public |internal |fun )', c2[start+10:])
        if next_fn:
            end = start + 10 + next_fn.start()
            func_body = c2[start:end]
            # Replace bare nativeCanvas accesses
            new_body = func_body.replace('nativeCanvas.', 'drawContext.canvas.nativeCanvas.')
            c2 = c2[:start] + new_body + c2[end:]
            print("  Fixed nativeCanvas receiver in drawConcreteHatchingOnRect")

# Fix Float->Double type mismatch at lines ~462-463
c2 = re.sub(
    r'(\w+)\.sqrt\((\w+\.toDouble\(\))\)',
    r'\1.sqrt(\2.toDouble())',
    c2
)

# Fix Offset with Double args at lines ~477-480
# Look for patterns like Offset(expr, expr) where expr might be Double
lines = c2.split('\n')
new_lines = []
for i, line in enumerate(lines):
    # Fix Offset() calls that might have Double args - add .toFloat()
    if 'Offset(' in line and 'drawTextAnnotated' not in line:
        # Simple approach: wrap each arg in .toFloat() if it contains arithmetic
        line = re.sub(r'Offset\(([^,]+),\s*([^)]+)\)', 
                      lambda m: f'Offset({m.group(1).strip()}.toFloat(), {m.group(2).strip()}.toFloat())' 
                      if '.toFloat()' not in m.group(1) else line,
                      line)
    new_lines.append(line)

# Fix drawTextAnnotated call at line ~481
c2 = '\n'.join(new_lines)
# The drawTextAnnotated error is likely a type mismatch in args
# Check what's on that line
for i, line in enumerate(c2.split('\n')):
    if 'drawTextAnnotated' in line and 'TankDrawing' in ''.join(c2.split('\n')[:i]):
        # Check if args have wrong types
        if 'offsetX' in line:
            line_num = i + 1
            print(f"  drawTextAnnotated at line {line_num}: {line.strip()[:100]}")

# Fix Color->Int at line ~789/790
c2 = c2.replace('this.color = color', 'this.color = color.toArgb()')

with open(f2, 'w') as fh: fh.write(c2)
print("Fixed ProfessionalTankDrawing.kt")
print("Done!")