package com.civileg.app.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tier-1 data safety: validates MIGRATION_7_8 against the exported
 * schemas 7.json → 8.json. Fails the build if SQL drifts from Room's
 * expected schema (columns, NOT NULL, indices).
 */
@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate7To8_validatesAndPreservesData() {
        // Create empty v7 database (schema from 7.json)
        helper.createDatabase("migration-test-7-8", 7).apply {
            // Seed a v7 row so we verify no data loss
            execSQL("INSERT INTO Project (id, name, code, description, createdAt) VALUES (1, 'Probe', 'ECP', 'probe', 0)")
            close()
        }

        // Run migration 7→8 and validate against 8.json
        helper.runMigrationsAndValidate("migration-test-7-8", 8, true, Migrations.MIGRATION_7_8).use { db ->
            // Probe table must survive
            db.query("SELECT COUNT(*) FROM Project").use { c ->
                c.moveToFirst()
                assert(c.getInt(0) == 1) { "Project row lost during migration 7→8" }
            }
            // New tables must exist
            listOf("flat_slabs", "pile_foundations", "shear_walls").forEach { table ->
                db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { c ->
                    assert(c.count == 1) { "Table $table missing after MIGRATION_7_8" }
                }
            }
            // Indices must exist
            listOf("index_flat_slabs_projectId", "index_pile_foundations_projectId", "index_shear_walls_projectId").forEach { idx ->
                db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='$idx'").use { c ->
                    assert(c.count == 1) { "Index $idx missing after MIGRATION_7_8" }
                }
            }
            // Insert into new tables must succeed (validates columns / NOT NULL)
            db.execSQL(
                "INSERT INTO flat_slabs (id, projectId, panelSpanX, panelSpanY, slabThickness, dropPanelThickness, dropPanelSizeX, dropPanelSizeY, columnSizeX, columnSizeY, hasDropPanel, hasShearReinforcement, deadLoad, liveLoad, fcu, fy, columnStripWidthX, columnStripWidthY, middleStripWidthX, middleStripWidthY, negMomentColStripX, posMomentColStripX, negMomentMidStripX, posMomentMidStripX, negMomentColStripY, posMomentColStripY, negMomentMidStripY, posMomentMidStripY, topReinColStripX, botReinColStripX, topReinMidStripX, botReinMidStripX, topReinColStripY, botReinColStripY, topReinMidStripY, botReinMidStripY, punchingShearStress, punchingShearCapacity, isPunchingSafe, deflection, allowableDeflection, isDeflectionSafe, isSafe, utilizationRatio, concreteVolume, steelWeight, totalCost, codeUsed, inputDataJson, resultsJson, createdAt, updatedAt) VALUES (1,1,1,1,0.2,0,0,0,0.4,0.4,0,0,1,1,25,360,1,1,1,1,0,0,0,0,0,0,0,0,'','','','','','','','',0,1,1,0,1,1,1,0,0,0,0,'ECP','{}','{}',0,0)"
            )
            db.execSQL(
                "INSERT INTO pile_foundations (id, projectId, pileType, pileDiameter, pileLength, numberOfPiles, axialLoad, lateralLoad, momentLoad, fcu, fy, soilType, cu, phi, ultimateCapacity, allowableCapacity, shaftResistance, endBearingResistance, settlement, isSafe, utilizationRatio, concreteVolume, steelWeight, totalCost, codeUsed, inputDataJson, resultsJson, createdAt, updatedAt) VALUES (1,1,'BORED',0.6,12,4,1000,0,0,25,360,'CLAY',50,0,100,50,0,0,10,1,0.5,1,100,0,'ECP','{}','{}',0,0)"
            )
            db.execSQL(
                "INSERT INTO shear_walls (id, projectId, wallLength, wallHeight, wallThickness, wallType, hasBoundaryElement, beLength, beThickness, axialLoad, shearForce, bendingMoment, fcu, fy, verticalReinforcement, horizontalReinforcement, boundaryVerticalReinforcement, boundaryTransverseReinforcement, webVerticalReinRatio, webHorizontalReinRatio, concreteShearCapacity, steelShearCapacity, totalShearCapacity, appliedShearStress, isShearSafe, axialCapacity, momentCapacity, isFlexureSafe, driftRatio, allowableDriftRatio, isDriftSafe, isSafe, utilizationRatio, concreteVolume, steelWeight, totalCost, codeUsed, inputDataJson, resultsJson, createdAt, updatedAt) VALUES (1,1,4,3,0.2,'RECT',0,0,0,500,200,300,25,360,'','','',0,0,0,0,0,0,1,0,0,1,0,0.002,1,0.5,1,100,0,'ECP','{}','{}',0,0)"
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_newDb_hasCorrectSchema() {
        // Fresh v8 database via migrations should pass validation even without prior data
        helper.createDatabase("migration-test-fresh", 7).close()
        helper.runMigrationsAndValidate("migration-test-fresh", 8, true, Migrations.MIGRATION_7_8).close()
    }
}
