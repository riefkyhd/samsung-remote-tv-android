package com.example.samsungremotetvandroid.presentation.remote

import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldRepeatControllerTest {
    @Test
    fun stopCancelsRepeatingImmediately_afterRelease() = runBlocking {
        val sendCount = AtomicInteger(0)
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Ready("tv-1"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = createController(scope, sendCount)

        controller.start(RemoteKey.D_PAD_UP, connectionState)
        delay(230)
        controller.stop(RemoteKey.D_PAD_UP)

        val countAtRelease = sendCount.get()
        delay(120)

        assertTrue(countAtRelease >= 3)
        assertEquals(countAtRelease, sendCount.get())
        scope.cancel()
    }

    @Test
    fun nonReadyStateStopsFurtherRepeats_afterInitialSend() = runBlocking {
        val sendCount = AtomicInteger(0)
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Error("not ready"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = createController(scope, sendCount)

        controller.start(RemoteKey.VOLUME_UP, connectionState)
        delay(200)

        assertEquals(1, sendCount.get())
        scope.cancel()
    }

    private fun createController(
        scope: CoroutineScope,
        sendCount: AtomicInteger
    ): HoldRepeatController {
        return HoldRepeatController(
            scope = scope,
            initialDelayMs = 80,
            repeatIntervalMs = 40,
            onSend = { _ -> sendCount.incrementAndGet() },
            shouldContinue = { state -> state is ConnectionState.Ready }
        )
    }
}
