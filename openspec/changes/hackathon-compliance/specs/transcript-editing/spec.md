# Spec Delta

## ADDED Requirements

### Requirement: Restore the recognized text of a phrase
The system SHALL let the user see and restore the originally recognized text of a phrase that the LLM corrected, from the phrase editing dialog.

#### Scenario: Show recognized text
- **WHEN** the user opens the dialog of a corrected phrase
- **THEN** the recognized text is shown next to the current text

#### Scenario: Restore
- **WHEN** the user presses "Вернуть распознанный текст" and saves
- **THEN** the phrase text becomes the recognized text and the phrase is no longer marked as corrected

#### Scenario: Uncorrected phrase
- **WHEN** a phrase has no stored recognized text
- **THEN** the restore action is not offered

### Requirement: Manual edits become the text of record
The system SHALL treat text typed by the user as final: it SHALL NOT be corrected by the LLM later, and the recognized text of that phrase SHALL stay available for restoring.

#### Scenario: Manual edit of a corrected phrase
- **WHEN** the user edits a corrected phrase by hand
- **THEN** the typed text is kept and the original recognized text is still restorable

## MODIFIED Requirements

### Requirement: Recount participants
The system SHALL let the user re-process the original recording of a transcript with a chosen number of participants ("Определить" or 1 to 6), using the same recognition model and language, and SHALL replace the transcript while keeping its id, title and favourite flag.

#### Scenario: Warning
- **WHEN** the "Пересчитать участников" dialog is shown
- **THEN** it states that the recording is processed again and manual edits are lost

#### Scenario: Started
- **WHEN** the user confirms
- **THEN** playback pauses, a job for the same recording starts (or waits in the queue when another one is running) and the app returns to the previous screen

#### Scenario: Original recording missing
- **WHEN** the recording is not available
- **THEN** a message "Исходная запись недоступна" or "Исходная запись удалена" is shown and nothing starts

#### Scenario: Another job running
- **WHEN** another transcription is in progress
- **THEN** the recount is queued and starts after it, and the transcript stays as it is until then

#### Scenario: Already queued
- **WHEN** a recount of the same transcript is already running, queued or paused
- **THEN** "Эта расшифровка уже в очереди" is shown and nothing is added

#### Scenario: Transcript not found
- **WHEN** the transcript no longer exists
- **THEN** "Расшифровка не найдена" is shown
