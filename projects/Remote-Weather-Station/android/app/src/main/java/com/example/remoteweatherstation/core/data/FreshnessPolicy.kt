package com.example.remoteweatherstation.core.data

import com.example.remoteweatherstation.core.model.HistorySource
import com.example.remoteweatherstation.core.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class ReadingStatus { FRESH, STALE, UNKNOWN }

@Singleton
class FreshnessPolicy @Inject constructor(private val clock: Clock) {
    fun local(
        retained: Boolean,
        processSessionId: String,
        currentSessionId: String,
        receiptElapsedMillis: Long,
    ): ReadingStatus {
        if (retained || processSessionId != currentSessionId) return ReadingStatus.STALE
        val age = clock.elapsedMillis() - receiptElapsedMillis
        return if (age in 0 until STALE_AFTER_MILLIS) ReadingStatus.FRESH else ReadingStatus.STALE
    }

    fun cloud(createdAt: Instant): ReadingStatus {
        val age = clock.now().toEpochMilli() - createdAt.toEpochMilli()
        if (age < -FUTURE_TOLERANCE_MILLIS) return ReadingStatus.UNKNOWN
        return if (age in 0 until STALE_AFTER_MILLIS) ReadingStatus.FRESH else ReadingStatus.STALE
    }

    companion object {
        const val STALE_AFTER_MILLIS = 90_000L
        const val FUTURE_TOLERANCE_MILLIS = 30_000L
    }
}

data class MetricReading(
    val value: Double,
    val source: HistorySource,
    val observedAt: Instant,
    val status: ReadingStatus,
    val retained: Boolean = false,
)
