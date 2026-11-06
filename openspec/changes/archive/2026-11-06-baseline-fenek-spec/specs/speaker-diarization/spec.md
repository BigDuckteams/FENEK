# Spec Delta

## Purpose

Determines who spoke when in a recording of any length, entirely on the device, and turns the result into clean, non-overlapping speaker turns that can be recognized one by one.

## ADDED Requirements

### Requirement: Recordings of any length are processed in bounded memory
The system SHALL find speech by processing the recording in blocks of about ten minutes, cutting each block boundary at a quiet moment (searched within about 20 seconds of the nominal boundary), so that memory use does not grow with recording length.

#### Scenario: Long recording
- **WHEN** a recording is longer than about 12.5 minutes
- **THEN** it is split into consecutive blocks of roughly ten minutes and the speech found in all blocks is combined on one timeline

#### Scenario: Short recording
- **WHEN** a recording is shorter than about 12.5 minutes
- **THEN** it is processed as a single block

### Requirement: No speech yields an empty result
The system SHALL return no speaker segments when no speech is found.

#### Scenario: Silence
- **WHEN** the recording contains no speech
- **THEN** diarization returns an empty list and the job completes with a transcript that has no phrases

### Requirement: Participants are identified by voice across the whole recording
The system SHALL identify participants from voice fingerprints computed on short windows of single-voice speech, and SHALL cluster the fingerprints of the whole recording together, so that a participant keeps the same identity throughout the recording regardless of how the segmentation step numbered voices locally.

#### Scenario: Same person in different blocks
- **WHEN** one person speaks in several blocks of a long recording
- **THEN** all their phrases belong to the same participant

#### Scenario: Window construction
- **WHEN** a stretch of single-voice speech is at least 0.8 seconds long
- **THEN** it contributes windows of 2 seconds with a 1 second step (one shorter window if the stretch is up to 2.5 seconds, and an extra end-aligned window for a leftover tail)

#### Scenario: Too little audio
- **WHEN** a window contains less than 0.5 seconds of audio
- **THEN** it gets no fingerprint and does not take part in clustering

#### Scenario: Short gap bridging
- **WHEN** two single-voice stretches of the same local speaker are separated by up to 0.3 seconds
- **THEN** they are treated as one stretch

### Requirement: Exact number of participants
The system SHALL use the number of participants given by the user when one is given, and SHALL put everyone into one participant when the number is 1.

#### Scenario: Number given
- **WHEN** the user specifies N participants (1 to 6 are offered)
- **THEN** the voice fingerprints are clustered into N groups (at most the number of fingerprints)

#### Scenario: One participant
- **WHEN** the number of participants is 1
- **THEN** all speech is attributed to one participant

### Requirement: Automatic number of participants
The system SHALL estimate the number of participants when it is not given, choosing between 1 and 6 by the best clustering quality, and SHALL prefer one participant unless a split is convincing; the user-controlled "voice separation" setting SHALL lower or raise the quality threshold a split must beat.

#### Scenario: Monologue
- **WHEN** no split of the fingerprints beats the quality threshold
- **THEN** the recording has one participant

#### Scenario: Separation setting
- **WHEN** voice separation is 0 (merge similar voices)
- **THEN** a split must reach a silhouette score of 0.25 to be accepted
- **AND WHEN** voice separation is 1 (split voices more)
- **THEN** a split needs only 0.05

#### Scenario: Tiny group
- **WHEN** a candidate split contains a group smaller than 4 % of the fingerprints
- **THEN** that split is ignored, because such groups consist of windows straddling a change of speaker

### Requirement: Clustering scales to long recordings
The system SHALL cluster at most 700 evenly spaced fingerprints and SHALL assign every remaining fingerprint to the nearest participant.

#### Scenario: More than 700 windows
- **WHEN** a recording yields more than 700 fingerprints
- **THEN** participants are determined from a sample and the other windows join the nearest participant by voice similarity

### Requirement: Labels cover all detected speech
The system SHALL label every stretch of detected speech with a participant: stretches covered by fingerprint windows take the window's participant, an isolated window that disagrees with both neighbours in its stretch takes their participant, and speech not covered by windows (short phrases, overlapping voices) takes the participant its local speaker mostly was, or the nearest labelled stretch in time.

#### Scenario: Short interjection
- **WHEN** a short phrase has no fingerprint window
- **THEN** it is attributed to the participant of its local speaker, or to the nearest labelled speech

#### Scenario: Single outlier window
- **WHEN** one window in the middle of a stretch is labelled differently from both neighbours
- **THEN** it takes the neighbours' participant

### Requirement: Participants are numbered by first appearance
The system SHALL number participants 0, 1, 2, … in order of their first appearance.

#### Scenario: Order
- **WHEN** participant B speaks before participant A in the recording
- **THEN** B is participant 0 and A is participant 1

### Requirement: Turns do not overlap
The system SHALL convert speaker segments into non-overlapping turns: where voices overlap the speaker who already holds the floor keeps it; a flicker to another speaker shorter than 0.3 seconds is absorbed by a neighbour (the longer neighbour wins); and a pause of less than 1 second between two stretches of the same speaker is bridged.

#### Scenario: Interruption
- **WHEN** speaker B starts talking while speaker A is still talking
- **THEN** the overlapped time stays with A until A stops

#### Scenario: Flicker
- **WHEN** a segment of another speaker lasts less than 0.3 seconds between two longer segments
- **THEN** it is merged into a neighbouring turn

#### Scenario: Short pause
- **WHEN** the same speaker pauses for less than 1 second
- **THEN** the two stretches become one turn

#### Scenario: Long pause
- **WHEN** the same speaker pauses for at least 1 second
- **THEN** two separate turns remain

### Requirement: Diarization precision is configurable
The system SHALL let the user trade speed for precision of speaker-change detection through the three levels "Быстро", "Баланс" (default) and "Точно", which move the analysis window by 50 %, 25 % and 10 % respectively.

#### Scenario: Fast
- **WHEN** the quality is "Быстро"
- **THEN** the segmentation window shifts by half a window
