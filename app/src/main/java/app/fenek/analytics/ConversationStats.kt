package app.fenek.analytics

import app.fenek.data.Transcript
import kotlin.math.roundToInt

data class SpeakerStats(
    val speaker: Int,
    val talkMs: Long,
    /** Share of the total talk time, 0..1. */
    val share: Float,
    val turns: Int,
    val words: Int,
    val wordsPerMinute: Int,
    val longestTurnMs: Long,
)

data class ConversationStats(
    /** Sorted by talk time, the most talkative first. */
    val speakers: List<SpeakerStats>,
    val talkMs: Long,
    val words: Int,
    val turns: Int,
    /** Frequent meaningful words: what the conversation was about. */
    val topics: List<String>,
) {
    /** Talked the most, if there was a dialogue at all. */
    val chattiest: SpeakerStats? get() = speakers.takeIf { it.size > 1 }?.first()

    /** Fastest speaker among those who said enough for the rate to mean something. */
    val fastest: SpeakerStats?
        get() = speakers.filter { it.words >= MIN_WORDS_FOR_RATE }.takeIf { it.size > 1 }?.maxBy { it.wordsPerMinute }

    private companion object {
        const val MIN_WORDS_FOR_RATE = 15
    }
}

object ConversationAnalytics {

    private val word = Regex("[\\p{L}\\p{N}]+(?:[-'][\\p{L}\\p{N}]+)*")

    fun compute(transcript: Transcript): ConversationStats {
        val turns = transcript.turns()
        val bySpeaker = transcript.segments.groupBy { it.speaker }
        val totalTalk = transcript.segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0) }
        val speakers = bySpeaker.map { (speaker, segments) ->
            val talk = segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0) }
            val words = segments.sumOf { countWords(it.text) }
            val ownTurns = turns.filter { it.speaker == speaker }
            SpeakerStats(
                speaker = speaker,
                talkMs = talk,
                share = if (totalTalk > 0) talk.toFloat() / totalTalk else 0f,
                turns = ownTurns.size,
                words = words,
                wordsPerMinute = if (talk > 0) (words * 60_000.0 / talk).roundToInt() else 0,
                longestTurnMs = ownTurns.maxOfOrNull { it.endMs - it.startMs } ?: 0,
            )
        }.sortedByDescending { it.talkMs }
        return ConversationStats(
            speakers = speakers,
            talkMs = totalTalk,
            words = speakers.sumOf { it.words },
            turns = turns.size,
            topics = topics(transcript.segments.map { it.text }),
        )
    }

    fun countWords(text: String): Int = word.findAll(text).count()

    /** Stems of the meaningful words of [text], the units [topics] counts. */
    fun contentStems(text: String): Set<String> =
        word.findAll(text.lowercase()).map { it.value }.filter(::isContent).map(::stem).toSet()

    private fun isContent(w: String): Boolean = w.length >= MIN_LENGTH && w !in StopWords.all && !w.all { it.isDigit() }

    private fun stem(w: String): String = if (w.length > STEM) w.take(STEM) else w

    /**
     * Most frequent content words. Russian is highly inflected, so word forms are grouped by
     * their first [STEM] letters ("продажи", "продажам" → "продаж") and the most frequent form
     * represents the group.
     */
    fun topics(texts: List<String>, limit: Int = 8): List<String> {
        val forms = HashMap<String, HashMap<String, Int>>()
        for (text in texts) {
            for (match in word.findAll(text.lowercase())) {
                val w = match.value
                if (!isContent(w)) continue
                val counts = forms.getOrPut(stem(w)) { HashMap() }
                counts[w] = (counts[w] ?: 0) + 1
            }
        }
        return forms.values
            .map { counts -> counts.maxBy { it.value }.key to counts.values.sum() }
            .filter { it.second >= MIN_COUNT }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenByDescending { it.first.length })
            .take(limit)
            .map { it.first }
    }

    private const val MIN_LENGTH = 4
    private const val STEM = 6
    private const val MIN_COUNT = 2
}

private object StopWords {
    val all: Set<String> = """
        когда тогда потом потому чтобы чтоб если ещё еще уже очень просто только можно нужно надо
        будет было были была быть есть этот эта это эти этого этой этим этих этом тому того такой
        такая такие такое так тоже также какой какая какие какое который которая которые которое
        которого которой котором которых себя себе свой своя свои своё свое мной мене меня тебя
        тебе нему него неё нее ними нами вами нашу наша наше наши ваша ваше ваши вашу всех всем
        всего всеми всей весь вся всё все сейчас здесь тут там туда сюда куда откуда где почему
        зачем сколько много мало более менее больше меньше даже вообще значит короче типа кстати
        вот ладно хорошо понятно конечно спасибо пожалуйста здравствуйте давайте давай добрый день
        вечер утро ничего нечего никто никогда нигде ведь вроде может могу могут мочь хочу хотел
        хотела хотим хотят сказал сказала говорю говорит говорил думаю знаю знаешь знает слушай
        смотри посмотрим через после перед между около против вместо кроме разве либо или ибо
        чего чему чем кому кого кто что сам сама сами само самый самая самое самые раз два три
        один одна одно одни первый второй потому-что ну-ка either
        сегодня завтра вчера послезавтра утром вечером договорились отлично коллеги
        понедельник понедельника понедельнику вторник вторника вторнику среда среду среды среде
        четверг четверга четвергу пятница пятницу пятницы пятнице суббота субботу субботы субботе
        воскресенье воскресенья воскресенью
        this that with have from they will would there their what about which when your just
        like know think yeah okay well really right also then than them these those been were
    """.trim().split(Regex("\\s+")).toSet()
}
