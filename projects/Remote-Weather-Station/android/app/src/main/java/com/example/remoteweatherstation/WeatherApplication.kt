package com.example.remoteweatherstation

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.remoteweatherstation.core.data.ForegroundWeatherSourceOwner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WeatherApplication : Application() {
    @Inject lateinit var sourceOwner: ForegroundWeatherSourceOwner

    override fun onCreate() {
        super.onCreate()
        sourceOwner.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(sourceOwner)
    }
}
