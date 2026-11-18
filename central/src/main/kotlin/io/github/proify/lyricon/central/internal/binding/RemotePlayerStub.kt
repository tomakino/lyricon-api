/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.binding

import android.media.session.PlaybackState
import android.os.SharedMemory
import android.util.Log
import io.github.proify.lyricon.central.internal.player.PlaybackStateTracker
import io.github.proify.lyricon.central.internal.player.PlayerSession
import io.github.proify.lyricon.central.internal.player.PlayerListener
import io.github.proify.lyricon.central.internal.player.PositionMemoryChannel
import io.github.proify.lyricon.central.internal.player.PositionTicker
import io.github.proify.lyricon.central.internal.util.ScreenStateMonitor
import io.github.proify.lyricon.core.wire.inflate
import io.github.proify.lyricon.core.wire.wireJson
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.IRemotePlayer
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.ProviderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 提供端播放命令的 AIDL 桩。
 *
 * 负责把 [IRemotePlayer] 命令写入 [PlayerSession] 并转发事件；
 * 位置计算由 [PlaybackStateTracker] 负责，本类只做 PlaybackState 与
 * 原始参数的适配。
 */
internal class RemotePlayerStub(
    info: ProviderInfo,
    private val playerEvents: PlayerListener
) : IRemotePlayer.Stub(), ScreenStateMonitor.ScreenStateListener {

    private val session = PlayerSession(info)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val closed = AtomicBoolean(false)
    private val positionTracker = PlaybackStateTracker()
    private val positionMemory = PositionMemoryChannel()
    private val positionTicker = PositionTicker(
        scope = scope,
        readPosition = positionTracker::computePosition,
        onPosition = ::publishPosition
    )

    @Volatile
    private var positionUpdateInterval: Long = ProviderConstants.DEFAULT_POSITION_UPDATE_INTERVAL

    /** 歌词内容队列：setSong/sendText 按到达顺序处理，避免解码与写入乱序。 */
    private val lyricQueue = Channel<LyricPayload>(Channel.UNLIMITED)

    init {
        positionTracker.manualPositionReader = positionMemory::readPosition
        ScreenStateMonitor.addListener(this)
        scope.launch {
            for (payload in lyricQueue) {
                when (payload) {
                    is LyricPayload.SongPayload -> processSong(payload.bytes)
                    is LyricPayload.TextPayload -> processText(payload.text)
                }
            }
        }
    }

    /** 关闭命令桩：停止轮询、释放共享内存与协程作用域。 */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        ScreenStateMonitor.removeListener(this)
        stopPositionUpdate()

        lyricQueue.cancel()
        positionMemory.close()
        scope.cancel()
    }

    override fun onScreenOn() {
        if (session.isPlaying) startPositionUpdate()
    }

    override fun onScreenOff() {
        stopPositionUpdate()
    }

    override fun onScreenUnlocked() = Unit

    override fun setPositionUpdateInterval(interval: Int) {
        if (closed.get()) return

        val next = interval.toLong().coerceAtLeast(MIN_INTERVAL_MS)
        if (positionUpdateInterval == next) return

        positionUpdateInterval = next
        if (session.isPlaying) {
            stopPositionUpdate()
            startPositionUpdate()
        }
    }

    override fun setSong(bytes: ByteArray?) {
        if (closed.get()) return

        // 入队顺序与调用顺序一致；解码在单消费者中按序完成。
        lyricQueue.trySend(LyricPayload.SongPayload(bytes))
    }

    override fun setPlaybackState(isPlaying: Boolean) {
        if (closed.get()) return

        positionTracker.useManualMode(isPlaying)

        if (session.isPlaying != isPlaying) {
            session.setPlaying(isPlaying)
            playerEvents.safeNotify { onPlaybackStateChanged(session, isPlaying) }
        }

        if (isPlaying) startPositionUpdate() else stopPositionUpdate()
    }

    override fun setPlaybackState2(state: PlaybackState?) {
        if (closed.get()) return

        if (state == null) {
            if (positionTracker.stopDriving()) {
                stopPositionUpdate()
            }
            return
        }

        Log.d(TAG, "setPlaybackState2: $state")

        if (state.state == PlaybackState.STATE_BUFFERING) return

        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        positionTracker.usePlaybackState(
            playing = isPlaying,
            position = state.position,
            lastUpdateTime = state.lastPositionUpdateTime,
            playbackSpeed = state.playbackSpeed
        )

        if (session.isPlaying != isPlaying) {
            session.setPlaying(isPlaying)
            playerEvents.safeNotify { onPlaybackStateChanged(session, isPlaying) }
        }

        if (isPlaying) startPositionUpdate() else stopPositionUpdate()
    }

    override fun seekTo(position: Long) {
        if (closed.get()) return

        val safe = position.coerceAtLeast(0L)
        positionTracker.seekTo(safe)
        session.setPosition(safe)
        playerEvents.safeNotify { onSeekTo(session, safe) }
    }

    override fun sendText(text: String?) {
        if (closed.get()) return

        lyricQueue.trySend(LyricPayload.TextPayload(text))
    }

    override fun setDisplayTranslation(isDisplayTranslation: Boolean) {
        if (closed.get()) return

        session.setDisplayTranslation(isDisplayTranslation)
        playerEvents.safeNotify { onDisplayTranslationChanged(session, isDisplayTranslation) }
    }

    override fun setDisplayRoma(isDisplayRoma: Boolean) {
        if (closed.get()) return

        session.setDisplayRoma(isDisplayRoma)
        playerEvents.safeNotify { onDisplayRomaChanged(session, isDisplayRoma) }
    }

    override fun getPositionMemory(): SharedMemory? = positionMemory.sharedMemory

    /** 开始位置轮询（灭屏时忽略）。 */
    /** 解码并应用一首歌曲（由歌曲队列的消费者按序调用）。 */
    @OptIn(ExperimentalSerializationApi::class)
    private fun processSong(bytes: ByteArray?) {
        if (closed.get()) return
        val song = bytes?.let {
            runCatching {
                it.inflate()
                    .inputStream()
                    .buffered()
                    .use { stream ->
                        wireJson.decodeFromStream(Song.serializer(), stream)
                    }
            }.getOrNull()
        }

        val normalized = runCatching { song?.normalize() }
            .onFailure { Log.e(TAG, "Failed to normalize song", it) }
            .getOrNull()
        if (closed.get()) return
        session.setSong(normalized)
        playerEvents.safeNotify { onSongChanged(session, normalized) }
    }

    private sealed interface LyricPayload {
        data class SongPayload(val bytes: ByteArray?) : LyricPayload {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as SongPayload

                return bytes.contentEquals(other.bytes)
            }

            override fun hashCode(): Int {
                return bytes?.contentHashCode() ?: 0
            }
        }

        data class TextPayload(val text: String?) : LyricPayload
    }

    private fun processText(text: String?) {
        if (closed.get()) return
        session.setText(text)
        playerEvents.safeNotify { onText(session, text) }
    }

    private fun startPositionUpdate() {
        if (closed.get()) return
        if (ScreenStateMonitor.state == ScreenStateMonitor.ScreenState.OFF) return
        positionTicker.start(positionUpdateInterval)
    }

    /** 停止位置轮询。 */
    private fun stopPositionUpdate() {
        positionTicker.stop()
    }

    /** 轮询回调：写入会话并广播位置变化。 */
    private fun publishPosition(position: Long) {
        if (closed.get()) return
        session.setPosition(position)
        playerEvents.safeNotify { onPositionChanged(session, position) }
    }

    private inline fun PlayerListener.safeNotify(crossinline block: PlayerListener.() -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "player event dispatch failed", e)
        }
    }

    private companion object {
        private const val TAG = "RemotePlayerStub"
        private const val MIN_INTERVAL_MS = 16L
    }
}
