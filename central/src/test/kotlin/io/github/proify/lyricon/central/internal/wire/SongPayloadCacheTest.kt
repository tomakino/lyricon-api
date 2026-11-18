/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.wire

import io.github.proify.lyricon.lyric.model.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SongPayloadCacheTest {

    @Test
    fun nullReturnsEmpty() {
        assertTrue(SongPayloadCache.encode(null).isEmpty())
    }

    @Test
    fun sameInstanceReusesBytes() {
        val song = Song(name = "same")
        val first = SongPayloadCache.encode(song)
        val second = SongPayloadCache.encode(song)

        assertSame(first, second)
        assertTrue(first.isNotEmpty())
    }

    @Test
    fun differentInstanceReencodes() {
        val first = SongPayloadCache.encode(Song(name = "a"))
        val second = SongPayloadCache.encode(Song(name = "b"))

        assertNotSame(first, second)
        assertFalse(first.contentEquals(second))
    }
}
