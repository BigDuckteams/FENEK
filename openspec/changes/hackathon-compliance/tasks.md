# Tasks

## 1. Build foundation (native LLM, packaging)

- [x] 1.1 Add llama.cpp (pinned commit) with a CMake build to the `app` module for `arm64-v8a` and `x86_64`; verify `./gradlew assembleDebug` succeeds and the APK contains `libllama*.so` for both ABIs
- [x] 1.2 Add the llama.cpp build output to `.gitignore` and R8/ProGuard keep rules for the JNI classes; verify `./gradlew assembleRelease` produces an APK that installs and starts on the emulator
- [x] 1.3 Document the build prerequisites (JDK, SDK, NDK, CMake versions, free disk space) in the README build section and verify the commands work on a clean checkout

## 2. Data model

- [x] 2.1 Add `Segment.rawText`, `Transcript.llmModelId`, `textCorrected`, `llmSummary`, `llmTasks` and `LlmItem(startMs, speaker, text)` as optional fields; verify a unit test decodes a transcript JSON written by version 1.1.0 and encodes/decodes a new one unchanged
- [x] 2.2 Add computed `Transcript.rtf` and verify a unit test for 30 s / 120 s = 0.25 and for a zero duration (no division error)

## 3. LLM engine wrapper

- [x] 3.1 Define the `LlmEngine` interface (load, generate with optional grammar, max tokens, timeout, close) and a deterministic fake for tests; verify the fake is used by the tests of groups 4 and 6
- [x] 3.2 Implement the llama.cpp JNI engine: greedy sampling, GBNF grammar support, UTF-8 accumulation, token and time limits, thread count from settings, release of native memory on close; verify an instrumented or emulator test generates Russian text without mojibake and stops at the limits
- [x] 3.3 Implement memory check and the crash marker file (written before load, removed after release); verify a unit test that the marker is detected at startup and turns both switches off, and that insufficient memory skips loading
- [x] 3.4 Document the engine (runtime choice, model format, limits) in `docs/ARCHITECTURE.md` and verify the document names the pinned llama.cpp commit

## 4. Text correction

- [x] 4.1 Implement the phrase guard (normalization, word-level edit distance ≤ 30 %, word-count ratio 70–140 %, digit preservation); verify unit tests for punctuation-only change, hallucinated rewrite, dropped number, empty answer and short phrase
- [x] 4.2 Implement batching, the Russian prompt, the `N: text` answer parser and per-phrase fallback; verify unit tests with the fake engine for a normal batch, a partly invalid batch, an unparseable batch and a timeout
- [x] 4.3 Store `rawText` only for changed phrases and keep speaker and times untouched; verify a test that phrase count, speakers and timestamps are identical before and after correction and that results repeat on a second run

## 5. Pipeline integration

- [x] 5.1 Add the correction and summary stages to the pipeline with the progress ranges of the spec, the status texts "Правка текста: N из M фраз" and "Выжимка и задачи…", and cancellation between batches; verify unit tests for the ranges (monotonic, ends at 1) with each mode on and off
- [x] 5.2 Write a recoverable copy of the transcript after recognition, run the LLM stages, then save the final transcript and emit "готова" only after that; recover pending copies at startup; verify tests where the LLM stage throws (the job still ends with the recognized text), where a pending copy is recovered with the favourite flag kept, and where cancel leaves no copy
- [x] 5.3 Release the recognizer before loading the LLM and close the LLM in `finally` for success, failure and cancellation; verify with the fake that load and close calls happen in that order in all three cases
- [x] 5.4 Show the processing time and RTF in the transcript header and record the job's wall-clock time including model loading; verify on the emulator that the header shows both after a job

## 6. Summary and tasks from the LLM

- [x] 6.1 Implement the numbered-phrase prompt, the GBNF grammar and the parser that drops items with invalid phrase references and stores `startMs`/speaker; verify unit tests with the fake engine for valid, invalid-reference and empty output
- [x] 6.2 Implement windowed processing of long conversations with merging, near-duplicate removal and the 5/50 caps; verify a unit test with a conversation larger than one window
- [x] 6.3 Resolve deadlines of LLM tasks with the existing deadline rules and prefer LLM highlights in the viewer and Markdown export with the rule-based fallback; verify unit tests that a transcript with and without LLM items show the right source and that the Markdown contains the LLM items
- [x] 6.4 Update the highlights card note to name the source (language model or rules); verify on the emulator with both kinds of transcripts

