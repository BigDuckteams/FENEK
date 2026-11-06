# Proposal

## Why

FENEK (an offline conversation transcriber for Android) has no specifications: `openspec/specs/` is empty and the only description of its behaviour is a README that has drifted from the code (it mentions `Ahc.kt`, `WindowClustering.kt`, 1.5 s windows and k-means, while the code uses 2 s windows, spectral clustering and the ERes2Net voice model). A baseline spec derived from the code gives future changes a trustworthy starting point.

## What Changes

- Document the behaviour of the existing `app` module as 12 capability specs. The source of truth is the code (and its unit tests), not the README.
- No code changes. This change only adds specs; it is meant to be archived so that the specs land in `openspec/specs/`.
- Requirement text is in English (OpenSpec requires SHALL/MUST); user-visible Russian strings are quoted verbatim.

## Capabilities

### New Capabilities
- `audio-decoding`: turning any supported audio or video file into 16 kHz mono PCM on disk; handling of shared and temporarily readable sources.
- `voice-recording`: microphone recording with pause and marks, the recording foreground service, the Quick Settings tile and launcher shortcut, and recordings waiting for transcription.
- `speaker-diarization`: finding speech, voice fingerprints, clustering into participants, honouring the requested number of participants, and building clean non-overlapping speaker turns.
- `transcription-job`: the end-to-end pipeline from source to transcript, stages and progress, one job at a time, background execution, cancellation, failures and notifications.
- `model-management`: the model catalog, resumable downloads with mirrors, validation, deletion, and which models a job requires.
- `transcript-store`: persistence of transcripts, favourites, deletion and audio ownership rules, and full-text search across transcripts.
- `transcript-editing`: renaming, speaker rename and merge, phrase correction, and recounting participants.
- `conversation-insights`: talk statistics, topics, the extractive summary, tasks and agreements, questions, and deadline resolution.
- `transcript-viewer`: the transcript screen behaviour: playback, karaoke following, conversation timeline, in-transcript search, marks, and adding a deadline to the calendar.
- `export-sharing`: TXT, Markdown, SRT, VTT and JSON rendering, sharing files and recordings, copying text.
- `app-settings`: persisted user settings and the settings screen.
- `app-entry-points`: incoming intents (share, open with, notification, tile, shortcut), navigation, and permission handling.

### Modified Capabilities

(none: there are no existing specs)

## Impact

- Affected code: none. Specs describe `app/src/main/java/app/fenek/**` as of version 1.1.0.
- Affected docs: `openspec/specs/` is populated after archive. The README is not modified here, but its stale parts are noted in the specs' scope.
- Dependencies, APIs, systems: none.
