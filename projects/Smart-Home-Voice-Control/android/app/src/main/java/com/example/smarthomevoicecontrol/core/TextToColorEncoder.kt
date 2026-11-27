package com.example.smarthomevoicecontrol.core

import org.json.JSONObject
import java.util.Locale

/** JSON-backed encoder for the supplied 48-value model input. */
class TextToColorEncoder(json: String) {
    private val bases: List<String>
    private val colors: Map<String, FloatArray>
    private val modifiers: List<String>
    private val words: List<String>
    private val forms: Map<String, String>

    init {
        val root = JSONObject(json)
        require(root.getInt("schema_version") == 1 && root.getInt("input_size") == 48)
        bases = root.getJSONArray("base_order").let { a -> (0 until a.length()).map(a::getString) }
        colors = root.getJSONObject("base_colors").let { o -> bases.associateWith { key ->
            val a = o.getJSONArray(key); require(a.length() == 3)
            FloatArray(3) { a.getDouble(it).toFloat().also { n -> require(n.isFinite() && n in 0f..1f) } }
        } }
        modifiers = root.getJSONArray("modifiers").let { a -> (0 until a.length()).map(a::getString) }
        words = root.getJSONArray("words").let { a -> (0 until a.length()).map(a::getString) }
        val f = root.getJSONObject("forms")
        forms = f.keys().asSequence().associateWith { f.getString(it) }
        require(bases.size == colors.size && bases.distinct().size == bases.size)
        require(modifiers.size == 13 && modifiers.distinct().size == modifiers.size)
        require(words.size == 32 && words.distinct().size == words.size && words.first() == "")
        require(forms.values.all { it in modifiers } && forms.keys.all { it in words })
    }

    fun encode(description: String): FloatArray? {
        var normalized = Regex("[a-z0-9]+").findAll(description.lowercase(Locale.ROOT)).joinToString(" ") { it.value }
        normalized = normalized.replace(Regex("\\bgrey\\b"), "gray")
        normalized = normalized.replace(Regex("^(?:a|an|the) "), "")
        normalized = normalized.replace(Regex(" (?:color|shade)$"), "")
        val base = bases.firstOrNull { normalized == it || normalized.endsWith(" $it") } ?: return null
        val phrase = if (normalized == base) "" else normalized.removeSuffix(" $base")
        val canonical = forms[phrase] ?: return null
        if (base in setOf("black", "white") && canonical != "plain") return null
        val mi = modifiers.indexOf(canonical); val wi = words.indexOf(phrase)
        if (mi < 0 || wi < 0) return null
        return FloatArray(48).also { out -> colors.getValue(base).copyInto(out); out[3 + mi] = 1f; out[16 + wi] = 1f }
    }

    fun firstBase(): String = bases.first()
}
