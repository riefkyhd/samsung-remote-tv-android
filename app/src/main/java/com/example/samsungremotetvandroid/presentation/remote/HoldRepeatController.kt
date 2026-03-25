package com.example.samsungremotetvandroid.presentation.remote

import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class HoldRepeatController(
    private val scope: CoroutineScope,
    private val initialDelayMs: Long,
    private val repeatIntervalMs: Long,
    private val onSend: suspend (RemoteKey) -> Unit,
    private val shouldContinue: (ConnectionState) -> Boolean
) {
    private val lock = Any()
    private val activeJobs = mutableMapOf<RemoteKey, Job>()

    fun start(key: RemoteKey, connectionState: StateFlow<ConnectionState>) {
        synchronized(lock) {
            if (activeJobs[key]?.isActive == true) {
                return
            }
        }

        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            onSend(key)
            delay(initialDelayMs)
            while (isActive) {
                if (!shouldContinue(connectionState.value)) {
                    break
                }
                onSend(key)
                delay(repeatIntervalMs)
            }
        }

        synchronized(lock) {
            activeJobs[key] = job
        }
        job.invokeOnCompletion {
            synchronized(lock) {
                activeJobs.remove(key)
            }
        }
    }

    fun stop(key: RemoteKey) {
        val job = synchronized(lock) {
            activeJobs.remove(key)
        }
        job?.cancel()
    }

    fun stopAll() {
        val jobsToCancel = synchronized(lock) {
            activeJobs.values.toList().also {
                activeJobs.clear()
            }
        }
        jobsToCancel.forEach { job ->
            job.cancel()
        }
    }
}
