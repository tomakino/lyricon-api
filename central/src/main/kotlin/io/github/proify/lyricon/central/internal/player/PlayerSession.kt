/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.player

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

/**
 * 单个提供端的播放会话。
 *
 * 状态以不可变 [State] 快照整体发布；读取方通过 [state] 一次取得一致快照。
 * 会话对象身份稳定，可安全用于活跃播放器选择。
 *
 * @property providerInfo 所属提供端信息，用于身份比较与报告。
 */
internal class PlayerSession(val providerInfo: ProviderInfo) {

    /** 一次完整的状态快照。 */
    internal data class State(
        val song: Song? = null,
        val text: String? = null,
        val lyricType: LyricType = LyricType.NONE,
        val isPlaying: Boolean = false,
        val position: Long = -1,
        val isDisplayTranslation: Boolean = false,
        val isDisplayRoma: Boolean = false,
    )

    /** 当前状态；每次更新整体替换。 */
    @Volatile
    internal var state: State = State()
        private set

    val song: Song? get() = state.song
    val text: String? get() = state.text
    val lyricType: LyricType get() = state.lyricType
    val isPlaying: Boolean get() = state.isPlaying
    val position: Long get() = state.position
    val isDisplayTranslation: Boolean get() = state.isDisplayTranslation
    val isDisplayRoma: Boolean get() = state.isDisplayRoma

    @Synchronized
    fun setSong(song: Song?) {
        state = state.copy(song = song, lyricType = LyricType.SONG)
    }

    @Synchronized
    fun setText(text: String?) {
        state = state.copy(text = text, lyricType = LyricType.TEXT)
    }

    @Synchronized
    fun setPlaying(isPlaying: Boolean) {
        state = state.copy(isPlaying = isPlaying)
    }

    @Synchronized
    fun setPosition(position: Long) {
        state = state.copy(position = position)
    }

    @Synchronized
    fun setDisplayTranslation(value: Boolean) {
        state = state.copy(isDisplayTranslation = value)
    }

    @Synchronized
    fun setDisplayRoma(value: Boolean) {
        state = state.copy(isDisplayRoma = value)
    }

    /** 歌词内容类型。 */
    enum class LyricType {
        /** 未设置内容。 */
        NONE,

        /** 结构化歌曲。 */
        SONG,

        /** 纯文本。 */
        TEXT
    }
}
