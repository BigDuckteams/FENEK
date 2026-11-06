package app.fenek.llm

/** The language model failed to load or to answer; the caller keeps the text it already has. */
open class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** One request ran longer than its time limit. */
class LlmTimeoutException(message: String) : LlmException(message)

/**
 * A language model running on the device. Everything that uses it (text correction, summary) is
 * written against this interface, so that it can be tested on the JVM with a fake and so that the
 * native runtime is only touched in one place.
 */
interface LlmEngine : AutoCloseable {

    /**
     * Answers one chat request: the model's own chat template is applied to [system] and [user].
     * Decoding is greedy, so the same request gives the same answer.
     *
     * @param grammar a GBNF grammar the answer must follow, or null for free text
     * @param maxTokens upper limit of the generated tokens
     * @param timeoutMs upper limit of the time spent on this request
     * @throws LlmTimeoutException when [timeoutMs] is exceeded
     * @throws LlmException on any other failure
     */
    suspend fun generate(
        system: String,
        user: String,
        grammar: String? = null,
        maxTokens: Int,
        timeoutMs: Long,
    ): String
}

/** Loads a model file into an engine. The native implementation lives behind this seam. */
fun interface LlmLoader {
    /** @throws LlmException when the model cannot be loaded */
    fun load(modelPath: String, threads: Int): LlmEngine
}
