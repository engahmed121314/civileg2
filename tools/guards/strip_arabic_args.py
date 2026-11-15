import pathlib

def strip_t_calls(filepath):
    p = pathlib.Path(filepath)
    text = p.read_text(encoding='utf-8')

    res = []
    i = 0
    n = len(text)
    while i < n:
        if text[i:i+2] == 't(':
            start = i
            i += 2
            depth = 1
            comma_idx = -1
            in_quote = False
            quote_char = ''
            while i < n and depth > 0:
                c = text[i]
                if not in_quote:
                    if c in ('"', "'"):
                        in_quote = True
                        quote_char = c
                    elif c == '(':
                        depth += 1
                    elif c == ')':
                        depth -= 1
                    elif c == ',' and depth == 1 and comma_idx == -1:
                        comma_idx = i
                else:
                    if c == '\\' and i + 1 < n:
                        i += 2
                        continue
                    elif c == quote_char:
                        in_quote = False
                i += 1

            if depth == 0 and comma_idx != -1:
                arg2 = text[comma_idx+1 : i-1].strip()
                res.append(arg2)
            else:
                res.append(text[start:i])
        else:
            res.append(text[i])
            i += 1

    p.write_text("".join(res), encoding='utf-8')
    print(f"Stripped t() calls in {filepath}")

strip_t_calls("app/src/main/java/com/civileg/app/utils/PdfGenerator.kt")
strip_t_calls("app/src/main/java/com/civileg/app/utils/PdfDrawingGenerator.kt")
