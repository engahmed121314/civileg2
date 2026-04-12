package com.civileg.app.pricing

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class MaterialPrice(
    val id: String,
    val name: String,
    val nameAr: String,
    val unit: String,
    val unitAr: String,
    var price: Double,
    val category: MaterialCategory,
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class MaterialCategory {
    CONCRETE, STEEL, CEMENT, AGGREGATE, BRICKS, FORMWORK,
    FINISHING, ELECTRICAL, PLUMBING, LABOR, EQUIPMENT, OTHER
}

enum class Currency {
    EGP, USD, SAR, AED, KWD, QAR, OMR, BHD
}

object PriceManager {
    private const val PREFS_NAME = "price_prefs"
    private const val KEY_PRICES = "material_prices"
    private const val KEY_CURRENCY = "currency"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_LAST_UPDATE = "last_update"
    
    // أسعار افتراضية (EGP)
    private val defaultPrices = mapOf(
        // الخرسانة
        "conc_c15" to MaterialPrice("conc_c15", "Concrete C15", "خرسانة C15", "m³", "م³", 750.0, MaterialCategory.CONCRETE),
        "conc_c20" to MaterialPrice("conc_c20", "Concrete C20", "خرسانة C20", "m³", "م³", 850.0, MaterialCategory.CONCRETE),
        "conc_c25" to MaterialPrice("conc_c25", "Concrete C25", "خرسانة C25", "m³", "م³", 950.0, MaterialCategory.CONCRETE),
        "conc_c30" to MaterialPrice("conc_c30", "Concrete C30", "خرسانة C30", "m³", "م³", 1050.0, MaterialCategory.CONCRETE),
        "conc_c35" to MaterialPrice("conc_c35", "Concrete C35", "خرسانة C35", "m³", "م³", 1150.0, MaterialCategory.CONCRETE),
        "conc_c40" to MaterialPrice("conc_c40", "Concrete C40", "خرسانة C40", "m³", "م³", 1250.0, MaterialCategory.CONCRETE),
        "conc_c45" to MaterialPrice("conc_c45", "Concrete C45", "خرسانة C45", "m³", "م³", 1350.0, MaterialCategory.CONCRETE),
        "conc_c50" to MaterialPrice("conc_c50", "Concrete C50", "خرسانة C50", "m³", "م³", 1450.0, MaterialCategory.CONCRETE),
        
        // الحديد
        "steel_8" to MaterialPrice("steel_8", "Steel Ø8mm", "حديد Ø8مم", "ton", "طن", 28500.0, MaterialCategory.STEEL),
        "steel_10" to MaterialPrice("steel_10", "Steel Ø10mm", "حديد Ø10مم", "ton", "طن", 28000.0, MaterialCategory.STEEL),
        "steel_12" to MaterialPrice("steel_12", "Steel Ø12mm", "حديد Ø12مم", "ton", "طن", 27500.0, MaterialCategory.STEEL),
        "steel_14" to MaterialPrice("steel_14", "Steel Ø14mm", "حديد Ø14مم", "ton", "طن", 27500.0, MaterialCategory.STEEL),
        "steel_16" to MaterialPrice("steel_16", "Steel Ø16mm", "حديد Ø16مم", "ton", "طن", 27000.0, MaterialCategory.STEEL),
        "steel_18" to MaterialPrice("steel_18", "Steel Ø18mm", "حديد Ø18مم", "ton", "طن", 27000.0, MaterialCategory.STEEL),
        "steel_20" to MaterialPrice("steel_20", "Steel Ø20mm", "حديد Ø20مم", "ton", "طن", 26500.0, MaterialCategory.STEEL),
        "steel_22" to MaterialPrice("steel_22", "Steel Ø22mm", "حديد Ø22مم", "ton", "طن", 26500.0, MaterialCategory.STEEL),
        "steel_25" to MaterialPrice("steel_25", "Steel Ø25mm", "حديد Ø25مم", "ton", "طن", 26000.0, MaterialCategory.STEEL),
        "steel_28" to MaterialPrice("steel_28", "Steel Ø28mm", "حديد Ø28مم", "ton", "طن", 26000.0, MaterialCategory.STEEL),
        "steel_32" to MaterialPrice("steel_32", "Steel Ø32mm", "حديد Ø32مم", "ton", "طن", 25500.0, MaterialCategory.STEEL),
        "steel_mesh" to MaterialPrice("steel_mesh", "Steel Mesh", "شبكة حديد", "m²", "م²", 45.0, MaterialCategory.STEEL),
        
        // الأسمنت
        "cement_ordinary" to MaterialPrice("cement_ordinary", "Ordinary Portland Cement", "أسمنت بورتلاند عادي", "ton", "طن", 2200.0, MaterialCategory.CEMENT),
        "cement_resistant" to MaterialPrice("cement_resistant", "Sulfate Resistant Cement", "أسمنت مقاوم للأملاح", "ton", "طن", 2500.0, MaterialCategory.CEMENT),
        "cement_white" to MaterialPrice("cement_white", "White Cement", "أسمنت أبيض", "ton", "طن", 4500.0, MaterialCategory.CEMENT),
        
        // الركام
        "agg_10" to MaterialPrice("agg_10", "Aggregate 10mm", "زلط 10مم", "m³", "م³", 450.0, MaterialCategory.AGGREGATE),
        "agg_20" to MaterialPrice("agg_20", "Aggregate 20mm", "زلط 20مم", "m³", "م³", 400.0, MaterialCategory.AGGREGATE),
        "agg_40" to MaterialPrice("agg_40", "Aggregate 40mm", "زلط 40مم", "m³", "م³", 350.0, MaterialCategory.AGGREGATE),
        "sand" to MaterialPrice("sand", "Sand", "رمل", "m³", "م³", 250.0, MaterialCategory.AGGREGATE),
        
        // الطوب والبلوك
        "brick_red" to MaterialPrice("brick_red", "Red Brick", "طوب أحمر", "1000", "1000طوبة", 1800.0, MaterialCategory.BRICKS),
        "block_10" to MaterialPrice("block_10", "Concrete Block 10cm", "بلوك خرساني 10سم", "m²", "م²", 35.0, MaterialCategory.BRICKS),
        "block_15" to MaterialPrice("block_15", "Concrete Block 15cm", "بلوك خرساني 15سم", "m²", "م²", 45.0, MaterialCategory.BRICKS),
        "block_20" to MaterialPrice("block_20", "Concrete Block 20cm", "بلوك خرساني 20سم", "m²", "م²", 55.0, MaterialCategory.BRICKS),
        "hollow_block" to MaterialPrice("hollow_block", "Hollow Block", "بلوك فارغ", "m²", "م²", 65.0, MaterialCategory.BRICKS),
        
        // الشدات
        "formwork_wood" to MaterialPrice("formwork_wood", "Wooden Formwork", "شدة خشبية", "m²", "م²", 180.0, MaterialCategory.FORMWORK),
        "formwork_metal" to MaterialPrice("formwork_metal", "Metal Formwork", "شدة معدنية", "m²", "م²", 350.0, MaterialCategory.FORMWORK),
        "formwork_plastic" to MaterialPrice("formwork_plastic", "Plastic Formwork", "شدة بلاستيكية", "m²", "م²", 280.0, MaterialCategory.FORMWORK),
        "props" to MaterialPrice("props", "Steel Props", "عمدان معدنية", "month", "شهر", 15.0, MaterialCategory.FORMWORK),
        
        // التشطيبات
        "tiles_ceramic" to MaterialPrice("tiles_ceramic", "Ceramic Tiles", "سيراميك", "m²", "م²", 120.0, MaterialCategory.FINISHING),
        "tiles_porcelain" to MaterialPrice("tiles_porcelain", "Porcelain Tiles", "بورسلين", "m²", "م²", 250.0, MaterialCategory.FINISHING),
        "tiles_marble" to MaterialPrice("tiles_marble", "Marble", "رخام", "m²", "م²", 450.0, MaterialCategory.FINISHING),
        "paint_interior" to MaterialPrice("paint_interior", "Interior Paint", "دهان داخلي", "m²", "م²", 35.0, MaterialCategory.FINISHING),
        "paint_exterior" to MaterialPrice("paint_exterior", "Exterior Paint", "دهان خارجي", "m²", "م²", 55.0, MaterialCategory.FINISHING),
        "plaster" to MaterialPrice("plaster", "Plaster", "لياسة", "m²", "م²", 65.0, MaterialCategory.FINISHING),
        
        // الكهرباء
        "electrical_cable" to MaterialPrice("electrical_cable", "Electrical Cable", "كابلات كهربائية", "m", "م", 25.0, MaterialCategory.ELECTRICAL),
        "electrical_points" to MaterialPrice("electrical_points", "Electrical Points", "نقاط كهربائية", "point", "نقطة", 350.0, MaterialCategory.ELECTRICAL),
        "electrical_panel" to MaterialPrice("electrical_panel", "Distribution Panel", "لوحة توزيع", "unit", "وحدة", 4500.0, MaterialCategory.ELECTRICAL),
        
        // السباكة
        "plumbing_pipes" to MaterialPrice("plumbing_pipes", "Plumbing Pipes", "مواسير سباكة", "m", "م", 45.0, MaterialCategory.PLUMBING),
        "plumbing_fixtures" to MaterialPrice("plumbing_fixtures", "Sanitary Fixtures", "أدوات صحية", "set", "طقم", 3500.0, MaterialCategory.PLUMBING),
        "plumbing_tank" to MaterialPrice("plumbing_tank", "Water Tank", "خزان مياه", "m³", "م³", 800.0, MaterialCategory.PLUMBING),
        
        // المصنعية
        "labor_concrete" to MaterialPrice("labor_concrete", "Concrete Labor", "مصنعية خرسانة", "m³", "م³", 180.0, MaterialCategory.LABOR),
        "labor_steel" to MaterialPrice("labor_steel", "Steel Fixing Labor", "مصنعية حديد", "ton", "طن", 2800.0, MaterialCategory.LABOR),
        "labor_formwork" to MaterialPrice("labor_formwork", "Formwork Labor", "مصنعية شدة", "m²", "م²", 85.0, MaterialCategory.LABOR),
        "labor_masonry" to MaterialPrice("labor_masonry", "Masonry Labor", "مصنعية مباني", "m²", "م²", 45.0, MaterialCategory.LABOR),
        "labor_plaster" to MaterialPrice("labor_plaster", "Plastering Labor", "مصنعية لياسة", "m²", "م²", 35.0, MaterialCategory.LABOR),
        "labor_painting" to MaterialPrice("labor_painting", "Painting Labor", "مصنعية دهان", "m²", "م²", 25.0, MaterialCategory.LABOR),
        "labor_tiles" to MaterialPrice("labor_tiles", "Tiles Labor", "مصنعية سيراميك", "m²", "م²", 55.0, MaterialCategory.LABOR),
        
        // المعدات
        "equipment_crane" to MaterialPrice("equipment_crane", "Tower Crane", "ونش برجي", "month", "شهر", 45000.0, MaterialCategory.EQUIPMENT),
        "equipment_concrete_pump" to MaterialPrice("equipment_concrete_pump", "Concrete Pump", "مضخة خرسانة", "m³", "م³", 35.0, MaterialCategory.EQUIPMENT),
        "equipment_excavator" to MaterialPrice("equipment_excavator", "Excavator", "حفار", "day", "يوم", 2500.0, MaterialCategory.EQUIPMENT),
        "equipment_mixer" to MaterialPrice("equipment_mixer", "Concrete Mixer", "خلاطة خرسانة", "day", "يوم", 450.0, MaterialCategory.EQUIPMENT)
    )
    
    // أسعار الصرف (مقابل USD)
    private val exchangeRates = mapOf(
        Currency.EGP to 30.90,
        Currency.USD to 1.0,
        Currency.SAR to 3.75,
        Currency.AED to 3.67,
        Currency.KWD to 0.31,
        Currency.QAR to 3.64,
        Currency.OMR to 0.38,
        Currency.BHD to 0.38
    )
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun getMaterialPrice(context: Context, materialId: String): MaterialPrice? {
        val prefs = getPrefs(context)
        val pricesJson = prefs.getString(KEY_PRICES, null)
        
        return if (pricesJson != null) {
            try {
                val json = JSONObject(pricesJson)
                if (json.has(materialId)) {
                    val obj = json.getJSONObject(materialId)
                    MaterialPrice(
                        id = materialId,
                        name = obj.getString("name"),
                        nameAr = obj.getString("nameAr"),
                        unit = obj.getString("unit"),
                        unitAr = obj.getString("unitAr"),
                        price = obj.getDouble("price"),
                        category = MaterialCategory.valueOf(obj.getString("category")),
                        lastUpdated = obj.getLong("lastUpdated")
                    )
                } else {
                    defaultPrices[materialId]?.copy()
                }
            } catch (e: Exception) {
                defaultPrices[materialId]?.copy()
            }
        } else {
            defaultPrices[materialId]?.copy()
        }
    }
    
    fun getAllPrices(context: Context): Map<String, MaterialPrice> {
        val prefs = getPrefs(context)
        val pricesJson = prefs.getString(KEY_PRICES, null)
        val result = mutableMapOf<String, MaterialPrice>()
        
        if (pricesJson != null) {
            try {
                val json = JSONObject(pricesJson)
                defaultPrices.keys.forEach { id ->
                    if (json.has(id)) {
                        val obj = json.getJSONObject(id)
                        result[id] = MaterialPrice(
                            id = id,
                            name = obj.getString("name"),
                            nameAr = obj.getString("nameAr"),
                            unit = obj.getString("unit"),
                            unitAr = obj.getString("unitAr"),
                            price = obj.getDouble("price"),
                            category = MaterialCategory.valueOf(obj.getString("category")),
                            lastUpdated = obj.getLong("lastUpdated")
                        )
                    } else {
                        result[id] = defaultPrices[id]!!.copy()
                    }
                }
            } catch (e: Exception) {
                defaultPrices.forEach { (id, price) ->
                    result[id] = price.copy()
                }
            }
        } else {
            defaultPrices.forEach { (id, price) ->
                result[id] = price.copy()
            }
        }
        
        return result
    }
    
    fun updatePrice(context: Context, materialId: String, newPrice: Double) {
        val prices = getAllPrices(context).toMutableMap()
        prices[materialId]?.let {
            prices[materialId] = it.copy(price = newPrice, lastUpdated = System.currentTimeMillis())
        }
        savePrices(context, prices)
    }
    
    fun updatePrices(context: Context, newPrices: Map<String, Double>) {
        val prices = getAllPrices(context).toMutableMap()
        newPrices.forEach { (id, price) ->
            prices[id]?.let {
                prices[id] = it.copy(price = price, lastUpdated = System.currentTimeMillis())
            }
        }
        savePrices(context, prices)
    }
    
    private fun savePrices(context: Context, prices: Map<String, MaterialPrice>) {
        val json = JSONObject()
        prices.forEach { (id, price) ->
            val obj = JSONObject().apply {
                put("name", price.name)
                put("nameAr", price.nameAr)
                put("unit", price.unit)
                put("unitAr", price.unitAr)
                put("price", price.price)
                put("category", price.category.name)
                put("lastUpdated", price.lastUpdated)
            }
            json.put(id, obj)
        }
        
        getPrefs(context).edit().putString(KEY_PRICES, json.toString()).apply()
        getPrefs(context).edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
    }
    
    fun resetToDefaults(context: Context) {
        getPrefs(context).edit().remove(KEY_PRICES).apply()
    }
    
    // تحديث الأسعار من الإنترنت
    suspend fun updatePricesFromInternet(context: Context, country: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val newPrices = when (country.lowercase()) {
                    "egypt", "eg" -> fetchEgyptPrices()
                    "saudi", "sa" -> fetchSaudiPrices()
                    "uae", "ae" -> fetchUAEPPrices()
                    else -> null
                }
                
                newPrices?.let {
                    updatePrices(context, it)
                    return@withContext true
                }
                return@withContext false
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }
    
    private fun fetchEgyptPrices(): Map<String, Double> {
        // في التطبيق الفعلي، هنا سيكون استدعاء API حقيقي
        // مثال: https://api.example.com/prices/egypt
        
        // محاكاة للتحديث (زيادة 5% مثلاً)
        return mapOf(
            "conc_c25" to 997.5,  // 950 * 1.05
            "steel_16" to 28350.0, // 27000 * 1.05
            "cement_ordinary" to 2310.0 // 2200 * 1.05
        )
    }
    
    private fun fetchSaudiPrices(): Map<String, Double> {
        // محاكاة للأسعار السعودية (بالريال)
        return mapOf(
            "conc_c25" to 240.0,
            "steel_16" to 2800.0,
            "cement_ordinary" to 280.0
        )
    }
    
    private fun fetchUAEPPrices(): Map<String, Double> {
        // محاكاة للأسعار الإماراتية (بالدرهم)
        return mapOf(
            "conc_c25" to 235.0,
            "steel_16" to 2750.0,
            "cement_ordinary" to 275.0
        )
    }
    
    // تحويل العملات
    fun convertPrice(price: Double, from: Currency, to: Currency): Double {
        if (from == to) return price
        
        val rateFrom = exchangeRates[from] ?: 1.0
        val rateTo = exchangeRates[to] ?: 1.0
        
        // Convert to USD first, then to target
        val inUSD = price / rateFrom
        return inUSD * rateTo
    }
    
    fun getCurrency(context: Context): Currency {
        val prefs = getPrefs(context)
        val currencyStr = prefs.getString(KEY_CURRENCY, Currency.EGP.name)
        return try {
            Currency.valueOf(currencyStr!!)
        } catch (e: Exception) {
            Currency.EGP
        }
    }
    
    fun setCurrency(context: Context, currency: Currency) {
        getPrefs(context).edit().putString(KEY_CURRENCY, currency.name).apply()
    }
    
    fun isAutoUpdateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_UPDATE, false)
    }
    
    fun setAutoUpdate(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }
    
    fun getLastUpdateTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_UPDATE, 0)
    }
    
    fun getLastUpdateFormatted(context: Context): String {
        val time = getLastUpdateTime(context)
        if (time == 0L) return "Never"
        
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(time))
    }
    
    // حساب تكلفة عنصر إنشائي
    fun calculateElementCost(
        context: Context,
        concreteGrade: String,
        concreteVolume: Double,
        steelRatio: Double,
        elementVolume: Double
    ): ElementCost {
        val concretePrice = getMaterialPrice(context, "conc_$concreteGrade")?.price ?: 950.0
        val steelPrice = getMaterialPrice(context, "steel_16")?.price ?: 27000.0
        
        val steelWeight = steelRatio * elementVolume * 7850 / 1000 // ton
        
        val concreteCost = concreteVolume * concretePrice
        val steelCost = steelWeight * steelPrice
        
        // المصنعية (تقريباً 40% من تكلفة المواد)
        val laborCost = (concreteCost + steelCost) * 0.4
        
        return ElementCost(
            concreteCost = concreteCost,
            steelCost = steelCost,
            laborCost = laborCost,
            totalCost = concreteCost + steelCost + laborCost,
            steelWeight = steelWeight
        )
    }
    
    // حساب تكلفة المشروع بالكامل
    fun calculateProjectCost(context: Context, elements: List<ProjectElement>): ProjectCost {
        var totalConcrete = 0.0
        var totalSteel = 0.0
        var totalLabor = 0.0
        var totalSteelWeight = 0.0
        
        elements.forEach { element ->
            val cost = calculateElementCost(
                context,
                element.concreteGrade,
                element.volume,
                element.steelRatio,
                element.volume
            )
            totalConcrete += cost.concreteCost
            totalSteel += cost.steelCost
            totalLabor += cost.laborCost
            totalSteelWeight += cost.steelWeight
        }
        
        return ProjectCost(
            concreteCost = totalConcrete,
            steelCost = totalSteel,
            laborCost = totalLabor,
            totalCost = totalConcrete + totalSteel + totalLabor,
            totalSteelWeight = totalSteelWeight,
            elementCount = elements.size
        )
    }
    
    // تصدير الأسعار
    fun exportPrices(context: Context): String {
        val prices = getAllPrices(context)
        val json = JSONObject()
        
        json.put("exportDate", System.currentTimeMillis())
        json.put("currency", getCurrency(context).name)
        
        val pricesObj = JSONObject()
        prices.forEach { (id, price) ->
            val obj = JSONObject().apply {
                put("price", price.price)
                put("lastUpdated", price.lastUpdated)
            }
            pricesObj.put(id, obj)
        }
        json.put("prices", pricesObj)
        
        return json.toString()
    }
    
    // استيراد الأسعار
    fun importPrices(context: Context, jsonString: String): Boolean {
        return try {
            val json = JSONObject(jsonString)
            val pricesObj = json.getJSONObject("prices")
            
            val newPrices = mutableMapOf<String, Double>()
            pricesObj.keys().forEach { id ->
                newPrices[id] = pricesObj.getJSONObject(id).getDouble("price")
            }
            
            updatePrices(context, newPrices)
            true
        } catch (e: Exception) {
            false
        }
    }
}

data class ElementCost(
    val concreteCost: Double,
    val steelCost: Double,
    val laborCost: Double,
    val totalCost: Double,
    val steelWeight: Double
)

data class ProjectElement(
    val id: String,
    val name: String,
    val type: ElementType,
    val concreteGrade: String,
    val volume: Double,
    val steelRatio: Double,
    val dimensions: String
)

enum class ElementType {
    FOOTING, COLUMN, BEAM, SLAB, STAIRCASE, WALL
}

data class ProjectCost(
    val concreteCost: Double,
    val steelCost: Double,
    val laborCost: Double,
    val totalCost: Double,
    val totalSteelWeight: Double,
    val elementCount: Int
)
