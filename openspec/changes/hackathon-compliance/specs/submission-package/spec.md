# Spec Delta

## Purpose

Defines the materials that must ship in the git repository next to the APK so that the organizers can install, run, evaluate and understand the solution, and a tool that measures its accuracy and speed the same way the organizers do.

## ADDED Requirements

### Requirement: Repository follows the organizers' template
The repository SHALL contain the template's top-level layout: `README.md`, `docs/ARCHITECTURE.md`, `docs/LIMITATIONS.md`, `docs/TEST_RESULTS.md`, `artifacts/demo/`, `artifacts/test-results/`, `config/` and `src/`, with every template file filled in or removed when it does not apply to this solution.

#### Scenario: Template files present
- **WHEN** the repository is opened
- **THEN** each of those paths exists and none contains the template's placeholder text (such as "replace-me" or "DELETEME")

#### Scenario: Inapplicable template file
- **WHEN** a template file belongs to a different task (the acoustic-protocol specification)
- **THEN** it is removed or replaced by a document on this solution's output format

### Requirement: Install and usage instructions
The `README.md` SHALL tell the reader how to install the APK, how to import a recording and transcribe it, how to export the result, how to download the models on first launch, how to check that processing works without a network, and how to build the project from source.

#### Scenario: Reproducible install
- **WHEN** a reader follows the install section on a phone
- **THEN** they download the models once, then reach a finished transcript of a recording and its exported JSON with the network switched off

#### Scenario: Build from source
- **WHEN** a reader follows the build section
- **THEN** it lists the required JDK, Android SDK and NDK versions and and the command that produces the APK

### Requirement: Architecture and models description
`docs/ARCHITECTURE.md` SHALL describe the context, the components and the processing pipeline, the models used (name, task, size, licence, source), the key technical decisions with their reasons, the dependencies, and the privacy guarantees.

#### Scenario: Models listed
- **WHEN** the document is read
- **THEN** every model (recognition, segmentation, voice fingerprints, LLM) appears with its size, licence and download source

#### Scenario: Local processing stated
- **WHEN** the privacy section is read
- **THEN** it states that all processing is on the device and names how this was verified

### Requirement: Limitations description
`docs/LIMITATIONS.md` SHALL list the supported conditions, the known limitations (including that the LLM may alter text and that diarization depends on recording quality), what is not included, the demonstration risks and possible further work.

#### Scenario: Honest limits
- **WHEN** the document is read
- **THEN** it names the languages that were actually tested, the total size of the models downloaded on first launch, and that a network connection is needed once for that download

### Requirement: Test method and results
`docs/TEST_RESULTS.md` and `artifacts/test-results/results.csv` SHALL describe how the solution was tested and contain, for each test recording, the device, the duration, the processing time, the RTF, the WER against a reference transcript, the expected and detected number of speakers, the models and LLM switches used and the commit.

#### Scenario: Result row
- **WHEN** a recording is tested
- **THEN** one row is added with its WER and RTF and the commit it was measured on

#### Scenario: LLM comparison
- **WHEN** the results are read
- **THEN** they include at least one comparison of the same recordings with phrase correction on and off

#### Scenario: Test data
- **WHEN** the results are read
- **THEN** the recordings and reference transcripts used are listed, or their absence from the repository is explained

### Requirement: Accuracy and speed tool
The repository SHALL contain a command-line tool that reads a reference transcript and an exported JSON file and prints the word error rate, the number of detected speakers, and, given the processing time, the RTF.

#### Scenario: WER
- **WHEN** the tool compares an export with a reference
- **THEN** it prints the WER computed on words after lowercasing, mapping "ё" to "е" and removing punctuation

#### Scenario: Identical texts
- **WHEN** the export equals the reference
- **THEN** the WER is 0

#### Scenario: RTF
- **WHEN** the processing time is 30 s for a 120 s recording
- **THEN** the tool prints an RTF of 0.25

#### Scenario: Schema check
- **WHEN** the exported file does not follow the required `transcript` schema
- **THEN** the tool reports which field is wrong

### Requirement: Demo materials
`artifacts/demo/` SHALL contain, or link to, a video that shows installing the APK, importing a recording, running transcription and diarization, viewing the result and exporting the JSON, with the models already downloaded and the device in airplane mode while processing.

#### Scenario: Airplane mode shown
- **WHEN** the video is watched
- **THEN** it shows airplane mode enabled before processing starts

### Requirement: Release APK
The submission SHALL include a release APK that is installable on an arm64 phone and, after the models have been downloaded once, runs the whole task without network access.

#### Scenario: Fresh install, then offline
- **WHEN** the APK is installed on a phone, the models are downloaded, and airplane mode is switched on
- **THEN** the app transcribes an imported recording completely and exports the JSON

#### Scenario: Warning before the check
- **WHEN** the README or the limitations are read
- **THEN** they tell the reader to open the app once with a network connection before an offline check
