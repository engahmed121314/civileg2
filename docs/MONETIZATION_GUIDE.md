# 💰 Monetization & Revenue Guide — Civil Engineer Pro
# دليل تحقيق الأرباح وتسييل المشروع — المهندس المدني برو

[English](#english) | [العربية](#العربية)

---

<div id="english">

## English

This guide outlines all monetization channels integrated into **Civil Engineer Pro** and how you can maximize your revenue from the project.

---

### 1. 📢 Google AdMob (In-App Advertising)

The application has AdMob fully integrated with GDPR/EEA User Messaging Platform (UMP) compliance.

#### Supported Ad Formats:
- **Banner Ads**: Displayed non-intrusively at the bottom of calculation screens.
- **Interstitial Ads**: Shown between completed structural calculation sessions.
- **Native Ads**: Embedded seamlessly within project lists and BOQ reports.
- **Rewarded Ads**: Unlock premium calculations (e.g., advanced PDF report generation, water tank design) in exchange for watching a short video.

#### How to Activate Production Ads:
1. Register at [Google AdMob](https://apps.admob.com).
2. Add your application: **Civil Engineer Pro** (Android).
3. Create 5 Ad Units:
   - App ID: `ca-app-pub-XXXXXXXXXXXXXXXX~NNNNNNNNNN`
   - Banner: `ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN`
   - Interstitial: `ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN`
   - Native: `ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN`
   - Rewarded: `ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN`
4. Add them to your `local.properties` file:
   ```properties
   admob.app.id=ca-app-pub-XXXXXXXXXXXXXXXX~NNNNNNNNNN
   admob.banner.id=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   admob.interstitial.id=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   admob.native.id=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   admob.rewarded.id=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   ```
5. For GitHub Actions CI/CD releases, add the exact same 5 keys under your GitHub repository:
   **Settings → Secrets and variables → Actions → Variables**.

---

### 2. 💳 Google Play Billing (In-App Subscriptions & Pro Unlock)

The app includes `BillingManager.kt` using Google Play Billing Library v7.

#### Subscription Products:
| Product ID | Type | Recommended Pricing | Features Unlocked |
|------------|------|--------------------|-------------------|
| `civileg_premium_monthly` | Auto-renewing subscription | $4.99 / month | Ad-free experience, Unlimited PDF exports, Water Tank & Steel Warehouse design |
| `civileg_premium_yearly` | Auto-renewing subscription | $39.99 / year (33% discount) | All Pro features, Priority engineering support, Early access to new design codes |

#### Setting up in Google Play Console:
1. Open [Google Play Console](https://play.google.com/console).
2. Go to **Monetize → Subscriptions**.
3. Create the two subscription IDs: `civileg_premium_monthly` and `civileg_premium_yearly`.
4. Configure base plans, regional pricing (e.g., Egypt EGP, Saudi Arabia SAR, USA USD), and optional 7-day free trial.
5. Activate the products.

---

### 3. 💖 GitHub Sponsors & Open Donations

Civil Engineer Pro has `.github/FUNDING.yml` configured:
- **GitHub Sponsors**: Direct sponsorship from structural engineers, students, and firms who use the open codebase.
- You can add additional platforms (Buy Me a Coffee, Patreon, PayPal) in `.github/FUNDING.yml`.

---

### 4. 🏢 Commercial & Enterprise Licensing

You can offer bespoke services to engineering consultancies and construction contractors:
- **White-Labeling**: Customize the app with a company's branding, custom title blocks on PDF reports, and internal material pricing databases.
- **Custom Design Codes**: Implement localized country codes or corporate design guidelines as private add-ons.
- **API & Backend Integration**: Connect the app to an enterprise ERP or structural drafting pipeline.

</div>

---

<div id="العربية" dir="rtl">

## العربية

دليل متكامل يوضح مصادر الدخل وتسييل تطبيق **المهندس المدني برو** وطرق تفعيل كل مسار لتحقيق أقصى عائد مالي.

---

### 1. 📢 إعلانات Google AdMob

التطبيق مجهز ومربوط بالكامل مع شبكة AdMob مع دعم لوائح الخصوصية الأوروبية (UMP / GDPR).

#### وحدات الإعلانات المدعومة:
- **إعلانات البانر (Banner)**: تظهر أسفل شاشات التصميم دون إزعاج المستخدم.
- **الإعلانات البينية (Interstitial)**: تظهر بين عمليات الحساب المنتهية.
- **الإعلانات المدمجة (Native)**: تظهر داخل قوائم البنود والكميات.
- **إعلانات المكافأة (Rewarded Ads)**: تتيح للمستخدم فك قفل تصدير تقارير PDF متقدمة أو فتح تصميمات معينة مقابل مشاهدة إعلان قصير.

#### خطوات تفعيل الإعلانات الرسمية:
1. أنشئ حساباً على [Google AdMob](https://apps.admob.com).
2. أضف التطبيق: **المهندس المدني برو** (Android).
3. أنشئ وحدات الإعلانات الخمسة واحصل على معرّفاتها.
4. أضفها إلى ملف `local.properties`:
   ```properties
   admob.app.id=ca-app-pub-XXXXXXXXXXXXXXXX~NNNNNNNNNN
   admob.banner.id=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   admob.interstitial.id=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   admob.native.id=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   admob.rewarded.id=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   ```
5. لإصدارات GitHub Actions، أضف المتغيرات نفسها في:
   **Settings → Secrets and variables → Actions → Variables**.

---

### 2. 💳 الاشتراكات المدفوعة (Google Play In-App Billing)

تم بناء التطبيق باستخدام أحدث مكتبات فوترة جوجل بلاي (`BillingManager.kt`).

#### خطط الاشتراك المقترحة:
| معرّف المنتج | النوع | السعر المقترح | الميزات المفعلة |
|--------------|-------|---------------|-----------------|
| `civileg_premium_monthly` | اشتراك شهري | 4.99$ شهرياً (أو ما يعادله محلياً) | إزالة الإعلانات بالكامل، تقارير PDF غير محدودة، تصميم الخزانات والمستودعات |
| `civileg_premium_yearly` | اشتراك سنوي | 39.99$ سنوياً (توفير 33%) | جميع ميزات Pro + دعم فني هندسي أولوية + وصول مبكر للأكواد الجديدة |

#### طريقة الإعداد في Google Play Console:
1. توجه إلى [Google Play Console](https://play.google.com/console).
2. اختر **Monetize → Subscriptions**.
3. أضف المعرفين: `civileg_premium_monthly` و `civileg_premium_yearly`.
4. حدد الأسعار المحلية لكل دولة (مصر، السعودية، الخليج، العالم).
5. فعّل المنتجات وستعمل مباشرة داخل التطبيق.

---

### 3. 💖 دعم المطور والتبرعات (GitHub Sponsors)

- ملف التمويل مُعد في `.github/FUNDING.yml`.
- يتيح للمهندسين والمكاتب الهندسية دعم المشروع مفتوح المصدر بمبالغ شهرية أو تبرعات لمرة واحدة.

---

### 4. 🏢 التخصيص للشركات والمكاتب الهندسية (Enterprise Services)

يمكنك الاستفادة من الكود تجارياً عبر:
- **إصدارات خاصة (White-Label)**: تخصيص التطبيق بشعار وهوية مكتب استشاري معين وإضافة جداول أسعاره ونماذج ترويسة الـ PDF الخاصة به.
- **إضافة أكواد خاصة**: تطوير وحدات حسابية خاصة بشركات مقاولات كبرى.
- **التدريب والدعم الفني**: تقديم دورات وورش عمل مدفوعة في التصميم الإنشائي باستخدام التطبيق.

</div>
