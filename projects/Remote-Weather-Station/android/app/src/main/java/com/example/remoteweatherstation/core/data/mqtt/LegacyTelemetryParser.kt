package com.example.remoteweatherstation.core.data.mqtt

import com.example.remoteweatherstation.core.model.Metric
import com.example.remoteweatherstation.core.model.MetricValues
import com.example.remoteweatherstation.core.model.ParsedTelemetry
import com.example.remoteweatherstation.core.model.PressureUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyTelemetryParser @Inject constructor() {
    private val json = Json { isLenient = false }

    fun parse(payload: ByteArray, pressureUnit: PressureUnit): ParsedTelemetry {
        if (payload.size > MAX_PAYLOAD_BYTES) {
            return ParsedTelemetry(MetricValues(), Metric.entries.toSet(), "Payload exceeds 16 KiB limit")
        }
        val root = try {
            json.parseToJsonElement(payload.decodeToString()) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return ParsedTelemetry(MetricValues(), Metric.entries.toSet(), "Invalid telemetry JSON")

        val invalid = linkedSetOf<Metric>()
        fun value(metric: Metric): Double? {
            val element = root[metric.wireKey]
            val parsed = element.asFiniteDouble()
            val accepted = when (metric) {
                Metric.TEMPERATURE -> parsed
                Metric.HUMIDITY -> parsed?.takeIf { it in 0.0..100.0 }
                Metric.PRESSURE -> parsed?.takeIf { it > 0.0 }?.let(pressureUnit::toHectopascals)
                Metric.LIGHT -> parsed?.takeIf { it >= 0.0 }
            }
            if (accepted == null || !accepted.isFinite()) invalid += metric
            return accepted?.takeIf(Double::isFinite)
        }

        val values = MetricValues(
            temperatureC = value(Metric.TEMPERATURE),
            humidityPercent = value(Metric.HUMIDITY),
            pressureHpa = value(Metric.PRESSURE),
            lightSensorUnits = value(Metric.LIGHT),
        )
        return ParsedTelemetry(
            values = values,
            invalidFields = invalid,
            diagnostic = if (invalid.isNotEmpty()) "Some fields were missing or invalid" else null,
        )
    }

    private fun JsonElement?.asFiniteDouble(): Double? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString && primitive.contentOrNull == null) return null
        val parsed = primitive.doubleOrNull ?: return null
        return parsed.takeIf(Double::isFinite)
    }

    private companion object {
        const val MAX_PAYLOAD_BYTES = 16 * 1024
    }
}
