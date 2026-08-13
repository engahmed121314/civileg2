package com.civileg.app.di

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.calculations.ecp.ECPColumn
import com.civileg.app.domain.calculations.aci.ACIColumn
import com.civileg.app.domain.calculations.sbc.SBCColumn
import com.civileg.app.domain.entities.DesignCode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DesignCodeModule {
    
    @Provides
    @IntoMap
    @StringKey("ECP")
    fun provideECPColumn(): ColumnDesign = ECPColumn()
    
    @Provides
    @IntoMap
    @StringKey("ACI")
    fun provideACIColumn(): ColumnDesign = ACIColumn()
    
    @Provides
    @IntoMap
    @StringKey("SBC")
    fun provideSBCColumn(): ColumnDesign = SBCColumn()
    
    @Provides
    @Singleton
    fun provideColumnDesigns(
        designs: @JvmSuppressWildcards Map<String, ColumnDesign>
    ): Map<DesignCode, ColumnDesign> {
        return designs.mapKeys { DesignCode.valueOf(it.key) }
    }
}
