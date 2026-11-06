import json
import tempfile
import unittest
from pathlib import Path

import wer


def doc(*items):
    return {"transcript": [
        {"speaker_id": s, "start_ms": a, "end_ms": b, "text": t} for s, a, b, t in items
    ]}


class WerTest(unittest.TestCase):
    def rate(self, reference: str, hypothesis: str) -> wer.WerResult:
        return wer.word_error_rate(wer.normalize(reference), wer.normalize(hypothesis))

    def test_identical_texts_have_zero_wer(self):
        result = self.rate("добрый день как дела", "добрый день как дела")
        self.assertEqual(0.0, result.wer)
        self.assertEqual(0, result.errors)

    def test_one_substitution(self):
        result = self.rate("добрый день как дела", "добрый вечер как дела")
        self.assertEqual((1, 0, 0), (result.substitutions, result.deletions, result.insertions))
        self.assertAlmostEqual(0.25, result.wer)

    def test_one_insertion(self):
        result = self.rate("добрый день", "добрый хороший день")
        self.assertEqual((0, 0, 1), (result.substitutions, result.deletions, result.insertions))
        self.assertAlmostEqual(0.5, result.wer)

    def test_one_deletion(self):
        result = self.rate("добрый хороший день", "добрый день")
        self.assertEqual((0, 1, 0), (result.substitutions, result.deletions, result.insertions))
        self.assertAlmostEqual(1 / 3, result.wer)

    def test_yo_and_ye_are_the_same_letter(self):
        self.assertEqual(0.0, self.rate("всё хорошо", "Все хорошо").wer)

    def test_case_and_punctuation_do_not_count(self):
        self.assertEqual(0.0, self.rate("привет как дела", "Привет, как дела?!").wer)
        self.assertEqual(0.0, self.rate("Привет, — как дела?", "привет как дела").wer)

    def test_everything_missing_is_a_wer_of_one(self):
        self.assertAlmostEqual(1.0, self.rate("раз два три", "").wer)

    def test_empty_reference(self):
        self.assertEqual(0.0, self.rate("", "").wer)
        self.assertEqual(1.0, self.rate("", "лишние слова").wer)

    def test_wer_can_exceed_one(self):
        self.assertGreater(self.rate("да", "нет совсем не так").wer, 1.0)


class SchemaTest(unittest.TestCase):
    def test_the_example_from_the_task_is_valid(self):
        example = {"transcript": [{"speaker_id": "speaker_1", "start_ms": 1250, "end_ms": 4380, "text": "Добрый день!"}]}
        self.assertEqual([], wer.validate_schema(example))

    def test_an_empty_transcript_is_valid(self):
        self.assertEqual([], wer.validate_schema({"transcript": []}))

    def test_times_must_be_integers(self):
        problems = wer.validate_schema({"transcript": [
            {"speaker_id": "speaker_1", "start_ms": 1.25, "end_ms": "4380", "text": "x"},
        ]})
        self.assertTrue(any("transcript[0].start_ms" in p for p in problems), problems)
        self.assertTrue(any("transcript[0].end_ms" in p for p in problems), problems)

    def test_a_boolean_is_not_a_time(self):
        problems = wer.validate_schema({"transcript": [
            {"speaker_id": "s", "start_ms": True, "end_ms": 5, "text": "x"},
        ]})
        self.assertTrue(any("start_ms" in p for p in problems), problems)

    def test_the_speaker_id_must_be_a_string(self):
        problems = wer.validate_schema(doc((1, 0, 10, "x")))
        self.assertTrue(any("speaker_id" in p for p in problems), problems)

    def test_missing_and_extra_fields_are_named(self):
        problems = wer.validate_schema({"transcript": [{"speaker_id": "s", "start_ms": 0, "end_ms": 1, "words": []}]})
        self.assertTrue(any("text: missing" in p for p in problems), problems)
        self.assertTrue(any("unexpected keys ['words']" in p for p in problems), problems)

    def test_the_document_must_have_only_the_transcript_key(self):
        self.assertTrue(wer.validate_schema({"transcript": [], "title": "x"}))
        self.assertTrue(wer.validate_schema([]))
        self.assertTrue(wer.validate_schema({"utterances": []}))

    def test_end_before_start_is_reported(self):
        problems = wer.validate_schema(doc(("s", 5000, 4000, "x")))
        self.assertTrue(any("before" in p for p in problems), problems)


