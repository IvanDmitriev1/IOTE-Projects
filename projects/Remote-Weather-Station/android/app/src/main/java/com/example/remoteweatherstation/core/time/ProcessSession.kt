package com.example.remoteweatherstation.core.time

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessSession @Inject constructor() {
    val id: String = UUID.randomUUID().toString()
}
