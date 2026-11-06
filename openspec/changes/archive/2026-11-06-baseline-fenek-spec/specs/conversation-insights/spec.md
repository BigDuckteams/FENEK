# Spec Delta

## Purpose

Extracts what matters from a transcript without a neural network: who talked how much, what the conversation was about, a short extractive summary, tasks and agreements with deadlines, and questions. The rules are written for Russian, with basic English support.

## ADDED Requirements

### Requirement: Per-participant talk statistics
The system SHALL compute for every participant who has phrases: talk time, share of total talk time, number of turns, word count, speaking rate in words per minute and longest turn, ordered by talk time with the most talkative first.

#### Scenario: Shares
- **WHEN** participant A talks 60 s and B talks 40 s
- **THEN** A has a share of 0.6 and B 0.4

#### Scenario: Turns
- **WHEN** a participant has several consecutive phrases
- **THEN** they count as one turn

#### Scenario: Speaking rate
- **WHEN** a participant said 150 words in 60 s of talk
- **THEN** the rate is 150 words per minute

### Requirement: Conversation facts
The system SHALL name the participant who talks the most only when there is more than one participant, SHALL name the fastest speaker only among at least two participants who said 15 or more words, and SHALL name the longest monologue when it lasts at least 20 seconds.

#### Scenario: Monologue
- **WHEN** there is only one participant
- **THEN** no "most talkative" fact is shown

#### Scenario: Short utterances
- **WHEN** fewer than two participants said at least 15 words
- **THEN** no "fastest" fact is shown

#### Scenario: Totals
- **WHEN** statistics are shown
- **THEN** the totals line reads "Всего: N слов, M реплик" with correct Russian plural forms

### Requirement: Topics
The system SHALL list up to eight topics: meaningful words of four or more letters that are not stop words or numbers, grouped by their first six letters so that Russian word forms count together, shown by the most frequent form, that occur at least twice, ordered by frequency and then by length.

#### Scenario: Inflected forms
- **WHEN** "продажи" occurs twice and "продажам" once
- **THEN** they form one topic represented by the most frequent form

#### Scenario: Rare words
- **WHEN** a word occurs once
- **THEN** it is not a topic

#### Scenario: Stop words
- **WHEN** a frequent word is a filler, pronoun, weekday or day word
- **THEN** it is not a topic

### Requirement: Sentences are located in time
The system SHALL split each phrase into sentences after ".", "!", "?" or "…" and SHALL place every sentence at a time proportional to its position inside the phrase.

#### Scenario: Second sentence
- **WHEN** a phrase from 10 s to 20 s has two sentences of equal length
- **THEN** the second sentence is placed at about 15 s

### Requirement: Questions
The system SHALL list as questions the sentences that end with "?" and contain at least three words, at most 50 items, in conversation order.

#### Scenario: Short question
- **WHEN** a sentence is "Да?"
- **THEN** it is not listed

#### Scenario: Question
- **WHEN** a sentence is "Когда будет готов отчёт?"
- **THEN** it is listed with its speaker and time

### Requirement: Tasks and agreements
The system SHALL list as tasks the sentences of at least three words that contain an obligation, a first-person promise, an imperative request, a marker phrase ("не забудь", "i will", "let's", "action item"…) or a joint plan after "давайте" (a first-person plural verb that is not an idle plan like "посмотрим"), at most 50 items; an obligation word preceded by "не" SHALL NOT count.

#### Scenario: Promise
- **WHEN** a sentence is "Я подготовлю отчёт к пятнице."
- **THEN** it is a task

#### Scenario: Request
- **WHEN** a sentence is "Пришлите, пожалуйста, договор."
- **THEN** it is a task

#### Scenario: Negation
- **WHEN** a sentence is "Это не нужно делать."
- **THEN** it is not treated as a task because of "нужно"

#### Scenario: Idle plan
- **WHEN** a sentence is "Давайте посмотрим."
- **THEN** it is not a task

