package com.civileg.app.utils

import android.content.Context
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.Dimension
import com.civileg.app.domain.modeler.FrameDxfBuilder
import com.civileg.app.domain.modeler.ModelerLayerVisibility
import java.io.File

object FrameDxfExporter {

    fun generateFrameDxf(
        context: Context,
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        slabs: List<SlabPanel>,
        dimensions: List<Dimension> = emptyList(),
        layers: ModelerLayerVisibility = ModelerLayerVisibility()
    ): File {
        val directory = context.getExternalFilesDir(null) ?: context.cacheDir
        val file = File(directory, "FrameModel_${System.currentTimeMillis()}.dxf")
        val content = FrameDxfBuilder.build(nodes, members, nodalLoads, memberLoads, slabs, dimensions, layers)
        file.writeText(content, Charsets.UTF_8)
        return file
    }
}
