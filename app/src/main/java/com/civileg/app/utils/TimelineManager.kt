package com.civileg.app.utils

import kotlin.math.ceil

/**
 * Timeline Management System
 * يدير الجدولة الزمنية والوقت المطلوب لكل بند
 */
object TimelineManager {

    // ==================== Work Duration Constants ====================
    object WorkRates {
        // معدلات العمل - الوحدة: العدد المنجز في اليوم
        
        // تشطيب الموقع والأساسات
        const val SITE_PREPARATION_PER_DAY = 100.0      // m²/day
        const val EXCAVATION_PER_DAY = 50.0             // m³/day
        const val FOUNDATION_LAYING_PER_DAY = 8.0       // m³/day
        
        // الحديد والقوالب
        const val STEEL_FIXING_PER_DAY = 500.0          // kg/day
        const val FORMWORK_PER_DAY = 15.0               // m²/day
        const val FORM_REMOVAL_PER_DAY = 25.0           // m²/day
        
        // الخرسانة
        const val CONCRETE_POURING_PER_DAY = 20.0       // m³/day (including compaction)
        const val CURING_DAYS = 7                        // minimum curing days
        
        // أنواع مختلفة من الأعمال
        const val BEAM_FIXING_PER_DAY = 10.0            // count/day
        const val COLUMN_FIXING_PER_DAY = 12.0          // count/day
        const val SLAB_FIXING_PER_DAY = 20.0            // m²/day
        const val STAIRCASE_FIXING_PER_DAY = 1.0        // count/day
    }

    // ==================== Work Phase ====================
    enum class WorkPhase(val durationInDays: Int, val description: String) {
        SITE_PREPARATION(3, "تحضير الموقع - Site Preparation"),
        EXCAVATION(5, "الحفر - Excavation"),
        FOOTING_WORK(7, "أعمال القواعد - Footing Work"),
        GROUND_FLOOR_FORMWORK(5, "قوالب الدور الأول - Ground Floor Formwork"),
        GROUND_FLOOR_STEEL(5, "حديد الدور الأول - Ground Floor Steel"),
        GROUND_FLOOR_CONCRETE(3, "صب الدور الأول - Ground Floor Concrete"),
        GROUND_FLOOR_CURING(7, "معالجة الخرسانة - Curing"),
        UPPER_FORMWORK(4, "قوالب الأدوار العليا - Upper Formwork"),
        UPPER_STEEL(4, "حديد الأدوار العليا - Upper Steel"),
        UPPER_CONCRETE(2, "صب الأدوار العليا - Upper Concrete"),
        UPPER_CURING(7, "معالجة الخرسانة - Curing"),
        FINISHING(10, "أعمال التشطيب - Finishing")
    }

    // ==================== Task ====================
    data class Task(
        val id: String,
        val name: String,
        val description: String,
        val phase: WorkPhase,
        val quantity: Double,
        val unit: String,
        val workRate: Double,           // الوحدة المنجزة في اليوم
        val durationInDays: Int,        // عدد الأيام المطلوبة
        val startDate: Long = 0L,       // تاريخ البداية (milliseconds)
        val endDate: Long = 0L,         // تاريخ النهاية (milliseconds)
        val dependencies: List<String> = emptyList(),  // معرفات المهام المتعلقة
        val workers: Int = 1,           // عدد العمال
        val equipmentRequired: String = "",
        val progressPercentage: Double = 0.0
    ) {
        fun getProgressStatus(): String {
            return when {
                progressPercentage == 0.0 -> "Not Started"
                progressPercentage < 100.0 -> "In Progress (${"%.1f".format(progressPercentage)}%)"
                else -> "Completed"
            }
        }
    }

