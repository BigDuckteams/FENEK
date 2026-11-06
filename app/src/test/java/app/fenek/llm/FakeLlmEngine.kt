package app.fenek.llm

/** A deterministic stand-in for the language model: the answer is computed from the request. */
class FakeLlmEngine(
    private val answer: (system: String, user: String, grammar: String?) -> String,
) : LlmEngine {

    class Request(val system: String, val user: String, val grammar: String?, val maxTokens: Int, val timeoutMs: Long)

    val requests = ArrayList<Request>()
    var closed = false
        private set

    override suspend fun generate(system: String, user: String, grammar: String?, maxTokens: Int, timeoutMs: Long): String {
        requests += Request(system, user, grammar, maxTokens, timeoutMs)
        return answer(system, user, grammar)
    }

    override fun close() {
        closed = true
    }
}
