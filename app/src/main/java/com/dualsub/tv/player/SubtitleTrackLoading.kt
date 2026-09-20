package com.dualsub.tv.player

import com.dualsub.tv.network.smb.SmbException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import java.io.IOException

/** One automatic retry for transient discovery failure; never retry format errors or user cancellation. */
internal suspend fun <T> retrySubtitleTrackRead(
    onRetry: (Exception) -> Unit,
    read: suspend () -> T
): T {
    try {
        return read()
    } catch (error: Exception) {
        if (error !is IOException && error !is SmbException && error !is TimeoutCancellationException) throw error
        onRetry(error)
        delay(1_000)
    }
    return read()
}
