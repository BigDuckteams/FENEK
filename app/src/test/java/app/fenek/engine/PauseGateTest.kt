package app.fenek.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class PauseGateTest {

    @Test
    fun aCheckpointPassesAtOnceUntilAPauseIsAskedFor() = runBlocking {
        val parked = AtomicInteger()
        val gate = PauseGate(onParked = { parked.incrementAndGet() })

        withTimeout(2_000) { gate.checkpoint() }

        assertEquals(0, parked.get())
    }

    @Test
    fun aPausedJobWaitsAtTheCheckpointAndGoesOnWhenResumed() = runBlocking {
        val parked = AtomicInteger()
        val gate = PauseGate(onParked = { parked.incrementAndGet() })
        val steps = CopyOnWriteArrayList<String>()
        gate.pause()

        val job = launch(Dispatchers.Default) {
            steps += "before"
            gate.checkpoint()
            steps += "after"
        }
        withTimeout(2_000) { while (parked.get() == 0) delay(5) }
        assertEquals(listOf("before"), steps.toList())

        gate.resume()
        withTimeout(2_000) { job.join() }
        assertEquals(listOf("before", "after"), steps.toList())
    }

    @Test
    fun aJobCancelledWhileItWaitsStopsAtTheCheckpoint() = runBlocking {
        val parked = AtomicInteger()
        val gate = PauseGate(onParked = { parked.incrementAndGet() })
        gate.pause()

        val job = launch(Dispatchers.Default) { gate.checkpoint() }
        withTimeout(2_000) { while (parked.get() == 0) delay(5) }
        withTimeout(2_000) { job.cancelAndJoin() }

        assertTrue(job.isCancelled)
    }
}
