/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.wire

import io.github.proify.lyricon.lyric.model.Song

/**
 * 歌曲载荷缓存：同一 [Song] 实例只序列化一次。
 *
 * 活跃播放器切换或订阅者加入时，多个订阅端会收到同一份歌曲数据；
 * 共享缓存避免每个订阅端各编码一次。
 *
 * 调用方不得在分发后原地修改传入的 [Song]；返回的字节数组只读。
 */
internal object SongPayloadCache {

    private val EMPTY = byteArrayOf()

    private var cachedSong: Song? = null
    private var cachedBytes: ByteArray = EMPTY

    /**
     * 返回 [song] 的 JSON 字节；同一实例复用上次结果。
     *
     * @return 只读缓存字节；调用方不得修改
     */
    @Synchronized
    fun encode(song: Song?): ByteArray {
        if (song == null) return EMPTY
        if (song === cachedSong) return cachedBytes

        val bytes = json.encodeToString(song).toByteArray()
        cachedSong = song
        cachedBytes = bytes
        return bytes
    }

}
