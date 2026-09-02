package com.civileg.app.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database Migrations
 * Every schema change MUST have a corresponding migration to preserve user data.
 */
object Migrations {

    class Migration6To7 : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // No schema changes yet
        }
    }
    val MIGRATION_6_7 = Migration6To7()

    class Migration7To8 : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `flat_slabs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`projectId` INTEGER NOT NULL, `panelSpanX` REAL NOT NULL, `panelSpanY` REAL NOT NULL, " +
                    "`slabThickness` REAL NOT NULL, `dropPanelThickness` REAL NOT NULL, " +
                    "`dropPanelSizeX` REAL NOT NULL, `dropPanelSizeY` REAL NOT NULL, " +
                    "`columnSizeX` REAL NOT NULL, `columnSizeY` REAL NOT NULL, " +
                    "`hasDropPanel` INTEGER NOT NULL, `hasShearReinforcement` INTEGER NOT NULL, " +
                    "`deadLoad` REAL NOT NULL, `liveLoad` REAL NOT NULL, " +
                    "`fcu` REAL NOT NULL, `fy` REAL NOT NULL, " +
                    "`columnStripWidthX` REAL NOT NULL, `columnStripWidthY` REAL NOT NULL, " +
                    "`middleStripWidthX` REAL NOT NULL, `middleStripWidthY` REAL NOT NULL, " +
                    "`negMomentColStripX` REAL NOT NULL, `posMomentColStripX` REAL NOT NULL, " +
                    "`negMomentMidStripX` REAL NOT NULL, `posMomentMidStripX` REAL NOT NULL, " +
                    "`negMomentColStripY` REAL NOT NULL, `posMomentColStripY` REAL NOT NULL, " +
                    "`negMomentMidStripY` REAL NOT NULL, `posMomentMidStripY` REAL NOT NULL, " +
                    "`topReinColStripX` TEXT NOT NULL, `botReinColStripX` TEXT NOT NULL, " +
                    "`topReinMidStripX` TEXT NOT NULL, `botReinMidStripX` TEXT NOT NULL, " +
                    "`topReinColStripY` TEXT NOT NULL, `botReinColStripY` TEXT NOT NULL, " +
                    "`topReinMidStripY` TEXT NOT NULL, `botReinMidStripY` TEXT NOT NULL, " +
                    "`punchingShearStress` REAL NOT NULL, `punchingShearCapacity` REAL NOT NULL, " +
                    "`isPunchingSafe` INTEGER NOT NULL, `deflection` REAL NOT NULL, " +
                    "`allowableDeflection` REAL NOT NULL, `isDeflectionSafe` INTEGER NOT NULL, " +
                    "`isSafe` INTEGER NOT NULL, `utilizationRatio` REAL NOT NULL, " +
                    "`concreteVolume` REAL NOT NULL, `steelWeight` REAL NOT NULL, " +
                    "`totalCost` REAL NOT NULL, `codeUsed` TEXT NOT NULL, " +
                    "`inputDataJson` TEXT NOT NULL, `resultsJson` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_flat_slabs_projectId` ON `flat_slabs` (`projectId`)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `pile_foundations` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`projectId` INTEGER NOT NULL, `pileType` TEXT NOT NULL, " +
                    "`pileDiameter` REAL NOT NULL, `pileLength` REAL NOT NULL, " +
                    "`numberOfPiles` INTEGER NOT NULL, `axialLoad` REAL NOT NULL, " +
                    "`lateralLoad` REAL NOT NULL, `momentLoad` REAL NOT NULL, " +
                    "`fcu` REAL NOT NULL, `fy` REAL NOT NULL, `soilType` TEXT NOT NULL, " +
                    "`cu` REAL NOT NULL, `phi` REAL NOT NULL, " +
                    "`ultimateCapacity` REAL NOT NULL, `allowableCapacity` REAL NOT NULL, " +
                    "`shaftResistance` REAL NOT NULL, `endBearingResistance` REAL NOT NULL, " +
                    "`settlement` REAL NOT NULL, `isSafe` INTEGER NOT NULL, " +
                    "`utilizationRatio` REAL NOT NULL, `concreteVolume` REAL NOT NULL, " +
                    "`steelWeight` REAL NOT NULL, `totalCost` REAL NOT NULL, " +
                    "`codeUsed` TEXT NOT NULL, `inputDataJson` TEXT NOT NULL, " +
                    "`resultsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pile_foundations_projectId` ON `pile_foundations` (`projectId`)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `shear_walls` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`projectId` INTEGER NOT NULL, `wallLength` REAL NOT NULL, " +
                    "`wallHeight` REAL NOT NULL, `wallThickness` REAL NOT NULL, " +
                    "`wallType` TEXT NOT NULL, `hasBoundaryElement` INTEGER NOT NULL, " +
                    "`beLength` REAL NOT NULL, `beThickness` REAL NOT NULL, " +
                    "`axialLoad` REAL NOT NULL, `shearForce` REAL NOT NULL, " +
                    "`bendingMoment` REAL NOT NULL, `fcu` REAL NOT NULL, `fy` REAL NOT NULL, " +
                    "`verticalReinforcement` TEXT NOT NULL, `horizontalReinforcement` TEXT NOT NULL, " +
                    "`boundaryVerticalReinforcement` TEXT NOT NULL, " +
                    "`boundaryTransverseReinforcement` TEXT NOT NULL, " +
                    "`webVerticalReinRatio` REAL NOT NULL, `webHorizontalReinRatio` REAL NOT NULL, " +
                    "`concreteShearCapacity` REAL NOT NULL, `steelShearCapacity` REAL NOT NULL, " +
                    "`totalShearCapacity` REAL NOT NULL, `appliedShearStress` REAL NOT NULL, " +
                    "`isShearSafe` INTEGER NOT NULL, `axialCapacity` REAL NOT NULL, " +
                    "`momentCapacity` REAL NOT NULL, `isFlexureSafe` INTEGER NOT NULL, " +
                    "`driftRatio` REAL NOT NULL, `allowableDriftRatio` REAL NOT NULL, " +
                    "`isDriftSafe` INTEGER NOT NULL, `isSafe` INTEGER NOT NULL, " +
                    "`utilizationRatio` REAL NOT NULL, `concreteVolume` REAL NOT NULL, " +
                    "`steelWeight` REAL NOT NULL, `totalCost` REAL NOT NULL, " +
                    "`codeUsed` TEXT NOT NULL, `inputDataJson` TEXT NOT NULL, " +
                    "`resultsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_shear_walls_projectId` ON `shear_walls` (`projectId`)"
            )
        }
    }
    val MIGRATION_7_8 = Migration7To8()

    class DynamicNoOpMigration(from: Int, to: Int) : Migration(from, to) {
        override fun migrate(database: SupportSQLiteDatabase) {
        }
    }

    fun noOpMigration(from: Int, to: Int): Migration = DynamicNoOpMigration(from, to)
}
