# transcript-store Specification

## Purpose
Keeps the user's transcripts safely on the device, lets them mark favourites and delete them, and finds text across all transcripts.

## Requirements

### Requirement: Transcripts are stored privately on the device
The system SHALL store each transcript as one JSON file in the app's private storage, SHALL exclude app data from system backup, and SHALL NOT upload transcripts or audio anywhere.

#### Scenario: Persistence across restarts
- **WHEN** the app is restarted
- **THEN** all saved transcripts are loaded from disk

#### Scenario: Backup
- **WHEN** the system performs a backup
- **THEN** transcripts and recordings are not included

### Requirement: Loading is tolerant
The system SHALL load transcripts newest first by creation time, SHALL ignore unknown fields so that files written by other versions still open, and SHALL skip a file that cannot be parsed instead of failing.

#### Scenario: Corrupt file
- **WHEN** one transcript file is unreadable
- **THEN** the other transcripts load normally

#### Scenario: Upgrade
- **WHEN** a newer app version opens transcripts created by an older version
- **THEN** they open and missing new fields take their defaults

#### Scenario: Loading state
- **WHEN** transcripts are still being read from disk
- **THEN** the home screen shows a progress indicator instead of the empty state

### Requirement: Writes are atomic and serialized
The system SHALL write a transcript through a temporary file that replaces the old one, SHALL serialize all changes, and SHALL write nothing when an update leaves the transcript unchanged.

#### Scenario: Interrupted write
- **WHEN** the process dies while a transcript is being written
- **THEN** the previous complete version of the file is still readable

#### Scenario: No-op update
- **WHEN** an update produces an identical transcript
- **THEN** the file is not rewritten

### Requirement: Saving replaces by id
The system SHALL replace an existing transcript that has the same id when saving.

#### Scenario: Re-saved transcript
- **WHEN** a transcript with an existing id is saved
- **THEN** the list contains exactly one transcript with that id, in newest-first order

### Requirement: Favourites
The system SHALL let the user mark a transcript as a favourite from the transcript screen and filter the home list to favourites.

#### Scenario: Toggle
- **WHEN** the user presses the star
- **THEN** the favourite flag flips and is saved

#### Scenario: Filter chips
- **WHEN** at least one transcript is a favourite
- **THEN** the home screen shows the chips "Все · N" and "Избранное · M"

#### Scenario: Only favourites shown
- **WHEN** the favourites chip is selected
- **THEN** only favourite transcripts are listed

#### Scenario: No favourites
- **WHEN** no transcript is a favourite
- **THEN** the filter chips are not shown and all transcripts are listed

### Requirement: Deleting a transcript
The system SHALL delete a transcript after confirmation ("Удалить расшифровку?"), and SHALL treat its audio according to ownership: delete owned audio together with its marks, and release the persisted read permission of a referenced external file unless another transcript still uses it.

#### Scenario: Owned audio
- **WHEN** a transcript that owns its audio is deleted
- **THEN** the audio file and its marks file are deleted

#### Scenario: External file
- **WHEN** a transcript that references a file from the system picker is deleted
- **THEN** the original file is left untouched and its persisted permission is released if no other transcript uses the same URI

#### Scenario: Warning text
- **WHEN** the delete dialog is shown
- **THEN** it explains that the original recording stays on the phone unless it was copied into the app

### Requirement: Full-text search across transcripts
The system SHALL search titles, participant names and phrases case-insensitively for the trimmed query, and SHALL return nothing for an empty query.

#### Scenario: Match in any field
- **WHEN** the query occurs in a title, in the name of a participant who spoke, or in a phrase
- **THEN** the transcript is a result

#### Scenario: Ordering
- **WHEN** several transcripts match
- **THEN** they are ordered by the number of matching phrases (most first), then newest first

#### Scenario: Snippet
- **WHEN** a transcript has matching phrases
- **THEN** the result shows "<speaker>: <fragment>" around the first match, about 50 characters on each side, cut at word boundaries with "…" at truncated ends, and the query highlighted

#### Scenario: Result count
- **WHEN** results are shown
- **THEN** the heading reads "Найдено: N расшифровка/расшифровки/расшифровок" with correct Russian plural, or "Ничего не найдено"

#### Scenario: Search hides recordings list
- **WHEN** a query is entered
- **THEN** the list of recordings without transcripts is hidden

### Requirement: Empty library
The system SHALL show a welcome state "Пока нет расшифровок" when there are no transcripts, no waiting recordings and no active recording.

#### Scenario: First launch
- **WHEN** the user opens the app for the first time
- **THEN** the empty state explains how to record or choose a file
