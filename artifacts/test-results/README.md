# Артефакты тестирования

Здесь лежат результаты запусков в таблице [`results.csv`](results.csv) и описание того, как они
получены. Методика и разбор — в [`docs/TEST_RESULTS.md`](../../docs/TEST_RESULTS.md).

**Все строки сейчас получены в эмуляторе, не на телефоне.** Скорость в эмуляторе не отражает
скорость телефона. Запись одна: синтезированный диалог двух голосов на русском, 62,9 с.

## Колонки `results.csv`

| Колонка | Значение |
|---|---|
| `test_id` | Номер запуска |
| `timestamp_utc` | Когда запущено (часы устройства в UTC) |
| `commit` | Коммит, на котором собран APK; `-dirty` значит, что в рабочем дереве были неотправленные изменения |
| `file_name`, `duration_seconds` | Запись и её длительность |
| `processing_seconds`, `rtf` | Время обработки целиком (по заголовку расшифровки) и RTF = время / длительность |
| `wer_percent` | WER экспорта «JSON расшифровки» относительно эталона, считает `tools/eval/wer.py` |
| `speakers_expected`, `speakers_detected` | Число говорящих в эталоне и найденных приложением |
| `device`, `build` | Устройство и тип сборки (`debug` / `release` с R8) |
| `asr_model`, `llm_model`, `llm_correct_text`, `llm_highlights` | Модели и переключатели языковой модели |
| `notes` | Что важно знать о запуске |

## Как воспроизвести

1. Эталон: `tools/eval/sample/reference.json` показывает формат (для записи с полными временами
   можно взять `reference.json`, созданный вместе с `dialogue.wav`).
2. Экспорт из приложения: кнопка «Сохранить JSON расшифровки» в карточке результата.
3. Оценка:

```bash
python tools/eval/wer.py --reference reference.json --hypothesis dialogue.json \
    --processing-seconds 54 --duration-seconds 62.9
```

## Данные

Тестовые записи и эталоны лежат в [`data/`](data) (синтезированная русская речь Windows SAPI,
голос Irina; второй участник получен понижением тона того же голоса). Результат новой release-проверки
2026-11-07 в авиарежиме сохранён в [`dialogue_03_phone_call.offline-release.json`](dialogue_03_phone_call.offline-release.json),
результат чистой установки fat APK — в [`dialogue_03_phone_call.fat-first-launch.json`](dialogue_03_phone_call.fat-first-launch.json).

## Что здесь не измерено

- Реальный телефон и реальные записи.
- Сравнение «правка текста включена / выключена» на данных, где распознавание не ставит
  пунктуацию (Zipformer, Whisper Base): там выигрыш от правки возможен, на GigaAM его нет.
