package com.example.samsungremotetvandroid.presentation.discovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryIpValidationTest {
    @Test
    fun isValidIpv4Address_acceptsValidIPv4() {
        assertTrue(isValidIpv4Address("192.168.1.20"))
        assertTrue(isValidIpv4Address("10.0.0.1"))
    }

    @Test
    fun isValidIpv4Address_rejectsOutOfRangeOctet() {
        assertFalse(isValidIpv4Address("192.168.1.999"))
    }

    @Test
    fun isValidIpv4Address_rejectsInvalidShape() {
        assertFalse(isValidIpv4Address(""))
        assertFalse(isValidIpv4Address("192.168.1"))
        assertFalse(isValidIpv4Address("192.168.1.20.4"))
        assertFalse(isValidIpv4Address("abc.def.ghi.jkl"))
    }
}
