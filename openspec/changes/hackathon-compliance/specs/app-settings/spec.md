# Spec Delta

## ADDED Requirements

### Requirement: Language model settings
The system SHALL let the user switch "Исправлять текст" and "Выжимка и задачи" on and off independently, choose the language model among the installed ones, and see its status (installed, missing) on the settings screen, with "Выжимка и задачи" on and "Исправлять текст" off by default.

#### Scenario: Switch persistence
- **WHEN** the user turns "Исправлять текст" on and restarts the app
- **THEN** it is still on

#### Scenario: Hint
- **WHEN** the section is shown
- **THEN** a hint explains that correction improves punctuation and obvious errors but makes processing slower, and that turning it off keeps the text exactly as recognized

#### Scenario: Missing model
- **WHEN** the language model is not installed
- **THEN** the switches stay available but the section states that the model is missing and offers to install it

#### Scenario: Open models
- **WHEN** the user taps the language model entry
- **THEN** the models screen opens with the language models

### Requirement: Language model choice is validated on load
The system SHALL replace an unknown saved language model with the default one.

#### Scenario: Removed LLM
- **WHEN** the saved language model is not in the catalog
- **THEN** the default language model is used

## MODIFIED Requirements

### Requirement: Persisted settings with defaults
The system SHALL persist the recognition model, the voice-fingerprint model, the language model, the two LLM switches, the recognition language, the audio-cleanup switch, the diarization precision, the voice separation and the number of CPU threads, and SHALL use the defaults GigaAM v3 RNNT, the default voice model, the default language model, the summary switch on and the correction switch off, automatic language, audio cleanup off, balanced precision, separation 0.5 and a thread count equal to the number of CPU cores limited to 1–4.

#### Scenario: First run
- **WHEN** no setting has been saved
- **THEN** the defaults apply

#### Scenario: Persistence
- **WHEN** the user changes a setting and restarts the app
- **THEN** the changed value is still in effect
