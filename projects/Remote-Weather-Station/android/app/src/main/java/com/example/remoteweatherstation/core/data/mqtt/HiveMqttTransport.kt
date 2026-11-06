package com.example.remoteweatherstation.core.data.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth
import com.example.remoteweatherstation.core.model.StationSecrets
import com.example.remoteweatherstation.core.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiveMqttTransportFactory @Inject constructor(private val clock: Clock) : MqttTransportFactory {
    override fun create(): MqttTransport = HiveMqttTransport(clock)
}

private class HiveMqttTransport(private val clock: Clock) : MqttTransport {
    private val mutableEvents = MutableSharedFlow<MqttEvent>(replay = 32, extraBufferCapacity = 64)
    override val events: Flow<MqttEvent> = mutableEvents
    @Volatile private var client: Mqtt5AsyncClient? = null

    override suspend fun connect(config: MqttConfig, secrets: StationSecrets) = withContext(Dispatchers.IO) {
        disconnect()
        val builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier("weather-${UUID.randomUUID()}")
            .serverHost(config.host)
            .serverPort(config.port)
            .addDisconnectedListener { mutableEvents.tryEmit(MqttEvent.Disconnected) }
        val transportBuilder = if (config.tls) builder.sslWithDefaultConfig() else builder
        val created = transportBuilder.buildAsync()
        client = created

        val connectBuilder = created.connectWith().cleanStart(true).keepAlive(30)
        val authenticatedConnectBuilder = if (secrets.mqttUsername.isNotBlank() || secrets.mqttPassword.isNotBlank()) {
            val auth: Mqtt5SimpleAuth = when {
                secrets.mqttUsername.isNotBlank() && secrets.mqttPassword.isNotBlank() -> Mqtt5SimpleAuth.builder()
                    .username(secrets.mqttUsername)
                    .password(secrets.mqttPassword.toByteArray(Charsets.UTF_8))
                    .build()
                secrets.mqttUsername.isNotBlank() -> Mqtt5SimpleAuth.builder().username(secrets.mqttUsername).build()
                else -> Mqtt5SimpleAuth.builder().password(secrets.mqttPassword.toByteArray(Charsets.UTF_8)).build()
            }
            connectBuilder.simpleAuth(auth)
        } else connectBuilder
        try {
            authenticatedConnectBuilder.send().get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            created.subscribeWith()
                .topicFilter(config.topic)
                .qos(MqttQos.AT_MOST_ONCE)
                .callback { publish ->
                    mutableEvents.tryEmit(MqttEvent.Message(
                        publish.payloadAsBytes,
                        publish.isRetain,
                        clock.now(),
                        clock.elapsedMillis(),
                    ))
                }
                .send()
                .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            Unit
        } catch (exception: ExecutionException) {
            val cause = exception.cause
            if (cause is Mqtt5ConnAckException && cause.mqttMessage.reasonCode.name in AUTH_REJECT_REASONS) {
                throw MqttAuthenticationException()
            }
            throw exception
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        val old = client
        client = null
        if (old != null) runCatching { old.disconnect().get(DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val DISCONNECT_TIMEOUT_SECONDS = 3L
        val AUTH_REJECT_REASONS = setOf("BAD_USER_NAME_OR_PASSWORD", "NOT_AUTHORIZED")
    }
}
