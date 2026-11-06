# Design

## Context

FENEK already does the whole speech path on the device (decode → diarize → recognize → highlights) with sherpa-onnx, see the archived baseline specs in `openspec/specs/`. What the hackathon task needs on top: a local LLM in the pipeline, the exact `transcript` JSON, models available with no internet, and a documented submission. See proposal.md for the motivation.

Constraints that shape the approach:
- The app runs `TranscriptionPipeline` in a foreground service; only one job at a time; the recognizer is closed at the end of recognition, so a second large model can be loaded afterwards without holding both.
- Transcripts are JSON files read with `ignoreUnknownKeys`, so new optional fields are backward compatible.
- sherpa-onnx has no LLM; the LLM needs a separate native runtime.
- Scoring (transcription 40, diarization 25, timestamps 10, speed 10, UX 10, defence 5) means the LLM must not lower WER and should cost little RTF.
- The task forbids online APIs and remote compute, not downloading models; the existing downloader (resume, mirrors, size checks) is reused for the LLM. The models must be on the device when the check starts, so the app has to be opened online once before it.
- I cannot run a real phone in this environment: all speed and quality numbers below are estimates that tasks 11.x must replace with measurements.

## Goals / Non-Goals

**Goals:**
- A local LLM that demonstrably takes part in producing the transcript, without being able to damage it.
- After one online first launch that downloads the default set, the app works in airplane mode with no manual model setup.
- The exported JSON is accepted by a strict parser of the task's schema.
- Every number in the submission docs comes from a recorded measurement.

