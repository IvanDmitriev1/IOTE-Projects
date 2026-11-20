package com.example.smarthomevoicecontrol

import android.app.Application
import com.example.smarthomevoicecontrol.data.AppContainer

class SmartHomeApplication : Application() {
    val container by lazy { AppContainer(this) }
}
