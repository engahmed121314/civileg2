import re, pathlib

def process_file(filepath):
    p = pathlib.Path(filepath)
    text = p.read_text(encoding='utf-8')

    # Replace t("ar", "en") or t(context, "ar", "en") with "en"
    # We can match t( ... , "en" ) where the last argument is a string literal.
    # Or specifically replace t("...", "...") with the second string literal.

    # Let's match t( "...", "..." )
    def replace_t(match):
        full_match = match.group(0)
        # Find all quoted strings inside full_match
        quoted = re.findall(r'"([^"]*)"', full_match)
        if len(quoted) >= 2:
            return f'"{quoted[1]}"'
        elif len(quoted) == 1:
            return f'"{quoted[0]}"'
        return full_match

    # Regex for t(...)
    text = re.sub(r'\bt\([^)]+\)', replace_t, text)

    p.write_text(text, encoding='utf-8')
    print(f"Processed {filepath}")

process_file("app/src/main/java/com/civileg/app/utils/PdfGenerator.kt")
process_file("app/src/main/java/com/civileg/app/utils/PdfDrawingGenerator.kt")
