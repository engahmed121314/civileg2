import re, os, sys

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg2"
files = [
    "ui/compose/components/drawings/ProfessionalColumnDrawing.kt",
    "ui/compose/components/drawings/ProfessionalFootingDrawing.kt",
    "ui/compose/components/drawings/ProfessionalRetainingWallDrawing.kt",
    "ui/compose/components/drawings/ProfessionalSlabDrawing.kt",
    "ui/compose/components/drawings/ProfessionalStairDrawing.kt",
    "ui/compose/components/drawings/ProfessionalTankDrawing.kt",
]

total = 0
for f in files:
    fp = os.path.join(BASE, f)
    if not os.path.exists(fp): continue
    c = open(fp).read()
    oc = c
    
    # 1. Add kotlin.math.* if needed  
    has_pow = 'pow(' in c and 'import kotlin.math' not in c
    if has_pow:
        c = c.replace('\nimport\n', '\nimport kotlin.math.*\n', 1)
        oc = c
        print(f"  [math] {f}")
        total += 1
    
    # 2. Fix X * scale where X might be Double
    # Replace with (X).toFloat() * scale
    # Simple: find `* scale`, look backwards for the expression, wrap in (expr).toFloat()
    new_c = []
    for line in c.split('\n'):
        stripped = line.lstrip()
        if '//' in stripped or 'Offset(' not in stripped or '* scale' not in stripped:
            new_c.append(line)
            continue
        
        idx = 0
        while True:
            pos = line.find('* scale', idx)
            if pos == -1: break
            
            # Everything before * scale
            before = line[:pos].rstrip()
            has_tof = before.endswith('.toFloat()')
            if has_tof:
                new_c.append(line[:pos] + line[pos:])
                idx = pos
                continue
            
            # Walk backwards from * to find expression start
            epos = pos - 1
            paren = 0
            while epos >= 0:
                ch = before[epos] if epos < len(before) else ''
                if ch == ')': paren -= 1; break
                elif ch == '(': paren += 1; break
                else: epos -= 1
            
            expr = before[epos+1:].strip()
            if not expr:
                new_c.append(line[:pos] + line[pos:])
                idx = pos
                continue
            
            # Wrap in parens and add .toFloat()
            wrapped = '(' + expr + ').toFloat()' + ')'
            new_c.append(before + wrapped + line[pos:])
            idx = pos
            total += 1
        
        new_c = '\n'.join(new_c)
        
        if new_c != c:
            with open(fp, 'w') as fw:
                fw.write(new_c)
            print(f"  [scale] {f}: {total} fixes")
            oc = new_c

print(f"\nTotal fixes: {total}")
PYEOF