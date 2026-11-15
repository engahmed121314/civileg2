import re, pathlib

p = pathlib.Path("app/src/main/java/com/civileg/app/utils/PdfDrawingGenerator.kt")
text = p.read_text(encoding='utf-8')

# 1. Update t definition
text = text.replace('fun t(ar: String, en: String): String =\n        if (LocaleHelper.isArabic()) ar else en', 'fun t(en: String): String = en')
text = text.replace('fun t(ar: String, en: String): String =', 'fun t(en: String): String = en //')

# 2. Replace bilingual titles
text = text.replace('"FRONT ELEVATION | مسقط رأسي"', '"FRONT ELEVATION"')
text = text.replace('"PLAN VIEW | مسقط أفقي"', '"PLAN VIEW"')
text = text.replace('"3D WIREFRAME | منظور هيكلي"', '"3D WIREFRAME"')
text = text.replace('"BASE PLATE PLAN | مسقط أفقي لقاعدة العمود"', '"BASE PLATE PLAN"')

# 3. Replace t("ar", "en") with "en"
def repl(m):
    content = m.group(0)
    strings = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', content)
    if strings:
        return f'"{strings[-1]}"'
    return content

text = re.sub(r'\bt\([^)]+\)', repl, text)

p.write_text(text, encoding='utf-8')
print("Cleaned PdfDrawingGenerator.kt")
