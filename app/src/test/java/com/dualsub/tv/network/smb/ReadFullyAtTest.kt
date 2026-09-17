package com.dualsub.tv.network.smb

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ReadFullyAtTest {
    @Test fun `short network reads advance both offsets until requested bytes are filled`() {
        val original = ByteArray(40) { it.toByte() }
        val result = ByteArray(30) { 99 }
        var calls = 0
        val size = readFullyAt(7, result, 4, 20) { target, position, offset, wanted ->
            calls++
            val count = minOf(3, wanted)
            original.copyInto(target, offset, position.toInt(), position.toInt() + count)
            count
        }
        assertEquals(20, size)
        assertEquals(7, calls)
        assertArrayEquals(original.copyOfRange(7, 27), result.copyOfRange(4, 24))
        assertEquals(99.toByte(), result[3])
        assertEquals(99.toByte(), result[24])
    }

    @Test fun `EOF returns only bytes actually read`() {
        var calls = 0
        assertEquals(2, readFullyAt(0, ByteArray(8), 0, 8) { _, _, _, _ ->
            if (calls++ == 0) 2 else -1
        })
        assertEquals(-1, readFullyAt(0, ByteArray(8), 0, 8) { _, _, _, _ -> -1 })
    }

    @Test(expected = IOException::class)
    fun `zero progress fails instead of looping or pretending EOF`() {
        readFullyAt(0, ByteArray(8), 0, 8) { _, _, _, _ -> 0 }
    }
}
