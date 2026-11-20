package com.example.smarthomevoicecontrol.core

data class PiConfig(val address: String = "", val port: Int = 8000) {
    fun validationError(): String? = when {
        !isPrivateIpv4(address) -> "Enter a private IPv4 address (10.x.x.x, 172.16–31.x.x, or 192.168.x.x)."
        port !in 1..65535 -> "Port must be between 1 and 65535."
        else -> null
    }
}

fun isPrivateIpv4(value: String): Boolean {
    val octets = value.split('.')
    if (octets.size != 4 || octets.any {
            it.isEmpty() || it.length > 3 || (it.length > 1 && it.startsWith('0')) || it.any { c -> c !in '0'..'9' }
        }) return false
    val parts = octets.map { it.toIntOrNull() ?: return false }
    if (parts.any { it !in 0..255 }) return false
    return parts[0] == 10 ||
        (parts[0] == 172 && parts[1] in 16..31) ||
        (parts[0] == 192 && parts[1] == 168)
}

enum class ReadingKind(val route: String, val label: String, val unit: String) {
    TEMPERATURE("temperature", "Temperature", "°C"),
    PRESSURE("pressure", "Pressure", "mmHg"),
    HUMIDITY("humidity", "Humidity", "%"),
    WEATHER("weather", "Pressure-based outlook", "")
}

data class SensorReading(val kind: ReadingKind, val displayValue: String, val spokenValue: String = displayValue)

enum class LightColor(val route: String, val label: String) {
    WHITE("FFFFFF", "white"),
    RED("FF0000", "red"),
    GREEN("00FF00", "green"),
    BLUE("0000FF", "blue"),
    YELLOW("FFFF00", "yellow"),
    MAGENTA("FF00FF", "purple"),
    RANDOM("random", "random"),
    OFF("000000", "off")
}

sealed interface VoiceCommand {
    data class Read(val kind: ReadingKind) : VoiceCommand
    data class SetLights(val color: LightColor) : VoiceCommand
}
