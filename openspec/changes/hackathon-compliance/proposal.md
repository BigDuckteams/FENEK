# Proposal

## Why

FENEK is to be submitted to a hackathon whose task is an Android app that turns dialogue recordings into a structured transcript entirely on the device. Experts install a prepared APK offline, import three test recordings, export the result and compare it with reference transcripts (WER, real-time factor RTF, diarization and timestamp accuracy). Measured against the task, the current app has four gaps: it uses **no local LLM** (a mandatory requirement), its JSON export does not match the required `transcript` schema, its first-launch setup does not yet cover the new LLM (the task requires all models to be on the device at check time), and the submission package (docs, test results, demo) does not exist.

## What Changes

- Add a **local on-device LLM** (GGUF, run through a native runtime) with two independent modes controlled by switches: (1) correction of each recognized phrase (punctuation, casing, obvious recognition slips) with a guard that keeps the original text when the LLM changes too much, and (2) generation of the summary ("Коротко") and the task list; both fall back to the existing rule-based behaviour when the LLM is off, missing or fails.
- Keep the recognized text of every phrase next to the corrected one so the user can restore it.
- Keep downloading models once, on first launch, from the network (the task forbids online *processing*, not downloading models): add the LLM to the existing model catalog and offer a one-tap download of the whole default set (recognition, segmentation, voice fingerprints, LLM); after that everything works without a network.
- Make the exported JSON follow the task's schema exactly: `{"transcript":[{"speaker_id":"speaker_1","start_ms":1250,"end_ms":4380,"text":"..."}]}`. **BREAKING**: the current rich JSON export moves to a separate "Расширенный JSON" format.
- Measure and show processing time and the real-time factor (RTF) of every job, and add an LLM stage with progress to the pipeline.
- Add a repository submission package following the organizers' template (architecture, limitations, test method and results, demo materials, install and usage instructions) and a tool to compute WER and RTF on reference transcripts.
- Out of scope: noise suppression, re-recognition of single fragments, hardware (NPU/GPU) acceleration of the recognizer, simultaneous-speech transcription. These are optional "Ultra" items of the task.

### Assumptions

- The test dialogues are in Russian (the existing default model GigaAM v3 targets Russian); other languages stay available through Whisper and Parakeet.
- Text for WER is taken from the exported `text` field as is; the organizers' normalization is unknown, so the app keeps punctuation and casing (these are normally stripped before computing WER).
- Downloading models over the network once is allowed; the forbidden things are online APIs and remote compute. The app is therefore opened once with a network connection before the offline check, and the README says so.

## Capabilities

### New Capabilities
- `local-llm`: on-device LLM runtime and its two uses (phrase correction with guardrails, summary and tasks), mode switches, fallbacks, resource limits and offline operation.
- `submission-package`: the documentation, test-result and demo materials, repository layout and accuracy tooling that the hackathon requires alongside the APK.

### Modified Capabilities
- `export-sharing`: the standard JSON export becomes the task's `transcript` schema; the previous rich JSON becomes a separate format.
- `model-management`: the catalog gains LLM models, a one-tap download of the default set, an optional (recommended) LLM, and the guarantee that nothing needs a network after the download.
- `transcription-job`: the pipeline gets an LLM stage with progress, and every job reports processing time and RTF.
- `app-settings`: switches and model choice for the LLM modes.
- `transcript-editing`: restoring the originally recognized text of a phrase.
- `conversation-insights`: the summary and tasks can come from the LLM, with the rule-based result as the fallback.

## Impact

- Code: `engine/` (pipeline, new LLM stage and runtime wrapper), `model/` (catalog with LLM entries, default-set download), `data/` (phrase keeps the recognized text, new settings), `export/` (new JSON), `analytics/` (LLM-backed summary and tasks), `ui/` (settings, progress, restore text, RTF), `service/` (notifications and progress text), Gradle build (native LLM library via NDK/CMake or prebuilt).
- Dependencies: a native LLM runtime (llama.cpp or equivalent) and a quantized instruct model.
- APK size: stays small (the native LLM runtime adds a few tens of MB); the language model adds about 1.1 GB to the one-time model download (about 265 MB today).
- Stored data: transcripts gain optional fields (recognized text, LLM summary and tasks); old files still load.
- Docs: new `docs/` files and `artifacts/` folders; the README is updated.
