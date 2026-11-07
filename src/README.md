# Исходный код

Платформа: **Android** (Kotlin, Jetpack Compose, minSdk 26).

По шаблону организаторов исходники лежат в `src/<платформа>/`. Проект Gradle здесь остался в
корне репозитория (переносить его значило бы менять все пути сборки, скрипты и описание), поэтому
эта папка только указывает, где что находится:

| Что | Где |
|---|---|
| Приложение (Kotlin, ресурсы) | [`app/`](../app) |
| Нативная часть: мост JNI и сборка llama.cpp | [`app/src/main/cpp/`](../app/src/main/cpp) |
| Юнит-тесты приложения | [`app/src/test/`](../app/src/test) |
| Инструмент оценки WER и RTF | [`tools/eval/`](../tools/eval) |
| Спецификации поведения | [`openspec/specs/`](../openspec/specs) |

## Инструкция по запуску и сборке

Требования и команды сборки описаны в разделе «Сборка» файла [`README.md`](../README.md).
Коротко:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew releaseApks
```

`releaseApks` собирает две универсальные подписанные версии в `dist/`: `FENEK-online-universal.apk`
(модели скачиваются при первом запуске) и `FENEK-fat-universal.apk` (модели внутри APK).

Конфигурационных файлов окружения у приложения нет, поэтому `config/example.env` из шаблона
не используется.
