# 🐛 دليل إصلاح الأخطاء والمشاكل الشائعة

## 📋 الأخطاء المكتشفة والحلول

---

## 1️⃣ مشكلة: التطبيق يتعطل عند فتح أيقونة معينة

### الأسباب المحتملة:
- [ ] NullPointerException في البيانات
- [ ] Memory leak في الـ Context
- [ ] Listener قديم لم يتم إزالته
- [ ] Fragment transition issue
- [ ] View reference after destroy

### الحلول:

#### أ. استخدام Safe Calls
```kotlin
// ❌ خطأ
val view = findViewById<View>(R.id.my_view)
view.setOnClickListener { }  // قد يكون null

// ✅ صحيح
val view: View? = findViewById(R.id.my_view)
view?.setOnClickListener { }
```

#### ب. إزالة Listeners في onDestroy
```kotlin
override fun onDestroy() {
    super.onDestroy()
    // إزالة جميع الـ listeners
    binding.button.setOnClickListener(null)
    binding.recyclerView.adapter = null
}
```

#### ج. استخدام WeakReference للـ Context
```kotlin
private var contextRef: WeakReference<Context>? = null

fun setContext(context: Context) {
    contextRef = WeakReference(context)
}

fun getContext(): Context? = contextRef?.get()
```

---

## 2️⃣ مشكلة: تكرار الكود في الـ Fragments

### الأسباب:
- [ ] عدم استخدام BaseFragment
- [ ] تكرار نفس الكود في عدة fragments
- [ ] عدم استخدام Extension functions

### الحلول:

#### أ. إنشاء BaseFragment
```kotlin
abstract class BaseDesignFragment : Fragment() {
    
    protected open fun setupCommonUI() {
        // الكود المشترك
    }
    
    protected open fun setupCommonListeners() {
        // الـ Listeners المشتركة
    }
}
```

#### ب. استخدام Extension Functions
```kotlin
// Extension Function مشتركة
fun Fragment.showError(message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

// الاستخدام في أي Fragment
showError("خطأ ما")
```

---

## 3️⃣ مشكلة: Memory Leak في الرسومات

### الأسباب:
- [ ] Bitmap غير محررة
- [ ] Paint objects غير محررة
- [ ] Canvas reference محفوظة

### الحلول:

#### أ. تحرير الـ Bitmaps
```kotlin
class DrawingView : View {
    private var bitmap: Bitmap? = null
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // استخدام bitmap
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bitmap?.recycle()  // تحرير الذاكرة
        bitmap = null
    }
}
```

#### ب. استخدام Object Pooling
```kotlin
object PaintPool {
    private val paints = mutableListOf<Paint>()
    
    fun getPaint(): Paint {
        return if (paints.isNotEmpty()) {
            paints.removeAt(0)
        } else {
            Paint(Paint.ANTI_ALIAS_FLAG)
        }
    }
    
    fun recyclePaint(paint: Paint) {
        paint.reset()
        paints.add(paint)
    }
}
```

---

## 4️⃣ مشكلة: ViewModel يحتفظ بـ References قديمة

### الأسباب:
- [ ] عدم استخدام Lifecycle-aware observers
- [ ] عدم إزالة observers
- [ ] استخدام GlobalScope في coroutines

### الحلول:

#### أ. استخدام viewLifecycleOwner
```kotlin
// ❌ خطأ - قد يسبب memory leak
viewModel.data.observe(this) { data ->
    // update UI
}

// ✅ صحيح - يتم إزالة observer تلقائياً
viewModel.data.observe(viewLifecycleOwner) { data ->
    // update UI
}
```

#### ب. استخدام viewModelScope بدلاً من GlobalScope
```kotlin
// ❌ خطأ
GlobalScope.launch {
    val data = fetchData()
}

// ✅ صحيح
viewModel.viewModelScope.launch {
    val data = fetchData()
}
```

---

## 5️⃣ مشكلة: Fragment Transition يسبب Crash

### الأسباب:
- [ ] Fragment state loss
- [ ] View binding بعد destroy
- [ ] Back press أثناء transaction

### الحلول:

#### أ. استخدام commitNow بدلاً من commit
```kotlin
// ✅ الطريقة الصحيحة
supportFragmentManager.beginTransaction()
    .replace(R.id.container, fragment)
    .commitNow()  // executes immediately
```

#### ب. التحقق من View قبل الاستخدام
```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    val myView: View = view.findViewById(R.id.my_view) ?: return
    // الآن آمن استخدام myView
}
```

#### ج. استخدام FragmentResultListener
```kotlin
// Sender Fragment
supportFragmentManager.setFragmentResult("key", bundleOf("data" to "value"))

// Receiver Fragment
setFragmentResultListener("key") { _, bundle ->
    val data = bundle.getString("data")
}
```

---

## 6️⃣ مشكلة: Drawable التسليح يظهر بشكل خاطئ

### الأسباب:
- [ ] حساب الإحداثيات خاطئ
- [ ] تحويل الوحدات غير صحيح
- [ ] مقياس الشاشة لم يتم احتسابه

### الحلول:

