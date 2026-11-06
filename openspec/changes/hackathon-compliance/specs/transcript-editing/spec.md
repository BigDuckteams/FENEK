# Spec Delta

## ADDED Requirements

### Requirement: Restore the recognized text of a phrase
The system SHALL let the user see and restore the originally recognized text of a phrase that the LLM corrected, from the phrase editing dialog.

#### Scenario: Show recognized text
- **WHEN** the user opens the dialog of a corrected phrase
- **THEN** the recognized text is shown next to the current text

#### Scenario: Restore
- **WHEN** the user presses "Вернуть распознанный текст" and saves
- **THEN** the phrase text becomes the recognized text and the phrase is no longer marked as corrected

#### Scenario: Uncorrected phrase
- **WHEN** a phrase has no stored recognized text
- **THEN** the restore action is not offered

### Requirement: Manual edits become the text of record
The system SHALL treat text typed by the user as final: it SHALL NOT be corrected by the LLM later, and the recognized text of that phrase SHALL stay available for restoring.

#### Scenario: Manual edit of a corrected phrase
- **WHEN** the user edits a corrected phrase by hand
- **THEN** the typed text is kept and the original recognized text is still restorable
