package io.github.nullbrash.quazio.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityTest {

    @Test
    fun deviceLockWinsOverPassword() {
        assertEquals(GateMode.DEVICE, gateMode(deviceAuthAvailable = true, hasPassword = true, passwordRequired = true))
        assertEquals(GateMode.DEVICE, gateMode(deviceAuthAvailable = true, hasPassword = false, passwordRequired = false))
    }

    @Test
    fun withoutDeviceLockPasswordIsUsed() {
        assertEquals(GateMode.PASSWORD, gateMode(deviceAuthAvailable = false, hasPassword = true, passwordRequired = true))
        assertEquals(GateMode.PASSWORD, gateMode(deviceAuthAvailable = false, hasPassword = true, passwordRequired = false))
    }

    @Test
    fun phoneWithoutAnyLockRequiresSetupDesktopStaysOpen() {
        assertEquals(GateMode.SETUP_REQUIRED, gateMode(deviceAuthAvailable = false, hasPassword = false, passwordRequired = true))
        assertEquals(GateMode.OPEN, gateMode(deviceAuthAvailable = false, hasPassword = false, passwordRequired = false))
    }

    @Test
    fun onlyFinanceIsSensitiveForNow() {
        assertTrue(Destination.FINANCE.isSensitive)
        assertFalse(Destination.SETTINGS.isSensitive)
        assertFalse(Destination.CALENDAR.isSensitive)
    }
}