    // ==================== Timeline ====================
    data class Timeline(
        val projectName: String,
        val projectStartDate: Long,
        val tasks: List<Task> = emptyList(),
        val totalDuration: Int = 0,        // أيام
        val actualProgress: Double = 0.0,  // نسبة التقدم
        val projectEndDate: Long = 0L
    ) {
        fun getTotalDurationInDays(): Int {
            return if (tasks.isEmpty()) 0 else {
                tasks.maxOf { it.durationInDays }.plus(
                    tasks.filter { it.dependencies.isNotEmpty() }.sumOf { it.durationInDays }
                )
            }
        }

        fun getCriticalPath(): List<Task> {
            // حساب المسار الحرج - يتم إرجاع المهام الحرجة
            return tasks.filter { task ->
                task.dependencies.isEmpty() || 
                tasks.filter { it.id in task.dependencies }.any { 
                    it.durationInDays + task.durationInDays >= getTotalDurationInDays() 
                }
            }
        }

        fun getOverallProgress(): Double {
            return if (tasks.isEmpty()) 0.0 else {
                tasks.map { it.progressPercentage }.average()
            }
        }
    }

    // ==================== Calculate Task Duration ====================
    fun calculateTaskDuration(
        quantity: Double,
        unit: String,
        workRate: Double,
        additionalDays: Int = 0,
        numberOfWorkers: Int = 1
    ): Int {
        val baseDays = ceil(quantity / (workRate * numberOfWorkers)).toInt()
        return baseDays + additionalDays
    }

    // ==================== Generate Beam Timeline ====================
    fun generateBeamTimeline(
        beamCount: Int,
        steelWeightKg: Double,
        concreteVolume: Double,
        formworkArea: Double
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        val taskIdPrefix = "BEAM_${System.currentTimeMillis()}"

        // Task 1: Formwork
        val formworkDays = calculateTaskDuration(
            formworkArea, "m²", WorkRates.FORMWORK_PER_DAY, 1, 2
        )
        tasks.add(
            Task(
                id = "${taskIdPrefix}_FORMWORK",
                name = "Beam Formwork",
                description = "تركيب القوالب - Installing formwork for beams",
                phase = WorkPhase.GROUND_FLOOR_FORMWORK,
                quantity = formworkArea,
                unit = "m²",
                workRate = WorkRates.FORMWORK_PER_DAY,
                durationInDays = formworkDays
            )
        )

        // Task 2: Steel Fixing
        val steelDays = calculateTaskDuration(
            steelWeightKg, "kg", WorkRates.STEEL_FIXING_PER_DAY, 0, 2
        )
        tasks.add(
            Task(
                id = "${taskIdPrefix}_STEEL",
                name = "Beam Reinforcement",
                description = "تسليح الكمرات - Fixing reinforcement steel",
                phase = WorkPhase.GROUND_FLOOR_STEEL,
                quantity = steelWeightKg,
                unit = "kg",
                workRate = WorkRates.STEEL_FIXING_PER_DAY,
                durationInDays = steelDays,
                dependencies = listOf("${taskIdPrefix}_FORMWORK")
            )
        )

        // Task 3: Concrete Pouring
        val concreteDays = calculateTaskDuration(
            concreteVolume, "m³", WorkRates.CONCRETE_POURING_PER_DAY, 1, 3
        )
        tasks.add(
            Task(
                id = "${taskIdPrefix}_CONCRETE",
                name = "Beam Concrete",
                description = "صب الخرسانة - Pouring concrete",
                phase = WorkPhase.GROUND_FLOOR_CONCRETE,
                quantity = concreteVolume,
                unit = "m³",
                workRate = WorkRates.CONCRETE_POURING_PER_DAY,
                durationInDays = concreteDays,
                dependencies = listOf("${taskIdPrefix}_STEEL"),
                workers = 3,
                equipmentRequired = "Concrete Pump, Vibrators"
            )
        )

        // Task 4: Curing
        tasks.add(
            Task(
                id = "${taskIdPrefix}_CURING",
                name = "Concrete Curing",
                description = "معالجة الخرسانة - Concrete curing",
                phase = WorkPhase.GROUND_FLOOR_CURING,
                quantity = concreteVolume,
                unit = "m³",
                workRate = 100.0,
                durationInDays = WorkRates.CURING_DAYS,
                dependencies = listOf("${taskIdPrefix}_CONCRETE"),
                workers = 1
            )
        )

        // Task 5: Formwork Removal
        val removalDays = calculateTaskDuration(
            formworkArea, "m²", WorkRates.FORM_REMOVAL_PER_DAY, 0, 2
        )
        tasks.add(
            Task(
                id = "${taskIdPrefix}_REMOVAL",
                name = "Formwork Removal",
                description = "نزع القوالب - Removing formwork",
                phase = WorkPhase.GROUND_FLOOR_CURING,
                quantity = formworkArea,
                unit = "m²",
                workRate = WorkRates.FORM_REMOVAL_PER_DAY,
                durationInDays = removalDays,
                dependencies = listOf("${taskIdPrefix}_CURING"),
                workers = 2
            )
        )

        return tasks
    }