## 7. Settings

- [x] 7.1 Persist the language model id and the two switches (summary on, correction off by default) with validation of unknown ids; verify unit tests for defaults, persistence and fallback to the default model
- [x] 7.2 Add the "Языковая модель" section (switches, model entry, status, hint, missing-model state) to the settings screen; verify on the emulator that switching persists across restarts and the hint texts match the spec
- [x] 7.3 Show the one-time message after a detected LLM crash and verify it appears once and the switches can be turned on again

## 8. Viewer and editing

- [x] 8.1 Show in the header whether and by which model the text was corrected, and mark corrected phrases with a marker that reveals the recognized text; verify on the emulator with a corrected and an uncorrected transcript
- [x] 8.2 Add "Вернуть распознанный текст" to the phrase dialog (only for phrases with `rawText`) and keep manual edits as the final text; verify unit tests on the view model for restore, manual edit of a corrected phrase and a phrase without `rawText`

## 9. Catalog and first-launch download of the default set

- [x] 9.1 Add the LLM kind and the default model entries (provisional Qwen2.5 1.5B Instruct Q4_K_M plus the benchmark candidates as downloadable-only) to the catalog with exact sizes; verify the catalog test checks ids, sizes and the default
- [x] 9.2 Implement the download planner: the default set for the current settings (required models, plus the LLM when an LLM mode is on), its missing parts and total size, and a single "download all" action that starts them through the existing manager; verify unit tests for first launch, required ready but LLM missing, everything ready, and LLM modes off
- [x] 9.3 Show the planner in the home setup card and in the new-transcription sheet ("Скачать (<size>)", "Скачать языковую модель (<size>)", progress, failure reason with "Повторить", the note that the internet is needed once); verify on the emulator that a fresh install downloads the set, shows progress and resumes after the app is killed, and that with no network it shows the failure and retry
- [x] 9.4 Make the language model optional for a job and keep the setup card only for required models; verify a test that deleting or not downloading the LLM does not block a transcription
- [x] 9.5 Document the first-launch download (sizes, time, that the internet is needed once, how to resume) in `docs/LIMITATIONS.md` and the README and verify the numbers match a measured download

## 10. Export

- [x] 10.1 Implement `taskJson` with `speaker_<id+1>`, integer times, UTF-8 and one entry per phrase, using the corrected text; verify unit tests for the example from the task, renamed speakers, empty transcript, Cyrillic and quotes, and that no extra keys exist
- [x] 10.2 Keep the previous document as "Расширенный JSON" and add processing time, RTF, language model and `rawText`; verify the existing JSON test is adapted and a new test covers the added fields
- [x] 10.3 Update the export menu to six formats and add "Сохранить в файл" through the system create-document dialog for every format; verify on the emulator that the saved file equals the shared text and that cancelling writes nothing
- [x] 10.4 Document both JSON formats in `docs/OUTPUT_FORMAT.md` and verify the examples in it are produced by the exporter test

## 11. Measurement on a real device

- [x] 11.1 Prepare at least three Russian dialogue recordings with reference transcripts (speakers and times for part of them) and store them under `artifacts/test-results/data/` or document where they are; verify the references open in the eval tool
- [ ] 11.2 Run each recording on a real arm64 phone with correction off and on, with each candidate LLM, and record WER, RTF, speaker count and device; verify results.csv has a row per run
- [x] 11.3 Choose the default LLM and the default of "Исправлять текст" from the results, update the catalog and settings default, and verify the changed defaults in the unit tests and in the specs' defaults (update the specs in the same change if the default of correction becomes off)
- [ ] 11.4 Check RTF of the whole pipeline on the target phone and tune thread counts if needed; verify the final RTF is recorded in docs/TEST_RESULTS.md

## 12. Accuracy and speed tool

- [x] 12.1 Implement `tools/eval/wer.py` (normalization, word-level WER, schema validation, speaker count, RTF); verify `python -m unittest` passes for identical texts, one substitution, insertion and deletion, `ё`/`е`, punctuation, bad schema and RTF 30/120
- [x] 12.2 Document the tool usage in `docs/TEST_RESULTS.md` and verify the documented command runs on a sample export

