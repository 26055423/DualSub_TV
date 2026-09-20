package com.dualsub.tv.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Process-owned cleanup; the caller's playback scope may already be cancelled. */
internal class ResourceCleanup(
    private val scope: CoroutineScope,
    private val closeTimeoutMs: Long = 5_000,
    private val report: (String, Throwable) -> Unit
) {
    fun closeAfterReads(name: String, io: Mutex, close: () -> Unit): Job = scope.launch {
        // Waiting for an in-flight reader suspends without occupying a thread. Do not time
        // out this wait and abandon its handle, or close the handle while it is still read.
        io.withLock {
            try {
                withTimeout(closeTimeoutMs) {
                    try {
                        // SMBJ's response Future supports interruption; withTimeout alone
                        // cannot stop its synchronous close. Other sources must also cooperate.
                        runInterruptible { close() }
                    } catch (error: Exception) {
                        // SMBJ wraps InterruptedException. Preserve timeout/cancellation even
                        // when the library reports it as a transport/runtime exception.
                        currentCoroutineContext().ensureActive()
                        throw error
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                report("关闭$name 超时（${closeTimeoutMs}ms）", timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                report("关闭$name 失败", error)
            }
        }
    }
}
