# Spec Delta

## Purpose

Turns a recording or audio file into a structured transcript (participants, phrases with timestamps) as a background job with visible progress, and tells the user how it ended.

## ADDED Requirements

### Requirement: Starting a transcription
The system SHALL offer a "Новая расшифровка" sheet for every audio source before processing, letting the user choose the number of participants ("Определить" or 1 to 6), the recognition model, and, for models with a selectable language, the language of speech.

#### Scenario: Defaults
- **WHEN** the sheet opens
- **THEN** the number of participants is "Определить" and the model and language are the saved defaults

#### Scenario: Model status
- **WHEN** the model list is shown
- **THEN** each model is labelled with its languages and whether it is downloaded ("скачана" or "не скачана")

#### Scenario: Language choice
- **WHEN** the selected model has a selectable language (Whisper)
- **THEN** a language picker is shown; for other models it is hidden

#### Scenario: Missing models
- **WHEN** any required model is not downloaded
- **THEN** the sheet lists them ("Нужно скачать: …") with a download button showing the total size, shows download progress, and keeps "Расшифровать" disabled until all are ready

#### Scenario: Remembered choice
- **WHEN** the user starts a transcription
- **THEN** the chosen model and language become the saved defaults

### Requirement: Pipeline and progress
The system SHALL process a job in the stages preparation, decoding, speaker detection, speech recognition and saving, and SHALL report overall progress from 0 to 1 with the stage ranges 0–2 %, 2–12 %, 12–55 %, 55–99 % and 99–100 %.

#### Scenario: Progress text
- **WHEN** the job is running
- **THEN** the status shows "Подготовка файла…", "Чтение аудио…", "Определение говорящих…", "Распознавание речи: N из M фраз" or "Сохранение…" according to the stage

#### Scenario: Recognition progress
- **WHEN** phrases are being recognized
- **THEN** progress within the stage is proportional to the speech duration already recognized

### Requirement: Models must be present
The system SHALL fail a job before any work when the selected recognition model, the segmentation model or the voice-fingerprint model is not fully downloaded.

#### Scenario: Missing model
- **WHEN** a job starts and a required model is absent
- **THEN** it fails with "Сначала скачайте модели: <titles>"

### Requirement: Each phrase is recognized separately
The system SHALL cut speaker turns into phrases of at most 25 seconds, splitting long turns at the quietest moment in the last 40 % of the allowed length, SHALL drop phrases shorter than 0.25 seconds, and SHALL recognize every phrase on its own (with 0.1 s of padding on both sides), so that no recognized text ever mixes two speakers.

#### Scenario: Long monologue
- **WHEN** one speaker talks for 70 seconds without a break
- **THEN** the turn is split into several phrases of at most 25 seconds, each cut at a quiet point

#### Scenario: Empty recognition
- **WHEN** recognition returns no text for a phrase
- **THEN** no segment is stored for it

#### Scenario: Cancellation between phrases
- **WHEN** the job is cancelled
- **THEN** recognition stops before the next phrase

### Requirement: Language handling for multilingual models
The system SHALL use the chosen language for multilingual models, and when the language is "auto" SHALL keep the language detected on the first phrase of at least 3 seconds for all remaining phrases.

#### Scenario: Auto language
- **WHEN** the language is "auto" and the first long phrase is detected as English
- **THEN** all later phrases are recognized as English

#### Scenario: Single-language model
- **WHEN** the selected model is not multilingual
- **THEN** the transcript stores no language

### Requirement: Resulting transcript
The system SHALL produce a transcript with a unique id, a title, creation time, duration, source name, audio reference, model id and title, processing time, participants and phrases with start and end times in milliseconds.

#### Scenario: Title
- **WHEN** no title is supplied
- **THEN** the title is the source file name without its extension

#### Scenario: Participant naming
- **WHEN** the transcript is built
- **THEN** participants are numbered by their first recognized phrase and named "Спикер 1", "Спикер 2", …

#### Scenario: App recording
- **WHEN** the source is a recording made in the app
- **THEN** the transcript stores the recording start time and its marks that fall within the duration

#### Scenario: Audio ownership
- **WHEN** the source was copied into app storage or is an app recording
- **THEN** the transcript owns the audio and deletes it together with itself

### Requirement: One job at a time
The system SHALL run at most one transcription at a time, independently of the screen being shown.

#### Scenario: Second start
- **WHEN** a job is running and the user tries to start another
- **THEN** the second one is not started and "Дождитесь окончания текущей расшифровки" is shown

#### Scenario: Leaving the screen
- **WHEN** the user leaves the home screen or the app while a job is running
- **THEN** the job continues

### Requirement: Background execution with a progress notification
The system SHALL keep a running job (and running model downloads) alive with a foreground service that holds a partial wake lock, shows an ongoing low-importance notification with progress and, for a job, a "Отменить" action, and stops itself as soon as nothing is running.

#### Scenario: Notification
- **WHEN** a job is running
- **THEN** the notification shows the file name, the status text and a progress bar

#### Scenario: Idle
- **WHEN** no job and no download is running
- **THEN** the service removes its notification, releases the wake lock and stops

#### Scenario: System time limit
- **WHEN** the system times the foreground service out (Android 15 limits it to 6 hours a day)
- **THEN** the running job is cancelled and the service stops

#### Scenario: Notification permission
- **WHEN** a job starts on Android 13 or later without the notification permission
- **THEN** the permission is requested

### Requirement: Cancellation
The system SHALL let the user cancel a running job from the home screen or the notification.

#### Scenario: Cancel
- **WHEN** the user cancels
- **THEN** the job stops, the job state returns to idle, temporary files and any copied audio are deleted, and no transcript is saved

### Requirement: Completion
The system SHALL save the finished transcript, show a notification "Расшифровка готова" with its title that opens it, and offer to open it from the home screen.

#### Scenario: Finished while the app is open
- **WHEN** a job finishes while the home screen is visible
- **THEN** the list scrolls to the top and a message «Расшифровка «<title>» готова» with the action "Открыть" is shown

#### Scenario: Redo keeps favourites
- **WHEN** a job re-creates an existing transcript (same id)
- **THEN** the new transcript replaces the old one and keeps its favourite flag

### Requirement: Failure reporting
The system SHALL report a failed job in the app and in a notification "Не удалось расшифровать «<file>»" with the reason, and SHALL describe the common causes in plain words.

#### Scenario: Out of memory
- **WHEN** the job runs out of memory
- **THEN** the message is "Не хватило памяти. Выберите модель поменьше или закройте другие приложения."

#### Scenario: Other errors
- **WHEN** an unexpected error occurs
- **THEN** the message is "Ошибка обработки: <reason>"

#### Scenario: Failure card
- **WHEN** a job has failed
- **THEN** the home screen shows a card with the reason and the actions "Модели" and "Закрыть"

### Requirement: Job card on the home screen
The system SHALL show a running job on the home screen as a card with its file name, status, progress and a cancel button, scrolling the list to the top when a job or a recording banner appears.

#### Scenario: Running job
- **WHEN** a job is running
- **THEN** the home screen shows its card at the top of the list

### Requirement: Empty result
The system SHALL save a transcript with no phrases when no speech is found and SHALL present it as "Речь не обнаружена".

#### Scenario: No speech
- **WHEN** the recording has no recognizable speech
- **THEN** the transcript is saved with zero phrases and its list card reads "Речь не обнаружена"
