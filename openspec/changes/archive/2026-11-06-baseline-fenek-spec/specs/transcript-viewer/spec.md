# Spec Delta

## Purpose

Presents a transcript as a readable, playable dialogue: details, participants, highlights, a conversation timeline, statistics, and audio playback that follows the text.

## ADDED Requirements

### Requirement: Transcript overview
The system SHALL show a header with the conversation date, the duration and processing time, the recognition model (with the language for multilingual models) and the participants as chips colored per participant, followed by the dialogue turns.

#### Scenario: Header
- **WHEN** a transcript is opened
- **THEN** the header shows "Длительность <time> · обработано за <time>" and the model title

#### Scenario: Participants
- **WHEN** the header is shown
- **THEN** only participants with at least one phrase appear, in order of first appearance, and tapping one opens the participant dialog

#### Scenario: Not found
- **WHEN** the transcript id does not exist
- **THEN** "Расшифровка не найдена" is shown

#### Scenario: No speech
- **WHEN** the transcript has no phrases
- **THEN** "В записи не найдено речи." is shown in place of the dialogue

### Requirement: Dialogue turns with timestamps
The system SHALL show the dialogue as turns, each with the participant's name and the start time, and SHALL show the marks made during recording at the turn that was in progress.

#### Scenario: Turn display
- **WHEN** a participant has consecutive phrases
- **THEN** they are shown together under one name and time

#### Scenario: Mark placement
- **WHEN** a mark was made at a time inside a turn
- **THEN** the turn shows a mark indicator, and a mark before the first turn belongs to the first turn

### Requirement: Playback of the original recording
The system SHALL play the original audio from the transcript screen, letting the user start playback from any phrase by tapping it, pause and resume, and seek.

#### Scenario: Tap a phrase
- **WHEN** the user taps a phrase
- **THEN** playback starts at that phrase's start time

#### Scenario: Replay at the end
- **WHEN** playback has finished and the user presses play
- **THEN** it restarts from the beginning

#### Scenario: Audio unavailable
- **WHEN** the original file is moved or deleted
- **THEN** the player shows "Исходная запись недоступна (файл перемещён или удалён)" and the text remains readable

#### Scenario: Playback failure
- **WHEN** the file cannot be played
- **THEN** "Не удалось воспроизвести запись" is shown

### Requirement: Playback speed
The system SHALL let the user cycle the playback speed through 1×, 1.25×, 1.5×, 2× and 0.75×, and SHALL apply the chosen speed to all later playback while the screen is open.

#### Scenario: Cycle
- **WHEN** the user presses the speed control at 2×
- **THEN** the speed becomes 0.75×
- **AND WHEN** it is pressed again
- **THEN** the speed becomes 1×

#### Scenario: Paused player
- **WHEN** the speed is changed while paused
- **THEN** playback does not start

### Requirement: Karaoke following
The system SHALL highlight the turn being played and, while "Следить за звуком" is on (the default), scroll the list to keep it fully visible.

#### Scenario: Following on
- **WHEN** playback moves into a turn that is not fully visible
- **THEN** the list scrolls to it

#### Scenario: Following off
- **WHEN** the user turns following off
- **THEN** the list is not scrolled by playback

### Requirement: Conversation timeline
The system SHALL show the whole recording as a strip colored by participant with the marks above it and a cursor for the playback position, and SHALL start playback from the tapped point.

#### Scenario: Tap on the strip
- **WHEN** the user taps the strip at 40 % of its width
- **THEN** playback starts at 40 % of the duration

### Requirement: Highlights card
The system SHALL show a "Главное" card with the recording marks, the summary ("Коротко"), the tasks ("Задачи и договорённости") and the questions ("Вопросы"), showing the first three items of each list and "Показать все (N)" / "Свернуть" to expand; every item shows its text, participant and time and plays from its position when tapped.

#### Scenario: Collapsed lists
- **WHEN** a list has more than three items
- **THEN** only three are shown with "Показать все (N)"

#### Scenario: Mark chip
- **WHEN** the user taps a mark chip
- **THEN** playback starts five seconds before the mark, but not before the beginning

#### Scenario: Disclaimer
- **WHEN** the card is shown
- **THEN** it states that the items were selected automatically from the text

### Requirement: Task deadline in the calendar
The system SHALL let the user add a task that has a resolved deadline to the calendar via the calendar app's insert-event screen, with the task sentence as the title and a description naming the conversation and the time of the sentence.

#### Scenario: Deadline with a time
- **WHEN** the deadline has a time
- **THEN** the event starts at that time and lasts 30 minutes

#### Scenario: Deadline with a date only
- **WHEN** the deadline has no time
- **THEN** the event is an all-day event on that date

#### Scenario: No date
- **WHEN** the deadline has no resolved date
- **THEN** no calendar action is offered

#### Scenario: No calendar app
- **WHEN** no application can handle the request
- **THEN** "Не найдено приложение календаря" is shown

### Requirement: Statistics card
The system SHALL show a statistics card with the conversation timeline, a stacked bar of talk shares, one row per participant ("<time> · <share> % · <turns>"), the conversation facts and the topics ("О чём говорили").

#### Scenario: Topic chip
- **WHEN** the user taps a topic
- **THEN** the in-transcript search opens with the topic as the query

#### Scenario: No participants
- **WHEN** nobody has phrases
- **THEN** the card is not shown

### Requirement: Search inside a transcript
The system SHALL let the user search the open transcript ("Поиск в расшифровке"), highlight all occurrences, show "<current>/<total>" and move between matches with previous and next controls that wrap around, scrolling to the current match.

#### Scenario: Wrap-around
- **WHEN** the user presses "next" on the last match
- **THEN** the first match becomes current

#### Scenario: No matches
- **WHEN** the query has no matches
- **THEN** the counter shows "0"

#### Scenario: Close search
- **WHEN** the user closes the search
- **THEN** the query is cleared and highlighting disappears

### Requirement: Transcript menu
The system SHALL provide in the transcript screen a favourite toggle, search, export and share ("Экспорт"), and an overflow menu with "Переименовать", "Пересчитать участников" and "Удалить".

#### Scenario: Favourite toggle
- **WHEN** the user presses the star
- **THEN** the transcript is added to or removed from the favourites

### Requirement: Leaving the screen
The system SHALL stop playback when the transcript screen is left or the transcript is deleted.

#### Scenario: Delete
- **WHEN** the user confirms deletion
- **THEN** playback pauses, the transcript is deleted and the app goes back
