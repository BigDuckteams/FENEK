package app.fenek.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import app.fenek.MainActivity
import app.fenek.appContainer
import app.fenek.record.RecorderState

/**
 * "Запись FENEK" tile in Quick Settings: one tap opens the recorder and starts recording.
 * The microphone is switched on by the visible activity, which every Android version allows.
 */
class RecordTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val recording = appContainer.recorder.state.value is RecorderState.Active
        tile.state = if (recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (recording) "Идёт запись" else null
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_RECORD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val REQUEST_CODE = 20

        /** Asks the system to redraw the tile, e.g. when recording starts or stops. */
        fun refresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, RecordTileService::class.java))
            } catch (e: Exception) {
                Log.w("RecordTileService", "Cannot refresh the tile", e)
            }
        }
    }
}
