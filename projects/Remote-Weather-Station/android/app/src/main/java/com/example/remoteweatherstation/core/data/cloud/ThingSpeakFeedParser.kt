package com.example.remoteweatherstation.core.data.cloud

import com.example.remoteweatherstation.core.data.CloudEntry
import com.example.remoteweatherstation.core.model.MetricValues
import com.example.remoteweatherstation.core.model.PressureUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedFeed(val entries: List<CloudEntry>, val invalidRows: Int, val feedCount: Int)

@Singleton
class ThingSpeakFeedParser @Inject constructor() {
    private val json = Json { isLenient = false }

    fun parse(body: String, pressureUnit: PressureUnit): ParsedFeed {
        val root = try { json.parseToJsonElement(body) as? JsonObject } catch (_: Exception) { null }
            ?: return ParsedFeed(emptyList(), 1, 0)
        val feeds = root["feeds"] as? JsonArray ?: return ParsedFeed(emptyList(), 1, 0)
        var invalidRows = 0
        val entries = feeds.mapNotNull { element ->
            val row = element as? JsonObject
            val id = (row?.get("entry_id") as? JsonPrimitive)?.longOrNull
            val created = (row?.get("created_at") as? JsonPrimitive)?.content
                ?.let { runCatching { Instant.parse(it).also(Instant::toEpochMilli) }.getOrNull() }
            if (row == null || id == null || id <= 0L || created == null) {
                invalidRows++
                return@mapNotNull null
            }
            fun field(number: Int): Double? {
                val primitive = row["field$number"] as? JsonPrimitive ?: return null
                val value = primitive.doubleOrNull?.takeIf(Double::isFinite) ?: return null
                val accepted = when (number) {
                    1 -> value
                    2 -> value.takeIf { it in 0.0..100.0 }
                    3 -> value.takeIf { it > 0.0 }?.let(pressureUnit::toHectopascals)
                    4 -> value.takeIf { it >= 0.0 }
                    else -> null
                }
                return accepted?.takeIf(Double::isFinite)
            }
            val values = MetricValues(field(1), field(2), field(3), field(4))
            if (values.isEmpty()) null else CloudEntry(id, created, values)
        }
        return ParsedFeed(entries.distinctBy(CloudEntry::entryId), invalidRows, feeds.size)
    }
}
