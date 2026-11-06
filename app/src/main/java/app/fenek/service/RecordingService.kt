package app.fenek.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.fenek.appContainer
import app.fenek.record.RecorderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service of type "microphone": without it Android cuts the app off the
 * microphone as soon as the screen turns off or the user switches apps. The recording itself
 * lives in [app.fenek.record.Recorder]; the notification offers pause and stop.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recorder = appContainer.recorder
        when (intent?.action) {
            ACTION_PAUSE -> recorder.pause()
            ACTION_RESUME -> recorder.resume()
            ACTION_MARK -> recorder.mark()
            ACTION_STOP -> recorder.stop()?.let { Notifications.showRecordingSaved(this, it) }
        }
        val state = recorder.state.value
        if (state !is RecorderState.Active) {
            stopRecordingService()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_RECORDING,
                Notifications.recording(this, state),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (e: Exception) {
            // Recording still works while the app stays on screen.
            Log.w(TAG, "Cannot enter the foreground", e)
        }
        if (observer == null) {
            observer = scope.launch {
                recorder.state
                    .map { state -> (state as? RecorderState.Active)?.let { it.paused to it.marks.size } }
                    .distinctUntilChanged()
                    .collect { shown ->
                        val current = recorder.state.value
                        if (shown == null || current !is RecorderState.Active) {
                            stopRecordingService()
                        } else {
                            Notifications.notify(
                                this@RecordingService,
                                Notifications.ID_RECORDING,
                                Notifications.recording(this@RecordingService, current),
                            )
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun stopRecordingService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_PAUSE = "app.fenek.action.RECORDING_PAUSE"
        const val ACTION_RESUME = "app.fenek.action.RECORDING_RESUME"
        const val ACTION_STOP = "app.fenek.action.RECORDING_STOP"
        const val ACTION_MARK = "app.fenek.action.RECORDING_MARK"
        private const val TAG = "RecordingService"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Cannot start the recording service", e)
            }
        }
    }
}
