# Spec Delta

## Purpose

Runs a quantized large language model entirely on the device to improve the recognized text (punctuation, casing, obvious recognition slips) and to write the summary and task list of a conversation, without ever contacting a server and without being able to damage the transcript when it misbehaves.

## ADDED Requirements

### Requirement: The LLM runs only on the device
The system SHALL run the language model locally from model files stored on the device and SHALL NOT send any text, audio or prompt to a remote service.

#### Scenario: Airplane mode
- **WHEN** the device has no network connection and the LLM modes are on
- **THEN** a transcription completes and the LLM modes work as usual

#### Scenario: No outgoing connections
- **WHEN** a transcription with the LLM modes runs
- **THEN** the app opens no network connection

### Requirement: Two independent LLM modes
The system SHALL provide two independent switches, "Исправлять текст" (phrase correction) and "Выжимка и задачи" (summary and tasks), with the summary on and the correction off by default, and SHALL load the LLM only when at least one of them is on.

#### Scenario: Defaults
- **WHEN** nothing has been changed in the settings
- **THEN** "Выжимка и задачи" is on and "Исправлять текст" is off, so the phrase texts are exported as recognized

#### Scenario: Both off
- **WHEN** both switches are off
- **THEN** the LLM is not loaded and a job takes the same path as without an LLM

#### Scenario: One on
- **WHEN** only "Выжимка и задачи" is on
- **THEN** the phrase texts stay exactly as recognized

#### Scenario: Choice per job
- **WHEN** a new transcription is started
- **THEN** the switches take their saved values

### Requirement: Phrase correction preserves meaning and structure
The system SHALL send each recognized phrase through the LLM to restore punctuation and casing and fix obvious recognition errors, SHALL NOT change the speaker, start time or end time of any phrase, and SHALL NOT merge, split, add or remove phrases.

#### Scenario: Missing punctuation
- **WHEN** a phrase "привет как дела" is recognized without punctuation
- **THEN** the corrected phrase is "Привет, как дела?" and its speaker and times are unchanged

#### Scenario: Phrase count
- **WHEN** correction finishes
- **THEN** the transcript has the same number of phrases, with the same speakers and timestamps, as before correction

#### Scenario: Context
- **WHEN** a phrase is corrected
- **THEN** neighbouring phrases may be given to the model as context, but only the phrase itself is replaced

### Requirement: Corrections are guarded
The system SHALL accept the corrected text of a phrase only when, ignoring case and punctuation, it differs from the recognized text by at most 30 % of its words, its word count is within 70 % to 140 % of the original, and every word that was replaced still looks like the original (at most 40 % of its letters differ), and otherwise SHALL keep the recognized text.

#### Scenario: Hallucinated rewrite
- **WHEN** the LLM returns a text that shares less than 70 % of its words with the recognized phrase
- **THEN** the recognized text is kept

#### Scenario: Empty answer
- **WHEN** the LLM returns an empty or unparseable answer for a phrase
- **THEN** the recognized text is kept

#### Scenario: A different word in place of a recognized one
- **WHEN** the LLM answers "Правильно" where the recognized text had "хорошо"
- **THEN** the recognized text is kept, while a typo fix such as "недилю" to "неделю" is accepted

#### Scenario: Pure punctuation change
- **WHEN** the corrected text differs only by punctuation and casing
- **THEN** it is accepted

#### Scenario: Digits and names
- **WHEN** the corrected text drops or changes a number that is in the recognized text
- **THEN** the recognized text is kept

### Requirement: Recognized text is preserved
The system SHALL store the originally recognized text of every phrase whose text the LLM changed, so that it can be shown, restored and exported.

#### Scenario: Changed phrase
- **WHEN** correction changes a phrase
- **THEN** the transcript holds both the corrected text (shown and exported) and the recognized text

#### Scenario: Unchanged phrase
- **WHEN** correction leaves a phrase as it was
- **THEN** no separate recognized text is stored

### Requirement: Summary and tasks from the LLM
The system SHALL ask the LLM, after correction, for a short summary of 2 to 5 points and for the tasks and agreements of the conversation, each tied to the phrase it comes from, SHALL drop any item that does not point to an existing phrase, and SHALL store the result with the transcript so that it is produced once per job.

#### Scenario: Items are playable
- **WHEN** the LLM returns a task referring to phrase 12
- **THEN** the task is shown with that phrase's speaker and time and plays from there when tapped

#### Scenario: Invalid reference
- **WHEN** an item refers to a phrase number that does not exist
- **THEN** the item is dropped

#### Scenario: Deadline
- **WHEN** an LLM task sentence contains a deadline expression
- **THEN** the deadline is resolved from the conversation date with the same rules as for rule-based tasks

#### Scenario: Long conversation
- **WHEN** the conversation does not fit in the model's context
- **THEN** it is processed in consecutive parts and the results are merged, with at most 5 summary points and 50 tasks

