package app.fenek.model

import android.content.res.AssetManager
import java.io.InputStream

/**
 * The models the "fat" build carries inside its APK, as `assets/models/<id>/<file>`. They are copied
 * into the app's model folder instead of being downloaded, so the app works with no network from the
 * very start. The build without them has no such assets and downloads the models as before.
 */
class BundledModels(private val assets: AssetManager) {

    /** The APK has this file of [model]. */
    fun has(model: ModelInfo, file: RemoteFile): Boolean =
        assets.list("$ROOT/${model.id}")?.contains(file.name) == true

    /** The APK has every file of [model]. */
    fun has(model: ModelInfo): Boolean {
        val names = assets.list("$ROOT/${model.id}")?.toSet() ?: return false
        return model.files.all { it.name in names }
    }

    fun open(model: ModelInfo, file: RemoteFile): InputStream =
        assets.open("$ROOT/${model.id}/${file.name}", AssetManager.ACCESS_STREAMING)

    private companion object {
        const val ROOT = "models"
    }
}
