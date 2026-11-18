/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.player

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSessionTest {

    private fun session() = PlayerSession(ProviderInfo("provider", "player"))

    @Test
    fun setSongSwitchesTypeAndKeepsOtherFields() {
        val session = session()
        session.setPlaying(true)
        session.setPosition(100L)

        session.setSong(Song(name = "hello"))

        val state = session.state
        assertEquals(PlayerSession.LyricType.SONG, state.lyricType)
        assertEquals("hello", state.song?.name)
        assertTrue(state.isPlaying)
        assertEquals(100L, state.position)
    }

    @Test
    fun setTextSwitchesTypeAndKeepsSong() {
        val session = session()
        session.setSong(Song(name = "song"))
        session.setText("plain")

        val state = session.state
        assertEquals(PlayerSession.LyricType.TEXT, state.lyricType)
        assertEquals("plain", state.text)
        assertEquals("song", state.song?.name)
    }

    @Test
    fun nullSongClearsContentButKeepsSongType() {
        val session = session()
        session.setSong(Song(name = "song"))
        session.setSong(null)

        val state = session.state
        assertEquals(PlayerSession.LyricType.SONG, state.lyricType)
        assertNull(state.song)
    }

    @Test
    fun displayAndPositionSettersOnlyChangeTheirField() {
        val session = session()
        session.setDisplayTranslation(true)
        session.setDisplayRoma(true)
        session.setPosition(42L)

        val state = session.state
        assertTrue(state.isDisplayTranslation)
        assertTrue(state.isDisplayRoma)
        assertEquals(42L, state.position)
        assertEquals(PlayerSession.LyricType.NONE, state.lyricType)
    }
}
