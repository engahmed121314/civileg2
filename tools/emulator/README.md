# محاكي اختبار CivilEG2 (للـ Agents)

محاكي Android جاهز للاستخدام غير التفاعلي (headless) مع سكربتات تبني التطبيق،
تثبّته، تشغّله، تصوّر الصفحات، وتشغّل الاختبارات — كل شيء من التيرمينال.

## المواصفات

| العنصر | القيمة |
|--------|--------|
| AVD | `civileg2_api36` |
| النظام | Android 16 (API 36.1) — Google Play x86_64 |
| الجهاز | Pixel 6 (1080×2400، 420dpi) |
| الذاكرة | 3 GB RAM، 2 GB data (لتوفير مساحة القرص) |
| التسريع | AEHD (Android Emulator Hypervisor Driver) |
| العرض | بلا نافذة افتراضياً، مع `-Windowed` تظهر نافذة |

## إعداد لمرة واحدة

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\emulator\Setup-Emulator.ps1
```

يفحص/يثبّت `cmdline-tools` وصورة النظام، ينشئ الـ AVD، ويضبط إعداداته.
آمن للتشغيل المتكرر.

## الاستخدام السريع

```powershell
# 1) تشغيل المحاكي (headless)
powershell -File tools\emulator\Start-Emulator.ps1

# 2) بناء + تثبيت + تشغيل
powershell -File tools\emulator\Build-App.ps1 -AndroidTest
powershell -File tools\emulator\Install-App.ps1 -Launch

# 3) صور الصفحات (التبويبات الخمسة)
powershell -File tools\emulator\Capture-Screens.ps1

# 4) الاختبارات (Unit + Instrumented)
powershell -File tools\emulator\Run-Tests.ps1

# إيقاف
powershell -File tools\emulator\Stop-Emulator.ps1
```

أو كل ما سبق بأمر واحد:

```powershell
powershell -File tools\emulator\Test-App.ps1
```

## السكربتات

| السكربت | الوظيفة |
|---------|---------|
| `Setup-Emulator.ps1` | تثبيت الأدوات/الصورة وإنشاء الـ AVD وضبطه |
| `Start-Emulator.ps1` | إقلاع المحاكي وانتظار `sys.boot_completed` وتجهيزه |
| `Stop-Emulator.ps1` | إيقاف المحاكي (`-All` لإيقاف الكل) |
| `Build-App.ps1` | `assembleDebug/Release` وبناء APK الاختبارات |
| `Install-App.ps1` | تثبيت APK وتشغيل التطبيق |
| `Capture-Screens.ps1` | لقطات شاشة + UI dump + logcat |
| `Run-Tests.ps1` | Unit tests و Instrumented tests مع ملخص |
| `Test-App.ps1` | سير العمل الكامل + تقرير |

## المخرجات

كل شيء يُحفظ تحت `artifacts/emulator/` (مستثنى من Git):

```
artifacts/emulator/
├─ current-serial.txt        # آخر جهاز متصل
├─ last-apk.txt              # آخر APK مبني
├─ screens/<timestamp>/      # 00-launch.png, 01-home.png, ...
├─ tests/<timestamp>/        # results.json + HTML reports
└─ runs/<timestamp>/         # report.md + screenshots + logcat + results.json
```

## أمثلة مفيدة

```powershell
# محاكي بنافذة مرئية
powershell -File tools\emulator\Start-Emulator.ps1 -Windowed

# إقلاع نظيف (بدون snapshot) أو مصنع جديد
powershell -File tools\emulator\Start-Emulator.ps1 -ColdBoot
powershell -File tools\emulator\Start-Emulator.ps1 -WipeData

# تصوير صفحة محددة بالضغط على نصّها
powershell -File tools\emulator\Capture-Screens.ps1 -TapText "الكمرات"

# تشغيل كل الاختبارات ما عدا الوحدة
powershell -File tools\emulator\Run-Tests.ps1 -SkipUnit

# اختبار واحد فقط
powershell -File tools\emulator\Run-Tests.ps1 -Filter "com.civileg.app.AppSmokeTest"

# وحدة معيّنة فقط
powershell -File tools\emulator\Run-Tests.ps1 -UnitFilter "*ColumnDesignEngine*"

# سير كامل مع إبقاء المحاكي يعمل بعد الانتهاء
powershell -File tools\emulator\Test-App.ps1 -KeepEmulator
```

## ملاحظات للوكلاء

- **لا تشغّل أكثر من محاكي واحد في وقت واحد** على هذا الجهاز (4 GB RAM للـ AVD).
- أول إقلاع بعد الإنشاء/الوايب قد يستغرق **3–6 دقائق**؛ السكربت ينتظر تلقائياً.
- استخدم `Test-App.ps1` كبوابة قبل تسليم أي تعديل واجهات؛ فهو يبني ويشغّل ويختبر.
- لقطات الشاشة تُلتقط من الـ emulator حتى في وضع `-no-window` (عبر adb).
- بعد أي تعديل على الـ AVD أو SDK أعد تشغيل `Setup-Emulator.ps1`.
- البناء يستخدم Gradle daemon مشتركاً؛ لا تطلق بناءين متوازيين من جلسات مختلفة.

## استكشاف الأعطال

| المشكلة | الحل |
|---------|------|
| `AVD not found` | شغّل `Setup-Emulator.ps1` |
| فشل الإقلاع/بطء شديد | `Start-Emulator.ps1 -ColdBoot -TimeoutSeconds 900` |
| تعارض المنفذ 5554 | `Start-Emulator.ps1 -Port 5556` |
| شاشة سوداء في اللقطات | انتظر انتهاء الإقلاع؛ ثم `Capture-Screens.ps1 -NoLaunch` |
| فشل التثبيت `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall com.civileg.app` ثم أعد التثبيت |
| اختبارات Instrumented لا تجد جهازاً | شغّل `Start-Emulator.ps1` وتأكد من `adb devices` |
| نفاد مساحة القرص | `Stop-Emulator.ps1`، ثم احذف `artifacts/emulator/screens` القديمة |
