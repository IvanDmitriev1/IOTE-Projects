package com.example.remoteweatherstation.core.data.mqtt

import com.example.remoteweatherstation.core.model.StationConfig
import com.example.remoteweatherstation.core.model.StationSecrets
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class MqttConfig(
    val host: String,
    val port: Int,
    val tls: Boolean,
    val topic: String,
)

sealed interface MqttEvent {
    data class Message(
        val payload: ByteArray,
        val retained: Boolean,
        val receivedAt: Instant,
        val elapsedMillis: Long,
    ) : MqttEvent
    data object Disconnected : MqttEvent
}

interface MqttTransport {
    val events: Flow<MqttEvent>
    suspend fun connect(config: MqttConfig, secrets: StationSecrets)
    suspend fun disconnect()
}

class MqttAuthenticationException : Exception("Broker rejected the configured credentials")

fun interface MqttTransportFactory {
    fun create(): MqttTransport
}

fun StationConfig.toMqttConfig() = MqttConfig(brokerHost, brokerPort, useTls, topic)
