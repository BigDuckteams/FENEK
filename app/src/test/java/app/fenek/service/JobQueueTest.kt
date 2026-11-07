package app.fenek.service

import app.fenek.service.JobQueue.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobQueueTest {

    private data class Job(val id: String, val source: String = "file-$id")

    private fun queue() = JobQueue<Job>(idOf = { it.id }, sourceOf = { it.source })

    private fun JobQueue<Job>.ids(status: Status) = entries.filter { it.status == status }.map { it.item.id }

    @Test
    fun jobsRunOneByOneInTheOrderTheyWereAdded() {
        val q = queue()
        assertEquals(0, q.add(Job("a")))
        q.start(q.next()!!.item.id)
        assertEquals(1, q.add(Job("b")))
        assertEquals(2, q.add(Job("c")))
        assertNull("nothing starts while one is running", q.next())

        q.remove("a")
        assertEquals("b", q.next()!!.item.id)
        q.start("b")
        q.remove("b")
        assertEquals("c", q.next()!!.item.id)
    }

    @Test
    fun theSameJobIsNotAddedTwiceInAnyState() {
        val q = queue()
        q.add(Job("a"))
        assertNull(q.add(Job("a", source = "other")))
        assertNull("same source, new id", q.add(Job("b", source = "file-a")))
        q.start("a")
        q.park("a", 0.4f)
        assertNull(q.add(Job("a")))
        assertEquals("a paused job does not delay the new one", 0, q.add(Job("c")))
    }

    @Test
    fun aPausedJobStaysPausedAndTheNextOneStarts() {
        val q = queue()
        q.add(Job("a")); q.add(Job("b"))
        q.start("a")
        q.park("a", 0.4f)

        assertEquals("b", q.next()!!.item.id)
        q.start("b")
        q.remove("b")
        assertNull("a paused job is never started by itself", q.next())
        assertEquals(0.4f, q.entries.single().progress, 0f)
    }

    @Test
    fun resumingPutsThePausedJobAheadOfTheWaitingOnes() {
        val q = queue()
        q.add(Job("a")); q.add(Job("b")); q.add(Job("c"))
        q.start("a")
        q.park("a", 0.5f)
        q.start("b")

        assertTrue(q.resume("a"))
        assertFalse("only a paused job can be resumed", q.resume("b"))
        assertEquals(listOf("a", "c"), q.ids(Status.WAITING))

        q.remove("b")
        assertEquals("a", q.next()!!.item.id)
    }

    @Test
    fun startNowMovesAJobToTheFrontAndLeavesThePausingToTheCaller() {
        val q = queue()
        q.add(Job("a")); q.add(Job("b")); q.add(Job("c"))
        q.start("a")

        assertTrue(q.runNow("c"))
        assertFalse("the running job cannot be moved", q.runNow("a"))
        assertFalse(q.runNow("missing"))
        assertEquals("a", q.running()!!.item.id)
        assertEquals(listOf("c", "b"), q.ids(Status.WAITING))

        q.park("a", 0.2f)
        assertEquals("c", q.next()!!.item.id)
    }

    @Test
    fun entriesAreShownRunningThenWaitingThenPaused() {
        val q = queue()
        q.add(Job("a")); q.add(Job("b")); q.add(Job("c"))
        q.start("a"); q.park("a", 0.3f)
        q.start("b")

        assertEquals(listOf("b", "c", "a"), q.entries.map { it.item.id })
        assertTrue(q.remove("a"))
        assertFalse(q.remove("a"))
    }
}
