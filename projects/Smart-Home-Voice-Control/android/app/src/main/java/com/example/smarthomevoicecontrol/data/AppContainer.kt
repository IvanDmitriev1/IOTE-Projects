package com.example.smarthomevoicecontrol.data

import android.content.Context

class AppContainer(context: Context) {
    val configStore = PiConfigStore.create(context)
    val piClient = PiClient()
    val textToColorModel = TextToColorModel(context)
}
