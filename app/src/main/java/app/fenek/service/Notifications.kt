package app.fenek.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fenek.MainActivity
import app.fenek.R
import app.fenek.data.Transcript
import app.fenek.engine.JobProgress
import app.fenek.engine.Stage
import app.fenek.model.ModelInfo
import app.fenek.model.ModelState
import app.fenek.record.RecorderState
import app.fenek.record.Recording
import app.fenek.util.TimeFormat

object Notifications {

    private const val CHANNEL_WORK = "work"
    private const val CHANNEL_RESULTS = "results"
    private const val CHANNEL_RECORDING = "recording"
    const val ID_WORK = 1
    private const val ID_RESULT = 2
    const val ID_RECORDING = 3
    private const val ID_RECORDING_SAVED = 4

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_WORK, "Обработка в фоне", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Прогресс расшифровки и загрузки моделей"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_RESULTS, "Готовые расшифровки", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Сообщение о завершении расшифровки"
                },
                NotificationChannel(CHANNEL_RECORDING, "Запись разговора", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Идущая запись с кнопками паузы и остановки"
                    setShowBadge(false)
                },
            ),
        )
    }

    fun recording(context: Context, state: RecorderState.Active): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification_mic)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context, MainActivity.EXTRA_OPEN_RECORDER, "1", requestCode = 10))
        if (state.paused) {
            builder
                .setContentTitle("Запись на паузе")
                .setContentText(
                    "Записано ${TimeFormat.clock(state.elapsedMs)}" +
                        if (state.marks.isEmpty()) "" else " · отмечено моментов: ${state.marks.size}",
                )
                .setShowWhen(false)
                .addAction(0, "Продолжить", recorderAction(context, RecordingService.ACTION_RESUME, 11))
        } else {
            builder
                .setContentTitle("Идёт запись разговора")
                .setContentText(
                    if (state.marks.isEmpty()) "Запись продолжается и при выключенном экране" else "Отмечено моментов: ${state.marks.size}",
                )
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() - state.elapsedMs)
                .addAction(0, "Пауза", recorderAction(context, RecordingService.ACTION_PAUSE, 12))
        }
        builder.addAction(0, "Отметить", recorderAction(context, RecordingService.ACTION_MARK, 15))
        builder.addAction(0, "Остановить", recorderAction(context, RecordingService.ACTION_STOP, 13))
        return builder.build()
    }

    fun showRecordingSaved(context: Context, recording: Recording) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification_mic)
            .setContentTitle("Запись сохранена · ${TimeFormat.clock(recording.durationMs)}")
            .setContentText("Нажмите, чтобы расшифровать")
            .setAutoCancel(true)
            .setContentIntent(
                openApp(context, MainActivity.EXTRA_RECORDING_PATH, recording.file.absolutePath, requestCode = 14),
            )
            .build()
        notify(context, ID_RECORDING_SAVED, notification)
    }

    private fun recorderAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun work(
        context: Context,
        job: JobState,
        downloads: Map<ModelInfo, ModelState.Downloading>,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context, null))
        when {
            job is JobState.Running -> builder
                .setContentTitle(job.request.displayName)
                .setContentText(job.progress.statusText())
                .setProgress(1000, (job.progress.overall * 1000).toInt(), false)
                .addAction(0, "Отменить", cancelIntent(context))
            downloads.isNotEmpty() -> {
                val bytes = downloads.values.sumOf { it.bytes }
                val total = downloads.values.sumOf { it.total }.coerceAtLeast(1)
                builder
                    .setContentTitle("Загрузка моделей")
                    .setContentText(downloads.keys.joinToString { it.title })
                    .setProgress(1000, (bytes * 1000 / total).toInt(), false)
            }
            else -> builder.setContentTitle(context.getString(R.string.app_name))
        }
        return builder.build()
    }

    fun showFinished(context: Context, transcript: Transcript) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Расшифровка готова")
            .setContentText(transcript.title)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, transcript.id))
            .build()
        notify(context, ID_RESULT, notification)
    }

    fun showFailed(context: Context, fileName: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Не удалось расшифровать «$fileName»")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, null))
            .build()
        notify(context, ID_RESULT, notification)
    }

    @SuppressLint("MissingPermission")
    fun notify(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked: the result is still visible in the app.
        }
    }

    private fun openApp(context: Context, transcriptId: String?): PendingIntent =
        openApp(context, MainActivity.EXTRA_TRANSCRIPT_ID, transcriptId, transcriptId?.hashCode() ?: 0)

    private fun openApp(context: Context, extra: String, value: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (value != null) intent.putExtra(extra, value)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun cancelIntent(context: Context): PendingIntent = PendingIntent.getService(
        context,
        1,
        Intent(context, WorkService::class.java).setAction(WorkService.ACTION_CANCEL),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

fun JobProgress.statusText(): String = when (stage) {
    Stage.PREPARING -> "Подготовка файла…"
    Stage.DECODING -> "Чтение аудио…"
    Stage.DIARIZING -> "Определение говорящих…"
    Stage.TRANSCRIBING -> if (chunksTotal > 0) "Распознавание речи: $chunksDone из $chunksTotal фраз" else "Распознавание речи…"
    Stage.CORRECTING -> if (chunksTotal > 0) "Правка текста: $chunksDone из $chunksTotal фраз" else "Правка текста…"
    Stage.SUMMARIZING -> "Выжимка и задачи…"
    Stage.SAVING -> "Сохранение…"
}
