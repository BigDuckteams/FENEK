# Spec Delta

## Purpose

Provides the on-device speech models the app depends on: a fixed catalog, resumable downloads from mirrors with integrity checks, and the screens that let the user see, download and delete them. Model download is the only network use of the app.

## ADDED Requirements

### Requirement: Model catalog
The system SHALL offer a fixed catalog of models: six speech-recognition models (GigaAM v3 RNNT, GigaAM v3 CTC, Zipformer RU, Whisper Base, Whisper Small, Parakeet TDT 0.6B v3), one speech-segmentation model (pyannote segmentation 3.0) and one voice-fingerprint model (3D-Speaker ERes2Net), each with title, summary, supported languages, licence and file sizes.

#### Scenario: Default recognition model
- **WHEN** nothing has been chosen
- **THEN** GigaAM v3 RNNT is the recognition model

#### Scenario: Unknown saved model
- **WHEN** a saved model id is no longer in the catalog
- **THEN** the default model of that kind is used

#### Scenario: Language capabilities
- **WHEN** the catalog is shown
- **THEN** only Whisper models offer a language choice (automatic detection plus Russian, English, Ukrainian, Belarusian, Kazakh, Uzbek, German, French, Spanish, Italian, Polish, Turkish, Arabic, Chinese and Japanese), and Zipformer RU is marked as producing text without punctuation

### Requirement: Required models for a job
The system SHALL treat the segmentation model, the voice-fingerprint model and the selected recognition model as the models required to process a recording.

#### Scenario: Setup card
- **WHEN** any required model for the current settings is not ready
- **THEN** the home screen shows "Скачайте модели для работы офлайн" with "Скачать (<total size>)" and "Выбрать модели"

#### Scenario: Everything ready
- **WHEN** all required models are ready
- **THEN** no setup card is shown

### Requirement: Readiness is verified by file size
The system SHALL consider a model ready only when every one of its files exists and has exactly the expected size.

#### Scenario: Truncated file
- **WHEN** a model file has a different size than expected
- **THEN** the model is not ready

#### Scenario: Corrupted result
- **WHEN** a download finishes but the files do not pass the check
- **THEN** the model state becomes failed with "Файлы модели повреждены"

### Requirement: Downloading
The system SHALL download a model's files one after another with progress counted in bytes of the whole model, and SHALL show progress on the models screen, the new-transcription sheet, the home screen and in a notification "Загрузка моделей".

#### Scenario: Start
- **WHEN** the user starts a download
- **THEN** the model state becomes downloading with the bytes already on disk and a foreground service keeps the process alive

#### Scenario: Already downloaded
- **WHEN** a download is requested for a model that is ready
- **THEN** nothing is downloaded and the state is ready

#### Scenario: Duplicate request
- **WHEN** a download is requested for a model that is already downloading
- **THEN** the request is ignored

### Requirement: Resumable downloads
The system SHALL download each file into a ".part" file and resume an interrupted download with an HTTP Range request.

#### Scenario: Interrupted
- **WHEN** a download is interrupted and started again
- **THEN** it continues from the end of the partial file

#### Scenario: Server rejects the range
- **WHEN** the server answers 416 for a resume request
- **THEN** the partial file is deleted and the download restarts once from the beginning

#### Scenario: Wrong size
- **WHEN** a finished file has a size different from the catalog
- **THEN** the partial file is deleted and that source counts as failed

### Requirement: Mirrors and redirects
The system SHALL try the sources of a file in order of preference (Hugging Face, hf-mirror.com and, for speaker models, GitHub releases) and SHALL follow up to eight redirects.

#### Scenario: Fallback
- **WHEN** a source fails
- **THEN** the next source is tried

#### Scenario: All sources fail
- **WHEN** every source fails
- **THEN** the model state is failed with "Не удалось скачать <file>: <reason>" (or "нет соединения") and a "Повторить" button is shown

#### Scenario: Too many redirects
- **WHEN** a source redirects more than eight times
- **THEN** that source fails

### Requirement: Free space check
The system SHALL refuse to start downloading when the remaining bytes plus a 100 MB reserve do not fit in free storage.

#### Scenario: Not enough space
- **WHEN** free space is insufficient
- **THEN** the download fails with "Недостаточно места: нужно ещё N МБ"

### Requirement: Cancelling and deleting
The system SHALL keep partial files when a download is cancelled so that it can resume later, and SHALL remove all files of a model when the user deletes it after confirmation.

#### Scenario: Cancel
- **WHEN** a download is cancelled with "Отмена"
- **THEN** the state returns to missing and the partial files stay on disk

#### Scenario: Delete
- **WHEN** the user confirms deleting a model on the models screen
- **THEN** its files are removed, the state is missing and the screen tells how much space was freed

### Requirement: Obsolete models are cleaned up
The system SHALL delete, at startup, model folders that are no longer in the catalog.

#### Scenario: Removed model
- **WHEN** a folder in model storage does not match any catalog id
- **THEN** it is deleted

### Requirement: Models screen
The system SHALL show the models grouped under "Распознавание речи" and "Определение говорящих" with their status (downloaded, downloading, failed, missing), licence, and the total space they occupy ("Занято моделями: …").

#### Scenario: Downloaded model
- **WHEN** a model is ready
- **THEN** the card shows "Скачана" and a delete action

#### Scenario: Choosing the default model
- **WHEN** the user taps a recognition model (or the voice-fingerprint model) on the models screen
- **THEN** it becomes the saved default, shown by a selected radio button, whether or not it is downloaded
- **AND** the segmentation model, which has no alternatives, is not selectable

#### Scenario: Model details
- **WHEN** a model card is shown
- **THEN** it displays the title, a "рекомендуется" badge for the recommended model, languages, size, whether it produces punctuation, a summary and the licence

### Requirement: Network use is limited to model download
The system SHALL use the network only for downloading models, and SHALL NOT transmit recordings, transcripts or analytics.

#### Scenario: Offline operation
- **WHEN** all required models are present and the device is offline
- **THEN** recording, transcription, viewing and export all work
