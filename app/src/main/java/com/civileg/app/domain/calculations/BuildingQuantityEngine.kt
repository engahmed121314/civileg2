package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.BbsItem
import com.civileg.app.domain.entities.BuildingBoqItem
import com.civileg.app.domain.entities.BuildingColumnCategory
import com.civileg.app.domain.entities.BuildingModel
import com.civileg.app.domain.entities.BuildingNode
import com.civileg.app.domain.entities.BuildingQuantitySummary
import kotlin.math.PI

object BuildingQuantityEngine {

    /**
     * Computes high-precision execution quantities, BBS, and BOQ items for the entire building model.
     */
    fun calculateQuantities(
        model: BuildingModel,
        priceMap: Map<String, Double> = emptyMap()
    ): BuildingQuantitySummary {
        val nodeMap = model.nodes.associateBy { it.id }

        // 1. Footings Quantities
        var footingConcreteVol = 0.0
        var footingExcavationVol = 0.0
        var footingFormworkArea = 0.0
        val rebarByDia = mutableMapOf<Int, Double>()
        val bbsList = mutableListOf<BbsItem>()

        model.footings.forEachIndexed { index, footing ->
            val vol = footing.length * footing.width * footing.depth
            footingConcreteVol += vol
            footingExcavationVol += (footing.length + 0.6) * (footing.width + 0.6) * (footing.depth + 1.2)
            footingFormworkArea += 2 * (footing.length + footing.width) * footing.depth

            // Rebar calculation for footing
            val dia = footing.rebarDia
            val unitWeightKgM = (dia * dia) / 162.0
            val barsX = (footing.width / footing.rebarSpacing).toInt() + 1
            val barsY = (footing.length / footing.rebarSpacing).toInt() + 1
            val barLenX = footing.length + 2 * (footing.depth - 0.1) // U-shape hook
            val barLenY = footing.width + 2 * (footing.depth - 0.1)

            val weightXKg = barsX * barLenX * unitWeightKgM
            val weightYKg = barsY * barLenY * unitWeightKgM
            val footingRebarKg = weightXKg + weightYKg

            val currentTon = rebarByDia.getOrDefault(dia, 0.0)
            rebarByDia[dia] = currentTon + (footingRebarKg / 1000.0)

            bbsList.add(
                BbsItem(
                    mark = "F${index + 1}-BotX",
                    elementType = "Footing",
                    elementId = footing.id,
                    barDiaMm = dia,
                    lengthM = Math.round(barLenX * 100.0) / 100.0,
                    count = barsX,
                    totalWeightKg = Math.round(weightXKg * 10.0) / 10.0,
                    shapeDescription = "U-Bend Hook"
                )
            )
            bbsList.add(
                BbsItem(
                    mark = "F${index + 1}-BotY",
                    elementType = "Footing",
                    elementId = footing.id,
                    barDiaMm = dia,
                    lengthM = Math.round(barLenY * 100.0) / 100.0,
                    count = barsY,
                    totalWeightKg = Math.round(weightYKg * 10.0) / 10.0,
                    shapeDescription = "U-Bend Hook"
                )
            )
        }

        // 2. Columns Quantities
        var columnConcreteVol = 0.0
        var columnFormworkArea = 0.0
        var steelProfileWeightTon = 0.0

        model.columns.forEachIndexed { index, col ->
            if (col.category == BuildingColumnCategory.STEEL_IPE ||
                col.category == BuildingColumnCategory.STEEL_HEB ||
                col.category == BuildingColumnCategory.STEEL_HSS
            ) {
                // Steel Column
                val weightKg = col.height * 45.0 // ~45 kg/m profile weight
                steelProfileWeightTon += (weightKg / 1000.0)
            } else {
                // Concrete Column
                val vol = if (col.category == BuildingColumnCategory.RC_CIRCULAR) {
                    (PI * (col.width / 2.0) * (col.width / 2.0)) * col.height
                } else {
                    col.width * col.depth * col.height
                }
                columnConcreteVol += vol
                columnFormworkArea += 2 * (col.width + col.depth) * col.height

                val dia = col.rebarDia
                val unitWeightKgM = (dia * dia) / 162.0
                val barLen = col.height + 0.8 // overlap
                val colRebarKg = col.rebarCount * barLen * unitWeightKgM

                val currentTon = rebarByDia.getOrDefault(dia, 0.0)
                rebarByDia[dia] = currentTon + (colRebarKg / 1000.0)

                bbsList.add(
                    BbsItem(
                        mark = "C${index + 1}-Main",
                        elementType = "Column",
                        elementId = col.id,
                        barDiaMm = dia,
                        lengthM = Math.round(barLen * 100.0) / 100.0,
                        count = col.rebarCount,
                        totalWeightKg = Math.round(colRebarKg * 10.0) / 10.0,
                        shapeDescription = "Straight with L-Lap"
                    )
                )
            }
        }

        // 3. Slabs Quantities
        var slabConcreteVol = 0.0
        var slabFormworkArea = 0.0

        model.slabs.forEachIndexed { index, slab ->
            val slabNodes = slab.nodeIds.mapNotNull { nodeMap[it] }
            require(slabNodes.size >= 3 && slabNodes.size == slab.nodeIds.size) {
                "Slab requires at least three valid nodes"
            }
            val area = calculatePolygonArea(slabNodes)
            require(area.isFinite() && area > 0.0) {
                "Slab nodes must define a valid non-zero area"
            }
            val vol = area * slab.thickness
            slabConcreteVol += vol
            slabFormworkArea += area

            val dia = slab.rebarDia
            val unitWeightKgM = (dia * dia) / 162.0
            val barsPerM = (1.0 / slab.rebarMeshSpacing).toInt()
            val totalMeshRebarKg = area * 2 * (barsPerM * 1.0) * unitWeightKgM // top + bot mesh approx

            val currentTon = rebarByDia.getOrDefault(dia, 0.0)
            rebarByDia[dia] = currentTon + (totalMeshRebarKg / 1000.0)

            bbsList.add(
                BbsItem(
                    mark = "S${index + 1}-Mesh",
                    elementType = "Slab",
                    elementId = slab.id,
                    barDiaMm = dia,
                    lengthM = 6.0,
                    count = ((totalMeshRebarKg / (6.0 * unitWeightKgM))).toInt().coerceAtLeast(1),
                    totalWeightKg = Math.round(totalMeshRebarKg * 10.0) / 10.0,
                    shapeDescription = "Mesh Fabric Grid"
                )
            )
        }

        // 4. Steel Framing Members
        model.steelMembers.forEach { member ->
            val weightKg = member.length * member.unitWeightKgM
            steelProfileWeightTon += (weightKg / 1000.0)
        }

        val totalConcreteVol = footingConcreteVol + columnConcreteVol + slabConcreteVol
        val totalRebarTon = rebarByDia.values.sum()
        val totalFormwork = footingFormworkArea + columnFormworkArea + slabFormworkArea

        // Price mapping from priceMap or defaults
        val concPricePerM3 = priceMap["conc_c30"] ?: 1550.0
        val rebarPricePerTon = priceMap["steel_16"] ?: 31000.0
        val formworkPricePerM2 = priceMap["formwork_wood"] ?: 180.0
        val excavationPricePerM3 = priceMap["equipment_excavator"] ?: 80.0

        val boqItems = mutableListOf<BuildingBoqItem>()

        if (footingExcavationVol > 0) {
            boqItems.add(
                BuildingBoqItem(
                    id = "BOQ_EXC",
                    itemCode = "01-EXC",
                    descriptionAr = "أعمال حفر وتربة للقواعد",
                    descriptionEn = "Excavation Works for Foundations",
                    unitAr = "م³",
                    quantity = Math.round(footingExcavationVol * 10.0) / 10.0,
                    unitPrice = excavationPricePerM3,
                    totalPrice = footingExcavationVol * excavationPricePerM3,
                    category = "Substructure"
                )
            )
        }

        if (totalConcreteVol > 0) {
            boqItems.add(
                BuildingBoqItem(
                    id = "BOQ_CONC",
                    itemCode = "02-CONC",
                    descriptionAr = "خرسانة مسلحة جاهزة C30",
                    descriptionEn = "Reinforced Concrete C30",
                    unitAr = "م³",
                    quantity = Math.round(totalConcreteVol * 10.0) / 10.0,
                    unitPrice = concPricePerM3,
                    totalPrice = totalConcreteVol * concPricePerM3,
                    category = "Concrete"
                )
            )
        }

        if (totalRebarTon > 0) {
            boqItems.add(
                BuildingBoqItem(
                    id = "BOQ_STEEL",
                    itemCode = "03-REBAR",
                    descriptionAr = "حديد تسليح عالي الإجهاد (مختلف الأقطار)",
                    descriptionEn = "High-Tensile Steel Rebar",
                    unitAr = "طن",
                    quantity = Math.round(totalRebarTon * 100.0) / 100.0,
                    unitPrice = rebarPricePerTon,
                    totalPrice = totalRebarTon * rebarPricePerTon,
                    category = "Steel"
                )
            )
        }

        if (steelProfileWeightTon > 0) {
            val steelProfilePriceTon = rebarPricePerTon * 1.35 // profile premium
            boqItems.add(
                BuildingBoqItem(
                    id = "BOQ_PROFILE",
                    itemCode = "04-STRUCT_STEEL",
                    descriptionAr = "منشآت وقطاعات حديد ستيل (IPE/HEB)",
                    descriptionEn = "Structural Steel Framing (IPE/HEB)",
                    unitAr = "طن",
                    quantity = Math.round(steelProfileWeightTon * 100.0) / 100.0,
                    unitPrice = steelProfilePriceTon,
                    totalPrice = steelProfileWeightTon * steelProfilePriceTon,
                    category = "Steel Structure"
                )
            )
        }

        if (totalFormwork > 0) {
            boqItems.add(
                BuildingBoqItem(
                    id = "BOQ_FORM",
                    itemCode = "05-FORM",
                    descriptionAr = "شدات وطبليات خشبية ومعدنية",
                    descriptionEn = "Wooden & Metal Formwork",
                    unitAr = "م²",
                    quantity = Math.round(totalFormwork * 10.0) / 10.0,
                    unitPrice = formworkPricePerM2,
                    totalPrice = totalFormwork * formworkPricePerM2,
                    category = "Formwork"
                )
            )
        }

        val totalCost = boqItems.sumOf { it.totalPrice }

        return BuildingQuantitySummary(
            totalConcreteVolumeM3 = Math.round(totalConcreteVol * 10.0) / 10.0,
            totalRebarWeightTon = Math.round(totalRebarTon * 100.0) / 100.0,
            totalSteelProfileWeightTon = Math.round(steelProfileWeightTon * 100.0) / 100.0,
            totalFormworkAreaM2 = Math.round(totalFormwork * 10.0) / 10.0,
            totalExcavationVolumeM3 = Math.round(footingExcavationVol * 10.0) / 10.0,
            totalCost = Math.round(totalCost * 100.0) / 100.0,
            rebarByDiameterTon = rebarByDia.mapValues { Math.round(it.value * 100.0) / 100.0 },
            boqItems = boqItems,
            bbsItems = bbsList
        )
    }

    private fun calculatePolygonArea(nodes: List<BuildingNode>): Double {
        var area = 0.0
        val n = nodes.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += nodes[i].x * nodes[j].y
            area -= nodes[j].x * nodes[i].y
        }
        return Math.abs(area) / 2.0
    }
}
