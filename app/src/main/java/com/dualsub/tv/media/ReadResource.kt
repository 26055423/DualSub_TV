package com.dualsub.tv.media

import java.io.Closeable
import java.util.concurrent.CancellationException

/** A fully decoded read result remains valid if only releasing its source fails. */
internal inline fun <T : Closeable, R> T.useReadResult(
    reportCloseFailure: (Exception) -> Unit,
    read: (T) -> R
): R {
    var readFailure: Throwable? = null
    try {
        return read(this)
    } catch (error: Throwable) {
        readFailure = error
        throw error
    } finally {
        try { close() } catch (cleanup: Exception) {
            if (readFailure != null) {
                if (cleanup !== readFailure) readFailure.addSuppressed(cleanup)
            } else {
                if (cleanup is CancellationException) throw cleanup
                reportCloseFailure(cleanup)
            }
        }
    }
}
