package app.fenek.data

/** Settings storage for tests: a map instead of shared preferences. */
class InMemorySettingsStorage(val values: MutableMap<String, Any> = HashMap()) : SettingsStorage {
    override fun getString(key: String) = values[key] as? String
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun getFloat(key: String, default: Float) = values[key] as? Float ?: default
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    override fun write(values: Map<String, Any>) {
        this.values.putAll(values)
    }
}
