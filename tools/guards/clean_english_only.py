import re, pathlib

def clean_file(filepath):
    p = pathlib.Path(filepath)
    text = p.read_text(encoding='utf-8')

    # We want to replace t(context, "ar", "en") or t("ar", "en") with "en".
    # Let's use a regex that matches t(...) where there are two string literals inside.
    # Pattern: \bt\(\s*(?:context\s*,\s*)?"([^"]*)"\s*,\s*"([^"]*)"\s*\)
    # What if the second argument is a string template or expression? We handle both.

    # Let's match t( [context,] "ar", "en" )
    pattern = re.compile(r'\bt\(\s*(?:context\s*,\s*)?"[^"]*"\s*,\s*("[^"]*"|[a-zA-Z0-9_\$\.\s\+\-\*\/\(\)\{\}]+)\s*\)')
    text = pattern.sub(r'\1', text)

    # Also handle single-argument t("en") -> "en"
    pattern_single = re.compile(r'\bt\(\s*"([^"]*)"\s*\)')
    text = pattern_single.sub(r'"\1"', text)

    # Update t function definition in PdfGenerator.kt and PdfDrawingGenerator.kt
    text = text.replace('private fun t(context: Context, ar: String, en: String): String {', 'private fun t(context: Context, en: String): String {')
    text = text.replace('return if (isEnglish(context)) en else ar', 'return en')

    text = text.replace('fun t(ar: String, en: String): String =\n        if (LocaleHelper.isArabic()) ar else en', 'fun t(en: String): String = en')
    text = text.replace('fun t(ar: String, en: String): String =', 'fun t(en: String): String = en //')

    p.write_text(text, encoding='utf-8')
    print(f"Cleaned {filepath}")

clean_file("app/src/main/java/com/civileg/app/utils/PdfGenerator.kt")
clean_file("app/src/main/java/com/civileg/app/utils/PdfDrawingGenerator.kt")
