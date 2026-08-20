package com.civileg.app.di

import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.calculations.base.SlabDesign
import com.civileg.app.domain.calculations.base.TankDesign
import com.civileg.app.domain.calculations.ecp.ECPColumn
import com.civileg.app.domain.calculations.aci.ACIColumn
import com.civileg.app.domain.calculations.ecp.ECPSlab
import com.civileg.app.domain.calculations.ecp.ECPTank
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CalculationModule {

    @Provides
    @Singleton
    @Named("ECPColumn")
    fun provideECPColumn(): ColumnDesign = ECPColumn()

    @Provides
    @Singleton
    @Named("ACIColumn")
    fun provideACIColumn(): ColumnDesign = ACIColumn()

    @Provides
    @Singleton
    @Named("ECPSlab")
    fun provideECPSlab(): SlabDesign = ECPSlab()

    @Provides
    @Singleton
    @Named("ECPTank")
    fun provideECPTank(): TankDesign = ECPTank()
}
