package app.fenek.llm

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeoutException

/** The functions of `libfenekllm.so` (llm_jni.cpp). Text goes through as UTF-8 byte arrays. */
internal object LlamaNative {
    external fun nativeInit(nativeLibDir: String)
    external fun nativeLoad(modelPath: String, threads: Int): Long
    external fun nativeGenerate(
        handle: Long,
        system: ByteArray,
        user: ByteArray,
        grammar: ByteArray?,
        maxTokens: Int,
        timeoutMs: Long,
    ): ByteArray
    external fun nativeCancel(handle: Long)
    external fun nativeFree(handle: Long)
}

/**
 * A GGUF model run by llama.cpp on the CPU. Generation is greedy, so the same request gives the
 * same answer; a grammar can force the shape of the answer. Not thread-safe: one request at a time.
 */
class LlamaEngine private constructor(
    private var handle: Long,
    /** Writes the answers to the log; only debuggable builds do, the text is the user's conversation. */
    private val logAnswers: Boolean = false,
) : LlmEngine {

    override suspend fun generate(
        system: String,
        user: String,
        grammar: String?,
        maxTokens: Int,
        timeoutMs: Long,
    ): String {
        val engine = handle
        if (engine == 0L) throw LlmException("Языковая модель уже выгружена")
        return coroutineScope {
            val work = async(Dispatchers.Default) {
                try {
                    val bytes = LlamaNative.nativeGenerate(
                        engine,
                        system.toByteArray(Charsets.UTF_8),
                        user.toByteArray(Charsets.UTF_8),
                        grammar?.toByteArray(Charsets.UTF_8),
                        maxTokens,
                        timeoutMs,
                    )
                    // A cut-off answer may end inside a character: drop the replacement mark it leaves.
                    String(bytes, Charsets.UTF_8).trimEnd('\uFFFD').also {
                        if (logAnswers) Log.d(TAG, "answer (${it.length} chars): $it")
                    }
                } catch (e: TimeoutException) {
                    Log.w(TAG, "request timed out after ${timeoutMs / 1000} s")
                    throw LlmTimeoutException("Запрос к языковой модели занял больше ${timeoutMs / 1000} с")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "request failed: ${e.message}")
                    throw LlmException(e.message ?: "Ошибка языковой модели", e)
                }
            }
            try {
                work.await()
            } catch (e: CancellationException) {
                // The job was cancelled: ask the native loop to stop, it ends within one token.
                LlamaNative.nativeCancel(engine)
                throw e
            }
        }
    }

    override fun close() {
        val engine = handle
        handle = 0
        if (engine != 0L) LlamaNative.nativeFree(engine)
    }

    companion object {
        private const val TAG = "LlamaEngine"
        private val lock = Any()
        private var initialized = false

        /** A loader that runs models with llama.cpp; the native library is only touched when it is used. */
        fun loader(context: Context): LlmLoader {
            val libDir = context.applicationInfo.nativeLibraryDir
            val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            return LlmLoader { modelPath, threads ->
                try {
                    synchronized(lock) {
                        if (!initialized) {
                            System.loadLibrary("fenekllm")
                            LlamaNative.nativeInit(libDir)
                            initialized = true
                        }
                    }
                } catch (e: UnsatisfiedLinkError) {
                    // For example the 32-bit build, which does not carry the language model runtime.
                    throw LlmException("Языковая модель не поддерживается на этом процессоре", e)
                }
                val handle = LlamaNative.nativeLoad(modelPath, threads)
                if (handle == 0L) throw LlmException("Не удалось загрузить языковую модель")
                LlamaEngine(handle, logAnswers = debuggable)
            }
        }
    }
}
