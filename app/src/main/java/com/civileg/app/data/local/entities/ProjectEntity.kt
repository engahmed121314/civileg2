package com.civileg.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.ElementType
import com.civileg.app.domain.entities.Project

@Entity(tableName = "project_entities")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val date: Long = System.currentTimeMillis(),
    val designCode: String,           // "ECP", "ACI", "SBC"
    val elementType: String,          // "COLUMN", "BEAM", etc.
    val inputsJson: String,           // JSON for inputs map
    val resultsJson: String,          // JSON for results map
    val notes: String = ""
) {
    companion object {
        private val gson = Gson()
        
        fun fromProject(project: Project): ProjectEntity {
            return ProjectEntity(
                id = project.id,
                name = project.name,
                date = project.date,
                designCode = project.designCode.name,
                elementType = project.elementType.name,
                inputsJson = gson.toJson(project.inputs),
                resultsJson = gson.toJson(project.results),
                notes = project.notes
            )
        }
    }
    
    fun toProject(): Project {
        val inputsType = object : TypeToken<Map<String, Double>>() {}.type
        val resultsType = object : TypeToken<Map<String, Any?>>() {}.type
        
        return Project(
            id = id,
            name = name,
            date = date,
            designCode = DesignCode.valueOf(designCode),
            elementType = ElementType.valueOf(elementType),
            inputs = gson.fromJson(inputsJson, inputsType),
            results = gson.fromJson(resultsJson, resultsType),
            notes = notes
        )
    }
}
