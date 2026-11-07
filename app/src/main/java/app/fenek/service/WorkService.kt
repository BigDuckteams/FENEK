package app.fenek.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.fenek.appContainer
import app.fenek.model.ModelCatalog
import app.fenek.model.ModelInfo
import app.fenek.model.ModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process (and the CPU) alive while a transcription or a
 * model download is running, and shows its progress. The work itself runs in the
 * application scope; the service stops itself as soon as nothing is left to do.
 */
class WorkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = appContainer
        when (intent?.action) {
            ACTION_CANCEL -> container.jobs.cancel()
            ACTION_PAUSE -> container.jobs.pause()
            ACTION_RESUME -> container.jobs.resumeFirstPaused()
            ACTION_CANCEL_ALL -> container.jobs.cancelAll()
        }
        val notification = Notifications.work(
            this,
            container.jobs.state.value,
            container.jobs.queue.value,
            downloads(container.models.states.value),
        )
        try {
            ServiceCompat.startForeground(this, Notifications.ID_WORK, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot enter the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        if (observer == null) {
            observer = scope.launch {
                combine(container.jobs.state, container.jobs.queue, container.models.states) { job, queue, states ->
                    Triple(job, queue, downloads(states))
                }
                    .conflate()
                    .collect { (job, queue, downloads) ->
                        if (job !is JobState.Running && queue.isEmpty() && downloads.isEmpty()) {
                            stopWork()
                        } else {
                            // Paused jobs only keep the process alive: nothing is computed, so no wake lock.
                            if (job is JobState.Running || downloads.isNotEmpty()) acquireWakeLock() else releaseWakeLock()
                            Notifications.notify(
                                this@WorkService,
                                Notifications.ID_WORK,
                                Notifications.work(this@WorkService, job, queue, downloads),
                            )
                            // The system drops notifications posted too often.
                            delay(700)
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ limits dataSync services to 6 hours a day.
        appContainer.jobs.cancelAll()
        stopWork()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun stopWork() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Fenek:work").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun downloads(states: Map<String, ModelState>): Map<ModelInfo, ModelState.Downloading> =
        ModelCatalog.all.mapNotNull { model ->
            (states[model.id] as? ModelState.Downloading)?.let { model to it }
        }.toMap()

    companion object {
        const val ACTION_CANCEL = "app.fenek.action.CANCEL"
        const val ACTION_PAUSE = "app.fenek.action.PAUSE"
        const val ACTION_RESUME = "app.fenek.action.RESUME"
        const val ACTION_CANCEL_ALL = "app.fenek.action.CANCEL_ALL"
        private const val TAG = "WorkService"
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkService::class.java))
            } catch (e: Exception) {
                // Not allowed from the background; the work still runs while the app is open.
                Log.w(TAG, "Cannot start the foreground service", e)
            }
        }
    }
}
