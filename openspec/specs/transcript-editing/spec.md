# transcript-editing Specification

## Purpose
Lets the user correct what automatic processing got wrong: titles, participant names, who said a phrase, the text of a phrase, and the number of participants.

## Requirements

### Requirement: Rename a transcript
The system SHALL let the user change the title of a transcript, ignoring surrounding whitespace, and SHALL keep the old title when the new one is blank.

#### Scenario: New title
- **WHEN** the user saves "  Планёрка  " in the "Название" dialog
- **THEN** the title becomes "Планёрка"

#### Scenario: Blank title
- **WHEN** the user saves an empty title
- **THEN** the title does not change

### Requirement: Rename a participant
The system SHALL let the user give a participant a name, and SHALL fall back to the default name "Спикер N" when the name is blank.

#### Scenario: Name
- **WHEN** the user renames "Спикер 2" to "Иван"
- **THEN** every place that shows the participant (phrases, statistics, highlights, exports, search) shows "Иван"

#### Scenario: Blank name
- **WHEN** the user saves an empty name
- **THEN** the participant is named "Спикер <number>" again

#### Scenario: Unnamed participant
- **WHEN** a participant has no stored name
- **THEN** the default "Спикер <id + 1>" is used

### Requirement: Merge participants
The system SHALL let the user merge one participant into another, giving all phrases of the first to the second and removing the first from the participant list.

#### Scenario: Split person
- **WHEN** the user merges "Спикер 3" into "Спикер 1"
- **THEN** all phrases of "Спикер 3" now belong to "Спикер 1" and "Спикер 3" no longer appears

#### Scenario: Dialog contents
- **WHEN** the participant dialog is opened
- **THEN** it shows the number of phrases and lets the user choose another participant to merge with

### Requirement: Correct a phrase
The system SHALL let the user change the text and the speaker of a phrase (opened by a long press), trim the text, delete the phrase when the text is blank, and offer "Новый участник" as a speaker.

#### Scenario: Change text
- **WHEN** the user saves new text
- **THEN** the phrase text is replaced

#### Scenario: Change speaker
- **WHEN** the user picks another participant
- **THEN** the phrase moves to that participant

#### Scenario: Delete
- **WHEN** the user saves an empty text
- **THEN** the phrase is removed

#### Scenario: New participant
- **WHEN** the user chooses "Новый участник"
- **THEN** a participant with the next unused id and the default name is created and assigned

#### Scenario: Stale index
- **WHEN** an edit refers to a phrase that no longer exists
- **THEN** the transcript is left unchanged

### Requirement: Recognize one phrase again
The system SHALL offer "Распознать фразу заново" in the phrase dialog when the original recording is available, and SHALL recognize only that phrase with the transcript's recognition model and language, a 250 ms audio context on each side and the current audio-cleanup setting.

#### Scenario: Successful retry
- **WHEN** recognition returns non-empty text
- **THEN** only the selected phrase's text changes; its speaker and timestamps and every other phrase stay unchanged

#### Scenario: Corrected phrase
- **WHEN** a phrase corrected by the language model is recognized again
- **THEN** the newly recognized text replaces the current text and the originally recognized text remains restorable

#### Scenario: Missing audio or model
- **WHEN** the recording or recognition model is unavailable
- **THEN** the existing transcript is left unchanged and a specific error is shown

#### Scenario: Empty result or stale phrase
- **WHEN** recognition returns no speech or the phrase no longer has the original timestamps
- **THEN** the existing text is left unchanged

### Requirement: Edits are saved immediately
The system SHALL persist every edit as soon as it is made.

#### Scenario: Leave and return
- **WHEN** the user edits a transcript, leaves the screen and returns
- **THEN** the edits are still there

### Requirement: Recount participants
The system SHALL let the user re-process the original recording of a transcript with a chosen number of participants ("Определить" or 1 to 6), using the same recognition model and language, and SHALL replace the transcript while keeping its id, title and favourite flag.

#### Scenario: Warning
- **WHEN** the "Пересчитать участников" dialog is shown
- **THEN** it states that the recording is processed again and manual edits are lost

#### Scenario: Started
- **WHEN** the user confirms
- **THEN** playback pauses, a job starts for the same recording and the app returns to the previous screen

#### Scenario: Original recording missing
- **WHEN** the recording is not available
- **THEN** a message "Исходная запись недоступна" or "Исходная запись удалена" is shown and nothing starts

#### Scenario: Another job running
- **WHEN** another transcription is in progress
- **THEN** "Дождитесь окончания текущей расшифровки" is shown and nothing starts

#### Scenario: Transcript not found
- **WHEN** the transcript no longer exists
- **THEN** "Расшифровка не найдена" is shown

### Requirement: Dialogue turns
The system SHALL present consecutive phrases of the same participant as one turn of the dialogue.

#### Scenario: Grouping
- **WHEN** a participant has three consecutive phrases
- **THEN** they are shown as one turn starting at the first phrase and ending at the last
