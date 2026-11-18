/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.player

import android.os.SystemClock

/**
 * 播放位置跟踪器：统一"手动上报位置"与"PlaybackState 驱动"两种模式的计算。
 *
 * 纯 Kotlin + 时钟注入（默认 [SystemClock.elapsedRealtime]），可单元测试。
 *
 * - 手动模式：位置来自共享内存读取器（[manualPositionReader]）；
 * - 驱动模式：位置由最近一次 [usePlaybackState] 记录的基点、时间与速度推算。
 */
internal class PlaybackStateTracker(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {

    /** 当前是否处于 PlaybackState 驱动模式。 */
    @Volatile
    var isStateDriven: Boolean = false
        private set

    @Volatile
    private var isPlaying: Boolean = false

    @Volatile
    private var basePosition: Long = 0L

    @Volatile
    private var lastUpdate: Long = 0L

    @Volatile
    private var speed: Float = 1.0f

    /** 手动模式 seek 后的临时覆盖值，避免共享内存旧值造成回跳。 */
    @Volatile
    private var manualSeek: ManualSeek? = null

    private data class ManualSeek(
        val position: Long,
        val startAt: Long,
        val until: Long,
        val sharedAtSeek: Long,
    )

    /** 手动模式的读取源（位置共享内存）。 */
    @Volatile
    var manualPositionReader: (() -> Long)? = null

    /** 切换到手动上报模式（setPlaybackState(Boolean) 路径）。 */
    @Synchronized
    fun useManualMode(playing: Boolean = false) {
        if (isStateDriven) {
            manualSeek = null
        } else {
            val seek = manualSeek
            if (seek != null && playing != isPlaying) {
                val now = clock()
                val current = if (isPlaying) {
                    seek.position + (now - seek.startAt).coerceAtLeast(0L)
                } else {
                    seek.position
                }
                manualSeek = seek.copy(
                    position = current,
                    startAt = now,
                    until = if (playing) now + MANUAL_SEEK_TIMEOUT_MS else Long.MAX_VALUE,
                )
            }
        }
        isStateDriven = false
        isPlaying = playing
    }

    /** 切换到 PlaybackState 驱动模式（setPlaybackState2 路径）。 */
    @Synchronized
    fun usePlaybackState(
        playing: Boolean,
        position: Long,
        lastUpdateTime: Long,
        playbackSpeed: Float,
    ) {
        manualSeek = null
        isStateDriven = true
        isPlaying = playing
        basePosition = position
        lastUpdate = lastUpdateTime
        speed = playbackSpeed
    }

    /**
     * 停止驱动并清空记录。
     *
     * @return 是否正处于驱动模式（调用方据此决定是否关闭位置更新）。
     */
    @Synchronized
    fun stopDriving(): Boolean {
        val wasDriving = isStateDriven
        manualSeek = null
        isStateDriven = false
        isPlaying = false
        basePosition = 0L
        lastUpdate = 0L
        speed = 1.0f
        return wasDriving
    }

    /** 将播放位置重置到 [position]，同时更新驱动模式的基数与时间。 */
    @Synchronized
    fun seekTo(position: Long) {
        val safe = position.coerceAtLeast(0L)
        val now = clock()
        basePosition = safe
        lastUpdate = now
        if (!isStateDriven) {
            val shared = (manualPositionReader?.invoke() ?: 0L).coerceAtLeast(0L)
            manualSeek = ManualSeek(
                position = safe,
                startAt = now,
                until = if (isPlaying) now + MANUAL_SEEK_TIMEOUT_MS else Long.MAX_VALUE,
                sharedAtSeek = shared,
            )
        }
    }

    /** 当前播放位置（毫秒），非负。 */
    fun computePosition(): Long {
        if (!isStateDriven) {
            val seek = manualSeek
            if (seek != null) {
                val now = clock()
                val shared = (manualPositionReader?.invoke() ?: 0L).coerceAtLeast(0L)
                val expected =
                    if (isPlaying) seek.position + (now - seek.startAt) else seek.position
                val readerUpdated = kotlin.math.abs(shared - seek.sharedAtSeek) > 1L
                val caughtUp = readerUpdated && (
                        !isPlaying ||
                                kotlin.math.abs(shared - expected) <= SEEK_MATCH_TOLERANCE_MS
                        )
                if (caughtUp || now >= seek.until) {
                    if (manualSeek === seek) manualSeek = null
                    return shared
                }
                return expected.coerceAtLeast(0L)
            }
            return (manualPositionReader?.invoke() ?: 0L).coerceAtLeast(0L)
        }

        val safeBase = basePosition.coerceAtLeast(0L)
        if (!isPlaying || lastUpdate <= 0L) return safeBase

        val delta = (clock() - lastUpdate).coerceAtLeast(0L)
        val advanced = if (speed == 1.0f) {
            safeBase + delta
        } else {
            safeBase + (delta * speed).toLong()
        }
        return advanced.coerceAtLeast(0L)
    }

    private companion object {
        private const val MANUAL_SEEK_TIMEOUT_MS = 2_000L
        private const val SEEK_MATCH_TOLERANCE_MS = 150L
    }
}
