package com.example.smarthomevoicecontrol.data

import android.content.Context
import com.example.smarthomevoicecontrol.core.TextToColorEncoder
import com.example.smarthomevoicecontrol.core.CommandParser
import com.example.smarthomevoicecontrol.core.ParseResult
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorType
import java.io.Closeable
import java.util.Locale
import kotlin.math.roundToInt
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** Application-scoped local text-to-RGB inference. Native resources are confined to one worker. */
class TextToColorModel(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val encoder by lazy { TextToColorEncoder(appContext.assets.open("text_to_color_encoder.json").bufferedReader().use { it.readText() }) }
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private var model: CompiledModel? = null
    private var environment: Environment? = null
    private var inputs = emptyList<com.google.ai.edge.litert.TensorBuffer>()
    private var outputs = emptyList<com.google.ai.edge.litert.TensorBuffer>()
    private var closed = false

    suspend fun parse(transcript: String): ParseResult = withContext(dispatcher) {
        check(!closed) { "Color model is closed." }
        CommandParser.parse(transcript) { encoder.encode(it) != null }
    }

    suspend fun infer(description: String): FloatArray = withContext(dispatcher) {
        check(!closed) { "Color model is closed." }
        val encoded = encoder.encode(description) ?: throw IllegalArgumentException("That color description isn't supported.")
        ensureModel()
        val active = checkNotNull(model)
        inputs[0].writeFloat(encoded)
        active.run(inputs, outputs)
        val rgb = outputs[0].readFloat()
        require(rgb.size == 3 && rgb.all(Float::isFinite)) { "The color model returned invalid output." }
        rgb
    }

    suspend fun color(description: String): String {
        val rgb = infer(description)
        return rgb.joinToString("") { channel -> "%02X".format(Locale.ROOT, (channel.coerceIn(0f, 1f) * 255f).roundToInt()) }
    }

    private fun ensureModel() {
        if (model != null) return
        val env = Environment.create(); environment = env
        try {
            val created = CompiledModel.create(appContext.assets, "text_to_color_dynamic_int8.tflite", CompiledModel.Options(Accelerator.CPU), env)
            model = created
            inputs = created.createInputBuffers(); outputs = created.createOutputBuffers()
            require(inputs.size == 1 && outputs.size == 1)
            // Validate the named model contract when tensor metadata is exposed by the runtime.
            val inputType = created.getInputTensorType("serving_default_args_0")
            // LiteRT exposes the SignatureDef alias here; the encoder JSON's output.name is
            // the underlying graph tensor name and is not the CompiledModel signature key.
            val outputType = created.getOutputTensorType("output_0")
            require(inputType.elementType == TensorType.ElementType.FLOAT && inputType.layout?.dimensions == listOf(1, 48))
            require(outputType.elementType == TensorType.ElementType.FLOAT && outputType.layout?.dimensions == listOf(1, 3))
            val warmup = checkNotNull(encoder.encode("${encoder.firstBase()}"))
            inputs[0].writeFloat(warmup)
            created.run(inputs, outputs)
            require(outputs[0].readFloat().let { it.size == 3 && it.all(Float::isFinite) })
        } catch (t: Throwable) {
            releaseNative()
            throw t
        }
    }

    private fun releaseNative() {
        (inputs + outputs).forEach { runCatching { it.close() } }
        inputs = emptyList(); outputs = emptyList()
        runCatching { model?.close() }; model = null
        runCatching { environment?.close() }; environment = null
    }

    override fun close() {
        if (closed) return
        runCatching { executor.submit { releaseNative(); closed = true }.get() }
        dispatcher.close(); executor.shutdown()
    }
}
