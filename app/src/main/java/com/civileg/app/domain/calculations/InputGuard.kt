package com.civileg.app.domain.calculations

/**
 * InputGuard — حراس المدخلات الموحد (Rule 1.4: loud failures, no silent zeros).
 *
 * كل نقطة دخول محرك يجب أن تستدعي InputGuard أولاً.
 * القيم الصفرية/السالبة/NaN ترفع IllegalArgumentException برسالة ثنائية اللغة
 * بدل إنتاج نتائج زائفة بصمت.
 *
 * ADR-010: التحقق قبل الحساب — لا معادلة على مدخلات غير صالحة.
 */
object InputGuard {

    fun positive(name: String, value: Double): Double {
        require(value.isFinite() && value > 0) {
            "Input '$name' must be finite and positive, got $value / يجب أن يكون '$name' موجباً ومنتهياً، القيمة: $value"
        }
        return value
    }

    fun positive(name: String, value: Int): Int {
        require(value > 0) {
            "Input '$name' must be positive, got $value / يجب أن يكون '$name' موجباً، القيمة: $value"
        }
        return value
    }

    fun positive(name: String, value: Float): Float {
        require(value.isFinite() && value > 0f) {
            "Input '$name' must be finite and positive, got $value / يجب أن يكون '$name' موجباً ومنتهياً، القيمة: $value"
        }
        return value
    }

    fun nonNegative(name: String, value: Double): Double {
        require(value.isFinite() && value >= 0) {
            "Input '$name' must be finite and non-negative, got $value / يجب أن يكون '$name' غير سالب ومنتهياً، القيمة: $value"
        }
        return value
    }

    fun nonNegative(name: String, value: Int): Int {
        require(value >= 0) {
            "Input '$name' must be non-negative, got $value / يجب أن يكون '$name' غير سالب، القيمة: $value"
        }
        return value
    }

    fun inRange(name: String, value: Double, min: Double, max: Double): Double {
        require(value.isFinite() && value >= min && value <= max) {
            "Input '$name' must be in [$min, $max], got $value / يجب أن يكون '$name' في المدى [$min, $max]، القيمة: $value"
        }
        return value
    }

    fun inRange(name: String, value: Int, min: Int, max: Int): Int {
        require(value >= min && value <= max) {
            "Input '$name' must be in [$min, $max], got $value / يجب أن يكون '$name' في المدى [$min, $max]، القيمة: $value"
        }
        return value
    }

    fun <T> notNull(name: String, value: T?): T {
        require(value != null) {
            "Input '$name' must not be null / لا يمكن أن يكون '$name' معدوماً"
        }
        return value
    }

    fun notBlank(name: String, value: String): String {
        require(value.isNotBlank()) {
            "Input '$name' must not be blank / لا يمكن أن يكون '$name' فارغاً"
        }
        return value
    }

    fun <T> notEmpty(name: String, collection: Collection<T>): Collection<T> {
        require(collection.isNotEmpty()) {
            "Input '$name' must not be empty / لا يمكن أن تكون '$name' فارغة"
        }
        return collection
    }

    /**
     * At least one of the named values must be positive.
     * Returns the positive values.
     */
    fun atLeastOne(vararg pairs: Pair<String, Double>): List<Double> {
        val positives = pairs.filter { it.second.isFinite() && it.second > 0 }.map { it.second }
        require(positives.isNotEmpty()) {
            "At least one of [${pairs.joinToString { it.first }}] must be positive / يجب أن يكون واحداً على الأقل من [${pairs.joinToString { it.first }}] موجباً"
        }
        return positives
    }

    fun require(condition: Boolean, message: String) {
        require(condition) { message }
    }

    /**
     * Finite check for computed intermediates — fails loud on NaN/Infinity.
     */
    fun finite(name: String, value: Double): Double {
        require(value.isFinite()) {
            "Computed '$name' must be finite, got $value / القيمة المحسوبة '$name' غير منتهية: $value"
        }
        return value
    }
}
