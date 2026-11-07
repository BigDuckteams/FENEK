# Spec Delta

## ADDED Requirements

### Requirement: LLM stages in the pipeline
The system SHALL run, after speech recognition and only when the corresponding mode is on and the model is available, a text-correction stage over all recognized phrases and then a summary-and-tasks stage, before saving.

#### Scenario: Both modes on
- **WHEN** both LLM modes are on and the LLM is installed
- **THEN** the job runs correction, then summary and tasks, then saves

#### Scenario: Correction only
- **WHEN** only "Исправлять текст" is on
- **THEN** the job runs correction and then saves, with rule-based highlights computed when the transcript is viewed

#### Scenario: Failure of an LLM stage
- **WHEN** an LLM stage fails
- **THEN** the job continues with the results it has and completes successfully

### Requirement: Processing time and real-time factor
The system SHALL measure the wall-clock processing time of every job from the start of preparation to the end of saving, including model loading, and SHALL show the real-time factor (processing time divided by recording duration) on the transcript screen.

#### Scenario: RTF value
- **WHEN** a 120-second recording is processed in 30 seconds
- **THEN** the transcript shows an RTF of 0.25

#### Scenario: Header
- **WHEN** a transcript is opened
- **THEN** its header shows the processing time and the RTF next to the duration

#### Scenario: Redone transcript
- **WHEN** a transcript is re-processed
- **THEN** the processing time and RTF are those of the latest run

### Requirement: Pausing and resuming a job
The system SHALL let the user pause the running job and continue it later without losing what it has done. A pause takes effect at the next safe point (between stages, between pieces of speaker detection, between phrases) and releases the speech recognizer; a paused job stays in the queue until it is resumed or removed, and the next waiting job starts at once.

#### Scenario: Pause
- **WHEN** the user presses "Пауза"
- **THEN** the card shows "Ставлю на паузу…" until the job stops, then the job is listed in the queue as "На паузе · N %" and the next waiting job starts

#### Scenario: Language model stages
- **WHEN** a pause is asked for while the language model works
- **THEN** it takes effect when those stages end, so that the model is not kept in memory while the job waits

#### Scenario: Start another now
- **WHEN** the user presses the start button of a waiting or paused job while another one runs
- **THEN** the running job is paused and the chosen one starts

#### Scenario: Resume
- **WHEN** a paused job is resumed and nothing else is running
- **THEN** it continues from where it stopped, not from the beginning

#### Scenario: Paused job alone
- **WHEN** a job is paused and no other job waits
- **THEN** nothing runs, and the job stays paused until the user resumes or removes it

#### Scenario: Removing a paused job
- **WHEN** the user removes a paused job
- **THEN** its temporary files and any copied audio are deleted and no transcript is saved

### Requirement: Queue on the home screen
The system SHALL list the jobs that wait or are paused on the home screen under "Очередь", below the running job, each with its file name, its state ("Ждёт своей очереди" or "На паузе · N %"), a button that starts or resumes it now and a button that removes it, and SHALL keep a recording that is queued out of "Записи без расшифровки".

#### Scenario: Listed
- **WHEN** a job waits or is paused
- **THEN** it is shown in the queue with its state and the two buttons

#### Scenario: Several results
- **WHEN** queued jobs finish one after another
- **THEN** each gets its own notification "Расшифровка готова" and its own message on the home screen, also if the home screen was not shown when it finished

## MODIFIED Requirements

### Requirement: Pipeline and progress
The system SHALL process a job in the stages preparation, decoding, speaker detection, speech recognition, text correction (only when on), summary and tasks (only when on) and saving, and SHALL report overall progress from 0 to 1 that never decreases; without LLM stages the stage ranges are 0–2 %, 2–12 %, 12–55 %, 55–99 % and 99–100 %, and with LLM stages enabled they are 0–2 %, 2–8 %, 8–35 %, 35–65 %, correction 65–88 %, summary and tasks 88–98 % and saving 98–100 %.

#### Scenario: Progress text
- **WHEN** the job is running
- **THEN** the status shows "Подготовка файла…", "Чтение аудио…", "Определение говорящих…", "Распознавание речи: N из M фраз", "Правка текста: N из M фраз", "Выжимка и задачи…" or "Сохранение…" according to the stage

#### Scenario: Recognition progress
- **WHEN** phrases are being recognized
- **THEN** progress within the stage is proportional to the speech duration already recognized

#### Scenario: Correction progress
- **WHEN** phrases are being corrected
- **THEN** progress within the stage is proportional to the number of phrases processed

#### Scenario: Skipped stage
- **WHEN** an LLM stage is switched off
- **THEN** it is not shown and progress continues from the end of the previous stage without going backwards

### Requirement: One job at a time
The system SHALL run at most one transcription at a time, independently of the screen being shown, and SHALL put every further one in a queue that starts each job by itself, in the order of adding, as soon as nothing is running.

#### Scenario: Second start
- **WHEN** a job is running and the user starts another
- **THEN** the second one is queued, "Добавлено в очередь, начнётся после текущей" is shown, and it starts when the first ends, fails or is cancelled

#### Scenario: Same file twice
- **WHEN** the recording or transcript is already running, queued or paused
- **THEN** it is not added again and "Эта запись уже в очереди" is shown

#### Scenario: Failure does not stop the queue
- **WHEN** a job fails
- **THEN** the next job starts, and the failure stays on the home screen until it is dismissed

#### Scenario: Leaving the screen
- **WHEN** the user leaves the home screen or the app while a job is running
- **THEN** the job continues

### Requirement: Background execution with a progress notification
The system SHALL keep running jobs and model downloads alive with a foreground service that shows an ongoing low-importance notification with progress and, for a job, the actions "Пауза" and "Отменить". It SHALL hold a partial wake lock while something is computed, stay alive without it while only paused jobs are left, and stop when nothing is running, queued or paused.

#### Scenario: Notification
- **WHEN** a job is running
- **THEN** the notification shows the file name, the status text, a progress bar and, if jobs wait, how many

#### Scenario: Paused jobs only
- **WHEN** a job is paused and nothing runs
- **THEN** the notification reads "Расшифровка на паузе" with the actions "Продолжить" and "Убрать", and no wake lock is held

#### Scenario: Idle
- **WHEN** no job is running, queued or paused and no download is running
- **THEN** the service removes its notification, releases the wake lock and stops

#### Scenario: System time limit
- **WHEN** the system times the foreground service out (Android 15 limits it to 6 hours a day)
- **THEN** the running job and the queue are cancelled and the service stops

#### Scenario: Notification permission
- **WHEN** a job starts or is queued on Android 13 or later without the notification permission
- **THEN** the permission is requested

### Requirement: Job card on the home screen
The system SHALL show a running job on the home screen as a card with its file name, status, progress and the buttons "Пауза" and "Отменить", scrolling the list to the top when a job or a recording banner appears.

#### Scenario: Running job
- **WHEN** a job is running
- **THEN** the home screen shows its card at the top of the list

#### Scenario: Pausing
- **WHEN** a pause was asked for and has not taken effect yet
- **THEN** the status reads "Ставлю на паузу…" and the pause button is disabled
