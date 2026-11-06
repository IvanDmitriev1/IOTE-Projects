package com.example.remoteweatherstation

import com.example.remoteweatherstation.core.data.InMemoryTelemetryRepository
import com.example.remoteweatherstation.core.data.TelemetryRepository
import com.example.remoteweatherstation.core.data.config.DataStoreStationConfigStore
import com.example.remoteweatherstation.core.data.config.StationConfigStore
import com.example.remoteweatherstation.core.data.cloud.HttpThingSpeakReader
import com.example.remoteweatherstation.core.data.cloud.ThingSpeakReader
import com.example.remoteweatherstation.core.data.mqtt.HiveMqttTransportFactory
import com.example.remoteweatherstation.core.data.mqtt.MqttTransportFactory
import com.example.remoteweatherstation.core.time.Clock
import com.example.remoteweatherstation.core.time.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds @Singleton abstract fun bindClock(implementation: SystemClock): Clock
    @Binds @Singleton abstract fun bindTelemetryRepository(implementation: InMemoryTelemetryRepository): TelemetryRepository
    @Binds @Singleton abstract fun bindConfigStore(implementation: DataStoreStationConfigStore): StationConfigStore
    @Binds @Singleton abstract fun bindMqttFactory(implementation: HiveMqttTransportFactory): MqttTransportFactory
    @Binds @Singleton abstract fun bindThingSpeakReader(implementation: HttpThingSpeakReader): ThingSpeakReader
}
