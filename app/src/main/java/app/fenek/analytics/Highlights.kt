package app.fenek.analytics

import app.fenek.data.Transcript
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A sentence of the conversation, located in time. */
data class Sentence(
    val segmentIndex: Int,
    val speaker: Int,
    /** Phrases have timestamps; sentences inside a phrase are placed proportionally to their position. */
    val startMs: Long,
    val text: String,
)

/** A deadline as it was said ("к пятнице") and the calendar date it means, if it means one. */
data class Deadline(val phrase: String, val date: LocalDate?, val time: LocalTime? = null) {

    /** "пт, 30 окт., 15:30" — or the phrase itself when it names no date. */
    fun label(): String {
        val date = date ?: return phrase
        val day = date.format(DateTimeFormatter.ofPattern("EE, d MMM", RUSSIAN))
        return if (time != null) "$day, ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}" else day
    }

    private companion object {
        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}

/** A promise, a request or an agreement. */
data class ActionItem(val sentence: Sentence, val deadline: Deadline?)

data class Highlights(
    /** The most informative sentences in conversation order: a short extractive summary. */
    val summary: List<Sentence>,
    val actions: List<ActionItem>,
    val questions: List<Sentence>,
) {
    val isEmpty: Boolean get() = summary.isEmpty() && actions.isEmpty() && questions.isEmpty()
}

/**
 * Finds what matters in a conversation without a language model: key sentences, tasks with
 * deadlines and questions. The rules are written for Russian, with basic English support;
 * they work on the punctuated output of GigaAM and Whisper.
 */
object ConversationHighlights {

    fun compute(transcript: Transcript, zone: ZoneId = ZoneId.systemDefault()): Highlights =
        compute(transcript, Instant.ofEpochMilli(transcript.conversationAt).atZone(zone).toLocalDate())

    /** @param today the day of the conversation: "завтра" is counted from it */
    fun compute(transcript: Transcript, today: LocalDate): Highlights {
        val sentences = sentences(transcript)
        return Highlights(
            summary = summary(sentences),
            actions = actions(sentences, today),
            questions = sentences.filter { isQuestion(it.text) }.take(MAX_ITEMS),
        )
    }

    private val sentenceEnd = Regex("(?<=[.!?…])\\s+")
    private val token = Regex("[\\p{L}\\p{N}]+(?:'\\p{L}+)?")

    fun sentences(transcript: Transcript): List<Sentence> {
        val result = ArrayList<Sentence>()
        transcript.segments.forEachIndexed { index, segment ->
            val text = segment.text
            val span = (segment.endMs - segment.startMs).coerceAtLeast(0)
            var from = 0
            // Each sentence runs up to the start of the next one.
            val bounds = sentenceEnd.findAll(text).map { it.range.last + 1 }.toList() + text.length
            for (end in bounds) {
                val raw = text.substring(from, end)
                val clean = raw.trim()
                if (clean.isNotEmpty()) {
                    val offset = from + raw.indexOf(clean[0])
                    result += Sentence(index, segment.speaker, segment.startMs + span * offset / text.length, clean)
                }
                from = end
            }
        }
        return result
    }

    fun isQuestion(text: String): Boolean =
        text.trimEnd().endsWith('?') && ConversationAnalytics.countWords(text) >= MIN_QUESTION_WORDS

    /** A promise ("подготовлю"), a request ("пришлите") or a plan ("давайте перенесём"). */
    fun isAction(text: String): Boolean {
        val words = tokens(text)
        if (words.size < MIN_ACTION_WORDS) return false
        if (isQuestion(text)) {
            // Only a request phrased as a question: "Сможешь прислать отчёт?"
            return words.any { it in REQUEST_WORDS } && words.any { it in TASK_INFINITIVES }
        }
        words.forEachIndexed { i, w ->
            if (w in ACTION_WORDS && words.getOrNull(i - 1) != "не") return true
            if ((w == "давай" || w == "давайте") && words.drop(i + 1).take(2).any(::isJointPlan)) return true
        }
        val joined = words.joinToString(" ", prefix = " ", postfix = " ")
        return ACTION_PHRASES.any { " $it " in joined }
    }

