# Spec Delta

## MODIFIED Requirements

### Requirement: Recordings waiting for transcription
The system SHALL list saved recordings that have no transcript and are not being processed or queued on the home screen under "Записи без расшифровки", newest first, as cards without buttons. Tapping a card SHALL open a bottom sheet with the recording's name, size, duration and format and the actions "Расшифровать", "Поделиться" and "Удалить".

#### Scenario: Recording not yet transcribed
- **WHEN** a finished recording is not referenced by any transcript and is not the source of a running or queued job
- **THEN** it appears in the list

#### Scenario: Recording transcribed
- **WHEN** a transcript references the recording file
- **THEN** it no longer appears in the list

#### Scenario: Details
- **WHEN** the user taps a listed recording
- **THEN** a bottom sheet shows its name, size, duration, format (and the number of marks, if there are any) and three buttons across the full width, so that their texts fit on any screen

#### Scenario: Transcribe
- **WHEN** the user presses "Расшифровать" in the sheet
- **THEN** the sheet closes and the new-transcription options open for that recording

#### Scenario: Delete
- **WHEN** the user presses "Удалить" and confirms
- **THEN** the sheet is closed, and the audio file and marks of the recording are deleted

#### Scenario: Share
- **WHEN** the user presses "Поделиться"
- **THEN** the recording is sent under the readable name "Запись dd.MM.yyyy HH:mm"
