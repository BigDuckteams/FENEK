# audio-decoding Specification

## Purpose
Turns any audio file, or the audio track of a video, that the device can decode into a uniform 16 kHz mono recording on disk that the rest of the transcription pipeline consumes, and reports clear errors when this is not possible.

## Requirements

### Requirement: Any decodable audio source is accepted
The system SHALL decode any audio file, or the first audio track of a video file, that the platform codecs support (including MP3, AAC/M4A, OGG/Opus, FLAC, WAV, AMR and 3GP).

#### Scenario: Audio track of a video
- **WHEN** the user transcribes a video file that contains an audio track
- **THEN** the audio track is decoded and the video is ignored

#### Scenario: Several tracks
- **WHEN** a file has several tracks
- **THEN** the first track whose MIME type starts with `audio/` is used

### Requirement: Output is 16 kHz mono PCM on disk
The system SHALL convert the decoded audio to 16 kHz, mono, 16-bit PCM and write it to a temporary file in the app cache instead of keeping it in memory, so that long recordings do not exhaust memory.

#### Scenario: Stereo source
- **WHEN** the source has more than one channel
- **THEN** the channels are mixed down to one before resampling

#### Scenario: Other sample rates
- **WHEN** the source sample rate is not 16 kHz
- **THEN** the audio is resampled to 16 kHz with a band-limited resampler

#### Scenario: Progress
- **WHEN** decoding runs
- **THEN** its progress is reported as a fraction from 0 to 1

### Requirement: Temporary PCM is always removed
The system SHALL delete the temporary PCM file when a transcription finishes, fails or is cancelled.

#### Scenario: Failure after decoding
- **WHEN** a later stage of a job fails
- **THEN** the temporary PCM file no longer exists

### Requirement: Undecodable input is rejected with a reason
The system SHALL fail with a specific message when the file cannot be opened, has no audio track, uses a format that the device cannot decode, or is shorter than 0.3 seconds.

#### Scenario: File cannot be opened
- **WHEN** the source cannot be opened
- **THEN** the job fails with "Не удалось открыть файл"

#### Scenario: No audio track
- **WHEN** the file contains no audio track
- **THEN** the job fails with "В файле нет звуковой дорожки"

#### Scenario: Unsupported codec
- **WHEN** the device has no decoder for the track's format
- **THEN** the job fails with "Формат <mime> не поддерживается устройством"

#### Scenario: Too short
- **WHEN** the decoded audio is shorter than 0.3 seconds
- **THEN** the job fails with "Запись пустая или слишком короткая"

### Requirement: Temporarily readable sources are copied into app storage
The system SHALL copy a source that is only temporarily readable (for example an audio file shared from another app) into the app's private audio folder before decoding, keep the file extension when it has 1 to 5 letters or digits (otherwise use "audio"), and treat the copy as owned by the resulting transcript.

#### Scenario: Shared file
- **WHEN** a job starts for a shared file marked as needing a copy
- **THEN** the file is copied under the job id and the transcript references the copy and owns it

#### Scenario: Failed job removes the copy
- **WHEN** a job that made a copy fails or is cancelled
- **THEN** the copy is deleted

#### Scenario: Copy cannot be read
- **WHEN** the source stream cannot be opened for copying
- **THEN** the job fails with "Не удалось прочитать файл"

### Requirement: Files picked in the system picker are referenced, not copied
The system SHALL keep a file chosen in the system file picker in place when read access can be persisted, so that it stays playable, and SHALL copy it into app storage only when persistable access is not granted.

#### Scenario: Persistable access granted
- **WHEN** the user picks a file and persistable read permission is granted
- **THEN** the transcript references the original URI and the file is not copied

#### Scenario: Persistable access refused
- **WHEN** persistable read permission cannot be taken
- **THEN** the file is copied into app storage before processing
