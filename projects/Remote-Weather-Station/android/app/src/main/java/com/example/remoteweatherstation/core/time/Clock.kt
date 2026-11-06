package com.example.remoteweatherstation.core.time

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface Clock {
    fun now(): Instant
    fun elapsedMillis(): Long
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun now(): Instant = Instant.now()
    override fun elapsedMillis(): Long = android.os.SystemClock.elapsedRealtime()
}
