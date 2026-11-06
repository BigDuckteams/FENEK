# Spec Delta

## Purpose

Gets a transcript out of the app in common formats and lets the user share it, its audio, or its plain text through the system, without the content going anywhere on its own.

## ADDED Requirements

### Requirement: Export formats
The system SHALL export a transcript as plain text (.txt), a Markdown note (.md), SubRip subtitles (.srt), WebVTT subtitles (.vtt) or structured JSON (.json), offered in the export menu as "Текст диалога (.txt)", "Заметка Markdown (.md)", "Субтитры SRT (.srt)", "Субтитры WebVTT (.vtt)" and "Структурированный JSON (.json)".

#### Scenario: Menu
- **WHEN** the user opens the export menu
- **THEN** all five formats, "Скопировать текст" and, for an owned audio file that still exists, "Аудиозапись" are offered

### Requirement: Plain text export
The system SHALL render plain text as the title, a line with the creation date, the duration and the number of participants, the list of participants, and then every turn as "[HH:MM:SS] <name>:" followed by the turn's text.

#### Scenario: Turn layout
- **WHEN** a participant "Иван" speaks at 75 seconds
- **THEN** the export contains "[00:01:15] Иван:" followed by the text on the next line

#### Scenario: Consecutive phrases
- **WHEN** a participant has several consecutive phrases
- **THEN** their texts are joined in one paragraph under one header

### Requirement: Markdown note export
The system SHALL render a Markdown note with the title, the conversation date, duration and participant count, a statistics table (participant, talk time, share, turns), the topics, the sections "Коротко", "Задачи и договорённости" (as a checklist with the speaker and "срок: <label>" where known), "Вопросы" and "Отметки при записи", a separator and then the dialogue, omitting sections that have no content.

#### Scenario: Task checklist
- **WHEN** a task by "Иван" has the deadline "пт, 30 окт."
- **THEN** the note contains "- [ ] <task> — Иван, срок: пт, 30 окт." followed by its timestamp

#### Scenario: Empty sections
- **WHEN** a conversation has no questions
- **THEN** no "Вопросы" section is written

#### Scenario: Table safety
- **WHEN** a participant name contains "|"
- **THEN** it is replaced by "/" in the table

### Requirement: Subtitle export
The system SHALL export one subtitle cue per phrase with the phrase's start and end times and the text prefixed by the participant's name.

#### Scenario: SRT
- **WHEN** a phrase runs from 75.25 s to 80 s
- **THEN** its cue is numbered from 1 and shows "00:01:15,250 --> 00:01:20,000" and "<name>: <text>"

#### Scenario: WebVTT
- **WHEN** the file is WebVTT
- **THEN** it starts with "WEBVTT", uses "." before milliseconds and marks the speaker as "<v name>" with ">" characters removed from the name

### Requirement: JSON export
The system SHALL export a pretty-printed JSON document with the title, creation time, duration in seconds, source name, model title, language, participants (ids starting at 1), recording marks in seconds and the utterances with speaker id and name, start and end in seconds, a start timestamp and the text.

#### Scenario: Language
- **WHEN** the transcript has a language code
- **THEN** the JSON shows its Russian title (or the code if it is unknown); otherwise the language is null

#### Scenario: Participants
- **WHEN** participants are listed
- **THEN** only participants who have phrases are included

### Requirement: Sharing files
The system SHALL share an exported file through the system share sheet ("Отправить расшифровку") using a temporary file in the app cache named after the title, with characters unsafe in file names replaced by "_", trimmed to 80 characters, falling back to "fenek".

#### Scenario: Title with special characters
- **WHEN** the title is "Встреча: план/бюджет?"
- **THEN** the file name contains no ":", "/" or "?" and ends with the format's extension

#### Scenario: Access
- **WHEN** a file is shared
- **THEN** it is exposed through the app's non-exported file provider with a temporary read grant

### Requirement: Sharing the recording
The system SHALL share the original audio recording owned by the transcript (or a waiting recording) under a readable name ("Отправить запись"), not under its internal file name.

#### Scenario: Readable name
- **WHEN** a recording "rec_1730000000000.aac" is shared
- **THEN** the shared file is named after the title or "Запись dd.MM.yyyy HH:mm"

### Requirement: Copy text
The system SHALL copy the plain-text rendering of a transcript to the clipboard.

#### Scenario: Older Android
- **WHEN** the device runs Android 12 or earlier
- **THEN** "Текст скопирован" is shown, since the system shows no confirmation of its own
