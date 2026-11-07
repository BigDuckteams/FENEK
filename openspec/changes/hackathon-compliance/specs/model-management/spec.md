# Spec Delta

## ADDED Requirements

### Requirement: One-tap download of the default model set
The system SHALL offer, on the home screen and in the new-transcription sheet, a single action that downloads every model needed for the full pipeline with the current settings: the recognition model, the segmentation model, the voice-fingerprint model and, when an LLM mode is on, the language model.

#### Scenario: First launch
- **WHEN** the app is opened for the first time on a device with a network connection
- **THEN** the setup card lists the missing models with their total size and a button that downloads all of them

#### Scenario: Progress and resume
- **WHEN** the download is running
- **THEN** the card shows the downloaded and total size, and after an interruption the next attempt continues from the partial files

#### Scenario: Ready
- **WHEN** all models of the set are downloaded
- **THEN** the setup card disappears and the app works with no network connection at all

#### Scenario: Offline first launch
- **WHEN** the download is started with no network
- **THEN** the card shows the failure reason with a "Повторить" button and states that the internet is needed once to download the models

### Requirement: The language model is recommended, not required
The system SHALL NOT require a language model for a job to run; a missing LLM only switches the LLM modes off for that job, and SHALL offer its download separately in the setup card when an LLM mode is on and the model is missing.

#### Scenario: Required models ready, LLM missing
- **WHEN** the required models are ready, an LLM mode is on and the LLM is not downloaded
- **THEN** the setup card offers "Скачать языковую модель (<size>)" and transcription is not blocked

#### Scenario: LLM deleted
- **WHEN** the LLM model is deleted and the user starts a transcription
- **THEN** the transcription runs without correction and with rule-based highlights

### Requirement: Bundled model set in the fat build
The system SHALL, in the build that carries the default model set inside its APK (the "fat" build), copy those models into the app's model folder on the first launch without using the network, check them by size like downloaded ones, and show the progress as an installation. The build without bundled models SHALL keep downloading them.

#### Scenario: First launch of the fat build
- **WHEN** the fat build is opened for the first time, with or without a network
- **THEN** the setup card reads "Устанавливаю встроенные модели" with progress, and when the copy ends the app transcribes with no network

#### Scenario: Interrupted installation
- **WHEN** the app is closed during the installation
- **THEN** the next launch continues it and replaces the files that were not finished

#### Scenario: Deleted model
- **WHEN** the user deletes a bundled model and later asks for it again
- **THEN** it is copied from the APK again, without a network, and it is not installed again by itself

#### Scenario: Online build
- **WHEN** the build without bundled models is used
- **THEN** the models are downloaded as before

## MODIFIED Requirements

### Requirement: Model catalog
The system SHALL offer a fixed catalog of models: six speech-recognition models (GigaAM v3 RNNT, GigaAM v3 CTC, Zipformer RU, Whisper Base, Whisper Small, Parakeet TDT 0.6B v3), one speech-segmentation model (pyannote segmentation 3.0), one voice-fingerprint model (3D-Speaker ERes2Net) and one or more language models in GGUF format (the default being Qwen2.5 1.5B Instruct, 4-bit), each with title, summary, supported languages, licence and file sizes.

#### Scenario: Default recognition model
- **WHEN** nothing has been chosen
- **THEN** GigaAM v3 RNNT is the recognition model

#### Scenario: Default LLM
- **WHEN** nothing has been chosen
- **THEN** the default language model of the catalog is the language model

#### Scenario: Unknown saved model
- **WHEN** a saved model id is no longer in the catalog
- **THEN** the default model of that kind is used

#### Scenario: Language capabilities
- **WHEN** the catalog is shown
- **THEN** only Whisper models offer a language choice (automatic detection plus Russian, English, Ukrainian, Belarusian, Kazakh, Uzbek, German, French, Spanish, Italian, Polish, Turkish, Arabic, Chinese and Japanese), and Zipformer RU is marked as producing text without punctuation

### Requirement: Required models for a job
The system SHALL treat the segmentation model, the voice-fingerprint model and the selected recognition model as the models required to process a recording; the language model is optional.

#### Scenario: Setup card
- **WHEN** any required model for the current settings is not ready
- **THEN** the home screen shows "Скачайте модели для работы офлайн" with "Скачать (<total size>)" and "Выбрать модели"

#### Scenario: Everything ready
- **WHEN** all required models are ready
- **THEN** no setup card for required models is shown

### Requirement: Network use is limited to model download
The system SHALL use the network only for downloading models, SHALL work completely without a network connection once the models are downloaded, and SHALL NOT transmit recordings, transcripts, prompts or analytics.

#### Scenario: Offline operation
- **WHEN** the models are downloaded and the device is offline
- **THEN** recording, import, transcription with the LLM modes, viewing and export all work

#### Scenario: No traffic during processing
- **WHEN** a transcription is running
- **THEN** the app sends and receives no network data