    /**
     * Luhn-style extractive summary: sentences dense in words that recur across the
     * conversation, without near-duplicates.
     */
    fun summary(sentences: List<Sentence>, limit: Int = summaryLength(sentences.size)): List<Sentence> {
        if (sentences.size < MIN_SENTENCES_FOR_SUMMARY) return emptyList()
        val stems = sentences.map { ConversationAnalytics.contentStems(it.text) }
        val frequency = HashMap<String, Int>()
        stems.forEach { set -> set.forEach { frequency[it] = (frequency[it] ?: 0) + 1 } }
        val ranked = sentences.indices
            .filter { ConversationAnalytics.countWords(sentences[it].text) in MIN_SUMMARY_WORDS..MAX_SUMMARY_WORDS }
            .filterNot { isQuestion(sentences[it].text) }
            .map { i ->
                val weight = stems[i].sumOf { (frequency.getValue(it) - 1).toDouble() }
                val bonus = if (tokens(sentences[i].text).any { it in DECISION_WORDS }) DECISION_BONUS else 1.0
                i to weight / sqrt(stems[i].size + 1.0) * bonus
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        val chosen = ArrayList<Int>()
        for ((i, _) in ranked) {
            if (chosen.size >= limit) break
            if (chosen.none { overlap(stems[it], stems[i]) > MAX_OVERLAP }) chosen += i
        }
        return chosen.sorted().map { sentences[it] }
    }

    private fun actions(sentences: List<Sentence>, today: LocalDate): List<ActionItem> =
        sentences.filterIndexed { i, s ->
            val reply = sentences.getOrNull(i + 1)
            // "— Отчёт к понедельнику. — Договорились." The proposal is the task.
            val accepted = reply != null && reply.speaker != s.speaker && isAcceptance(reply.text) &&
                tokens(s.text).size >= MIN_ACTION_WORDS
            isAction(s.text) || accepted
        }
            .take(MAX_ITEMS)
            .map { ActionItem(it, Deadlines.find(it.text, today)) }

    private fun isAcceptance(text: String): Boolean {
        val words = tokens(text)
        return words.size <= 3 && words.any { it in ACCEPTANCE_WORDS }
    }

    private fun isJointPlan(word: String): Boolean =
        word !in IDLE_PLANS && JOINT_ENDINGS.any { word.endsWith(it) }

    private fun summaryLength(sentences: Int): Int = (sqrt(sentences.toDouble()) / 1.5).roundToInt().coerceIn(2, 5)

    private fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.count { it in b }.toDouble() / (a.size + b.size - a.count { it in b })
    }

    internal fun tokens(text: String): List<String> =
        token.findAll(normalize(text)).map { it.value }.toList()

    internal fun normalize(text: String): String = text.lowercase().replace('ё', 'е').replace('’', '\'')

    private const val MAX_ITEMS = 50
    private const val MIN_QUESTION_WORDS = 3
    private const val MIN_ACTION_WORDS = 3
    private const val MIN_SENTENCES_FOR_SUMMARY = 6
    private const val MIN_SUMMARY_WORDS = 6
    private const val MAX_SUMMARY_WORDS = 45
    private const val MAX_OVERLAP = 0.5
    private const val DECISION_BONUS = 1.3

    private fun words(text: String): Set<String> = text.trim().split(Regex("\\s+")).toSet()

    /** Obligations, first-person promises and requests; ambiguous forms ("пришли") are left out. */
    private val ACTION_WORDS = words(
        """
        нужно надо необходимо должен должна должно должны обязательно поручаю поручить дедлайн
        договорились договоримся решили желательно
        сделаю сделаем подготовлю подготовим отправлю отправим пришлю пришлем скину скинем позвоню
        позвоним наберу напишу напишем проверю проверим уточню уточним узнаю согласую согласуем
        займусь займемся закажу закажем куплю купим оплачу оплатим назначу назначим перенесу
        перенесем исправлю исправим доделаю доделаем допишу отвечу ответим организую организуем
        соберу соберем подпишу подпишем вышлю вышлем передам передадим обновлю обновим запущу
        запустим посчитаю посчитаем созвонимся встретимся обсудим свяжусь свяжемся напомню напомним
        разберусь заберу привезу принесу
        сделай сделайте подготовь подготовьте отправь отправьте пришлите скинь скиньте позвони
        позвоните набери наберите напиши напишите проверь проверьте уточни уточните узнай узнайте
        согласуй согласуйте займись займитесь закажи закажите купи купите оплати оплатите назначь
        назначьте перенеси перенесите исправь исправьте доделай доделайте ответь ответьте организуй
        организуйте собери соберите подпиши подпишите вышлите передай передайте обнови обновите
        запусти запустите посчитай посчитайте свяжись свяжитесь напомни напомните разберись
        разберитесь забери заберите привези привезите принеси принесите
        todo deadline
        """,
    )

    private val ACTION_PHRASES = listOf(
        "не забудь", "не забудьте", "не забыть", "беру на себя", "возьму на себя", "за мной",
        "i will", "i'll", "we will", "we'll", "need to", "needs to", "have to", "has to", "let's",
        "action item", "follow up",
    )

