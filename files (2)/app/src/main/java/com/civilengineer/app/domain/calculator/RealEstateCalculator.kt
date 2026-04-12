package com.civilengineer.app.domain.calculator

import com.civilengineer.app.data.models.CostBreakdown
import com.civilengineer.app.data.models.ProjectType

/**
 * محسبة دراسة العقار والجدوى الاقتصادية
 */
class RealEstateCalculator {

    /**
     * تقدير التكلفة لكل متر مربع بناءً على نوع المشروع وعدد الأدوار
     */
    fun estimateCostPerM2(
        projectType: ProjectType,
        numberOfFloors: Int
    ): Double {
        // التكاليف الأساسية لكل نوع مشروع (بالعملة المحلية)
        val baseCost = when (projectType) {
            ProjectType.RESIDENTIAL -> 800.0
            ProjectType.COMMERCIAL -> 1200.0
            ProjectType.INDUSTRIAL -> 600.0
            ProjectType.MIXED_USE -> 1000.0
            ProjectType.RESIDENTIAL_COMMERCIAL -> 1100.0
        }

        // معامل إضافي بناءً على عدد الأدوار
        val floorFactor = when {
            numberOfFloors <= 3 -> 1.0
            numberOfFloors <= 6 -> 0.95
            numberOfFloors <= 10 -> 0.90
            else -> 0.85
        }

        // إضافة تكاليف العمالة والمواد الإضافية
        val laborAndMaterials = baseCost * 0.30

        return (baseCost + laborAndMaterials) * floorFactor
    }

    /**
     * حساب توزيع التكاليف بناءً على نوع المشروع
     */
    fun generateCostBreakdown(projectType: ProjectType): CostBreakdown {
        return when (projectType) {
            ProjectType.RESIDENTIAL -> CostBreakdown(
                foundationPercentage = 8.0,
                structuralPercentage = 20.0,
                finishingPercentage = 40.0,
                utilitiesPercentage = 12.0,
                miscellaneousPercentage = 20.0
            )
            ProjectType.COMMERCIAL -> CostBreakdown(
                foundationPercentage = 10.0,
                structuralPercentage = 25.0,
                finishingPercentage = 35.0,
                utilitiesPercentage = 15.0,
                miscellaneousPercentage = 15.0
            )
            ProjectType.INDUSTRIAL -> CostBreakdown(
                foundationPercentage = 12.0,
                structuralPercentage = 30.0,
                finishingPercentage = 20.0,
                utilitiesPercentage = 20.0,
                miscellaneousPercentage = 18.0
            )
            ProjectType.MIXED_USE -> CostBreakdown(
                foundationPercentage = 9.0,
                structuralPercentage = 22.0,
                finishingPercentage = 38.0,
                utilitiesPercentage = 13.0,
                miscellaneousPercentage = 18.0
            )
            ProjectType.RESIDENTIAL_COMMERCIAL -> CostBreakdown(
                foundationPercentage = 9.0,
                structuralPercentage = 23.0,
                finishingPercentage = 37.0,
                utilitiesPercentage = 13.0,
                miscellaneousPercentage = 18.0
            )
        }
    }

    /**
     * حساب عدد الوحدات المتوقع في المشروع
     */
    fun estimateUnits(
        buildingAreaM2: Double,
        numberOfFloors: Int,
        projectType: ProjectType
    ): Int {
        val areaPerFloor = buildingAreaM2 / numberOfFloors
        
        return when (projectType) {
            ProjectType.RESIDENTIAL -> {
                // تقريباً 100-120 متر لكل شقة سكنية
                (areaPerFloor / 110 * numberOfFloors).toInt()
            }
            ProjectType.COMMERCIAL -> {
                // تقريباً 200-300 متر لكل وحدة تجارية
                (areaPerFloor / 250 * numberOfFloors).toInt()
            }
            ProjectType.INDUSTRIAL -> {
                // وحدة صناعية واحدة عادة
                1
            }
            else -> (areaPerFloor / 150 * numberOfFloors).toInt()
        }
    }

    /**
     * حساب الكثافة السكانية / التجارية
     */
    fun calculateDensity(
        numberOfUnits: Int,
        buildingAreaM2: Double
    ): Double {
        return (numberOfUnits * 100) / buildingAreaM2
    }

    /**
     * حساب فترة استرجاع الاستثمار (للمشاريع التجارية)
     */
    fun calculatePaybackPeriod(
        totalInvestment: Double,
        monthlyIncome: Double
    ): Double {
        return if (monthlyIncome > 0) {
            totalInvestment / (monthlyIncome * 12)
        } else {
            0.0
        }
    }

    /**
     * حساب العائد على الاستثمار
     */
    fun calculateROI(
        totalInvestment: Double,
        annualProfit: Double
    ): Double {
        return if (totalInvestment > 0) {
            (annualProfit / totalInvestment) * 100
        } else {
            0.0
        }
    }
}