# بوت مساعد تطوير CivilEG2

بوت تليجرام (`@Civieg22_bot`) يتيح لك **المحادثة والتطوير من هاتفك**: ترسل رسالة للبوت، فينفذها opencode داخل مشروع `civileg2`، وترجع لك النتيجة — نفس جلسة التطوير هنا، لكن عبر تليجرام.

```
أنت (تليجرام)  →  @Civieg22_bot  →  جهازك  →  opencode على civileg2
                                                        ↓ (يعدّل الكود)
النتيجة ترجع لك على تليجرام  ←  ←  ←  ←  ←  ←  ←  ←
```

- يعمل على **جهازك** — لا سيرفر خارجي، لا منافذ مفتوحة.
- يرد **عليك فقط** (Chat ID مسموح واحد) ويتجاهل أي شخص آخر.

## الطريقة الأساسية (موصى بها): opencode-telegram

أداة ناضجة مثبتة لديك (`@grinev/opencode-telegram-bot`): جلسات، مشاريع،
تبديل موديلات، بث مباشر للتقدم، واجهة **عربية**، مرفقات diff.

### التشغيل (تلقائي)

البوت يعمل تلقائياً عند تسجيل الدخول عبر مهمة مجدولة اسمها
`CivilEG2-Assistant-Bot`، ويشرف عليها `keep-alive.ps1`:
يفحص كل دقيقة ويشغّل `opencode serve` (المنفذ **4097**) والبوت إن سقطا.

**تشغيل يدوي:** دبل كليك على `assistant-bot/start-assistant.bat`
(يشغّل المهمة المجدولة، أو المشرف مباشرة إن لم تكن موجودة).

**يدوياً من التيرمينال:**
```bat
:: خادم opencode المحلي (v1.18+ من npm)
"%APPDATA%\npm\node_modules\opencode-ai\bin\opencode.exe" serve --port 4097 --hostname 127.0.0.1

:: البوت
node "%APPDATA%\npm\node_modules\@grinev\opencode-telegram-bot\dist\cli.js"
```

الإعداد جاهز في `%APPDATA%\opencode-telegram-bot\.env`:
التوكن + الـ Chat ID + اللغة العربية + الموديل.

### بعد تحديث الحزمة (npm update)

شغّل مرة واحدة:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File assistant-bot\patch-bot.ps1
```
يعيد تطبيق إصلاحين مهمّين:
1. **عدم إسقاط الرسائل** التي وصلت أثناء توقف البوت (`drop_pending_updates=false`).
2. قبول الرسائل المتأخرة حتى **12 ساعة** بدل 60 ثانية (كانت تُهمَل بصمت بعد إعادة التشغيل).


### أوامر مفيدة داخل تليجرام

| الأمر | الوظيفة |
|------|---------|
| `/start` | بدء / مساعدة |
| `/status` | حالة السيرفر والمشروع والجلسة والموديل |
| `/projects` | اختيار مشروع (اختر `civileg2`) |
| `/sessions` | الجلسات (يمكنك متابعة نفس الجلسة من الهاتف والحاسوب) |
| `/opencode_start` و `/opencode_stop` | تشغيل/إيقاف السيرفر من الهاتف |

أي رسالة عادية = مهمة برمجية تُنفَّذ على المشروع المختار.

## الطريقة البديلة (خفيفة): bridge.py

سكريبت Python بدون أي مكتبات، للحالات السريعة:
```
python assistant-bot/bridge.py
```
- `/start` مساعدة، `/status` حالة git، `/diff` ملخص التغييرات، `/log` آخر commits.
- أي نص آخر يُنفَّذ عبر `opencode run` داخل `civileg2`.
- الإعداد في `assistant-bot/config.env` (جاهز، ولا يُرفع لـ Git).

## بناء APK وإرسال لينك التثبيت لتليجرام

سكريبت واحد يبني الـ APK، يرفعه لاستضافة خارجية (catbox.moe)، ويرسل الرابط + الملف نفسه لبوت تليجرام:

```bat
:: دبل كليك
assistant-bot\publish-apk.bat

:: أو من التيرمينال
pwsh -File assistant-bot/publish-apk.ps1
pwsh -File assistant-bot/publish-apk.ps1 -SkipBuild    :: بدون إعادة بناء
pwsh -File assistant-bot/publish-apk.ps1 -NoDocument   :: رابط فقط بدون ملف
```

- يقرأ `BOT_TOKEN` و`ALLOWED_CHAT_ID` من `config.env` (لا يُرفع لـ Git).
- الرابط المباشر من `files.catbox.moe` صالح للفتح من أي جهاز.
- إن كان الـ APK أكبر من 50MB يُرسل الرابط فقط (حد تليجرام للبوت).

## ملاحظات أمان

- ملفات `.env` و `config.env` فيها التوكن السري — **لا ترفعها لـ Git**.
- البوت يرد على Chat ID واحد فقط.
- أي رسالة ترسلها قد **تعدّل ملفات المشروع** — راجع بـ `git diff` قبل الـ commit.
