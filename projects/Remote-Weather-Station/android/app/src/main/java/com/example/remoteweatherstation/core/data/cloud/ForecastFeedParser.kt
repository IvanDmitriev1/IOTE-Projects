package com.example.remoteweatherstation.core.data.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class ForecastReading(val text: String, val createdAt: Instant)

sealed interface ForecastParseResult {
    data object Empty : ForecastParseResult
    data class Reading(val value: ForecastReading) : ForecastParseResult
    data object Invalid : ForecastParseResult
}

data class ForecastFetchResult(
    val reading: ForecastReading? = null,
    val failure: CloudReadFailure? = null,
)

@Singleton
class ForecastFeedParser @Inject constructor() {
    private val json = Json { isLenient = false }

    fun parse(body: String): ForecastParseResult {
        val root = try { json.parseToJsonElement(body) as? JsonObject } catch (_: Exception) { null }
            ?: return ForecastParseResult.Invalid
        val feeds = root["feeds"] as? JsonArray ?: return ForecastParseResult.Invalid
        val row = feeds.firstOrNull() as? JsonObject ?: return if (feeds.isEmpty()) ForecastParseResult.Empty else ForecastParseResult.Invalid
        val text = (row["field1"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
            ?: return ForecastParseResult.Invalid
        val createdAt = (row["created_at"] as? JsonPrimitive)?.contentOrNull
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return ForecastParseResult.Invalid
        return ForecastParseResult.Reading(ForecastReading(text, createdAt))
    }
}
