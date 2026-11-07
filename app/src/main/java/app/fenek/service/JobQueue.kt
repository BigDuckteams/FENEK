package app.fenek.service

/**
 * The order of transcription jobs. At most one runs; the others wait for their turn or are paused.
 * Plain bookkeeping with no threads and no Android types, so that the rules can be tested on the JVM;
 * [JobController] runs the jobs and keeps this in step with them. It is not thread-safe by itself.
 */
class JobQueue<T : Any>(private val idOf: (T) -> String, private val sourceOf: (T) -> String) {

    enum class Status { RUNNING, WAITING, PAUSED }

    data class Entry<T>(val item: T, val status: Status, val progress: Float = 0f)

    // In order of adding: waiting jobs run in this order, a resumed one is moved to the front of them.
    private val list = ArrayList<Entry<T>>()

    /** For display: the running job, then the waiting ones in turn, then the paused ones. */
    val entries: List<Entry<T>>
        get() = list.filter { it.status == Status.RUNNING } +
            list.filter { it.status == Status.WAITING } +
            list.filter { it.status == Status.PAUSED }

    fun running(): Entry<T>? = list.firstOrNull { it.status == Status.RUNNING }

    /** The job to run now, or null while one is running or none is waiting. */
    fun next(): Entry<T>? = if (running() != null) null else list.firstOrNull { it.status == Status.WAITING }

    /**
     * Puts a job at the end of the line. Returns how many running or waiting jobs are ahead of it, or
     * null when the same job (the same id or the same source) is already here, in any state.
     */
    fun add(item: T): Int? {
        if (list.any { idOf(it.item) == idOf(item) || sourceOf(it.item) == sourceOf(item) }) return null
        val ahead = list.count { it.status != Status.PAUSED }
        list += Entry(item, Status.WAITING)
        return ahead
    }

    fun start(id: String) = update(id) { it.copy(status = Status.RUNNING) }

    /** The running job stopped at a checkpoint: it stays here, paused, until it is resumed. */
    fun park(id: String, progress: Float) = update(id) { it.copy(status = Status.PAUSED, progress = progress) }

    /** Puts a paused job at the front of the waiting ones. Returns false if the job is not paused. */
    fun resume(id: String): Boolean {
        val entry = find(id) ?: return false
        if (entry.status != Status.PAUSED) return false
        moveToFrontOfWaiting(entry.copy(status = Status.WAITING))
        return true
    }

    /**
     * Makes a waiting or paused job the next to run. The caller pauses the running job, if any, so
     * that this one can start. Returns false if there is no such job or it is the running one.
     */
    fun runNow(id: String): Boolean {
        val entry = find(id) ?: return false
        if (entry.status == Status.RUNNING) return false
        moveToFrontOfWaiting(entry.copy(status = Status.WAITING))
        return true
    }

    fun remove(id: String): Boolean = list.removeAll { idOf(it.item) == id }

    private fun find(id: String): Entry<T>? = list.firstOrNull { idOf(it.item) == id }

    private fun update(id: String, transform: (Entry<T>) -> Entry<T>) {
        val index = list.indexOfFirst { idOf(it.item) == id }
        if (index >= 0) list[index] = transform(list[index])
    }

    private fun moveToFrontOfWaiting(entry: Entry<T>) {
        val id = idOf(entry.item)
        list.removeAll { idOf(it.item) == id }
        val index = list.indexOfFirst { it.status == Status.WAITING }.let { if (it < 0) list.size else it }
        list.add(index, entry)
    }
}
