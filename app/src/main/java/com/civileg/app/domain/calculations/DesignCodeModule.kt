package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.calculations.aci.ACIColumn
import com.civileg.app.domain.calculations.ecp.ECPColumn
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
    fun provideSBCColumnDesign(): ColumnDesign = ACIColumn() // Placeholder for SBC
}