    // ==================== Generate Column Timeline ====================
    fun generateColumnTimeline(
        columnCount: Int,
        steelWeightKg: Double,
        concreteVolume: Double,
        columnHeight: Double
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        val taskIdPrefix = "COLUMN_${System.currentTimeMillis()}"

        // Formwork
        val formworkArea = columnCount * columnHeight // estimated as perimeter × height
        val formworkDays = calculateTaskDuration(
            formworkArea, "m²", WorkRates.FORMWORK_PER_DAY, 1, 2
        )
        tasks.add(
            Task(
                id = "${taskIdPrefix}_FORMWORK",
                name = "Column Formwork",
                description = "قوالب الأعمدة - Column formwork",
                phase = WorkPhase.GROUND_FLOOR_FORMWORK,
                quantity = formworkArea,
                unit = "m²",
                workRate = WorkRates.FORMWORK_PER_DAY,
                durationInDays = formworkDays
            )
        )

        // Steel Fixing
        val steelDays = calculateTaskDuration(
            steelWeightKg, "kg", WorkRates.STEEL_FIXING_PER_DAY, 0, 2
        )
        tasks.add(
            Task(
                id = "${taskIdPrefix}_STEEL",
                name = "Column Reinforcement",
                description = "تسليح الأعمدة - Column steel fixing",
                phase = WorkPhase.GROUND_FLOOR_STEEL,
                quantity = steelWeightKg,
                unit = "kg",
                workRate = WorkRates.STEEL_FIXING_PER_DAY,
                durationInDays = steelDays,
                dependencies = listOf("${taskIdPrefix}_FORMWORK"),
                workers = 2
            )
        )

        // Concrete Pouring
        val concreteDays = calculateTaskDuration(
            concreteVolume, "m³", WorkRates.CONCRETE_POURING_PER_DAY, 1, 2
        )
        tasks.add(
            Task(
                id = "${taskIdPrefix}_CONCRETE",
                name = "Column Concrete",
                description = "صب الأعمدة - Column concrete pouring",
                phase = WorkPhase.GROUND_FLOOR_CONCRETE,
                quantity = concreteVolume,
                unit = "m³",
                workRate = WorkRates.CONCRETE_POURING_PER_DAY,
                durationInDays = concreteDays,
                dependencies = listOf("${taskIdPrefix}_STEEL"),
                workers = 2,
                equipmentRequired = "Concrete Pump, Vibrators, Formwork"
            )
        )

        // Curing
        tasks.add(
            Task(
                id = "${taskIdPrefix}_CURING",
                name = "Concrete Curing",
                description = "معالجة الخرسانة - Curing",
                phase = WorkPhase.GROUND_FLOOR_CURING,
                quantity = concreteVolume,
                unit = "m³",
                workRate = 100.0,
                durationInDays = WorkRates.CURING_DAYS,
                dependencies = listOf("${taskIdPrefix}_CONCRETE"),
                workers = 1
            )
        )

        return tasks
    }