#### أ. استخدام ResponsiveDesignManager
```kotlin
// في onDraw
val scale = ResponsiveDesignManager.getDrawingScale(
    context, 
    baseWidth = 400f, 
    baseHeight = 600f
)

val drawW = beamWidth * scale
val drawH = beamHeight * scale
```

#### ب. حساب الإحداثيات بشكل صحيح
```kotlin
// ✓ صحيح
val x = (width - drawW) / 2  // توسيط أفقي
val y = (height - drawH) / 2  // توسيط عمودي

// ثم رسم العناصر نسبة إلى (x, y)
canvas.drawRect(x, y, x + drawW, y + drawH, paint)
```

---

## 7️⃣ مشكلة: قيم الحسابات غير صحيحة

### الأسباب:
- [ ] تحويل الوحدات خاطئ
- [ ] ترتيب العمليات الحسابية خاطئ
- [ ] round-off errors

### الحلول:

#### أ. التحقق من الوحدات
```kotlin
// ❌ خطأ - خلط الوحدات
val area = width * height  // assuming mm

// ✅ صحيح - تحويل صريح
val widthM = width / 1000.0  // convert mm to m
val heightM = height / 1000.0
val areaMetre = widthM * heightM
```

#### ب. استخدام Double بدلاً من Float
```kotlin
// ❌ قد يفقد دقة
val result: Float = 1.4f * 25.5f * 3.333f

// ✅ دقة أعلى
val result: Double = 1.4 * 25.5 * 3.333

// تحويل النتيجة فقط عند الحاجة
val resultFloat = result.toFloat()
```

#### ج. استخدام Math.round للتقريب
```kotlin
// دقة معقولة
val rounded = String.format("%.2f", value).toDouble()

// أو
val rounded = (value * 100).toLong() / 100.0
```

---

## 8️⃣ مشكلة: تكرار الرسم (Overdraw)

### الأسباب:
- [ ] layers مكررة في الـ layout
- [ ] رسم الخلفية عدة مرات
- [ ] transparency في الألوان

### الحلول:

#### أ. استخدام android:hardwareAccelerated
```xml
<!-- في AndroidManifest.xml -->
<activity
    android:name=".MainActivity"
    android:hardwareAccelerated="true" />
```

#### ب. تقليل الـ layers
```kotlin
// ❌ طبقات كثيرة
LinearLayout {
    CardView { ImageView { } }
}

// ✅ طبقات أقل
CardView {
    ImageView { }
}
```

---

## 9️⃣ مشكلة: Fragments تحتفظ بـ Old Data

### الأسباب:
- [ ] Fragment المُعاد استخدامه
- [ ] ViewPager caching
- [ ] Shared ViewModel

### الحلول:

#### أ. تنظيف البيانات في onDestroyView
```kotlin
override fun onDestroyView() {
    super.onDestroyView()
    // تنظيف البيانات
    binding = null
    lastData = null
}
```

#### ب. استخدام savedInstanceState
```kotlin
override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString("key", value)
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    value = savedInstanceState?.getString("key") ?: ""
}
```

---

## 🔟 مشكلة: تصدير PDF بطيء

### الأسباب:
- [ ] الصور كبيرة الحجم
- [ ] الجداول معقدة
- [ ] تشغيل في Main Thread

### الحلول:

#### أ. تقليل حجم الصور
```kotlin
fun compressBitmap(bitmap: Bitmap, quality: Int = 85): Bitmap {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return BitmapFactory.decodeByteArray(
        stream.toByteArray(), 0, stream.toByteArray().size
    )
}
```

#### ب. تشغيل في Background Thread
```kotlin
// ✓ صحيح
Thread {
    val file = AdvancedPdfExporter.exportBeamDesignReport(...)
    runOnUiThread {
        // عرض النتيجة
    }
}.start()

// أو
Coroutines
viewModel.viewModelScope.launch(Dispatchers.IO) {
    val file = AdvancedPdfExporter.exportBeamDesignReport(...)
    withContext(Dispatchers.Main) {
        // عرض النتيجة
    }
}
```

---

## ✅ قائمة تحقق لإصلاح الأخطاء

- [ ] تم إزالة جميع NullPointerExceptions
- [ ] تم إزالة جميع Memory Leaks
- [ ] تم استخدام الـ Lifecycle-aware components
- [ ] تم اختبار على أجهزة مختلفة
- [ ] تم اختبار مع أحجام شاشات مختلفة
- [ ] تم اختبار في الـ Landscape و Portrait
- [ ] تم التحقق من الأداء
- [ ] تم التحقق من البطارية والذاكرة

---

## 📝 نصائح سريعة

```kotlin
// 1. استخدم viewBinding بدلاً من findViewById
// 2. استخدم view?.let بدلاً من if (view != null)
// 3. استخدم Elvis operator: value ?: defaultValue
// 4. استخدم scope functions: apply, let, run
// 5. استخدم safe calls: object?.method()
// 6. تحقق من الـ Fragment state قبل الاستخدام
// 7. نظف الـ listeners في onPause/onStop
// 8. استخدم Lifecycle observers للبيانات الطويلة الأجل
// 9. لا تستخدم GlobalScope
// 10. استخدم WeakReference للـ Context
```

---

**تاريخ الإنشاء:** 12 مارس 2026
**الحالة:** دليل شامل
**الإصدار:** 1.0

