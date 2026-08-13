import os, subprocess, re, glob

BASE = "/home/z/my-project/civileg2/app/src/main/java/com.civileg2"

# Get all Professional drawing kt files
result = subprocess.run(
    ["python3", "-c",
     "import glob, os; files=sorted(glob('" + BASE + "/ui/compose/components/drawings/Professional*.kt')); print('\\n'.join(files))"],
    capture_output=True, text=True
).stdout.strip()
).strip()

files = [f.strip() for f in result.split('\n') if f.endswith('.kt') and os.path.exists(os.path.join(BASE, f))]

total = 0
for fpath in files:
    with open(fpath, 'r') as f:
        c = f.read()
        orig_len = len(c)
        
        # 1. Add kotlin.math.* if needed
        need_math = False
        for func in ['pow', 'sqrt', 'abs']:
            if re.search(rf'\b{func}\s*\(', c):
                if '//' not in c[:c.find(func)]: need_math = True; break
        if need_math:
            c = c.replace('\nimport\n', '\nimport kotlin.math.*\n', 1)
        
        # 2. Fix .color = Color(...) -> .toArgb() in paint assignments  
        # Pattern: paint.color = Color(0x...)  or  paint.color = color
        # But NOT Color.Red, Color.Green etc (which are properties, not function calls)
        c = re.sub(
            r'(\w+)\.color\s*=\s*(?!Color\([^)]+)\)(?!\.(?:toArgb|toInt))',
            r'\1.color = \2.toArgb()',
            c
        )
        
        # 3. Fix * scale: (expr).toFloat() * scale
        # We need to add .toFloat() before the multiplier  
        new_c = []
        for line in c.split('\n'):
            sl = line.lstrip()
            if '//' in sl or 'Offset(' not in sl or '* scale' not in sl:
                new_c.append(line)
                continue
            
            star_pos = sl.find('* scale')
            if star_pos == -1:
                new_c.append(line)
                continue
            
            after = sl[star_pos + 7:].lstrip()
            
            if after.startswith('.toFloat()'):
                new_c.append(line)
                continue
            
            p = star_pos - 1
            paren = 0
            while p >= 0:
                ch = sl[p]
                if ch == ')': paren -= 1; break
                elif ch == '(': paren += 1; break
                else: p -= 1
            
            expr = sl[p+1:star_pos].strip()
            
            if not expr:
                new_c.append(sl[:p+1] + '(' + expr + ').toFloat()' + sl[star_pos+7:])
                modified = True
                total += 1
        
        nc = '\n'.join(new_c)
        
        if modified:
            with open(fpath, 'w') as fw:
                fw.write(nc)
            print(f"  {os.path.basename(fpath)}: {nc - orig_len} chars, {total} fixes")

print(f"\nTotal fixes: {total}")