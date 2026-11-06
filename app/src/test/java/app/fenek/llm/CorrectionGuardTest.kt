package app.fenek.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionGuardTest {

    @Test
    fun punctuationAndCasingChangesAreAccepted() {
        assertTrue(CorrectionGuard.accept("привет как дела", "Привет, как дела?"))
        assertTrue(CorrectionGuard.accept("давайте встретимся завтра утром", "Давайте встретимся завтра утром."))
    }

    @Test
    fun yoAndYeAreTheSameLetter() {
        assertTrue(CorrectionGuard.accept("всё хорошо спасибо", "Все хорошо, спасибо."))
    }

    @Test
    fun aSmallFixOfAWordIsAccepted() {
        val recognized = "я думаю что нам надо перенести встречу на следующую недилю потому что я занят"
        val corrected = "Я думаю, что нам надо перенести встречу на следующую неделю, потому что я занят."
        assertTrue(CorrectionGuard.accept(recognized, corrected))
    }

    @Test
    fun aRewriteIsRejected() {
        assertFalse(CorrectionGuard.accept("мы вчера ходили в магазин за хлебом", "Сегодня я поехал на работу на машине."))
    }

    @Test
    fun tooManyChangedWordsAreRejected() {
        // 4 of 10 words differ: more than 30 %.
        val recognized = "один два три четыре пять шесть семь восемь девять десять"
        val corrected = "один два три четыре пять шесть пятница суббота воскресенье понедельник"
        assertFalse(CorrectionGuard.accept(recognized, corrected))
    }

    @Test
    fun aDroppedNumberIsRejected() {
        assertFalse(CorrectionGuard.accept("встреча в 15 часов в офисе", "Встреча в пятнадцать часов в офисе."))
    }

    @Test
    fun aKeptNumberIsAccepted() {
        assertTrue(CorrectionGuard.accept("встреча в 15 часов в офисе", "Встреча в 15 часов в офисе."))
    }

    @Test
    fun emptyAnswersAreRejected() {
        assertFalse(CorrectionGuard.accept("привет как дела", ""))
        assertFalse(CorrectionGuard.accept("привет как дела", "  ... "))
    }

    @Test
    fun phrasesThatGrowOrShrinkTooMuchAreRejected() {
        assertFalse(CorrectionGuard.accept("привет как дела у тебя сегодня", "Привет."))
        assertFalse(CorrectionGuard.accept("привет как дела", "Привет, как дела у тебя сегодня вечером дома?"))
    }

    @Test
    fun wordDistanceCountsSubstitutionsInsertionsAndDeletions() {
        assertEquals(0, CorrectionGuard.distance(listOf("а", "б"), listOf("а", "б")))
        assertEquals(1, CorrectionGuard.distance(listOf("а", "б"), listOf("а", "в")))
        assertEquals(1, CorrectionGuard.distance(listOf("а", "б"), listOf("а", "б", "в")))
        assertEquals(2, CorrectionGuard.distance(listOf("а", "б", "в"), listOf("в")))
    }

    @Test
    fun aDifferentWordInThePlaceOfARecognizedOneIsRejected() {
        // Seen with a 0.5B model: it answered "Правильно" for "Хорошо" and for "Отлично".
        assertFalse(CorrectionGuard.accept("хорошо когда вы сможете прислать мне итоговый вариант", "Правильно, когда вы сможете прислать мне итоговый вариант"))
        assertFalse(CorrectionGuard.accept("отлично тогда я забронирую переговорную комнату и напишу всем", "Правильно тогда я забронирую переговорную комнату и напишу всем"))
    }

    @Test
    fun aTypoFixIsStillAccepted() {
        assertTrue(CorrectionGuard.accept("я подготовлю отчет к пятнице и отпрвлю его завтра утром", "Я подготовлю отчёт к пятнице и отправлю его завтра утром."))
    }

    @Test
    fun alignmentNamesTheSwappedWords() {
        val alignment = CorrectionGuard.align(listOf("а", "хорошо", "б"), listOf("а", "правильно", "б"))
        assertEquals(1, alignment.edits)
        assertEquals(listOf("хорошо" to "правильно"), alignment.substitutions)
    }

    @Test
    fun anInsertedOrDroppedWordIsNotASubstitution() {
        val alignment = CorrectionGuard.align(listOf("а", "б", "в"), listOf("а", "в"))
        assertEquals(1, alignment.edits)
        assertTrue(alignment.substitutions.isEmpty())
    }
}
