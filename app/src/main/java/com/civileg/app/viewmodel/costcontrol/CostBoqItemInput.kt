package com.civileg.app.viewmodel.costcontrol

import com.civileg.app.db.costcontrol.CostBoqItemEntity

/** Exact editor text is UI state, never a price/quantity confirmation. Safe to persist as strings. */
data class CostBoqItemInput(val number:String,val description:String,val unit:String,val quantity:String,val rate:String) {
    fun values()=listOf(number,description,unit,quantity,rate)
    companion object {
        fun from(item:CostBoqItemEntity)=CostBoqItemInput(item.displayedItemNumber,item.originalDescription,item.originalUnit,item.quantityText.orEmpty(),item.quotedRateText.orEmpty())
        fun decode(values:List<String>):CostBoqItemInput { require(values.size==5);return CostBoqItemInput(values[0],values[1],values[2],values[3],values[4]) }
    }
}
