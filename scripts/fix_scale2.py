#!/usr/bin/env python3
"""Fix length * scale -> (length).toFloat() * scale in drawing files."""
import re, os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg2"

FILES = [
    "ui/compose/components/drawings/ProfessionalColumnDrawing.kt",
    "ui/compose/components/drawings/ProfessionalFootingDrawing.kt",
    "ui/compose/components/drawings/ProfessionalRetainingWallDrawing.kt",
    "ui/compose/components/drawings/ProfessionalSlabDrawing.kt",
    "ui/compose/components/drawings/ProfessionalStairDrawing.kt",
    "ui/compose/components/drawings/ProfessionalTankDrawing.kt",
]

def read(path):
    with open(path, 'r') as f:
        return f.read()

def write(path, content):
    with open(path, 'w') as f:
        f.write(content)

def find_matching_paren(s, start):
    """Find the closing paren that matches the opening paren at `start`."""
    depth = 1
    i = start + 1
    while i < len(s) and depth > 0:
        if s[i] == '(':
            depth += 1
        elif s[i] == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(s)

total = 0
for rel_path in FILES:
    fpath = os.path.join(BASE, rel_path)
    if not os.path.exists(fpath):
        continue
    
    content = read(fpath)
    original = content
    new_lines = []
    changes = 0
    
    for line in content.split('\n'):
        stripped = line.lstrip()
        if '// ' in stripped:
            new_lines.append(line)
            continue
        
        # Find all `* scale` occurrences
        idx = 0
        while True:
            pos = line.find('* scale', idx)
            if pos == -1:
                break
            
            # Check: not already wrapped in .toFloat()
            # Find the expression before * scale
            before = line[:pos].rstrip()
            if before.endswith('.toFloat()'):
                new_lines.append(line[:pos + 7])
                line = line[pos + 7:]
                idx = pos + 7
                continue
            
            # Check if there's a closing paren before this (expression is in parens)
            # Walk backwards to find the start of the expression
            expr_start = pos - 1
            paren_depth = 0
            in_str = False
            while expr_start >= 0:
                ch = before[expr_start] if expr_start < len(before) else ''
                if ch == ')':
                    paren_depth += 1
                elif ch == '(':
                    paren_depth -= 1
                    if paren_depth < 0:
                        in_str = True
                        expr_start += 1  # include the (
                        break
                elif ch == '=':
                    in_str = True
                    expr_start += 1  # include the =
                    break
                elif ch == ',' and paren_depth == 0:
                    # This is in a function call with multiple args, don't modify
                    break
                else:
                    expr_start -= 1
            
            if not in_str:
                # Safe to wrap
                expr = before[expr_start+1:].strip() if expr_start + 1 < len(before) else ''
                # Wrap in parens and add .toFloat()
                wrapped = f'({expr}).toFloat()'
                line = before[:expr_start+1] + wrapped + line[pos + 7:]
                changes += 1
            
            idx = pos + 7
        
        new_lines.append(line)
    
    new_content = '\n'.join(new_lines)
    
    if new_content != original:
        write(fpath, new_content)
        print(f"  {rel_path}: {changes} fixes")
        total += changes

print(f"\nTotal fixes: {total}")