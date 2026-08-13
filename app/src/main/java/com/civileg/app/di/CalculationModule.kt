package com.civileg.app.di

import com.civileg.app.domain.calculations.*
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.calculations.ecp.*
import com.civileg.app.domain.calculations.aci.*
import com.civileg.app.domain.calculations.sbc.*
import com.civileg.app.domain.entities.DesignCode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CalculationModule {

    @Provides @Singleton
    fun provideColumnDesignMap(): Map<DesignCode, ColumnDesign> = mapOf(
        DesignCode.ECP to ECPColumn(),
        DesignCode.ACI to ACIColumn(),
        DesignCode.SBC to SBCColumn()
    )

    @Provides @Singleton
    fun provideBeamDesignMap(): Map<DesignCode, BeamDesign> = mapOf(
        DesignCode.ECP to ECPBeam(),
        DesignCode.ACI to ACIBeam(),
        DesignCode.SBC to SBCBeam()
    )

    @Provides @Singleton
    fun provideSlabDesignMap(): Map<DesignCode, SlabDesign> = mapOf(
        DesignCode.ECP to ECPSlab(),
        DesignCode.ACI to ACISlab(),
        DesignCode.SBC to SBCSlab()
    )

    @Provides @Singleton
    fun provideFootingDesignMap(): Map<DesignCode, FootingDesign> = mapOf(
        DesignCode.ECP to ECPFooting(),
        DesignCode.ACI to ACIFooting(),
        DesignCode.SBC to SBCFooting()
    )

    @Provides @Singleton
    fun provideTankDesignMap(): Map<DesignCode, TankDesign> = mapOf(
        DesignCode.ECP to ECPTank(),
        DesignCode.ACI to ACITank(),
        DesignCode.SBC to SBCTank()
    )

    @Provides @Singleton
    fun provideRetainingWallDesignMap(): Map<DesignCode, RetainingWallDesign> = mapOf(
        DesignCode.ECP to ECPRetainingWall(),
        DesignCode.ACI to ACIRetainingWall(),
        DesignCode.SBC to SBCRetainingWall()
    )

    @Provides @Singleton
    fun provideStaircaseDesignMap(): Map<DesignCode, StaircaseDesign> = mapOf(
        DesignCode.ECP to ECPStaircase(),
        DesignCode.ACI to ACIStaircase(),
        DesignCode.SBC to SBCStaircase()
    )
}
