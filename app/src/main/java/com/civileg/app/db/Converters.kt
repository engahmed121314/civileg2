package com.civileg.app.db

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
            MaterialCategory.CONCRETE
        }
    }

    @TypeConverter
    fun materialCategoryToString(category: MaterialCategory): String {
        return category.name
    }
}
