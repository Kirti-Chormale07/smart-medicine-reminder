package com.example.mediremind.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsApiTest {

    @Test
    fun tenDigitIndianNumber_getsCountryCode() {
        assertEquals("+919876543210", SmsApi.toE164("9876543210"))
    }

    @Test
    fun leadingZeroIndianNumber_getsCountryCode() {
        assertEquals("+919876543210", SmsApi.toE164("09876543210"))
    }

    @Test
    fun alreadyE164_unchanged() {
        assertEquals("+15551234567", SmsApi.toE164("+15551234567"))
    }

    @Test
    fun formattedNumber_isCleaned() {
        assertEquals("+919876543210", SmsApi.toE164("98765-43210"))
        assertEquals("+919876543210", SmsApi.toE164("+91 98765 43210"))
    }

    @Test
    fun internationalDialPrefix_zeroZero() {
        assertEquals("+441234567890", SmsApi.toE164("00441234567890"))
    }

    @Test
    fun formEncode_handlesUnicodeAndSpaces() {
        val enc = SmsApi.formEncode(mapOf("Body" to "खुराक छूट गई", "To" to "+919876543210"))
        assertTrue(enc.startsWith("Body="))
        assertFalse(enc.contains(" "))
        assertTrue(enc.contains("To=%2B919876543210"))
        assertTrue(enc.contains("%"))
    }

    @Test
    fun twilioConfig_requiresAllFields() {
        assertFalse(SmsApi.hasTwilioConfig(true, "AC123", "", "+1555"))
        assertFalse(SmsApi.hasTwilioConfig(false, "AC123", "token", "+1555"))
        assertTrue(SmsApi.hasTwilioConfig(true, "AC123", "token", "+1555"))
    }
}
