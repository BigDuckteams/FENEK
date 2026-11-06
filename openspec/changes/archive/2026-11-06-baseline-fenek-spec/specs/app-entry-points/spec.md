# Spec Delta

## Purpose

Defines how the app is started and steered from the outside (share sheet, "Open with", notifications, Quick Settings tile, launcher shortcuts) and how its screens are connected.

## ADDED Requirements

### Requirement: Screens and navigation
The system SHALL consist of a single activity with the screens home, recorder, transcript, models and settings, starting on the home screen, with the system back action returning to the previous screen.

#### Scenario: Home actions
- **WHEN** the user is on the home screen
- **THEN** it offers search, models and settings in the top bar and the buttons "Из файла" and "Записать"

#### Scenario: Settings to models
- **WHEN** the user opens models from the settings
- **THEN** back returns to the settings

### Requirement: Receive audio from other apps
The system SHALL accept an audio or video file shared to it ("Поделиться") or an audio file opened with it ("Открыть с помощью"), and SHALL open the new-transcription options for that file on the home screen.

#### Scenario: Shared audio
- **WHEN** another app shares an audio or video stream to FENEK
- **THEN** FENEK opens on the home screen with the "Новая расшифровка" sheet for that file, named after the file

#### Scenario: Open with
- **WHEN** the user opens a content:// audio file with FENEK
- **THEN** the same sheet is shown

#### Scenario: Sent while on another screen
- **WHEN** the app is on the transcript screen and a file is shared to it
- **THEN** the app returns to the home screen and shows the sheet

#### Scenario: Temporary access
- **WHEN** the shared file is only temporarily readable
- **THEN** it is copied into app storage when processing starts

### Requirement: Pick a file in the app
The system SHALL let the user choose an audio, video or Ogg file from the system document picker with "Из файла".

#### Scenario: Pick
- **WHEN** the user chooses a file
- **THEN** read access is persisted when possible and the "Новая расшифровка" sheet opens

### Requirement: Open recorder from outside
The system SHALL open the recorder, and start recording, when launched through the tile or the "Записать" shortcut, and SHALL open the recorder without starting when launched from the recording notification.

#### Scenario: Shortcut
- **WHEN** the user picks "Записать" on the launcher icon
- **THEN** the recorder opens and recording begins

#### Scenario: Notification tap
- **WHEN** the user taps the ongoing recording notification
- **THEN** the recorder opens without starting a new recording

#### Scenario: File shortcut
- **WHEN** the user picks the launcher file shortcut
- **THEN** the system file picker opens

### Requirement: Open results from notifications
The system SHALL open the corresponding transcript when the user taps the "Расшифровка готова" notification, and SHALL open the transcription options for the recording when the user taps the "Запись сохранена" notification.

#### Scenario: Finished transcript
- **WHEN** the user taps "Расшифровка готова"
- **THEN** that transcript opens

#### Scenario: Saved recording
- **WHEN** the user taps "Запись сохранена · <duration>"
- **THEN** the home screen shows the options sheet for that recording, which will be owned by its transcript

### Requirement: Only own recordings can be requested by path
The system SHALL accept a recording path from a notification only when it is a file with a valid recording name located directly in the app's private recordings folder.

#### Scenario: Valid recording
- **WHEN** the path points to `recordings/rec_<epoch ms>.aac`
- **THEN** the transcription options open for it

#### Scenario: Foreign path
- **WHEN** the path points anywhere else, or to a file that does not exist
- **THEN** the request is ignored

#### Scenario: Path tricks
- **WHEN** the path escapes the folder (for example through "..")
- **THEN** the request is ignored

### Requirement: Components exposed to the system
The system SHALL expose only the main activity and the Quick Settings tile (protected by the tile-binding permission) to other apps, keep its services and file provider private, and SHALL work on devices without a microphone for file transcription.

#### Scenario: Services
- **WHEN** another app tries to start the work or recording service
- **THEN** it cannot, because they are not exported

#### Scenario: No microphone
- **WHEN** the device has no microphone
- **THEN** the app can still be installed and used to transcribe files

### Requirement: Requests are handled once
The system SHALL handle each external request one time, clearing it after navigation, and SHALL reuse the existing activity instance for new intents.

#### Scenario: Rotation
- **WHEN** the screen is rotated after a request was handled
- **THEN** the request is not handled again

#### Scenario: Repeated launch
- **WHEN** a second intent arrives while the app is open
- **THEN** it is delivered to the existing activity instead of opening another one