#### Scenario: Request phrased as a question
- **WHEN** a question contains a request word and a task verb ("Сможешь прислать отчёт?")
- **THEN** it is a task, while other questions are not

### Requirement: Acceptance turns a proposal into a task
The system SHALL treat a sentence of at least three words as a task when the next sentence is by a different participant and is a short (up to three words) acceptance such as "Договорились".

#### Scenario: Agreement
- **WHEN** A says "Отчёт нужен к понедельнику." and B replies "Договорились."
- **THEN** A's sentence is listed as a task

#### Scenario: Same speaker
- **WHEN** the acceptance is said by the same participant
- **THEN** it does not create a task

### Requirement: Extractive summary
The system SHALL build a short summary ("Коротко") of 2 to 5 sentences, growing with the square root of the sentence count, only for conversations of at least six sentences, choosing questions-free sentences of 6 to 45 words that are dense in words recurring across the conversation, favouring sentences with decision words ("договорились", "решили", "итог"), skipping near-duplicates, and presenting them in conversation order.

#### Scenario: Short conversation
- **WHEN** a conversation has fewer than six sentences
- **THEN** the summary is empty

#### Scenario: Duplicates
- **WHEN** two candidate sentences share more than half of their meaningful words
- **THEN** only the higher-ranked one is included

#### Scenario: Order
- **WHEN** the summary is shown
- **THEN** its sentences follow the order of the conversation, not the ranking

### Requirement: Deadlines are resolved from the conversation date
The system SHALL find a deadline in a task sentence and resolve relative expressions from the day of the conversation (the recording start for app recordings, otherwise the transcript creation day), keeping the longest of overlapping matches.

#### Scenario: Day words
- **WHEN** the task says "завтра" or "послезавтра" or "сегодня"
- **THEN** the deadline is one day, two days or zero days after the conversation date

#### Scenario: Weekday
- **WHEN** the task says "к пятнице" on a Wednesday
- **THEN** the date is the coming Friday
- **AND WHEN** it says "в следующую среду"
- **THEN** the date is the Wednesday of the following calendar week

#### Scenario: Relative offset
- **WHEN** the task says "через неделю" or "через 3 дня"
- **THEN** the date is 7 or 3 days after the conversation date
- **AND WHEN** it says "через несколько дней"
- **THEN** the phrase is kept but no date is resolved

#### Scenario: Period end
- **WHEN** the task says "до конца месяца"
- **THEN** the date is the last day of that month
- **AND WHEN** it says "до конца недели" on a working day
- **THEN** the date is that week's Friday (on a weekend, the Sunday)

#### Scenario: Calendar date
- **WHEN** the task says "к 5 ноября" and 5 November has already passed this year
- **THEN** the date is 5 November of the next year

#### Scenario: Day of month
- **WHEN** the task says "до 15 числа" and the 15th has already passed this month
- **THEN** the date is the 15th of next month

#### Scenario: Clock time
- **WHEN** the task says "в 15:30" or "в 3 часа дня"
- **THEN** the time is 15:30 or 15:00, and without another date it falls on the conversation day

#### Scenario: English
- **WHEN** the task says "by Friday", "tomorrow" or "next week"
- **THEN** it is resolved like its Russian counterpart

### Requirement: Deadline display label
The system SHALL display a resolved deadline as an abbreviated Russian weekday and date, with the time when known, and SHALL display the spoken phrase when no date could be resolved.

#### Scenario: Date with time
- **WHEN** the deadline is Friday, 30 October at 15:30
- **THEN** the label is "пт, 30 окт., 15:30"

#### Scenario: Phrase only
- **WHEN** no date was resolved
- **THEN** the label is the spoken phrase

### Requirement: Nothing is invented
The system SHALL show only items found by its rules and SHALL show no highlights section when none are found and no marks exist.

#### Scenario: No findings
- **WHEN** no summary, tasks or questions are found and there are no marks
- **THEN** the "Главное" section is not shown
