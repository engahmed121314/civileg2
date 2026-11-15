#!/usr/bin/env python3
import re
import pathlib

def clean_t_calls(text):
    # Matches t(context, "ar", "EN") or t("ar", "EN")
    # We want to extract the second string literal (EN) and replace the whole t(...) call with just "EN" (or unquoted if it's an expression).

    # Pattern for t(..., "ar", "EN") where "ar" contains Arabic or anything, and "EN" is the second string literal.
    # Actually, let's match t( [optional arg,] "...", "..." )
    pattern = re.compile(r'\bt\(\s*(?:context\s*,\s*)?"([^"]*)"\s*,\s*"([^"]*)"\s*\)')

    def repl(m):
        en_str = m.group(2)
        return f'"{en_str}"'

    # Also handle multiline or single quotes or string concatenation inside t(...)
    # Let's do a more general approach or targeted regex replacements for PdfGenerator.kt and PdfDrawingGenerator.kt
    return text

# Let's write specific replacements for PdfGenerator.kt and PdfDrawingGenerator.kt
print("Helper script")
