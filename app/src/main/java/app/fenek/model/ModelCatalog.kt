package app.fenek.model

enum class ModelKind { ASR, SEGMENTATION, EMBEDDING, LLM }

enum class AsrArchitecture { WHISPER, NEMO_CTC, NEMO_TRANSDUCER, TRANSDUCER }

/** A model file and the mirrors it can be downloaded from, in order of preference. */
data class RemoteFile(val name: String, val sizeBytes: Long, val urls: List<String>)

data class AsrSpec(
    val architecture: AsrArchitecture,
    val tokens: String,
    val model: String = "",
    val encoder: String = "",
    val decoder: String = "",
    val joiner: String = "",
    /** The recognition language can be chosen (multilingual Whisper). */
    val selectableLanguage: Boolean = false,
    /** Produces punctuation and capital letters. */
    val punctuation: Boolean,
)

data class ModelInfo(
    val id: String,
    val kind: ModelKind,
    val title: String,
    val summary: String,
    val languages: String,
    val license: String,
    val files: List<RemoteFile>,
    val asr: AsrSpec? = null,
    val recommended: Boolean = false,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

/**
 * Models used by the app. Everything runs on the device; the files are only downloaded
 * once (the exact sizes below are used to validate the downloads).
 */
object ModelCatalog {

    private fun hf(repo: String, name: String, size: Long) = RemoteFile(
        name, size,
        listOf(
            "https://huggingface.co/$repo/resolve/main/$name",
            "https://hf-mirror.com/$repo/resolve/main/$name",
        ),
    )

    private fun speakerModel(name: String, size: Long) = RemoteFile(
        name, size,
        listOf(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/$name",
            "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/main/$name",
            "https://hf-mirror.com/csukuangfj/speaker-embedding-models/resolve/main/$name",
        ),
    )

    private const val GIGA_RNNT = "csukuangfj/sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16"
    private const val GIGA_CTC = "csukuangfj/sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16"
    private const val ZIPFORMER_RU = "csukuangfj/sherpa-onnx-zipformer-ru-int8-2025-04-20"
    private const val WHISPER_BASE = "csukuangfj/sherpa-onnx-whisper-base"
    private const val WHISPER_SMALL = "csukuangfj/sherpa-onnx-whisper-small"
    private const val PARAKEET_V3 = "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"
    private const val PYANNOTE = "csukuangfj/sherpa-onnx-pyannote-segmentation-3-0"

    val gigaAmRnnt = ModelInfo(
        id = "gigaam-v3-rnnt-punct",
        kind = ModelKind.ASR,
        title = "GigaAM v3 RNNT",
        summary = "Лучшая точность для русской речи, расставляет знаки препинания и заглавные буквы. " +
            "Обучена в том числе на звонках и голосовых сообщениях.",
        languages = "Русский",
        license = "MIT",
        files = listOf(
            hf(GIGA_RNNT, "encoder.int8.onnx", 224_570_820),
            hf(GIGA_RNNT, "decoder.onnx", 4_600_132),
            hf(GIGA_RNNT, "joiner.onnx", 2_712_896),
            hf(GIGA_RNNT, "tokens.txt", 13_354),
        ),
        asr = AsrSpec(
            AsrArchitecture.NEMO_TRANSDUCER,
            tokens = "tokens.txt",
            encoder = "encoder.int8.onnx",
            decoder = "decoder.onnx",
            joiner = "joiner.onnx",
            punctuation = true,
        ),
        recommended = true,
    )

    val gigaAmCtc = ModelInfo(
        id = "gigaam-v3-ctc-punct",
        kind = ModelKind.ASR,
        title = "GigaAM v3 CTC",
        summary = "Та же модель GigaAM v3 с более простым декодером: немного быстрее, чуть менее точна.",
        languages = "Русский",
        license = "MIT",
        files = listOf(
            hf(GIGA_CTC, "model.int8.onnx", 224_893_661),
            hf(GIGA_CTC, "tokens.txt", 2_007),
        ),
        asr = AsrSpec(
            AsrArchitecture.NEMO_CTC,
            tokens = "tokens.txt",
            model = "model.int8.onnx",
            punctuation = true,
        ),
    )

    val zipformerRu = ModelInfo(
        id = "zipformer-ru-int8",
        kind = ModelKind.ASR,
        title = "Zipformer RU",
        summary = "Компактная и быстрая модель для слабых устройств. Текст без пунктуации.",
        languages = "Русский",
        license = "Apache-2.0",
        files = listOf(
            hf(ZIPFORMER_RU, "encoder.int8.onnx", 70_876_638),
            hf(ZIPFORMER_RU, "decoder.onnx", 2_093_080),
            hf(ZIPFORMER_RU, "joiner.int8.onnx", 259_417),
            hf(ZIPFORMER_RU, "tokens.txt", 6_388),
        ),
        asr = AsrSpec(
            AsrArchitecture.TRANSDUCER,
            tokens = "tokens.txt",
            encoder = "encoder.int8.onnx",
            decoder = "decoder.onnx",
            joiner = "joiner.int8.onnx",
            punctuation = false,
        ),
    )

    val whisperBase = ModelInfo(
        id = "whisper-base",
        kind = ModelKind.ASR,
        title = "Whisper Base",
        summary = "Многоязычная модель OpenAI с автоопределением языка и пунктуацией. Для русского заметно уступает GigaAM.",
        languages = "≈100 языков",
        license = "MIT",
        files = listOf(
            hf(WHISPER_BASE, "base-encoder.int8.onnx", 29_120_534),
            hf(WHISPER_BASE, "base-decoder.int8.onnx", 130_672_026),
            hf(WHISPER_BASE, "base-tokens.txt", 816_730),
        ),
        asr = AsrSpec(
            AsrArchitecture.WHISPER,
            tokens = "base-tokens.txt",
            encoder = "base-encoder.int8.onnx",
            decoder = "base-decoder.int8.onnx",
            selectableLanguage = true,
            punctuation = true,
        ),
    )

    val whisperSmall = ModelInfo(
        id = "whisper-small",
        kind = ModelKind.ASR,
        title = "Whisper Small",
        summary = "Точнее Whisper Base, но в несколько раз медленнее. Подходит для записей на других языках.",
        languages = "≈100 языков",
        license = "MIT",
        files = listOf(
            hf(WHISPER_SMALL, "small-encoder.int8.onnx", 112_442_483),
            hf(WHISPER_SMALL, "small-decoder.int8.onnx", 262_226_114),
            hf(WHISPER_SMALL, "small-tokens.txt", 816_730),
        ),
        asr = AsrSpec(
            AsrArchitecture.WHISPER,
            tokens = "small-tokens.txt",
            encoder = "small-encoder.int8.onnx",
            decoder = "small-decoder.int8.onnx",
            selectableLanguage = true,
            punctuation = true,
        ),
    )

    val parakeetV3 = ModelInfo(
        id = "parakeet-tdt-0.6b-v3",
        kind = ModelKind.ASR,
        title = "Parakeet TDT 0.6B v3",
        summary = "Модель NVIDIA для 25 европейских языков (включая русский и украинский) с пунктуацией. " +
            "Большая: нужен телефон с 6 ГБ ОЗУ и больше.",
        languages = "25 европейских языков",
        license = "CC-BY-4.0",
        files = listOf(
            hf(PARAKEET_V3, "encoder.int8.onnx", 652_184_281),
            hf(PARAKEET_V3, "decoder.int8.onnx", 11_845_275),
            hf(PARAKEET_V3, "joiner.int8.onnx", 6_355_277),
            hf(PARAKEET_V3, "tokens.txt", 93_939),
        ),
        asr = AsrSpec(
            AsrArchitecture.NEMO_TRANSDUCER,
            tokens = "tokens.txt",
            encoder = "encoder.int8.onnx",
            decoder = "decoder.int8.onnx",
            joiner = "joiner.int8.onnx",
            punctuation = true,
        ),
    )

    val segmentation = ModelInfo(
        id = "pyannote-segmentation-3.0",
        kind = ModelKind.SEGMENTATION,
        title = "pyannote segmentation 3.0",
        summary = "Находит участки речи и смены говорящих, в том числе наложения голосов.",
        languages = "Любой язык",
        license = "MIT",
        files = listOf(hf(PYANNOTE, "model.onnx", 5_992_913)),
    )

    /**
     * Chosen by measurement on real meeting recordings made with one distant microphone (AMI):
     * WeSpeaker ResNet34-LM, the model of pyannote speaker-diarization 3.1 that was used before,
     * mixed up about half of the voices in two-second windows there, this model a few percent.
     */
    val eres2net = ModelInfo(
        id = "3dspeaker-eres2net-en",
        kind = ModelKind.EMBEDDING,
        title = "3D-Speaker ERes2Net",
        summary = "«Отпечаток голоса», по которому реплики группируются по участникам. " +
            "Устойчива к записи на расстоянии: телефон на столе, комната с эхом.",
        languages = "Любой язык",
        license = "Apache-2.0",
        files = listOf(speakerModel("3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx", 26_485_263)),
    )

    /**
     * Language models in GGUF format for the llama.cpp runtime. The model is only used after
     * recognition (text correction, summary and tasks), so a missing one never blocks a job.
     */
    val qwen15 = ModelInfo(
        id = "qwen2.5-1.5b-instruct-q4",
        kind = ModelKind.LLM,
        title = "Qwen2.5 1.5B Instruct",
        summary = "Языковая модель для правки текста (пунктуация, регистр, явные ошибки) и для выжимки с задачами. " +
            "Работает на телефоне. Нужен телефон с 4 ГБ ОЗУ и больше.",
        languages = "Русский, английский и другие",
        license = "Apache-2.0",
        files = listOf(hf("Qwen/Qwen2.5-1.5B-Instruct-GGUF", "qwen2.5-1.5b-instruct-q4_k_m.gguf", 1_117_320_736)),
        recommended = true,
    )

    val qwen05 = ModelInfo(
        id = "qwen2.5-0.5b-instruct-q4",
        kind = ModelKind.LLM,
        title = "Qwen2.5 0.5B Instruct",
        summary = "Маленькая и быстрая языковая модель для слабых телефонов. Чаще ошибается, поэтому больше правок отклоняется.",
        languages = "Русский, английский и другие",
        license = "Apache-2.0",
        files = listOf(hf("Qwen/Qwen2.5-0.5B-Instruct-GGUF", "qwen2.5-0.5b-instruct-q4_k_m.gguf", 491_400_032)),
    )

    val gemma1 = ModelInfo(
        id = "gemma-3-1b-it-q4",
        kind = ModelKind.LLM,
        title = "Gemma 3 1B Instruct",
        summary = "Языковая модель Google среднего размера. Условия использования Gemma ограничивают применение.",
        languages = "Русский, английский и другие",
        license = "Gemma Terms of Use",
        files = listOf(hf("unsloth/gemma-3-1b-it-GGUF", "gemma-3-1b-it-Q4_K_M.gguf", 806_058_272)),
    )

    val asrModels = listOf(gigaAmRnnt, gigaAmCtc, zipformerRu, whisperBase, whisperSmall, parakeetV3)

    val embeddingModels = listOf(eres2net)
    val llmModels = listOf(qwen15, qwen05, gemma1)
    val all = asrModels + segmentation + embeddingModels + llmModels

    val defaultAsr = gigaAmRnnt
    val defaultEmbedding = eres2net
    val defaultLlm = qwen15

    fun asr(id: String): ModelInfo = asrModels.firstOrNull { it.id == id } ?: defaultAsr

    fun embedding(id: String): ModelInfo = embeddingModels.firstOrNull { it.id == id } ?: defaultEmbedding

    fun llm(id: String): ModelInfo = llmModels.firstOrNull { it.id == id } ?: defaultLlm
}

/** Languages offered for models with a selectable language (Whisper). */
object Languages {
    const val AUTO = "auto"

    val whisper = listOf(
        AUTO to "Определять автоматически",
        "ru" to "Русский",
        "en" to "Английский",
        "uk" to "Украинский",
        "be" to "Белорусский",
        "kk" to "Казахский",
        "uz" to "Узбекский",
        "de" to "Немецкий",
        "fr" to "Французский",
        "es" to "Испанский",
        "it" to "Итальянский",
        "pl" to "Польский",
        "tr" to "Турецкий",
        "ar" to "Арабский",
        "zh" to "Китайский",
        "ja" to "Японский",
    )

    fun title(code: String?): String? = whisper.firstOrNull { it.first == code }?.second
}
