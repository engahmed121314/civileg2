#!/usr/bin/env python3
"""
Fix Offset/Size internal constructor errors in Kotlin Compose files.
The problem: Offset(Double, Double) resolves to internal Offset(Long) constructor.
Fix: Ensure all arguments to Offset() and Size() are Float.
"""

import re
import sys

def fix_offset_size_calls(content):
    """Fix Offset(x, y) and Size(w, h) calls where args might be Double."""
    lines = content.split('\n')
    fixed_lines = []
    
    for line in lines:
        original = line
        
        # Fix Offset(x, y) - ensure both args are Float
        line = fix_constructor_args(line, 'Offset')
        # Fix Size(w, h) - ensure both args are Float  
        line = fix_constructor_args(line, 'Size')
        # Fix CornerRadius(x) or CornerRadius(x, y) - ensure Float
        line = fix_constructor_args(line, 'CornerRadius')
        
        fixed_lines.append(line)
    
    return '\n'.join(fixed_lines)

def fix_constructor_args(line, name):
    """Find calls like Name(expr, expr) and ensure args are Float."""
    # Pattern: find Name( ... ) but not Name::class or Name.
    # We need to handle nested parentheses carefully
    
    # Find all occurrences of the constructor call
    result = line
    idx = 0
    while True:
        # Find next occurrence of the constructor name followed by (
        search_start = idx
        pos = result.find(f'{name}(', search_start)
        if pos == -1:
            break
        
        # Check it's actually a constructor call (not part of another word)
        if pos > 0 and (result[pos-1].isalnum() or result[pos-1] == '_'):
            idx = pos + 1
            continue
        
        # Find the matching closing paren
        paren_start = pos + len(name)  # position of '('
        depth = 0
        i = paren_start
        while i < len(result):
            if result[i] == '(':
                depth += 1
            elif result[i] == ')':
                depth -= 1
                if depth == 0:
                    break
            i += 1
        
        if depth != 0:
            idx = pos + 1
            continue
        
        paren_end = i  # position of ')'
        inner = result[paren_start+1:paren_end]
        
        # Parse arguments (split by comma, respecting nested parens)
        args = split_args(inner)
        
        # Check if any argument needs .toFloat()
        needs_fix = False
        for arg in args:
            stripped = arg.strip()
            if not stripped:
                continue
            # Skip if already has .toFloat()
            if '.toFloat()' in stripped:
                continue
            # Skip if it's a simple float literal (ends with f or F, or is a plain number used as float)
            if re.match(r'^-?\d+\.?\d*[fF]$', stripped):
                continue
            # Skip if it's just a Float variable (we can't know for sure, but if it has
            # any arithmetic with Double variables, the result is Double)
            # We need to add .toFloat() if the expression involves Double arithmetic
            # Heuristic: if the expression contains variables that are likely Double
            # (like arithmetic operations), add .toFloat()
            if contains_double_expr(stripped):
                needs_fix = True
                break
        
        if needs_fix:
            new_args = []
            for arg in args:
                stripped = arg.strip()
                if not stripped:
                    new_args.append(arg)
                    continue
                if '.toFloat()' in stripped:
                    new_args.append(arg)
                elif re.match(r'^-?\d+\.?\d*[fF]$', stripped):
                    new_args.append(arg)
                elif contains_double_expr(stripped):
                    # Add .toFloat() - wrap the whole expression
                    new_args.append(f'{arg}.toFloat()')
                else:
                    new_args.append(arg)
            
            new_inner = ', '.join(new_args)
            result = result[:paren_start+1] + new_inner + result[paren_end:]
            idx = paren_start + 1 + len(new_inner) + 1
        else:
            idx = paren_end + 1
    
    return result

def split_args(s):
    """Split arguments by comma, respecting nested parentheses."""
    args = []
    depth = 0
    current = []
    for ch in s:
        if ch == '(' or ch == '[':
            depth += 1
            current.append(ch)
        elif ch == ')' or ch == ']':
            depth -= 1
            current.append(ch)
        elif ch == ',' and depth == 0:
            args.append(''.join(current))
            current = []
        else:
            current.append(ch)
    args.append(''.join(current))
    return args

def contains_double_expr(expr):
    """Check if an expression likely produces a Double result."""
    expr = expr.strip()
    # If it contains arithmetic operators with non-float operands, it's likely Double
    # Check for * or / operations
    if ('*' in expr or '/' in expr) and not all_float_arithmetic(expr):
        return True
    # Check for variables that are known to be Double (heuristic)
    # Expressions with .toDouble() are definitely Double
    if '.toDouble()' in expr:
        return True
    # Variables named like Double (common patterns in the codebase)
    # If expression has arithmetic and no .toFloat(), it's probably Double
    has_arithmetic = any(op in expr for op in ['*', '/', '+', '-']) 
    has_float_literal = bool(re.search(r'\d+\.\d*[fF]', expr))
    if has_arithmetic and not has_float_literal and '.toFloat()' not in expr:
        return True
    return False

def all_float_arithmetic(expr):
    """Check if all numeric values in the expression are Float."""
    # Remove variable names, keep numbers
    numbers = re.findall(r'\d+\.?\d*', expr)
    for n in numbers:
        if not n.endswith('f') and not n.endswith('F') and '.' in n:
            return False  # Has a Double literal like 300.0
    return True

def main():
    if len(sys.argv) < 2:
        print("Usage: python fix_offset_float.py <file1.kt> [file2.kt ...]")
        sys.exit(1)
    
    for filepath in sys.argv[1:]:
        print(f"Processing: {filepath}")
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        fixed = fix_offset_size_calls(content)
        
        if fixed != content:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(fixed)
            print(f"  Fixed: {filepath}")
        else:
            print(f"  No changes: {filepath}")

if __name__ == '__main__':
    main()