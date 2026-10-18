package com.civileg.app.utils

import com.civileg.app.domain.entities.ConnectionDesignResult
import com.civileg.app.domain.entities.ConnectionType
import kotlin.math.roundToInt

/**
 * Derives the parameters used by the steel drawing (screen + PDF) from the actual
 * connection design result, so the drawing and the report stay consistent.
 *
 * Bolt diameter / count and weld size are read from the real [ConnectionType].
 * Gauge and pitch are not stored on [ConnectionType]; they are derived from the
 * bolt diameter using the AISC 360 J3.3 preferred spacing of 3·d (minimum 2⅔·d),
 * rounded to the nearest 5 mm, so the drawing scales with the actual bolt size
 * instead of fixed literals.
 */
object SteelConnectionDrawing {

    data class Params(
        val boltDia: Double,
        val boltCount: Int,
        val boltGauge: Double,
        val boltPitch: Double,
        val weldSize: Double
    )

    fun from(
        connectionDesign: ConnectionDesignResult?,
        defaultBoltDia: Double = 20.0,
        defaultBoltCount: Int = 4,
        defaultWeldSize: Double = 6.0
    ): Params {
        val connection = connectionDesign?.connectionType
        val bolted = when (connection) {
            is ConnectionType.Bolted -> connection
            is ConnectionType.Hybrid -> connection.bolted
            else -> null
        }
        val weld = when (connection) {
            is ConnectionType.Welded -> connection.weldSize
            is ConnectionType.Hybrid -> connection.welded.weldSize
            else -> null
        }

        val dia = bolted?.boltDiameter ?: defaultBoltDia
        val count = bolted?.numberOfBolts ?: defaultBoltCount
        val spacing = (3.0 * dia / 5.0).roundToInt() * 5.0

        return Params(
            boltDia = dia,
            boltCount = count,
            boltGauge = spacing,
            boltPitch = spacing,
            weldSize = weld ?: defaultWeldSize
        )
    }
}