    /** "Сможешь…", "Можете…" in a question make it a request. */
    private val REQUEST_WORDS = words("можешь сможешь можете сможете могли мог могла could can")

    private val TASK_INFINITIVES = words(
        """
        сделать подготовить отправить прислать скинуть позвонить набрать написать проверить уточнить
        узнать согласовать заказать купить оплатить назначить перенести исправить доделать ответить
        организовать собрать подписать выслать передать обновить запустить посчитать напомнить
        связаться забрать привезти принести send check call prepare
        """,
    )

    /** "Давайте посмотрим" is not a plan. */
    private val IDLE_PLANS = words("посмотрим подумаем начнем продолжим послушаем попробуем познакомимся разберемся")
    private val JOINT_ENDINGS = listOf("ем", "им", "емся", "имся")

    private val ACCEPTANCE_WORDS = words("договорились сделаю сделаем принято беру deal")
    private val DECISION_WORDS = words("договорились решили итак итог итого главное предлагаю договоренность")
}

/** Finds and resolves deadlines: "завтра к 15:00", "к пятнице", "через неделю", "до 5 ноября". */
object Deadlines {

    private const val B = "(?<![\\p{L}\\p{N}])"
    private const val E = "(?![\\p{L}\\p{N}])"

    private class Hit(val range: IntRange, val date: LocalDate?, val time: LocalTime? = null)

    private val dayWord = Regex("${B}(сегодня|завтра|послезавтра|today|tonight|tomorrow)$E")
    private val weekday = Regex(
        "${B}(?:к|ко|до|в|во|на|by|on|until|before)\\s+(?:(следующ\\p{L}*|эт\\p{L}*|next|this)\\s+)?" +
            "(понедельник\\p{L}*|вторник\\p{L}*|сред[ауеы]|четверг\\p{L}*|пятниц[ауеы]|суббот[ауеы]|воскресень[еяю]|" +
            "monday|tuesday|wednesday|thursday|friday|saturday|sunday)$E",
    )
    private val partOfDay = Regex("${B}(?:до|к)\\s+(?:вечера|вечеру|обеда|обеду|конц[ау]\\s+(?:рабочего\\s+)?дня)$E")
    private val periodEnd = Regex("${B}(?:до|к)\\s+конц[ау]\\s+(недели|месяца|квартала|года)$E")
    private val englishEnd = Regex("${B}(?:by\\s+)?(?:the\\s+)?end\\s+of\\s+(?:the\\s+)?(day|week|month)$E")
    private val week = Regex("${B}(?:на|в)\\s+(этой|следующей|эту|следующую)\\s+недел[ею]$E|${B}(next|this)\\s+week$E")
    private val within = Regex("${B}в\\s+течени[ие]\\s+(дня|недели|месяца)$E")
    private val after = Regex(
        "${B}через\\s+(?:(\\d{1,3}|один|одну|два|две|три|четыре|пять|шесть|семь|восемь|девять|десять|пару|несколько)\\s+)?" +
            "(минут\\p{L}*|час\\p{L}*|день|дн[яейю]\\p{L}*|недел\\p{L}*|месяц\\p{L}*)$E",
    )
    private val monthDate = Regex(
        "${B}(?:(?:к|до|на|by)\\s+)?(\\d{1,2})(?:-?(?:го|е|ое))?\\s+" +
            "(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)$E",
    )
    private val dayOfMonth = Regex("${B}(?:к|до|на)\\s+(\\d{1,2})(?:-?(?:го|е|ое|му|ому))?\\s+числ[ауо]$E")
    private val clock = Regex("${B}(?:в|к|до|на|by|at)\\s+(\\d{1,2})[:.](\\d{2})$E")
    private val hour = Regex("${B}(?:в|к|до)\\s+(\\d{1,2})\\s+(час\\p{L}*|утра|вечера|дня|ночи)$E")

    private val months = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val numbers = mapOf(
        "один" to 1, "одну" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5,
        "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10, "пару" to 2,
    )

