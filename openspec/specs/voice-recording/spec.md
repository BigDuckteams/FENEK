# voice-recording Specification

## Purpose
Lets the user record a conversation from the microphone inside the app, keep recording with the screen off, mark important moments, start recording in one tap from outside the app, and keep finished recordings until they are transcribed or deleted.

## Requirements

### Requirement: Recording format and location
The system SHALL record mono AAC (ADTS) audio at 44.1 kHz and 64 kbit/s from the microphone into the app's private "recordings" folder, in a file named from its start time (`rec_<epoch ms>.aac`).

#### Scenario: Recording starts
- **WHEN** the user starts a recording
- **THEN** a new `rec_<epoch ms>.aac` file appears in the private recordings folder and grows while recording

#### Scenario: Process dies mid-recording
- **WHEN** the app process is killed during a recording
- **THEN** everything captured so far stays in the file, can be played and can be transcribed

### Requirement: Recording preconditions
The system SHALL refuse to start a recording when less than 50 MB of free space is available or when the microphone cannot be opened, and SHALL tell the user why.

#### Scenario: Low storage
- **WHEN** the user starts a recording and less than 50 MB is free
- **THEN** recording does not start and "Недостаточно свободного места для записи" is shown

#### Scenario: Microphone busy
- **WHEN** the microphone cannot be started
- **THEN** any partial file is deleted and "Не удалось включить микрофон. Возможно, он занят другим приложением." is shown

#### Scenario: Already recording
- **WHEN** start is requested while a recording is in progress
- **THEN** nothing changes and no second recording is created

### Requirement: Microphone permission flow
The system SHALL request the microphone permission (and, on Android 13 and later, the notification permission) when the user presses record, and SHALL start recording as soon as the microphone permission is granted.

#### Scenario: Permission denied
- **WHEN** the user denies the microphone permission
- **THEN** a message explains that recording is impossible without it and an "Открыть настройки" button opens the app's system settings

#### Scenario: Permission granted
- **WHEN** the user grants the microphone permission
- **THEN** recording starts immediately

### Requirement: Pause and resume
The system SHALL let the user pause and resume an active recording, and SHALL count only unpaused time in the elapsed time.

#### Scenario: Pause
- **WHEN** the user pauses
- **THEN** the elapsed timer stops, the screen shows "Пауза" and the level indicator is idle

#### Scenario: Resume
- **WHEN** the user resumes
- **THEN** the timer continues from the paused value

### Requirement: Live level feedback
The system SHALL show the current input level and a waveform of the most recent levels while recording, with levels mapped from silence (0) to full scale (1) over a 60 dB range.

#### Scenario: Recording is silent
- **WHEN** the microphone amplitude is 0
- **THEN** the level is 0

### Requirement: Marking important moments
The system SHALL let the user mark the current moment of an active recording from the recording screen and from the recording notification, SHALL store the marks next to the recording file, and SHALL ignore a mark made less than one second after the previous one.

#### Scenario: Mark
- **WHEN** the user presses "Отметить момент"
- **THEN** the elapsed position is saved as a mark and the button shows the number of marks

#### Scenario: Double tap
- **WHEN** a second mark is requested within one second of the previous mark
- **THEN** it is ignored

#### Scenario: Not recording
- **WHEN** a mark is requested while nothing is recording
- **THEN** no mark is saved

### Requirement: Finishing and discarding
The system SHALL discard a recording that is empty or shorter than 0.5 seconds, SHALL return a usable recording otherwise, and SHALL let the user throw away an active recording after confirmation.

#### Scenario: Too short
- **WHEN** the user stops a recording shorter than 0.5 seconds
- **THEN** the file and its marks are deleted and "Запись слишком короткая" is shown

#### Scenario: Normal stop
- **WHEN** the user stops a longer recording
- **THEN** the recording is kept and the transcription options are offered

#### Scenario: Discard
- **WHEN** the user confirms "Удалить запись?" during a recording
- **THEN** recording stops and the file and its marks are deleted

### Requirement: Recording continues in the background
The system SHALL run an ongoing foreground notification of type "microphone" while a recording is active, so that recording continues with the screen off or the app in the background, and SHALL remove it when the recording ends.

#### Scenario: Notification while recording
- **WHEN** a recording is active and not paused
- **THEN** a silent ongoing notification "Идёт запись разговора" with a running chronometer and the actions "Пауза", "Отметить" and "Остановить" is shown

#### Scenario: Notification while paused
- **WHEN** the recording is paused
- **THEN** the notification reads "Запись на паузе", shows the recorded time and offers "Продолжить"

#### Scenario: Stop from the notification
- **WHEN** the user presses "Остановить" in the notification
- **THEN** the recording is saved, the service stops, and a notification "Запись сохранена · <duration>" is shown that opens the transcription options when tapped

### Requirement: One-tap start from outside the app
The system SHALL offer a Quick Settings tile "Запись FENEK" and a launcher shortcut "Записать" that open the recorder and start recording immediately, once per visit.

#### Scenario: Tile tap
- **WHEN** the user taps the tile
- **THEN** the app opens on the recording screen and recording starts

#### Scenario: Tile state
- **WHEN** a recording is active
- **THEN** the tile is shown as active (with the subtitle "Идёт запись" on Android 10 and later)

#### Scenario: Recording already active
- **WHEN** the tile or shortcut is used while a recording is already active
- **THEN** no second recording is started

### Requirement: Recordings waiting for transcription
The system SHALL list saved recordings that have no transcript and are not being processed on the home screen under "Записи без расшифровки", newest first, with the actions "Расшифровать", "Поделиться" and "Удалить".

#### Scenario: Recording not yet transcribed
- **WHEN** a finished recording is not referenced by any transcript and is not the source of the running job
- **THEN** it appears in the list

#### Scenario: Recording transcribed
- **WHEN** a transcript references the recording file
- **THEN** it no longer appears in the list

#### Scenario: Delete
- **WHEN** the user confirms deleting a listed recording
- **THEN** its audio file and marks are deleted

#### Scenario: Share
- **WHEN** the user shares a listed recording
- **THEN** it is sent under the readable name "Запись dd.MM.yyyy HH:mm"

### Requirement: Recording banner
The system SHALL show a banner on the home screen while a recording is active, displaying whether it is running or paused and the elapsed time, and opening the recorder when tapped.

#### Scenario: Banner
- **WHEN** a recording is active and the user is on the home screen
- **THEN** the banner shows "Идёт запись · <time>" or "Запись на паузе · <time>"
