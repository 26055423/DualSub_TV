package com.dualsub.tv.core

import com.hierynomus.protocol.commons.concurrent.Futures
import com.hierynomus.protocol.commons.concurrent.Promise
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.common.SMBRuntimeException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceCleanupTest {
    @Test fun `close waits for the active reader without abandoning its handle`() = runTest {
        val io = Mutex(locked = true)
        var closed = false
        val failures = mutableListOf<Throwable>()
        val cleanup = ResourceCleanup(backgroundScope, closeTimeoutMs = 100) { _, error -> failures += error }
        val job = cleanup.closeAfterReads("test", io) { closed = true }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(closed)
        assertTrue(job.isActive)
        assertTrue(failures.isEmpty())
        io.unlock()
        runCurrent()
        assertTrue(closed)
        assertTrue(job.isCompleted)
    }

    @Test fun `close failure is reported and does not cancel later cleanup`() = runTest {
        val error = IOException("remote close failed")
        val failures = mutableListOf<Throwable>()
        val cleanup = ResourceCleanup(backgroundScope) { _, failure -> failures += failure }
        cleanup.closeAfterReads("test", Mutex()) { throw error }
        runCurrent()
        // Coroutine stacktrace recovery may copy the exception; verify the reported failure.
        assertEquals(1, failures.size)
        assertTrue(failures.single() is IOException)
        assertEquals(error.message, failures.single().message)
        var nextClosed = false
        cleanup.closeAfterReads("next", Mutex()) { nextClosed = true }
        runCurrent()
        assertTrue(nextClosed)
    }

    @Test fun `timeout interrupts real SMBJ response wait and releases cleanup worker`() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val owner = CoroutineScope(SupervisorJob() + dispatcher)
        val response = Promise<Unit, TransportException>("close-response", TransportException.Wrapper)
        val started = CompletableDeferred<Unit>()
        val failure = CompletableDeferred<Throwable>()
        var waitFailed = false
        val cleanup = ResourceCleanup(owner, closeTimeoutMs = 250) { _, error -> failure.complete(error) }
        try {
            val job = cleanup.closeAfterReads("SMB", Mutex()) {
                started.complete(Unit)
                try {
                    // Same Future + exception wrapping used by SMBJ Share.receive on close.
                    Futures.get(response.future(), 15, TimeUnit.SECONDS, TransportException.Wrapper)
                } catch (error: TransportException) {
                    waitFailed = true
                    throw SMBRuntimeException(error)
                }
            }
            withTimeout(5_000) { started.await(); job.join() }
            assertTrue(waitFailed)
            assertTrue(withTimeout(5_000) { failure.await() } is TimeoutCancellationException)
            assertFalse(response.isFulfilled)
            val next = CompletableDeferred<Boolean>()
            cleanup.closeAfterReads("next", Mutex()) { next.complete(Thread.currentThread().isInterrupted) }
            assertFalse("Worker must be reusable without an interrupt flag", withTimeout(5_000) { next.await() })
        } finally {
            response.deliver(Unit) // Always unblock the fake server response if the assertion fails.
            owner.cancel()
            dispatcher.close()
        }
    }
}
