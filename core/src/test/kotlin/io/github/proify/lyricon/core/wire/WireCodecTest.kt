/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.core.wire

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireCodecTest {

    @Test
    fun deflateRoundTrip() {
        val source = "lyricon payload".toByteArray()

        assertArrayEquals(source, source.deflate().inflate())
    }

    @Test
    fun emptyRoundTrip() {
        assertTrue(byteArrayOf().deflate().isEmpty())
        assertTrue(byteArrayOf().inflate().isEmpty())
    }

    @Test
    fun jsonIgnoresUnknownKeys() {
        val decoded = wireJson.decodeFromString(
            MapSerializer(String.serializer(), String.serializer()),
            """{"known":"value","unknown":"ignored"}"""
        )

        assertEquals("value", decoded["known"])
    }
}
