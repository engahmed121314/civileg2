import re, os, glob

BASE = "/home/z/my-project/civileg2/app/src/main/java/com.civileg2"

drawing_files = sorted(glob.glob(BASE + "/ui/compose/components/drawings/Professional*.kt"))

total = 0
for fpath in drawing_files:
    with open(fpath, 'r') as f:
        c = f.read()
        original = c
        modified = False
        
        lines = c.split('\n')
        new_lines = []
        
        for line in lines:
            sl = line.lstrip()
            if '//' in sl or 'Offset(' not in sl or '* scale' not in sl:
                new_lines.append(line)
                continue
            
            star_pos = sl.find('* scale')
            if star_pos == -1:
                new_lines.append(line)
                continue
            
            after = sl[star_pos + 7:].lstrip()
            
            # Check if .toFloat() is already after the expression
            if after.startswith('.toFloat()'):
                new_lines.append(line)
                continue
            
            # Walk backwards to find expression start
            p = star_pos - 1
            paren = 0
            while p >= 0:
                ch = sl[p]
                if ch == ')': paren -= 1; break
                elif ch == '(': paren += 1; break
                else: p -= 1
            
            expr = sl[p+1:star_pos].strip()
            
            if not expr:
                new_lines.append(sl[:p+1] + '(' + expr + ').toFloat()' + sl[star_pos+7:])
                modified = True
                total += 1
        
        nc = '\n'.join(new_lines)
        
        if modified:
            with open(fpath, 'w') as fw:
                fw.write(nc)

print(f"Total files modified: {total}, Total fixes: {total}")
SCRIPT