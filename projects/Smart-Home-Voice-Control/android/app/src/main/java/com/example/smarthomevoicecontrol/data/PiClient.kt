package com.example.smarthomevoicecontrol.data

import com.example.smarthomevoicecontrol.core.LightColor
import com.example.smarthomevoicecontrol.core.PiConfig
import com.example.smarthomevoicecontrol.core.ReadingKind
import com.example.smarthomevoicecontrol.core.SensorReading
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PiClient(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun read(config: PiConfig, kind: ReadingKind): Result<SensorReading> = withContext(ioDispatcher) {
        request(config, "/${kind.route}").mapCatching { parseReading(kind, it) }
    }

    suspend fun setLights(config: PiConfig, color: LightColor): Result<Unit> = withContext(ioDispatcher) {
        val route = "/${color.route}"
        request(config, route).mapCatching { echoed ->
            validateLightEcho(route, echoed)
        }
    }

    suspend fun setLights(config: PiConfig, hex: String): Result<Unit> = withContext(ioDispatcher) {
        require(Regex("[0-9A-Fa-f]{6}").matches(hex)) { "RGB color must be six hexadecimal digits." }
        val route = "/${hex.uppercase(java.util.Locale.ROOT)}"
        request(config, route).mapCatching { echoed -> validateLightEcho(route, echoed) }
    }

    internal fun parseReading(kind: ReadingKind, response: String): SensorReading {
        val body = response.trim()
        if (kind == ReadingKind.WEATHER) {
            val outlook = parseOutlook(body)
            return SensorReading(kind, outlook.first, outlook.second)
        }
        require(Regex("[+-]?\\d+").matches(body)) { "The Pi returned an invalid ${kind.label.lowercase()} value." }
        val value = if (kind == ReadingKind.HUMIDITY) "$body%" else "$body ${kind.unit}"
        return SensorReading(kind, value, value)
    }

    internal fun validateLightEcho(path: String, response: String) {
        require(response.trim() == path) { "Couldn't confirm the light command." }
    }

    private fun parseOutlook(value: String): Pair<String, String> {
        val normalized = value.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', '!')
        return when (normalized) {
            "the weather will be stable" -> "Stable" to "The weather will be stable"
            "the weather will be cloudy" -> "Cloudy" to "The weather will be cloudy"
            "the weather will be rainy" -> "Rainy" to "The weather will be rainy"
            else -> error("The Pi returned an unknown pressure-based outlook.")
        }
    }

    private fun request(config: PiConfig, path: String): Result<String> {
        config.validationError()?.let { return Result.failure(IllegalArgumentException(it)) }
        var connection: HttpURLConnection? = null
        return try {
            val uri = URI("http", null, config.address, config.port, path, null, null)
            connection = uri.toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
            connection.requestMethod = "GET"
            connection.doOutput = false
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            val status = connection.responseCode
            val stream = if (status == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream
            val body = stream?.use { readBounded(it) }.orEmpty().trim()
            require(status == HttpURLConnection.HTTP_OK) { "The Pi returned HTTP $status." }
            Result.success(body)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(128)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 256) { "The Pi returned an oversized response." }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
