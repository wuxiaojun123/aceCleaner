package com.nice.library_float

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatReminderPolicyTest {
    @Test fun `permission reminder precedes cooldown and other reminders`() {
        assertEquals(FloatBallState.FILES_ACCESS_NEEDED, FloatReminderPolicy.resolve(true, false, true, true, 10, 99))
    }

    @Test fun `regular reminders wait for initial cooldown`() {
        assertEquals(FloatBallState.DEFAULT, FloatReminderPolicy.resolve(false, false, true, true, 10, 99))
    }

    @Test fun `download then photo then notification then memory determine priority`() {
        assertEquals(FloatBallState.JUNK_CLEAN, FloatReminderPolicy.resolve(false, true, true, true, 10, 99))
        assertEquals(FloatBallState.IMAGE_CLEAN, FloatReminderPolicy.resolve(false, true, false, true, 10, 99))
        assertEquals(FloatBallState.NOTIFICATION_CLEAN, FloatReminderPolicy.resolve(false, true, false, false, 4, 99))
        assertEquals(FloatBallState.PROCESS_CLEAN, FloatReminderPolicy.resolve(false, true, false, false, 3, 80))
    }

    @Test fun `three notifications and memory below eighty fall back to network`() {
        assertEquals(FloatBallState.NETWORK_TEST, FloatReminderPolicy.resolve(false, true, false, false, 3, 79))
    }
}
