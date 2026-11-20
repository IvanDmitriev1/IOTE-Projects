package com.example.smarthomevoicecontrol.core

sealed interface ParseResult {
    data class Valid(val commands: List<VoiceCommand>) : ParseResult
    data class Invalid(val reason: String) : ParseResult
}

object CommandParser {
    private val politePrefix = Regex("^(please\\s+|can you\\s+|could you\\s+|would you\\s+|tell me\\s+|show me\\s+|what is\\s+|so\\s+|also\\s+|separately\\s+|the values of\\s+)+")
    private val splitClause = Regex("\\s*(?:,|\\band\\b)\\s*")
    private val word = Regex("[a-z]+")
    private val lightColors = mapOf(
        "white" to LightColor.WHITE, "red" to LightColor.RED, "green" to LightColor.GREEN,
        "blue" to LightColor.BLUE, "yellow" to LightColor.YELLOW,
        "magenta" to LightColor.MAGENTA, "purple" to LightColor.MAGENTA,
        "random" to LightColor.RANDOM, "black" to LightColor.OFF
    )
    private val target = "(?:the |my |a )?(?:lights?|led strip|led|strip)"
    private val color = "(?:${lightColors.keys.joinToString("|")})"
    private val leadingLightAction = Regex("(?:turn|switch) (?:off $target|on $target(?: (?:colou?r )?($color))?)")
    private val trailingLightAction = Regex("(?:turn|switch|set|change|make) $target (?:to )?(?:off|on(?: (?:colou?r )?($color))?|(?:colou?r )?($color))")
    private val colorContinuation = Regex("colou?r $color")
    private val bareLightOn = Regex("(?:(?:turn|switch) on $target|(?:turn|switch|set|change|make) $target (?:to )?on)")

    fun parse(transcript: String): ParseResult {
        val normalized = transcript.lowercase().replace(Regex("[^a-z,']+"), " ").trim()
        if (normalized.isBlank()) return ParseResult.Invalid("I didn't hear a command.")
        val commands = mutableListOf<VoiceCommand>()
        val requestedReadings = mutableSetOf<ReadingKind>()
        var lights: LightColor? = null

        val clauses = buildList<String> {
            for (clause in splitClause.split(normalized)) {
                if (colorContinuation.matches(clause.trim()) &&
                    lastOrNull()?.let { bareLightOn.matches(politePrefix.replace(it.trim(), "")) } == true
                ) {
                    add(removeAt(lastIndex).trimEnd() + " " + clause.trim())
                } else {
                    add(clause)
                }
            }
        }

        for (rawClause in clauses) {
            if (rawClause.trim() in setOf("so", "also", "separately", "hello", "hi", "hey")) continue
            val clause = politePrefix.replace(rawClause.trim(), "").replace("'", "").replace("along with", " ")
            if (clause.isBlank()) continue
            val words = word.findAll(clause).map { it.value }.toList()
            if (words.any { it in setOf("not", "never", "dont", "without") })
                return ParseResult.Invalid("Negated commands aren't supported.")
            val mentionsLights = words.any { it in setOf("light", "lights", "led", "strip") }
            if (mentionsLights) {
                val parsed = parseLights(words) ?: return ParseResult.Invalid("I couldn't identify one supported light action.")
                if (lights != null && lights != parsed)
                    return ParseResult.Invalid("Choose only one light action per command.")
                if (lights == null) commands += VoiceCommand.SetLights(parsed)
                lights = parsed
                continue
            }
            if (words.windowed(2).any { it == listOf("sensor", "data") } || words == listOf("sensors")) {
                addReading(commands, requestedReadings, ReadingKind.TEMPERATURE)
                addReading(commands, requestedReadings, ReadingKind.PRESSURE)
                addReading(commands, requestedReadings, ReadingKind.HUMIDITY)
                continue
            }
            val kinds = buildList {
                if ("temperature" in words || "temp" in words) add(ReadingKind.TEMPERATURE)
                if ("pressure" in words) add(ReadingKind.PRESSURE)
                if ("humidity" in words || "humid" in words) add(ReadingKind.HUMIDITY)
                if ("weather" in words || "outlook" in words || "forecast" in words) add(ReadingKind.WEATHER)
            }
            if (kinds.isEmpty()) return ParseResult.Invalid("I couldn't match “$rawClause” to a supported command.")
            kinds.forEach { addReading(commands, requestedReadings, it) }
        }
        if (commands.isEmpty()) return ParseResult.Invalid("Say a sensor reading or a light action.")
        return ParseResult.Valid(commands.toList())
    }

    private fun addReading(commands: MutableList<VoiceCommand>, requested: MutableSet<ReadingKind>, kind: ReadingKind) {
        if (requested.add(kind)) commands += VoiceCommand.Read(kind)
    }

    private fun parseLights(words: List<String>): LightColor? {
        val clause = words.joinToString(" ")
        val match = leadingLightAction.matchEntire(clause) ?: trailingLightAction.matchEntire(clause) ?: return null
        val selected = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
        if (selected == "black" && "on" in words) return null
        return when {
            selected != null -> lightColors.getValue(selected)
            "off" in words -> LightColor.OFF
            "on" in words -> LightColor.WHITE
            else -> null
        }
    }
}
