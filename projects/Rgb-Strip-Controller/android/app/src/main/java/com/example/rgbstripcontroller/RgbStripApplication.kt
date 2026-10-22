package com.example.rgbstripcontroller

import android.app.Application
import com.example.rgbstripcontroller.data.LightService
import com.example.rgbstripcontroller.data.NetworkLightService
import com.example.rgbstripcontroller.di.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@HiltAndroidApp
class RgbStripApplication : Application() {
    @Module
    @InstallIn(ViewModelComponent::class)
    abstract class ControllerModule {
        @Binds
        @ViewModelScoped
        abstract fun bindLightService(service: NetworkLightService): LightService

        companion object {
            @Provides
            @IoDispatcher
            fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
        }
    }
}
