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

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DesignCodeType(val code: DesignCode)

@Module
@InstallIn(SingletonComponent::class)
object DesignCodeModule {

    @Provides
    @DesignCodeType(DesignCode.ECP)
    fun provideECPColumnDesign(): ColumnDesign = ECPColumn()

    @Provides
    @DesignCodeType(DesignCode.ACI)
    fun provideACIColumnDesign(): ColumnDesign = ACIColumn()

    @Provides
    @DesignCodeType(DesignCode.SBC)
    fun provideSBCColumnDesign(): ColumnDesign = SBCColumn()

    @Provides
    @DesignCodeType(DesignCode.ECP)
    fun provideECPBeamDesign(): BeamDesign = ECPBeam()

    @Provides
    @DesignCodeType(DesignCode.ACI)
    fun provideACIBeamDesign(): BeamDesign = ACIBeam()

    @Provides
    @DesignCodeType(DesignCode.SBC)
    fun provideSBCBeamDesign(): BeamDesign = SBCBeam()

    @Provides
    @DesignCodeType(DesignCode.ECP)
    fun provideECPSlabDesign(): SlabDesign = ECPSlab()

    @Provides
    @DesignCodeType(DesignCode.ACI)
    fun provideACISlabDesign(): SlabDesign = ACISlab()

    @Provides
    @DesignCodeType(DesignCode.SBC)
    fun provideSBCSlabDesign(): SlabDesign = SBCSlab()

    @Provides
    @DesignCodeType(DesignCode.ECP)
    fun provideECPTankDesign(): TankDesign = ECPTank()

    @Provides
    @DesignCodeType(DesignCode.ACI)
    fun provideACITankDesign(): TankDesign = ACITank()

    @Provides
    @DesignCodeType(DesignCode.SBC)
    fun provideSBCTankDesign(): TankDesign = SBCTank()

    @Provides
    @DesignCodeType(DesignCode.ECP)
    fun provideECPFootingDesign(): FootingDesign = ECPFooting()

    @Provides
    @DesignCodeType(DesignCode.ACI)
    fun provideACIFootingDesign(): FootingDesign = ACIFooting()

    @Provides
    @DesignCodeType(DesignCode.SBC)
    fun provideSBCFootingDesign(): FootingDesign = SBCFooting()
}