### Requirement: Fallback to the rule-based result
The system SHALL use the rule-based summary and tasks whenever the LLM is off, unavailable, fails, times out or returns nothing usable, and SHALL never fail a job because of the LLM.

#### Scenario: Model missing
- **WHEN** an LLM mode is on but the model files are not on the device
- **THEN** the job completes with uncorrected text and rule-based highlights

#### Scenario: Load failure
- **WHEN** the LLM cannot be loaded or runs out of memory
- **THEN** the job completes with uncorrected text and rule-based highlights, and the transcript shows that the text was not corrected

#### Scenario: Unusable summary
- **WHEN** the LLM output cannot be parsed
- **THEN** the rule-based summary and tasks are shown

### Requirement: The recognized transcript survives a crash in the LLM stages
The system SHALL keep a recoverable copy of the transcript with the recognized text before the LLM stages start, SHALL turn it into a normal transcript at the next launch if the process died before the job finished, and SHALL discard it when the job ends, fails or is cancelled.

#### Scenario: Process dies in the LLM
- **WHEN** the app process is killed while the LLM is working
- **THEN** after the next launch the transcript exists in the list with its recognized text and without LLM changes

#### Scenario: Recovered redo
- **WHEN** a transcript that was being re-processed is recovered
- **THEN** it replaces the old one and keeps its favourite flag

#### Scenario: Cancel
- **WHEN** the user cancels during the LLM stages
- **THEN** no recoverable copy remains and nothing is added to the list

#### Scenario: Done signal
- **WHEN** a job runs the LLM stages
- **THEN** the "Расшифровка готова" signal is given after the LLM stages end, not after the checkpoint

### Requirement: Crash protection
The system SHALL detect that a previous run died while the LLM was loaded, SHALL then switch both LLM modes off and tell the user once, and SHALL skip the LLM stages when the device has too little free memory to load the model.

#### Scenario: Previous crash
- **WHEN** the app starts and the previous run ended inside the LLM
- **THEN** both switches are off, a message explains why, and they can be turned on again manually

#### Scenario: Low memory
- **WHEN** the free memory is below what the model needs
- **THEN** the LLM stages are skipped and the job completes with the recognized text

### Requirement: Bounded generation time
The system SHALL limit every LLM request in generated length (proportional to its input) and in time (a per-request timeout), and SHALL treat a request that exceeds either limit as failed for that request only.

#### Scenario: Runaway generation
- **WHEN** the model keeps generating beyond the length limit
- **THEN** generation stops and the affected phrases keep their recognized text

### Requirement: Time budget of the LLM stages
The system SHALL give all LLM stages of one job a common time budget of half the recording's length, but not less than 2 minutes and not more than 10 minutes, counted from the moment the model is ready. A request SHALL get at most what is left of the budget, and the remaining requests SHALL be skipped when less than 15 seconds are left.

#### Scenario: Slow device
- **WHEN** the requests of the LLM stages together use up the time budget
- **THEN** the remaining requests are skipped, what is finished is kept and the job completes, with the rule-based highlights if the summary produced nothing

#### Scenario: Budget ends inside a long conversation
- **WHEN** the budget ends before every window of a long conversation is summarized
- **THEN** the windows that were done are spread over the whole conversation (the first and the last window first, then the middle of every gap)

#### Scenario: Both stages on
- **WHEN** both "Исправлять текст" and "Выжимка и задачи" are on
- **THEN** the correction may use at most half of the budget and the summary gets the rest, including whatever the correction left unused

### Requirement: Repeatable output
The system SHALL generate with deterministic settings, so that processing the same recording twice with the same settings gives the same corrected text.

#### Scenario: Repeat run
- **WHEN** a recording is processed twice with the same model and settings
- **THEN** both transcripts contain the same text

### Requirement: Memory is released between stages
The system SHALL release the speech recognizer before loading the LLM and SHALL release the LLM as soon as its stages end, so that the two models are not in memory at the same time.

#### Scenario: Sequential models
- **WHEN** a job moves from recognition to correction
- **THEN** the recognizer is closed before the LLM is loaded

#### Scenario: After the job
- **WHEN** a job ends, fails or is cancelled
- **THEN** the LLM is released

### Requirement: Cancellation and progress
The system SHALL report LLM progress as a stage "Правка текста…" (and "Выжимка и задачи…") with the number of phrases processed, and SHALL stop between requests when the job is cancelled.

#### Scenario: Cancel during correction
- **WHEN** the user cancels while phrases are being corrected
- **THEN** the job stops after the current request and nothing is saved

### Requirement: Transcript shows what the LLM did
The system SHALL show in the transcript header whether the text was corrected by the LLM and which model did it, and SHALL mark phrases whose text differs from the recognized text.

#### Scenario: Corrected transcript
- **WHEN** a transcript was corrected
- **THEN** the header names the LLM and a phrase that was changed shows a marker that reveals the recognized text

#### Scenario: Uncorrected transcript
- **WHEN** correction was off or failed
- **THEN** the header says the text is shown as recognized
