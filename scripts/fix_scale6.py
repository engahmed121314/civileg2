import re, os, sys

BASE = "/home/z/my-project/civileg2/app/src/main/java/com.civileg2"

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
    if not os.path.exists(fp):
        continue
    c = open(fp).read()
    oc = c
    fixes = 0

    # 1. Add kotlin.math.* if pow/sqrt used but not imported  
    if 'pow(' in c and 'import kotlin.math' not in c:
        c = c.replace('\nimport\n', '\nimport kotlin.math.*\n', 1)
        oc = c; fixes += 1

    lines = c.split('\n')
    new_lines = []
    
    i = 0
    n = len(lines)
    
    while i < n:
        line = lines[i]
        sl = line.lstrip()
        
        if '//' in sl or 'Offset(' not in sl or '* scale' not in sl:
            new_lines.append(line)
            i += 1
            continue
        
        star_pos = sl.find('* scale')
        if star_pos == -1:
            new_lines.append(line)
            i += 1
            continue
        
        # Everything from star_pos+7 onwards is the argument
        after_arg = sl[star_pos + 7:]
        
        # Check if argument already has .toFloat()
        before_arg_stripped = after_arg.lstrip()
        if after_arg_stripped.startswith('.toFloat()'):
            new_lines.append(line)
            i += 1
            continue
        
        # Get the expression before * scale (everything from start of expression to *)
        expr_start = star_pos - 1
        paren_count = 0
        p = expr_start
        while p >= 0:
            ch = sl[p]
            if ch == '(':
                paren_count += 1
            elif ch == ')':
                paren_count -= 1
                if paren_count == 0:
                    break
            else:
                p -= 1
        
        expr = sl[p+1:star_pos].strip()
        
        # Wrap in (expr).toFloat()
        wrapped = '(' + expr + ').toFloat()' + ')'
        new_lines.append(sl[:star_pos+7] + wrapped + after_arg)
        i += 1
    
    nc = '\n'.join(new_lines)
    
    if nc != oc:
        with open(fp, 'w') as fw:
            fw.write(nc)
        print(f"  {f}: {nc - len(oc)} chars, {fixes} fixes")
            total += fixes

print(f"\nTotal fixes: {total}")