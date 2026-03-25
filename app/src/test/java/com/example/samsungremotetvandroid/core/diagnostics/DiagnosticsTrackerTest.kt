package com.example.samsungremotetvandroid.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTrackerTest {
    @Test
    fun log_redactsSensitiveMetadataValues() {
        val tracker = InMemoryDiagnosticsTracker()

        tracker.log(
            category = DiagnosticsCategory.PAIRING,
            message = "pairing state updated",
            metadata = mapOf(
                "pin" to "1234",
                "token" to "abcd-token",
                "tvId" to "living-room-tv",
                "attempt" to "1"
            )
        )

        val event = tracker.recentEvents.value.last()
        assertTrue(event.contains("pin=[REDACTED]"))
        assertTrue(event.contains("token=[REDACTED]"))
        assertFalse(event.contains("1234"))
        assertFalse(event.contains("abcd-token"))
        assertFalse(event.contains("living-room-tv"))
    }

    @Test
    fun recordError_updatesLastErrorSummaryAndAddsErrorEvent() {
        val tracker = InMemoryDiagnosticsTracker()

        tracker.recordError(
            context = "submit_pin",
            errorMessage = "pin timeout while waiting for tv",
            metadata = mapOf("tvId" to "office-tv")
        )

        val summary = tracker.lastErrorSummary.value
        assertNotNull(summary)
        assertTrue(summary!!.contains("submit_pin"))

        val event = tracker.recentEvents.value.last()
        assertTrue(event.contains("[error]"))
        assertTrue(event.contains("context=submit_pin"))
        assertFalse(event.contains("office-tv"))
    }

    @Test
    fun log_keepsBoundedRecentEventBuffer() {
        val tracker = InMemoryDiagnosticsTracker()

        repeat(25) { index ->
            tracker.log(
                category = DiagnosticsCategory.LIFECYCLE,
                message = "event-$index"
            )
        }

        val events = tracker.recentEvents.value
        assertEquals(20, events.size)
        assertTrue(events.first().contains("event-5"))
        assertTrue(events.last().contains("event-24"))
    }
}
