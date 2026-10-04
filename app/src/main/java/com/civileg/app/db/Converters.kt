package com.civileg.app.db

import android.util.Log
import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromProjectStatus(value: String): ProjectStatus {
        return try {
            ProjectStatus.valueOf(value)
        } catch (e: Exception) {
            Log.w("Converters", "Unexpected ProjectStatus value: $value, falling back to ACTIVE", e)
            ProjectStatus.ACTIVE
        }
    }

    @TypeConverter
    fun projectStatusToString(status: ProjectStatus): String {
        return status.name
    }

    @TypeConverter
    fun fromDesignType(value: String): DesignType {
        return try {
            DesignType.valueOf(value)
        } catch (e: Exception) {
            Log.w("Converters", "Unexpected DesignType value: $value, falling back to BEAM", e)
            DesignType.BEAM
        }
    }

    @TypeConverter
    fun designTypeToString(type: DesignType): String {
        return type.name
    }

    @TypeConverter
    fun fromMaterialCategory(value: String): MaterialCategory {
        return try {
            MaterialCategory.valueOf(value)
        } catch (e: Exception) {
            Log.w("Converters", "Unexpected MaterialCategory value: $value, falling back to CONCRETE", e)
            MaterialCategory.CONCRETE
        }
    }

    @TypeConverter
    fun materialCategoryToString(category: MaterialCategory): String {
        return category.name
    }

    @TypeConverter
    fun fromInventoryType(value: String): InventoryType {
        return try {
            InventoryType.valueOf(value)
        } catch (e: Exception) {
            Log.w("Converters", "Unexpected InventoryType value: $value, falling back to RAW_MATERIAL", e)
            InventoryType.RAW_MATERIAL
        }
    }

    @TypeConverter
    fun inventoryTypeToString(type: InventoryType): String {
        return type.name
    }
}
