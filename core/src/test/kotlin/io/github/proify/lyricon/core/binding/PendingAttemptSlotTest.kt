/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.core.binding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingAttemptSlotTest {

    @Test
    fun consumeReturnsAttemptOnlyOnce() {
        val slot = PendingAttemptSlot<String>()

        slot.begin("attempt")

        assertEquals("attempt", slot.consume())
        assertNull(slot.consume())
    }

    @Test
    fun endOnlyClearsMatchingAttempt() {
        val slot = PendingAttemptSlot<String>()
        val first = "first"
        val second = "second"

        slot.begin(first)
        slot.begin(second)
        slot.end(first)

        assertEquals(second, slot.consume())
    }

    @Test
    fun endCurrentClearsPending() {
        val slot = PendingAttemptSlot<String>()

        slot.begin("attempt")
        slot.end("attempt")

        assertNull(slot.consume())
    }
}
