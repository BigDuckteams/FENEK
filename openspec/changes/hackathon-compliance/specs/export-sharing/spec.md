# Spec Delta

## ADDED Requirements

### Requirement: Save an export to a file
The system SHALL let the user save an exported transcript in any format as a file in a location of their choice (including the Downloads folder) through the system "create document" dialog, without needing another app to receive it.

#### Scenario: Save JSON
- **WHEN** the user chooses "Сохранить в файл" and picks the JSON format
- **THEN** the system file dialog proposes the name "<title>.json" and the chosen file contains the same text as the shared export

#### Scenario: Cancelled dialog
- **WHEN** the user dismisses the file dialog
- **THEN** nothing is written

#### Scenario: Confirmation
- **WHEN** the file has been written
- **THEN** a message "Файл сохранён" is shown

### Requirement: Task JSON export
The system SHALL export the transcript in the hackathon's JSON schema: an object with a single key `transcript` holding an array of utterances in time order, each with `speaker_id` (string), `start_ms` (integer), `end_ms` (integer) and `text` (string), and no other keys.

#### Scenario: Shape
- **WHEN** a transcript with a phrase "Добрый день!" by the first speaker from 1.25 s to 4.38 s is exported
- **THEN** the output is `{"transcript":[{"speaker_id":"speaker_1","start_ms":1250,"end_ms":4380,"text":"Добрый день!"}]}` (whitespace aside)

#### Scenario: Speaker ids
- **WHEN** participants are numbered 0, 1, 2 internally
- **THEN** their `speaker_id` values are "speaker_1", "speaker_2" and "speaker_3", the same for every phrase of a participant, and do not change when the participant is renamed

#### Scenario: Integers
- **WHEN** times are exported
- **THEN** `start_ms` and `end_ms` are JSON integers (not decimals or strings) with `end_ms` not less than `start_ms`

#### Scenario: Encoding
- **WHEN** the text contains Cyrillic letters or quotes
- **THEN** the file is UTF-8 without a byte-order mark, Cyrillic is written as is and quotes are escaped

#### Scenario: One entry per phrase
- **WHEN** a transcript has several consecutive phrases of one speaker
- **THEN** each phrase is its own entry with its own start and end times

#### Scenario: Corrected text
- **WHEN** the LLM corrected a phrase
- **THEN** the exported `text` is the corrected text

#### Scenario: Empty transcript
- **WHEN** the transcript has no phrases
- **THEN** the output is `{"transcript":[]}`

### Requirement: Extended JSON export
The system SHALL keep the previous rich JSON as a separate pretty-printed format "Расширенный JSON (.json)" that describes the transcript, its participants, marks and utterances.

#### Scenario: Document fields
- **WHEN** a transcript is exported as extended JSON
- **THEN** the document has the title, creation time, duration, processing time and RTF, source name, recognition model title, the language model title when it corrected the text, language, marks in seconds, and participants with ids starting at 1 (only those who have phrases)

#### Scenario: Utterance fields
- **WHEN** utterances are written
- **THEN** each has speaker id and name, start and end in seconds, a start timestamp and the text

#### Scenario: Language
- **WHEN** the transcript has a language code
- **THEN** the extended JSON shows its Russian title (or the code if it is unknown); otherwise the language is null

#### Scenario: Recognized text
- **WHEN** a phrase was corrected by the LLM
- **THEN** its utterance contains both the corrected text and the recognized text

## MODIFIED Requirements

### Requirement: Export formats
The system SHALL export a transcript as plain text (.txt), a Markdown note (.md), SubRip subtitles (.srt), WebVTT subtitles (.vtt), the task's JSON (.json) or the extended JSON (.json), offered in the export menu as "Текст диалога (.txt)", "Заметка Markdown (.md)", "Субтитры SRT (.srt)", "Субтитры WebVTT (.vtt)", "JSON расшифровки (.json)" and "Расширенный JSON (.json)".

#### Scenario: Menu
- **WHEN** the user opens the export menu
- **THEN** all six formats, "Скопировать текст" and, for an owned audio file that still exists, "Аудиозапись" are offered, each format both for sharing and for saving to a file

## REMOVED Requirements

### Requirement: JSON export
**Reason**: The "structured JSON" export is replaced by two explicit formats: the task's `transcript` schema ("Task JSON export") and the previous rich document kept as "Extended JSON export".
**Migration**: Consumers of the old document use "Расширенный JSON (.json)", which keeps its fields and adds the recognized text; consumers following the hackathon schema use "JSON расшифровки (.json)".