**Non-Goals:**
- Noise suppression, per-fragment re-recognition, NPU/GPU acceleration of the recognizer, overlapping-speech transcription (task's "Ultra" items).
- Regenerating the LLM summary after the user edits text (it is produced once per job).
- Streaming LLM output in the UI.
- Changing the diarization or recognition algorithms.

## Decisions

### D1. LLM runtime: llama.cpp (GGUF) through JNI
Build llama.cpp with the NDK (CMake, `arm64-v8a` and `x86_64` for the emulator; no 32-bit ABI, an LLM on such phones is unusable) and wrap it in a small Kotlin interface `LlmEngine` (`load`, `generate(prompt, grammar?, maxTokens, timeout)`, `close`). The interface keeps the correction and summary code testable with a fake engine on the JVM.
- Why: GGUF has the widest choice of small instruct models, ARM dot-product/i8mm kernels are good, and GBNF grammars give guaranteed output structure (D5).
- Alternatives: MediaPipe LLM Inference / LiteRT-LM (easy Maven integration but fewer models and no grammar constraints); ONNX Runtime GenAI (a second ONNX Runtime next to sherpa-onnx's would clash); MLC LLM (heavy toolchain).
- At implementation time use the maintained Android example of llama.cpp as the JNI base, or a maintained Maven wrapper if one exists then; pin the llama.cpp commit.

### D2. Default model: Qwen2.5-1.5B-Instruct, Q4_K_M (about 1.1 GB)
- Why: the best Russian quality among models that fit a 4 GB phone; instruct-tuned; Apache-2.0.
- Alternatives: Qwen2.5-0.5B-Instruct (about 0.4 GB, about 3× faster, but visibly worse Russian, likely to fail the guard often); Gemma-3-1B-it (about 0.8 GB, decent Russian, restrictive licence); Llama-3.2-1B-Instruct (weaker Russian).
- The choice is provisional: task 11 compares them on the real recordings by WER and RTF and the catalog default follows the result. Because the catalog and settings address the model by id, changing it changes no code.

### D3. LLM runs after recognition, on phrases, with the recognizer closed
Order: recognition → **recoverable copy** → correction → summary → final save. The recognizer is closed before the LLM loads (existing `finally`), and the LLM is closed in its own `finally`.
- Why a recoverable copy: a native crash or kill in the LLM cannot be caught in Kotlin. The transcript with recognized text is written to `filesDir/pending/<id>.json` before the LLM loads; at the next launch any pending file is turned into a normal transcript (keeping the favourite flag of the one it replaces), and the job's `finally` deletes it on completion, failure or cancellation. It is a separate file, not a repository entry, so a cancelled job still leaves nothing in the list and a redo does not overwrite the old transcript before it is finished.
- A marker file is written before the LLM is loaded and deleted after it is released; if it exists at startup the previous run died inside the LLM, so both LLM switches are turned off and the user is told once.
- Before loading, check available memory; if it is below the model's need, skip the LLM stages (a documented fallback, not an error).

### D4. Correction protocol: numbered batches, greedy decoding, hard guard
- Phrases are grouped in batches of up to 8 phrases or about 600 input tokens; phrases of two words or fewer are not sent. The prompt (Russian) asks to restore punctuation and capitals and to fix only obvious recognition errors, to keep the number of lines, not to add or drop content, and to answer as `N: text` lines. Previous and next phrases are given as read-only context.
- Decoding is greedy (temperature 0) so runs repeat; `max_new_tokens` = 1.5 × input + 16; timeout 60 s per batch.
- Guard per phrase (word-level, after lowercasing, `ё→е`, stripping punctuation): accept if edit distance ≤ 30 % of the longer side, word count in 70–140 % of the original, and every digit sequence of the original still present; otherwise keep the recognized text. A batch that cannot be parsed keeps all its phrases.
- Alternatives: one call per phrase (more prompt overhead, slower); the whole transcript in one call (context limits, higher hallucination risk, one failure loses everything).
- Because punctuation is stripped before comparison, pure punctuation/casing fixes always pass, which is the main benefit for models without punctuation (Zipformer, Whisper Base).

### D5. Summary and tasks: grammar-constrained JSON with phrase references
The prompt receives numbered phrases `12 [Анна]: text`. A GBNF grammar forces `{"summary":[{"phrase":int,"text":str}],"tasks":[{"phrase":int,"text":str}]}` so small models cannot produce broken JSON. Items whose `phrase` does not exist are dropped. Long conversations are split into windows of about 1500 tokens, results merged, near-duplicates removed, capped at 5 points and 50 tasks. Deadlines are found by the existing rule-based `Deadlines` on the task text.
- Items are stored by `startMs` and speaker (not by phrase index) so they stay valid after the user deletes or edits phrases.
- Questions stay rule-based: they are reliable and free.

### D6. Data model additions (backward compatible)
- `Segment.rawText: String? = null` — set only when the LLM changed the phrase.
- `Transcript.llmModelId: String?`, `textCorrected: Boolean = false`, `llmSummary: List<LlmItem>?`, `llmTasks: List<LlmItem>?`; `LlmItem(startMs, speaker, text)`.
- RTF is computed (`processingMs / durationMs`), not stored.

### D7. Models are downloaded on first launch (no bundling)
The default set (recognition, segmentation, voice fingerprints and the LLM) is downloaded by the existing `ModelManager` and `Downloader` (resume with `.part`, mirrors, size checks, free-space check, foreground service). New work is only: LLM entries in the catalog with exact sizes and Hugging Face / mirror URLs, a single "download all" action in the setup card that includes the optional LLM when an LLM mode is on, and a separate "download language model" offer when only the LLM is missing.
- Why: it keeps the APK small (tens of MB instead of over a gigabyte), needs no build-time model fetching, no first-launch copy and no doubled disk use, and the task allows downloading models; only online processing is forbidden.
- Alternatives considered: bundling all models in the APK as assets (works with no network at all, but a 1.4 GB APK, a 10–30 s copy on first launch, about 2.8 GB of disk and a heavier build and release); a second "models" APK (experts install one APK).
- Trade-off accepted: the app does nothing until the models are downloaded once. Mitigations: the README and LIMITATIONS tell to open the app online before the offline check; the demo shows the download and then airplane mode. If the check devices turn out to have no network at all, the fallback is a second build with bundled assets (not planned here).

### D8. Task JSON export
`Exporter.taskJson` writes `{"transcript":[{speaker_id,start_ms,end_ms,text}]}` with a dedicated `@Serializable` class using `@SerialName` for snake_case, compact enough but human-readable. `speaker_id` is `speaker_<id+1>`; renaming a participant never changes it. One entry per phrase.
- Why per phrase and not per turn: the phrase is the unit with its own measured timestamps; diarization already bridges pauses under 1 s, so splits appear only on real pauses or at the 25 s limit. Merging at export would give a reference-style "long reply" but hide real boundaries. This is a trade-off for the timestamp criterion; revisit after the first comparison with a reference.
- "Save to file" uses `ActivityResultContracts.CreateDocument(mime)` and writes the same rendered text through the content resolver; no storage permission is needed.

### D9. WER / RTF tool: stdlib Python script
`tools/eval/wer.py` (no third-party packages) normalizes (lowercase, `ё→е`, strip punctuation), computes word-level edit distance, validates the schema of the exported JSON, and prints `WER`, speaker count and `RTF = processing / duration`. It has `unittest` tests. Python is chosen over a Gradle module because the experts or teammates can run it anywhere without building the app.

### D10. Repository layout
The Gradle project stays at the repository root (moving it under `src/android/` would churn every path, build script and the OpenSpec references). `src/README.md` states the platform (Android), where the project lives and how to build it. The template's `docs/PROTOCOL.md` belongs to an unrelated task: it is replaced by `docs/OUTPUT_FORMAT.md` describing the JSON schema and the extended JSON. `artifacts/test-results/results.csv` gets the columns listed in the `submission-package` spec.

## Risks / Trade-offs

- **LLM lowers WER** (hallucinated rewrites) → the guard of D4, the recognized text is kept, and "Исправлять текст" is off by default; the code stays for models without punctuation.
- **RTF cost of the LLM** (estimate: 1.5B Q4 at 8–15 tokens/s; a 5-minute dialogue has about 700 words ≈ 1500 tokens in and out, so about 2–3 minutes, an RTF increase of 0.4–0.8) → skip short phrases, batch to reuse the prompt, small-model option, and the switches; measure on a real phone.
- **Native crash in the LLM** kills the process → checkpoint save and the crash marker (D3).
- **A first launch without a network at the check** → the app shows the reason and a retry button; docs and the demo say to open it online once; resumable downloads make a flaky connection acceptable.
- **A 1.1 GB model download** on a slow or metered connection → progress with sizes, resume, the LLM is offered separately so the required 265 MB can be fetched first.
- **Low-memory phones** → memory check before loading, fall back to no LLM.
- **Two native runtimes** (sherpa-onnx and llama.cpp) both use their own thread pools → run them sequentially (D3) and give the LLM `numThreads` from settings.
- **Build complexity** (NDK/CMake, R8 keep rules for JNI classes) → the first task group proves the build before any feature work, and `proguard-rules.pro` keeps the JNI classes.
- **Unknown evaluation normalization** (case, punctuation, numbers as digits or words) → the app keeps the model's own punctuation and casing, which is what the models were trained to produce; the tool uses the common normalization. If the organizers publish a normalization, the post-processing can be adapted.
- **JNI decoding of Russian** (UTF-8 across the boundary, partial multi-byte tokens) → the wrapper accumulates bytes and decodes at the end, covered by a test with Cyrillic.

## Migration Plan

- Existing transcripts need no migration; new fields default to absent.
- Users who had downloaded models keep them; only the missing ones (the LLM) are offered.
- The rich JSON export is renamed in the menu, not removed, so no data format is lost.
- Rollback: both LLM switches off restores the previous behaviour; the code path without an LLM is unchanged and covered by the existing tests.

## Open Questions

- The default LLM (D2) stays Qwen2.5 1.5B on the strength of the emulator runs (the 0.5B summary was unusable); phone measurements may still move it. "Исправлять текст" is **off by default** (decided with the user): on GigaAM, which already punctuates, correction left WER unchanged and added 37–178 s to a 54 s job in the emulator. The summary stays on, so the LLM remains part of every default job.
- Whether the organizers' reference transcripts are per phrase or per turn — affects only the timestamp score, not the schema.