    fun find(text: String, today: LocalDate): Deadline? {
        val s = ConversationHighlights.normalize(text)
        val hits = ArrayList<Hit>()

        dayWord.findAll(s).forEach {
            val days = when (it.groupValues[1]) {
                "завтра", "tomorrow" -> 1L
                "послезавтра" -> 2L
                else -> 0L
            }
            hits += Hit(it.range, today.plusDays(days))
        }
        weekday.findAll(s).forEach {
            val day = weekdayOf(it.groupValues[2])
            val modifier = it.groupValues[1]
            val date = when {
                modifier.startsWith("следующ") || modifier == "next" ->
                    today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(day))
                modifier.startsWith("эт") || modifier == "this" -> today.with(TemporalAdjusters.nextOrSame(day))
                else -> today.with(TemporalAdjusters.next(day))
            }
            hits += Hit(it.range, date)
        }
        partOfDay.findAll(s).forEach { hits += Hit(it.range, today) }
        periodEnd.findAll(s).forEach { hits += Hit(it.range, endOf(it.groupValues[1], today)) }
        englishEnd.findAll(s).forEach {
            val period = when (it.groupValues[1]) {
                "week" -> "недели"
                "month" -> "месяца"
                else -> "дня"
            }
            hits += Hit(it.range, if (period == "дня") today else endOf(period, today))
        }
        week.findAll(s).forEach {
            val which = it.groupValues[1].ifEmpty { it.groupValues[2] }
            val next = which.startsWith("следующ") || which == "next"
            hits += Hit(it.range, if (next) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else endOf("недели", today))
        }
        within.findAll(s).forEach {
            val date = when (it.groupValues[1]) {
                "дня" -> today.plusDays(1)
                "недели" -> today.plusWeeks(1)
                else -> today.plusMonths(1)
            }
            hits += Hit(it.range, date)
        }
        after.findAll(s).forEach {
            val count = it.groupValues[1].let { n -> n.toLongOrNull() ?: numbers[n]?.toLong() ?: if (n.isEmpty()) 1L else null }
            val unit = it.groupValues[2]
            val date = when {
                count == null -> null // "через несколько дней"
                unit.startsWith("минут") || unit.startsWith("час") -> today
                unit.startsWith("недел") -> today.plusWeeks(count)
                unit.startsWith("месяц") -> today.plusMonths(count)
                else -> today.plusDays(count)
            }
            hits += Hit(it.range, date)
        }
        monthDate.findAll(s).forEach {
            val date = runCatching {
                val d = LocalDate.of(today.year, months.indexOf(it.groupValues[2]) + 1, it.groupValues[1].toInt())
                if (d.isBefore(today)) d.plusYears(1) else d
            }.getOrNull()
            hits += Hit(it.range, date)
        }
        dayOfMonth.findAll(s).forEach {
            val day = it.groupValues[1].toInt()
            val date = if (day in 1..31) {
                val month = if (day < today.dayOfMonth) today.plusMonths(1) else today
                month.withDayOfMonth(day.coerceAtMost(month.lengthOfMonth()))
            } else {
                null
            }
            hits += Hit(it.range, date)
        }
        clock.findAll(s).forEach {
            val time = runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull()
            if (time != null) hits += Hit(it.range, null, time)
        }
        hour.findAll(s).forEach {
            var h = it.groupValues[1].toInt()
            if (it.groupValues[2] in setOf("вечера", "дня") && h < 12) h += 12
            if (h in 0..23) hits += Hit(it.range, null, LocalTime.of(h, 0))
        }

        if (hits.isEmpty()) return null
        // Overlapping matches ("в пятницу" inside "в следующую пятницу") keep the longest one.
        val kept = hits.sortedWith(compareBy<Hit> { it.range.first }.thenByDescending { it.range.last })
            .fold(ArrayList<Hit>()) { acc, hit ->
                if (acc.none { it.range.first <= hit.range.first && hit.range.last <= it.range.last }) acc += hit
                acc
            }
        val time = kept.firstNotNullOfOrNull { it.time }
        val date = kept.firstNotNullOfOrNull { it.date } ?: if (time != null) today else null
        val phrase = kept.joinToString(" ") { s.substring(it.range) }
        return Deadline(phrase, date, time)
    }

    private fun weekdayOf(word: String): DayOfWeek = when {
        word.startsWith("понедельник") || word == "monday" -> DayOfWeek.MONDAY
        word.startsWith("вторник") || word == "tuesday" -> DayOfWeek.TUESDAY
        word.startsWith("сред") || word == "wednesday" -> DayOfWeek.WEDNESDAY
        word.startsWith("четверг") || word == "thursday" -> DayOfWeek.THURSDAY
        word.startsWith("пятниц") || word == "friday" -> DayOfWeek.FRIDAY
        word.startsWith("суббот") || word == "saturday" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    /** The working week ends on Friday; at the weekend "до конца недели" means Sunday. */
    private fun endOf(period: String, today: LocalDate): LocalDate = when (period) {
        "недели" -> if (today.dayOfWeek <= DayOfWeek.FRIDAY) {
            today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        } else {
            today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        }
        "месяца" -> today.with(TemporalAdjusters.lastDayOfMonth())
        "квартала" -> today.withMonth((today.monthValue - 1) / 3 * 3 + 3).with(TemporalAdjusters.lastDayOfMonth())
        else -> today.with(TemporalAdjusters.lastDayOfYear())
    }
}
