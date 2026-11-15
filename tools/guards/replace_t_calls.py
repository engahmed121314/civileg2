import re, pathlib

def clean_file(filepath):
    p = pathlib.Path(filepath)
    text = p.read_text(encoding='utf-8')

    # Update function definition of t
    text = re.sub(r'fun\s+t\([^)]*\)\s*:\s*String\s*=\s*[^\n]+', 'fun t(en: String): String = en', text)
    text = re.sub(r'private\s+fun\s+t\([^)]*\)\s*:\s*String\s*\{[^}]*\}', 'private fun t(en: String): String = en', text, flags=re.DOTALL)
    text = re.sub(r'private\s+fun\s+t\([^)]*\)\s*=\s*[^\n]+', 'private fun t(en: String): String = en', text)

    # Replace t(context, "ar", "en") or t("ar", "en") with "en"
    # We can use a regex that finds t(...) and keeps the last string literal.
    def repl(m):
        content = m.group(0)
        # Find all double-quoted strings
        strings = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', content)
        if strings:
            # The English string is typically the last quoted string
            return f'"{strings[-1]}"'
        return content

    text = re.sub(r'\bt\([^)]+\)', repl, text)

    p.write_text(text, encoding='utf-8')
    print(f"Cleaned {filepath}")

clean_file("app/src/main/java/com/civileg/app/utils/PdfGenerator.kt")
clean_file("app/src/main/java/com/civileg/app/utils/PdfDrawingGenerator.kt")
