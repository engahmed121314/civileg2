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
    
    for line in lines:
        sl = line.lstrip()
        if '//' in sl or 'Offset(' not in sl:
            new_lines.append(line)
            continue
        
        # Find * scale
        if '* scale' not in sl:
            new_lines.append(line)
            continue
        
        # We found `* scale` - need to add .toFloat() to expression before *
        # Find the position of *
        star_pos = sl.find('* scale')
        if star_pos == -1:
            new_lines.append(line)
            continue
        
        # Check if there's already .toFloat() wrapping (right before *)
        right_after_star = sl[star_pos + 7:].lstrip()
        if right_after_star.startswith('.toFloat()'):
            new_lines.append(line)
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
        
        # Wrap expression in (expr).toFloat()
        wrapped = '(' + expr + ').toFloat()' + ')'
        new_lines.append(sl[:star_pos+7] + wrapped + sl[star_pos+7:])
        
    nc = '\n'.join(new_lines)
    
    if nc != oc:
        with open(fp, 'w') as fw:
            fw.write(nc)
        print(f"  [math+scale] {f}: {nc - len(oc)} chars changed")
            total += 1

print(f"\nTotal fixes: {total}")