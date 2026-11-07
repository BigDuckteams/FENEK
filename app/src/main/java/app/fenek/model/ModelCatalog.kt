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

    /**
     * A Hugging Face repository at one commit. Files are addressed by the commit, not by `main`: a repository
     * that is rewritten upstream changes its files (and their sizes) under the same names, and the exact size
     * check then rejects every download. To take a newer model, change the commit and the sizes together.
     */
    private class Repo(val id: String, val commit: String)

    private fun hf(repo: Repo, name: String, size: Long) = RemoteFile(
        name, size,
        listOf(
            "https://huggingface.co/${repo.id}/resolve/${repo.commit}/$name",
            "https://hf-mirror.com/${repo.id}/resolve/${repo.commit}/$name",
        ),
    )

    private val SPEAKER_MODELS = Repo("csukuangfj/speaker-embedding-models", "0743f301363dec56491a490f6d6cbc9d67f9a3bf")

    private fun speakerModel(name: String, size: Long) = RemoteFile(
        name, size,
        listOf(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/$name",
            "https://huggingface.co/${SPEAKER_MODELS.id}/resolve/${SPEAKER_MODELS.commit}/$name",
            "https://hf-mirror.com/${SPEAKER_MODELS.id}/resolve/${SPEAKER_MODELS.commit}/$name",
        ),
    )

    private val GIGA_RNNT = Repo(
        "csukuangfj/sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16",
        "a6039be7cee829a9044a69ac0ebaf1c191217c97",
    )
    private val GIGA_CTC = Repo(
        "csukuangfj/sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16",
        "4fb5407ff028a69fec516cdf4c10fac9ddea7c16",
    )
    private val ZIPFORMER_RU = Repo("csukuangfj/sherpa-onnx-zipformer-ru-int8-2025-04-20", "641de8d322c05b9087ad2927ccda4bda3cccc159")
    private val WHISPER_BASE = Repo("csukuangfj/sherpa-onnx-whisper-base", "bb53ee204431c90d314c1cc08d28d23e5b7927cc")
    private val WHISPER_SMALL = Repo("csukuangfj/sherpa-onnx-whisper-small", "8f3c18b358db4d1f2fc1eae49d75cd20989e4309")

    // The commit of August 2025. The repository was rewritten on 2026-11-07 (another export of the same
    // model: the encoder is 98 KB bigger, the decoder 1 byte smaller); that one has not been tried here.
    private val PARAKEET_V3 = Repo(
        "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
        "2bda32ec70b097a55adaa07d9a7173915b43cc78",
    )
    private val PYANNOTE = Repo("csukuangfj/sherpa-onnx-pyannote-segmentation-3-0", "9403a6902bb58e3d5ae8c7e77c3422de279db2e0")
    private val QWEN_15 = Repo("Qwen/Qwen2.5-1.5B-Instruct-GGUF", "91cad51170dc346986eccefdc2dd33a9da36ead9")
    private val QWEN_05 = Repo("Qwen/Qwen2.5-0.5B-Instruct-GGUF", "9217f5db79a29953eb74d5343926648285ec7e67")
    private val GEMMA_1B = Repo("unsloth/gemma-3-1b-it-GGUF", "f0b45be0aac41bd6a100a4b5734cad5f67255bfb")

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
        files = listOf(hf(QWEN_15, "qwen2.5-1.5b-instruct-q4_k_m.gguf", 1_117_320_736)),
        recommended = true,
    )

    val qwen05 = ModelInfo(
        id = "qwen2.5-0.5b-instruct-q4",
        kind = ModelKind.LLM,
        title = "Qwen2.5 0.5B Instruct",
        summary = "Маленькая и быстрая языковая модель для слабых телефонов. Чаще ошибается, поэтому больше правок отклоняется.",
        languages = "Русский, английский и другие",
        license = "Apache-2.0",
        files = listOf(hf(QWEN_05, "qwen2.5-0.5b-instruct-q4_k_m.gguf", 491_400_032)),
    )

    val gemma1 = ModelInfo(
        id = "gemma-3-1b-it-q4",
        kind = ModelKind.LLM,
        title = "Gemma 3 1B Instruct",
        summary = "Языковая модель Google среднего размера. Условия использования Gemma ограничивают применение.",
        languages = "Русский, английский и другие",
        license = "Gemma Terms of Use",
        files = listOf(hf(GEMMA_1B, "gemma-3-1b-it-Q4_K_M.gguf", 806_058_272)),
    )

    val asrModels = listOf(gigaAmRnnt, gigaAmCtc, zipformerRu, whisperBase, whisperSmall, parakeetV3)

    val embeddingModels = listOf(eres2net)
    val llmModels = listOf(qwen15, qwen05, gemma1)
    val all = asrModels + segmentation + embeddingModels + llmModels

    val defaultAsr = gigaAmRnnt
    val defaultEmbedding = eres2net
    val defaultLlm = qwen15

    /**
     * What the "fat" build carries inside its APK: the default set for the default settings.
     * `app/bundled-models.json` lists the same files for the build; a test keeps the two equal.
     */
    val bundledSet = listOf(segmentation, defaultEmbedding, defaultAsr, defaultLlm)

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
