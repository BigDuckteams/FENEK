# Spec Delta

## Purpose

Stores the few user preferences that tune recognition, speaker detection and performance, and presents them, together with quick-start help and app information, on the settings screen.

## ADDED Requirements

### Requirement: Persisted settings with defaults
The system SHALL persist the recognition model, the voice-fingerprint model, the recognition language, the diarization precision, the voice separation and the number of CPU threads, and SHALL use the defaults GigaAM v3 RNNT, the default voice model, automatic language, balanced precision, separation 0.5 and a thread count equal to the number of CPU cores limited to 1–4.

#### Scenario: First run
- **WHEN** no setting has been saved
- **THEN** the defaults apply

#### Scenario: Persistence
- **WHEN** the user changes a setting and restarts the app
- **THEN** the changed value is still in effect

### Requirement: Saved values are validated on load
The system SHALL replace an unknown saved model with the default of its kind, an unknown precision level with "Баланс", and SHALL limit the saved thread count to between 1 and the device maximum (the number of cores, at most 8).

#### Scenario: Removed model
- **WHEN** the saved recognition model is no longer in the catalog
- **THEN** the default recognition model is used

#### Scenario: Too many threads
- **WHEN** the saved thread count exceeds the device maximum
- **THEN** the maximum is used

### Requirement: Recognition settings
The system SHALL let the user open the model list from "Модель по умолчанию" (showing the current model's title) and choose the speech language used by multilingual models, defaulting to automatic detection.

#### Scenario: Open models
- **WHEN** the user taps "Модель по умолчанию"
- **THEN** the models screen opens

#### Scenario: Language
- **WHEN** the user picks a language
- **THEN** it becomes the default language offered when starting a transcription with a multilingual model

### Requirement: Speaker detection settings
The system SHALL let the user choose the diarization precision ("Быстро", "Баланс", "Точно") and set the voice separation on a slider from "Объединять похожие" to "Разделять сильнее", applying the slider value when the user releases it.

#### Scenario: Precision hint
- **WHEN** the precision control is shown
- **THEN** a hint explains that "Быстро" is about twice as fast as "Баланс" and "Точно" is about 2.5 times slower

#### Scenario: Slider commit
- **WHEN** the user drags the separation slider
- **THEN** the value is saved only when the drag ends

#### Scenario: Effect
- **WHEN** the next recording is processed
- **THEN** the new precision and separation are used (finished transcripts are not changed)

### Requirement: Performance setting
The system SHALL let the user choose the number of CPU threads from 1 to the device maximum, and SHALL hide the slider on a single-core device.

#### Scenario: Threads
- **WHEN** the user sets 3 threads
- **THEN** the next job uses 3 threads for recognition and speaker analysis

#### Scenario: Hint
- **WHEN** the section is shown
- **THEN** it warns that more threads are faster but heat the phone and use more battery

### Requirement: Quick start help
The system SHALL explain the "Запись FENEK" tile and the "Записать" launcher shortcut, and on Android 13 and later SHALL offer a button "Добавить плитку в шторку" that asks the system to add the tile.

#### Scenario: Android 13 and later
- **WHEN** the user presses the button
- **THEN** the system add-tile prompt is shown and "Плитка добавлена" or "Плитка уже в шторке" is reported

#### Scenario: Earlier Android
- **WHEN** the device runs Android 12 or earlier
- **THEN** instructions for adding the tile manually are shown instead of the button

### Requirement: About
The system SHALL show the app name and version, state that the app works fully offline with the internet needed only once for models, and name the open-source engines and models used.

#### Scenario: Version
- **WHEN** the screen is opened
- **THEN** it shows "FENEK <versionName>"
