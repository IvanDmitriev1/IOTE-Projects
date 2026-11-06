package com.example.remoteweatherstation.core.data.cloud

import com.example.remoteweatherstation.core.model.PressureUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpThingSpeakReader @Inject constructor(
    private val parser: ThingSpeakFeedParser,
    private val forecastParser: ForecastFeedParser,
) : ThingSpeakReader {
    override suspend fun readTail(config: ThingSpeakConfig, readKey: String): CloudFetchResult {
        val response = fetch(config.channelId, readKey, listOf("results" to TAIL_RESULTS.toString()))
        if (response.failure != null) return CloudFetchResult(failure = response.failure)
        val parsed = parser.parse(response.body.orEmpty(), config.pressureUnit)
        if (parsed.invalidRows > 0 && parsed.entries.isEmpty()) return CloudFetchResult(failure = CloudReadFailure.INVALID_RESPONSE)
        return CloudFetchResult(parsed.entries)
    }

    override suspend fun readForecast(channelId: String, readKey: String): ForecastFetchResult {
        val response = fetch(channelId, readKey, listOf("results" to "1"))
        if (response.failure != null) return ForecastFetchResult(failure = response.failure)
        return when (val result = forecastParser.parse(response.body.orEmpty())) {
            ForecastParseResult.Empty -> ForecastFetchResult()
            ForecastParseResult.Invalid -> ForecastFetchResult(failure = CloudReadFailure.INVALID_RESPONSE)
            is ForecastParseResult.Reading -> ForecastFetchResult(result.value)
        }
    }

    private suspend fun fetch(
        channelId: String,
        readKey: String,
        extra: List<Pair<String, String>>,
    ): HttpResponse = withContext(Dispatchers.IO) {
        if (channelId.isBlank() || !channelId.all(Char::isDigit)) {
            return@withContext HttpResponse(failure = CloudReadFailure.NOT_CONFIGURED)
        }
        var connection: HttpURLConnection? = null
        try {
            val parameters = buildList {
                if (readKey.isNotBlank()) add("api_key" to readKey)
                addAll(extra)
            }.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
            val url = URL("https://api.thingspeak.com/channels/$channelId/feeds.json?$parameters")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            val status = connection.responseCode
            if (status !in 200..299) return@withContext HttpResponse(failure = CloudReadFailure.HTTP)
            val stream = connection.inputStream
            val bytes = stream.use { readBounded(it, MAX_RESPONSE_BYTES + 1) }
            if (bytes.size > MAX_RESPONSE_BYTES) return@withContext HttpResponse(failure = CloudReadFailure.RESPONSE_TOO_LARGE)
            HttpResponse(String(bytes, StandardCharsets.UTF_8))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            HttpResponse(failure = CloudReadFailure.NETWORK)
        } finally {
            connection?.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun readBounded(stream: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            if (output.size() >= limit) break
        }
        return output.toByteArray()
    }

    private data class HttpResponse(val body: String? = null, val failure: CloudReadFailure? = null)

    private companion object {
        const val TIMEOUT_MILLIS = 8_000
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val TAIL_RESULTS = 20
    }
}