class RtfTest(unittest.TestCase):
    def test_thirty_seconds_for_a_two_minute_recording(self):
        self.assertAlmostEqual(0.25, wer.rtf(30, 120))

    def test_slower_than_real_time(self):
        self.assertAlmostEqual(1.5, wer.rtf(90, 60))

    def test_the_duration_must_be_positive(self):
        with self.assertRaises(ValueError):
            wer.rtf(1, 0)


class EndToEndTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)

    def write(self, name: str, content) -> Path:
        path = self.dir / name
        text = content if isinstance(content, str) else json.dumps(content, ensure_ascii=False)
        path.write_text(text, encoding="utf-8")
        return path

    def test_json_reference_and_export(self):
        reference = self.write("ref.json", doc(("a", 0, 2000, "Добрый день."), ("b", 2500, 4000, "Здравствуйте, как дела?")))
        export = self.write("out.json", doc(("speaker_1", 0, 2100, "добрый день"), ("speaker_2", 2600, 4100, "здравствуйте как дела")))
        report = wer.evaluate(reference, export, processing_seconds=30, duration_seconds=120)

        self.assertEqual(0.0, report["wer"])
        self.assertEqual(2, report["speakers_detected"])
        self.assertEqual(2, report["speakers_reference"])
        self.assertAlmostEqual(0.25, report["rtf"])

    def test_plain_text_reference(self):
        reference = self.write("ref.txt", "добрый день как дела")
        export = self.write("out.json", doc(("speaker_1", 0, 1000, "добрый день"), ("speaker_1", 1000, 2000, "как дела")))
        report = wer.evaluate(reference, export)

        self.assertEqual(0.0, report["wer"])
        self.assertIsNone(report["speakers_reference"])
        self.assertIsNone(report["rtf"])

    def test_phrases_are_read_in_time_order(self):
        reference = self.write("ref.txt", "первое второе")
        export = self.write("out.json", doc(("s", 5000, 6000, "второе"), ("s", 0, 1000, "первое")))
        self.assertEqual(0.0, wer.evaluate(reference, export)["wer"])

    def test_the_duration_defaults_to_the_end_of_the_last_phrase(self):
        reference = self.write("ref.txt", "раз")
        export = self.write("out.json", doc(("s", 0, 60_000, "раз")))
        self.assertAlmostEqual(0.5, wer.evaluate(reference, export, processing_seconds=30)["rtf"])

    def test_a_file_that_breaks_the_schema_is_rejected_with_the_reason(self):
        reference = self.write("ref.txt", "раз")
        export = self.write("out.json", {"utterances": [{"speaker": "s", "start": 0.5, "end": 1.0, "text": "раз"}]})
        with self.assertRaises(wer.SchemaError) as caught:
            wer.evaluate(reference, export)
        self.assertTrue(any("transcript" in p for p in caught.exception.problems))

    def test_the_command_line_prints_the_report_and_fails_on_a_bad_schema(self):
        reference = self.write("ref.txt", "раз два")
        good = self.write("good.json", doc(("speaker_1", 0, 1000, "раз два")))
        bad = self.write("bad.json", {"transcript": [{"speaker_id": "s", "start_ms": 1.5, "end_ms": 2, "text": "раз два"}]})

        import contextlib
        import io

        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = wer.main(["--reference", str(reference), "--hypothesis", str(good), "--processing-seconds", "30", "--duration-seconds", "120"])
        self.assertEqual(0, code)
        self.assertIn("WER: 0.00%", out.getvalue())
        self.assertIn("RTF: 0.250", out.getvalue())

        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            code = wer.main(["--reference", str(reference), "--hypothesis", str(bad)])
        self.assertEqual(2, code)
        self.assertIn("start_ms", err.getvalue())


if __name__ == "__main__":
    unittest.main()
