package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.calculations.aci.ACIColumn
import com.civileg.app.domain.calculations.aci.ACIBeam
import com.civileg.app.domain.calculations.aci.ACISlab
import com.civileg.app.domain.calculations.aci.ACITank
import com.civileg.app.domain.calculations.aci.ACIFooting
import com.civileg.app.domain.calculations.ecp.ECPColumn
import com.civileg.app.domain.calculations.ecp.ECPBeam
import com.civileg.app.domain.calculations.ecp.ECPSlab
import com.civileg.app.domain.calculations.ecp.ECPTank
import com.civileg.app.domain.calculations.ecp.ECPFooting
import com.civileg.app.domain.calculations.sbc.SBCColumn
import com.civileg.app.domain.calculations.sbc.SBCBeam
import com.civileg.app.domain.calculations.sbc.SBCSlab
import com.civileg.app.domain.calculations.sbc.SBCTank
import com.civileg.app.domain.calculations.sbc.SBCFooting
import com.civileg.app.domain.entities.DesignCode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/**
 * NOTE: This module is currently unused by Hilt.
 * The active DI module is at com.civileg.app.di.DesignCodeModule.
 * This file is retained for reference; rename to avoid class name collision.
 */
@Deprecated("Unused by Hilt. See com.civileg.app.di.DesignCodeModule for the active module.")
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DesignCodeQualifier(val code: DesignCode)

@Deprecated("Unused by Hilt. See com.civileg.app.di.DesignCodeModule for the active module.")
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
object LegacyDesignCodeModule {

    @Provides
    @DesignCodeQualifier(DesignCode.ECP)
    fun provideECPColumnDesign(): ColumnDesign = ECPColumn()

    @Provides
    @DesignCodeQualifier(DesignCode.ACI)
    fun provideACIColumnDesign(): ColumnDesign = ACIColumn()

    @Provides
    @DesignCodeQualifier(DesignCode.SBC)
    fun provideSBCColumnDesign(): ColumnDesign = SBCColumn()

    @Provides
    @DesignCodeQualifier(DesignCode.ECP)
    fun provideECPBeamDesign(): BeamDesign = ECPBeam()

    @Provides
    @DesignCodeQualifier(DesignCode.ACI)
    fun provideACIBeamDesign(): BeamDesign = ACIBeam()

    @Provides
    @DesignCodeQualifier(DesignCode.SBC)
    fun provideSBCBeamDesign(): BeamDesign = SBCBeam()

    @Provides
    @DesignCodeQualifier(DesignCode.ECP)
    fun provideECPSlabDesign(): SlabDesign = ECPSlab()

    @Provides
    @DesignCodeQualifier(DesignCode.ACI)
    fun provideACISlabDesign(): SlabDesign = ACISlab()

    @Provides
    @DesignCodeQualifier(DesignCode.SBC)
    fun provideSBCSlabDesign(): SlabDesign = SBCSlab()

    @Provides
    @DesignCodeQualifier(DesignCode.ECP)
    fun provideECPTankDesign(): TankDesign = ECPTank()

    @Provides
    @DesignCodeQualifier(DesignCode.ACI)
    fun provideACITankDesign(): TankDesign = ACITank()

    @Provides
    @DesignCodeQualifier(DesignCode.SBC)
    fun provideSBCTankDesign(): TankDesign = SBCTank()

    @Provides
    @DesignCodeQualifier(DesignCode.ECP)
    fun provideECPFootingDesign(): FootingDesign = ECPFooting()

    @Provides
    @DesignCodeQualifier(DesignCode.ACI)
    fun provideACIFootingDesign(): FootingDesign = ACIFooting()

    @Provides
    @DesignCodeQualifier(DesignCode.SBC)
    fun provideSBCFootingDesign(): FootingDesign = SBCFooting()
}
