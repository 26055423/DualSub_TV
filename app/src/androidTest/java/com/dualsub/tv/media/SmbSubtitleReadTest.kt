package com.dualsub.tv.media

import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import com.dualsub.tv.network.smb.SmbMediaDataSource
import com.dualsub.tv.network.smb.SmbSessionPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmbSubtitleReadTest {
    @Test fun suppliedSmbMovieDeliversBothSelectedTracks() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("smbHost")
        assumeTrue("No SMB movie supplied", host != null)
        val path = String(Base64.decode(requireNotNull(args.getString("smbPathBase64")), Base64.DEFAULT), Charsets.UTF_8)
        val location = RemoteLocation("test", RemoteType.SMB, "test", requireNotNull(host), args.getString("smbShare"), username = args.getString("smbUsername"), password = args.getString("smbPassword"))
        val pool = SmbSessionPool()
        val sources = mutableListOf<SmbMediaDataSource>()
        val source = { SmbMediaDataSource(pool, location, path, args.getString("blockSize")?.toInt() ?: 4096).also { sources += it } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            val tracks = withTimeout(30_000) { EmbeddedSubtitleReader.listTracks(context, source) }
            Log.i("DualSubTV", "SMB TEST tracks: " + tracks.joinToString { "${it.index}:${it.language}:${it.title}:${it.mimeType}" })
            val selected = args.getString("tracks")?.split(",")?.map { it.toInt() }?.toSet()
                ?: tracks.filterNot { it.isBitmap }.take(2).map { it.index }.toSet()
            assertEquals(2, selected.size)
            for (start in (args.getString("starts") ?: "0,570000,3570000").split(",").map { it.toLong() }) {
                sources.clear()
                val begin = System.nanoTime()
                val first = mutableMapOf<Int, Long>()
                try {
                    val window = withTimeout(args.getString("timeoutMs")?.toLong() ?: 25_000L) {
                        EmbeddedSubtitleReader.readWindow(context, source, selected, start, start + 90_000) { partial ->
                            partial.cues.forEach { (track, cues) ->
                                if (cues.isNotEmpty() && track !in first) {
                                    first[track] = (System.nanoTime() - begin) / 1_000_000
                                    Log.i("DualSubTV", "SMB TEST first: start=$start track=$track ms=${first[track]} cue=${cues.first()}")
                                }
                            }
                        }
                    }
                    Log.i("DualSubTV", "SMB TEST done: start=$start ms=${(System.nanoTime()-begin)/1_000_000} input=${window.bytesRead} counts=${window.cues.mapValues { it.value.size }}")
                    if (args.getString("expectSubtitleIndex") == "true") assertTrue(window.usesSubtitleIndex)
                    if (start > 0) assertTrue(window.cues.values.all { it.isNotEmpty() })
                } finally {
                    Log.i("DualSubTV", "SMB TEST IO: start=$start calls=${sources.sumOf { it.networkReadCount }} bytes=${sources.sumOf { it.networkBytesRead }}")
                }
            }
        } finally { pool.clear() }
    }
}
