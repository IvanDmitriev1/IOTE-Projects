package com.example.rgbstripcontroller.data

import com.example.rgbstripcontroller.data.model.LightState
import com.example.rgbstripcontroller.data.model.RainbowDirection
import com.example.rgbstripcontroller.di.IoDispatcher
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val REQUEST_TIMEOUT_MS = 5_000

class NetworkLightService @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LightService {
    override suspend fun applyState(endpointUrl: String, state: LightState): Result<Unit> =
        request(endpointUrl, "rgb", "r=${if (state.isOn) state.red else 0}&g=${if (state.isOn) state.green else 0}&b=${if (state.isOn) state.blue else 0}")

    override suspend fun setRainbow(endpointUrl: String, direction: RainbowDirection): Result<Unit> =
        request(endpointUrl, "rainbow", "direction=${direction.queryValue()}")

    private fun RainbowDirection.queryValue(): Int = when (this) {
        RainbowDirection.FORWARD -> 1
        RainbowDirection.REVERSE -> 2
    }

    private suspend fun request(baseUrl: String, path: String, query: String): Result<Unit> {
        if (!isValidEndpoint(baseUrl))
            return Result.failure(IllegalArgumentException("Invalid controller base address."))

        return withContext(ioDispatcher) {
            var connection: HttpURLConnection? = null
            try {
                val base = URI(baseUrl.trim())
                val uri = URI(base.scheme, null, base.host, base.port, "/$path", query, null)
                connection = uri.toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.doOutput = false
                connection.useCaches = false
                connection.instanceFollowRedirects = false
                connection.connectTimeout = REQUEST_TIMEOUT_MS
                connection.readTimeout = REQUEST_TIMEOUT_MS
                val code = connection.responseCode
                (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { }
                if (code !in 200..299) error("Controller returned HTTP $code.")
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                connection?.disconnect()
            }
        }
    }
}