    // ==================== Generate Slab Timeline ====================
    fun generateSlabTimeline(
        slabArea: Double,
        steelWeightKg: Double,
        concreteVolume: Double
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        val taskIdPrefix = "SLAB_${System.currentTimeMillis()}"

        // Formwork
        val formworkDays = calculateTaskDuration(
            slabArea, "m²", WorkRates.FORMWORK_PER_DAY, 1, 2
        )
        tasks.add(
            Task(
                id = "${taskIdPrefix}_FORMWORK",
                name = "Slab Formwork",
                description = "قوالب البلاطة - Slab formwork",
                phase = WorkPhase.UPPER_FORMWORK,
                quantity = slabArea,
                unit = "m²",
                workRate = WorkRates.FORMWORK_PER_DAY,
                durationInDays = formworkDays
            )
        )

        // Steel Fixing
        val steelDays = calculateTaskDuration(
            steelWeightKg, "kg", WorkRates.STEEL_FIXING_PER_DAY, 0, 2
        )
        tasks.add(
            Task(
                id = "${taskIdPrefix}_STEEL",
                name = "Slab Reinforcement",
                description = "تسليح البلاطة - Slab steel fixing",
                phase = WorkPhase.UPPER_STEEL,
                quantity = steelWeightKg,
                unit = "kg",
                workRate = WorkRates.STEEL_FIXING_PER_DAY,
                durationInDays = steelDays,
                dependencies = listOf("${taskIdPrefix}_FORMWORK"),
                workers = 3
            )
        )

        // Concrete Pouring
        val concreteDays = calculateTaskDuration(
            concreteVolume, "m³", WorkRates.CONCRETE_POURING_PER_DAY, 1, 3
        )
        tasks.add(
            Task(
                id = "${taskIdPrefix}_CONCRETE",
                name = "Slab Concrete",
                description = "صب البلاطة - Slab concrete pouring",
                phase = WorkPhase.UPPER_CONCRETE,
                quantity = concreteVolume,
                unit = "m³",
                workRate = WorkRates.CONCRETE_POURING_PER_DAY,
                durationInDays = concreteDays,
                dependencies = listOf("${taskIdPrefix}_STEEL"),
                workers = 3,
                equipmentRequired = "Concrete Pump, Vibrators, Laser Screeds"
            )
        )

        // Curing
        tasks.add(
            Task(
                id = "${taskIdPrefix}_CURING",
                name = "Concrete Curing",
                description = "معالجة الخرسانة - Curing",
                phase = WorkPhase.UPPER_CURING,
                quantity = concreteVolume,
                unit = "m³",
                workRate = 100.0,
                durationInDays = WorkRates.CURING_DAYS,
                dependencies = listOf("${taskIdPrefix}_CONCRETE"),
                workers = 1
            )
        )

        return tasks
    }

    // ==================== Cost of Timeline ====================
    data class TimelineCost(
        val laborCost: Double,
        val equipmentCost: Double,
        val overheadCost: Double,
        val totalTimingCost: Double
    )

    fun calculateTimelineCost(
        tasks: List<Task>,
        laborRatePerDay: Double = 2000.0,  // EGP/day
        equipmentDailyRate: Double = 500.0  // EGP/day
    ): TimelineCost {
        val totalDays = tasks.sumOf { it.durationInDays }
        val laborCost = tasks.sumOf { it.durationInDays * it.workers * laborRatePerDay }
        val equipmentCost = tasks.count { it.equipmentRequired.isNotEmpty() } * totalDays * equipmentDailyRate
        val overheadCost = (laborCost + equipmentCost) * 0.1  // 10% overhead

        return TimelineCost(
            laborCost = laborCost,
            equipmentCost = equipmentCost,
            overheadCost = overheadCost,
            totalTimingCost = laborCost + equipmentCost + overheadCost
        )
    }
}

