#!/usr/bin/env python3
"""Accuracy and speed of an exported transcript, the way the hackathon organizers measure it.

    python tools/eval/wer.py --reference ref.json --hypothesis export.json \
        --processing-seconds 30 --duration-seconds 120

* WER: word error rate of the exported text against the reference, after lowercasing, mapping
  "ё" to "е" and removing punctuation.
* Speakers: the number of different `speaker_id` values in the export (and in the reference).
* RTF: real-time factor, processing time divided by the duration of the recording.
* The export is checked against the required schema:
  {"transcript": [{"speaker_id": str, "start_ms": int, "end_ms": int, "text": str}]}.

The reference is either a JSON file of the same schema or a plain text file.
Only the Python standard library is used. Exit status: 0 on success, 2 for a file that does
not follow the schema.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

REQUIRED_FIELDS = {"speaker_id": str, "start_ms": int, "end_ms": int, "text": str}


class SchemaError(ValueError):
    """The file does not follow the required `transcript` schema."""

    def __init__(self, problems: list[str]):
        super().__init__("; ".join(problems))
        self.problems = problems


def validate_schema(document: object) -> list[str]:
    """Returns the list of problems; an empty list means the document is valid."""
    if not isinstance(document, dict):
        return ["document: expected an object with the key 'transcript'"]
    problems: list[str] = []
    extra = sorted(set(document) - {"transcript"})
    if extra:
        problems.append(f"document: unexpected keys {extra}")
    items = document.get("transcript")
    if not isinstance(items, list):
        return problems + ["transcript: expected an array"]
    for index, item in enumerate(items):
        where = f"transcript[{index}]"
        if not isinstance(item, dict):
            problems.append(f"{where}: expected an object")
            continue
        for name, kind in REQUIRED_FIELDS.items():
            if name not in item:
                problems.append(f"{where}.{name}: missing")
                continue
            value = item[name]
            # bool is a subclass of int in Python, and JSON `true` is not a time.
            if kind is int and (isinstance(value, bool) or not isinstance(value, int)):
                problems.append(f"{where}.{name}: expected integer, got {type(value).__name__}")
            elif kind is str and not isinstance(value, str):
                problems.append(f"{where}.{name}: expected string, got {type(value).__name__}")
        unknown = sorted(set(item) - set(REQUIRED_FIELDS))
        if unknown:
            problems.append(f"{where}: unexpected keys {unknown}")
        start, end = item.get("start_ms"), item.get("end_ms")
        if isinstance(start, int) and isinstance(end, int) and not isinstance(start, bool) and end < start:
            problems.append(f"{where}: end_ms is before start_ms")
    return problems


def normalize(text: str) -> list[str]:
    """Lowercase, 'ё' -> 'е', punctuation removed; returns the words."""
    text = text.lower().replace("ё", "е")
    text = re.sub(r"[^\w\s]|_", " ", text, flags=re.UNICODE)
    return text.split()


@dataclass(frozen=True)
class WerResult:
    substitutions: int
    deletions: int
    insertions: int
    reference_words: int

    @property
    def errors(self) -> int:
        return self.substitutions + self.deletions + self.insertions

    @property
    def wer(self) -> float:
        if self.reference_words == 0:
            return 0.0 if self.errors == 0 else 1.0
        return self.errors / self.reference_words


def word_error_rate(reference: list[str], hypothesis: list[str]) -> WerResult:
    """Word-level edit distance with the numbers of substitutions, deletions and insertions."""
    # Each cell holds (distance, substitutions, deletions, insertions); two rows are enough.
    previous = [(j, 0, 0, j) for j in range(len(hypothesis) + 1)]
    for i in range(1, len(reference) + 1):
        current = [(i, 0, i, 0)]
        for j in range(1, len(hypothesis) + 1):
            if reference[i - 1] == hypothesis[j - 1]:
                current.append(previous[j - 1])
                continue
            sub, dele, ins = previous[j - 1], previous[j], current[j - 1]
            best = min(
                (sub[0] + 1, sub[1] + 1, sub[2], sub[3]),
                (dele[0] + 1, dele[1], dele[2] + 1, dele[3]),
                (ins[0] + 1, ins[1], ins[2], ins[3] + 1),
            )
            current.append(best)
        previous = current
    _, s, d, i = previous[-1]
    return WerResult(s, d, i, len(reference))


def load_transcript(path: Path, *, strict: bool) -> dict:
    """Reads a JSON file of the schema. With `strict` a schema problem raises SchemaError."""
    document = json.loads(path.read_text(encoding="utf-8"))
    problems = validate_schema(document)
    if problems and strict:
        raise SchemaError(problems)
    return document


def reference_text_and_speakers(path: Path) -> tuple[str, set[str], int]:
    """Text, speaker ids and the end of the last phrase (ms) of the reference file."""
    if path.suffix.lower() == ".json":
        document = load_transcript(path, strict=True)
        items = sorted(document["transcript"], key=lambda x: x["start_ms"])
        return (
            " ".join(x["text"] for x in items),
            {x["speaker_id"] for x in items},
            max((x["end_ms"] for x in items), default=0),
        )
    return path.read_text(encoding="utf-8"), set(), 0


def rtf(processing_seconds: float, duration_seconds: float) -> float:
    if duration_seconds <= 0:
        raise ValueError("the duration of the recording must be positive")
    return processing_seconds / duration_seconds


def evaluate(
    reference: Path,
    hypothesis: Path,
    processing_seconds: float | None = None,
    duration_seconds: float | None = None,
) -> dict:
    document = load_transcript(hypothesis, strict=True)
    items = sorted(document["transcript"], key=lambda x: x["start_ms"])
    ref_text, ref_speakers, ref_end_ms = reference_text_and_speakers(reference)
    result = word_error_rate(normalize(ref_text), normalize(" ".join(x["text"] for x in items)))
    report = {
        "wer": result.wer,
        "substitutions": result.substitutions,
        "deletions": result.deletions,
        "insertions": result.insertions,
        "reference_words": result.reference_words,
        "speakers_detected": len({x["speaker_id"] for x in items}),
        "speakers_reference": len(ref_speakers) if ref_speakers else None,
        "rtf": None,
    }
    if processing_seconds is not None:
        duration = duration_seconds
        if duration is None:
            hypothesis_end = max((x["end_ms"] for x in items), default=0)
            duration = max(ref_end_ms, hypothesis_end) / 1000.0
        report["rtf"] = rtf(processing_seconds, duration)
        report["duration_seconds"] = duration
    return report


def format_report(report: dict) -> str:
    lines = [
        f"WER: {report['wer'] * 100:.2f}% "
        f"(S={report['substitutions']} D={report['deletions']} I={report['insertions']} "
        f"N={report['reference_words']})"
    ]
    reference = report["speakers_reference"]
    lines.append(
        f"Speakers detected: {report['speakers_detected']}"
        + (f" (reference: {reference})" if reference is not None else "")
    )
    if report["rtf"] is not None:
        lines.append(f"RTF: {report['rtf']:.3f}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--reference", required=True, type=Path, help="reference transcript (JSON of the schema, or plain text)")
    parser.add_argument("--hypothesis", required=True, type=Path, help="JSON exported by the app")
    parser.add_argument("--processing-seconds", type=float, help="processing time of the job, for the RTF")
    parser.add_argument("--duration-seconds", type=float, help="duration of the recording (default: end of the last phrase)")
    args = parser.parse_args(argv)
    try:
        report = evaluate(args.reference, args.hypothesis, args.processing_seconds, args.duration_seconds)
    except SchemaError as error:
        print("The file does not follow the required schema:", file=sys.stderr)
        for problem in error.problems:
            print(f"  {problem}", file=sys.stderr)
        return 2
    print(format_report(report))
    return 0


if __name__ == "__main__":
    sys.exit(main())
