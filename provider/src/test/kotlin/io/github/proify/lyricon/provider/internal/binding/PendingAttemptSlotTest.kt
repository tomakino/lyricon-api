/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.provider.internal.binding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingAttemptSlotTest {

    @Test
    fun consumeReturnsAndClearsAttempt() {
        val attempts = PendingAttemptSlot<String>()
        attempts.begin("a")

        assertEquals("a", attempts.consume())
        assertNull(attempts.consume())
    }

    @Test
    fun consumeWithNoPendingAttemptReturnsNull() {
        val attempts = PendingAttemptSlot<String>()
        assertNull(attempts.consume())
    }

    @Test
    fun endClearsOnlyMatchingAttempt() {
        val attempts = PendingAttemptSlot<String>()
        attempts.begin("a")

        attempts.end("b")
        assertEquals("a", attempts.consume())

        attempts.begin("a")
        attempts.end("a")
        assertNull(attempts.consume())
    }

    @Test
    fun beginReplacesPendingAttempt() {
        val attempts = PendingAttemptSlot<String>()
        attempts.begin("a")
        attempts.begin("b")

        assertEquals("b", attempts.consume())
    }

    @Test
    fun endAfterAttemptWasConsumedIsNoOp() {
        val attempts = PendingAttemptSlot<String>()
        attempts.begin("a")
        assertEquals("a", attempts.consume())

        attempts.end("a")
        assertNull(attempts.consume())
    }
}