## 13. Submission package

- [x] 13.1 Replace the template placeholders: fill `docs/ARCHITECTURE.md` (context, components, pipeline, models with size and licence, decisions, dependencies, privacy), `docs/LIMITATIONS.md`, `docs/TEST_RESULTS.md`, and replace `docs/PROTOCOL.md` with `docs/OUTPUT_FORMAT.md`; verify no placeholder text remains (`grep` for "replace-me", "DELETEME")
- [x] 13.2 Fill `artifacts/test-results/results.csv` and its README with the columns from the spec and the measured rows; verify every row has a commit hash
- [x] 13.3 Add `src/README.md` (platform, location of the Gradle project, how to build) and `config/example.env` adapted or removed; verify the paths it mentions exist
- [x] 13.4 Update the root README: install, first-launch model download, import, transcribe, export, offline check, build, tests, and refresh the stale parts (clustering description, model table, shortcuts); verify the commands in it run as written
- [ ] 13.5 Build the two release APKs (`./gradlew releaseApks`), attach them to a GitHub Release and put the links in the README; verify the online one installs on a clean phone and the README tells to open it online once before the offline check, and that the fat one works in airplane mode from the first launch
- [ ] 13.6 Record the demo video (install, download the models, switch airplane mode on, import, transcribe, view, export JSON) and link it in `artifacts/demo/README.md`; verify the video shows airplane mode before processing starts

## 14. Final integration checks

- [ ] 14.1 Fresh install of the release APK on a phone: download the models, switch airplane mode on, import a test recording, process with both LLM modes on, export the task JSON, and validate the file with the eval tool; verify schema, WER and RTF match the recorded results
- [x] 14.2 Check that no network traffic occurs during processing (for example `adb shell dumpsys netstats` before and after, or a firewall-blocked run) and that all unit tests (`./gradlew testDebugUnitTest`) and the eval tool tests pass

## 15. Time budget of the LLM stages

- [x] 15.1 Add `LlmBudget` (half the recording's length, 2–10 minutes, requests get what is left, none under 15 s), pass it to the correction and the summary (the correction takes at most half when both are on) and ask the summary windows first/last/middle first; verify the unit tests for the arithmetic, the early stop with two requests of 45 s in a 100 s budget, and the window order
- [x] 15.2 Document the budget in `docs/ARCHITECTURE.md` and `docs/LIMITATIONS.md`; verify on the emulator that a job logs its budget (120 s for a 63 s recording) and ends inside it (an exhausted budget is covered by the unit test only: with the 120 s floor a one-window recording cannot be cut earlier than before on the emulator)

## 16. Recording sheet

- [x] 16.1 Replace the three buttons of a saved recording with a tap that opens a bottom sheet (name, size, duration, format, "Расшифровать", "Поделиться", "Удалить" across the full width); verify the build
- [ ] 16.2 Check on a phone that the card has no buttons and that the sheet texts fit

## 17. Transcription queue and pause

- [x] 17.1 Add `JobQueue` (order, duplicates, pause, resume, start now) and `PauseGate`; verify the unit tests in `JobQueueTest`
- [x] 17.2 Make `JobController` a queue with pause, result and failure lists; put the pause points into the pipeline and the speaker detection; update the notification, the service, the recount and the home screen (running card, queue, failures); verify the build and the existing tests
- [ ] 17.3 Check on a phone: two files run one after another; a pause with another file waiting starts it, and resuming continues the first from where it stopped; cancel and remove work in every state; the notification actions work

## 18. Two universal release builds

- [x] 18.1 Add the flavors `online` and `fat` without ABI splits, `bundled-models.json` with the fetch task and the generated assets, the install from the APK, signing with the local key and `./gradlew releaseApks`; verify `BundledModelsManifestTest`
- [x] 18.2 Build both APKs and check their sizes; verify that the fat one installs in airplane mode, installs the models and transcribes a recording

## Workflow follow-up

- Archive the change after the group 11 defaults are settled so the main specs show the final defaults.
- Re-run `openspec validate --specs --strict` after archiving.
