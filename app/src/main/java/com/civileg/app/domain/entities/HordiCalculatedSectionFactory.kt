package com.civileg.app.domain.entities

import java.util.Collections

/** Older results without actual geometry return null, never a reconstructed layout. */
fun HordiCalculatedSectionSnapshot.Companion.from(snapshot: HordiSlabDesignSnapshot): HordiCalculatedSectionSnapshot? {
    require(snapshot.hasValidFingerprint()) { "Frozen calculation fingerprint is invalid" }
    val result = snapshot.result()
    val geometry = result.calculatedSection ?: return null
    require(geometry.geometryVersion == 1) { "Unsupported calculated section geometry version" }
    val input = snapshot.input()
    val context = requireNotNull(input.sectionContext)
    require(input.designCode == "ECP")
    require(geometry.coverToOutsideLinksMm == context.coverToOutsideLinksMm &&
        geometry.linkDiameterMm == context.links.diameterMm && geometry.linkLegs == context.links.legs &&
        geometry.linkSpacingMm == context.links.spacingMm)
    geometry.bars?.let { bars ->
        require(bars.size.toLong() == context.tensionLayers.sumOf { it.count.toLong() })
        context.tensionLayers.forEachIndexed { layerIndex, layer ->
            val row = bars.filter { it.layerIndex == layerIndex }
            require(row.size == layer.count && row.all { it.diameterMm == layer.diameterMm } &&
                row.map { it.indexInLayer }.sorted() == (0 until layer.count).toList())
        }
    }
    require(geometry.ribWidthMm == input.ribWidth && geometry.enteredRibPitchMm == input.ribSpacing &&
        geometry.totalDepthMm == input.totalThickness && geometry.toppingThicknessMm == input.toppingThickness)
    val frozen = geometry.copy(
        bars = geometry.bars?.let { Collections.unmodifiableList(it.toList()) },
        incompleteGeometry = Collections.unmodifiableList(geometry.incompleteGeometry.toList())
    )
    return HordiCalculatedSectionSnapshot(snapshot.revisionSha256, frozen,
        result.requiredReinforcement, result.providedReinforcement,
        "ECP203-2020 bounded rib section; ${geometry.placementBasis}",
        Collections.unmodifiableList((result.warnings + geometry.incompleteGeometry).distinct()))
}
