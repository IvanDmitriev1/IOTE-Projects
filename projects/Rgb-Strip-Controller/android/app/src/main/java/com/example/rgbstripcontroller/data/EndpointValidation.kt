package com.example.rgbstripcontroller.data

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

fun isValidEndpoint(value: String): Boolean {
    val candidate = value.trim()
    if (candidate.isEmpty() || candidate.any {
            Character.isWhitespace(it) || Character.isSpaceChar(it) || Character.isISOControl(it)
        }) {
        return false
    }

    val uri = try {
        URI(candidate)
    } catch (_: URISyntaxException) {
        return false
    }
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val port = uri.port
    return uri.isAbsolute && (scheme == "http" || scheme == "https") &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null &&
        (port == -1 || port in 1..65535) && uri.rawAuthority?.endsWith(':') != true &&
        (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") && uri.rawQuery == null
}

