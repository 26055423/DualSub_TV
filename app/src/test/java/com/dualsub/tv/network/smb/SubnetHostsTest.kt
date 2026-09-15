package com.dualsub.tv.network.smb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubnetHostsTest {

    @Test
    fun `enumerates the local class c range`() {
        val hosts = SubnetHosts.enumerate("192.168.1.5")

        assertEquals(254, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
    }

    @Test
    fun `excludes the local address from the scan`() {
        val hosts = SubnetHosts.enumerate("192.168.1.5", exclude = "192.168.1.5")

        assertEquals(253, hosts.size)
        assertFalse(hosts.contains("192.168.1.5"))
    }

    @Test
    fun `returns nothing for unusable input`() {
        assertTrue(SubnetHosts.enumerate(null).isEmpty())
        assertTrue(SubnetHosts.enumerate("").isEmpty())
        assertTrue(SubnetHosts.enumerate("not-an-ip").isEmpty())
        assertTrue(SubnetHosts.enumerate("192.168.1").isEmpty())
        assertTrue(SubnetHosts.enumerate("192.168.1.999").isEmpty())
    }

    @Test
    fun `derives the class c prefix used for prefilling the host field`() {
        assertEquals("192.168.1.", SubnetHosts.subnetPrefix("192.168.1.5"))
        assertEquals("10.0.0.", SubnetHosts.subnetPrefix("10.0.0.254"))
        assertEquals("172.16.31.", SubnetHosts.subnetPrefix("172.16.31.1"))
    }

    @Test
    fun `prefix is empty when the address is unusable`() {
        // 空串意味着「不要预填」，界面据此保持输入框为空
        assertEquals("", SubnetHosts.subnetPrefix(null))
        assertEquals("", SubnetHosts.subnetPrefix(""))
        assertEquals("", SubnetHosts.subnetPrefix("192.168.1"))
        assertEquals("", SubnetHosts.subnetPrefix("garbage"))
    }

    @Test
    fun `parses dotted quad ipv4`() {
        assertArrayEquals(intArrayOf(10, 0, 0, 1), SubnetHosts.parseIpv4("10.0.0.1"))
        assertArrayEquals(intArrayOf(255, 255, 255, 255), SubnetHosts.parseIpv4("255.255.255.255"))
        assertArrayEquals(intArrayOf(192, 168, 1, 5), SubnetHosts.parseIpv4(" 192.168.1.5 "))
    }

    @Test
    fun `rejects malformed ipv4`() {
        assertNull(SubnetHosts.parseIpv4("10.0.0"))
        assertNull(SubnetHosts.parseIpv4("10.0.0.256"))
        assertNull(SubnetHosts.parseIpv4("10.0.0.-1"))
        assertNull(SubnetHosts.parseIpv4("10.0.0.1.2"))
        assertNull(SubnetHosts.parseIpv4("10.0.0.a"))
        assertNull(SubnetHosts.parseIpv4(null))
    }

    @Test
    fun `sorts numerically rather than lexically`() {
        // 字典序会把 .100 排在 .9 前面，扫描结果列表就会看起来乱七八糟
        val hosts = listOf("192.168.1.100", "192.168.1.9", "192.168.1.20")

        val sorted = hosts.sortedBy { SubnetHosts.toSortableLong(it) }

        assertEquals(listOf("192.168.1.9", "192.168.1.20", "192.168.1.100"), sorted)
    }

    @Test
    fun `sort key is zero for unusable addresses`() {
        assertEquals(0L, SubnetHosts.toSortableLong("garbage"))
        assertEquals(0L, SubnetHosts.toSortableLong(null))
    }
}
