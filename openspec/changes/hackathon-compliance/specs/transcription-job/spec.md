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
