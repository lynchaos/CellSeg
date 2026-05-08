package uk.yaylali.cellseg.data.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the ONNX Runtime session.
 *
 * Preferred execution order: NNAPI EP → XNNPACK EP → CPU (plain).
 * Falls back gracefully if a provider is unavailable on the device.
 *
 * Input tensor:  (1, 2, H, W) FP32
 * Output tensor: (1, 3, H, W) FP32  — channels: dY, dX, cellprob
 */
@Singleton
class OnnxRuntimeWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    fun loadModel(modelFile: File) {
        session?.close()
        session = null

        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            // NNAPI is intentionally disabled: on the Android emulator its drivers are
            // example stubs (version = JUST_AN_EXAMPLE) that silently return zeros/garbage.
            // XNNPACK provides real SIMD acceleration on both emulator (x86) and devices.
            try {
                addXnnpack(emptyMap())
                Timber.d("OnnxRuntimeWrapper: XNNPACK EP enabled")
            } catch (e: Exception) {
                Timber.d("OnnxRuntimeWrapper: XNNPACK EP unavailable, using plain CPU")
            }
        }
        session = env.createSession(modelFile.absolutePath, opts)
        Timber.i("OnnxRuntimeWrapper: model loaded — inputs: %s", session!!.inputNames)
    }

    fun isLoaded(): Boolean = session != null

    fun unload() {
        session?.close()
        session = null
    }

    /**
     * Run a single inference.
     *
     * @param inputData   Flat float array, row-major, shape (1, 2, H, W)
     * @param height      H
     * @param width       W
     * @return Flat float array, shape (1, 3, H, W) — dY, dX, cellprob
     */
    fun run(inputData: FloatArray, height: Int, width: Int): FloatArray {
        val sess = checkNotNull(session) { "Model not loaded" }
        val shape = longArrayOf(1L, 2L, height.toLong(), width.toLong())
        val buffer = FloatBuffer.wrap(inputData)
        val inputTensor = OnnxTensor.createTensor(env, buffer, shape)
        inputTensor.use {
            val inputName = sess.inputNames.iterator().next()
            val results = sess.run(mapOf(inputName to inputTensor))
            results.use { res ->
                val outputTensor = res[0] as OnnxTensor
                // Flatten output (1, 3, H, W) to FloatArray via direct buffer access.
                // OnnxTensor.floatBuffer is safe for FP32 output and avoids the
                // fragile nested Array<*> / FloatArray cast chain.
                val flat = FloatArray(3 * height * width)
                outputTensor.floatBuffer.get(flat)
                return flat
            }
        }
    }

    override fun close() {
        session?.close()
        session = null
        // OrtEnvironment.getEnvironment() is a JVM-wide singleton managed by the ONNX Runtime
        // native layer. Calling env.close() here would permanently destroy it for the process
        // lifetime, causing all subsequent inference calls to fail with "OrtEnvironment is closed".
        // The environment is intentionally left open; the OS reclaims native resources on exit.
    }
}
