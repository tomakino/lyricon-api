/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.player

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * 播放位置轮询器：按固定间隔读取位置并回调。
 *
 * 使用 elapsedRealtime 校正漂移；start/stop 可被幂等地重复调用，
 * 由 @Synchronized 串行化任务切换。
 *
 * @property scope 宿主协程作用域。
 * @property readPosition 位置读取源。
 * @property onPosition 每次读到位置后的回调。
 */
internal class PositionTicker(
    private val scope: CoroutineScope,
    private val readPosition: () -> Long,
    private val onPosition: (Long) -> Unit
) {

    @Volatile
    private var job: Job? = null

    /**
     * 开始轮询（若已在运行则忽略）。
     *
     * @param interval 轮询间隔（毫秒），最小 16ms。
     */
    @Synchronized
    fun start(interval: Long) {
        if (job?.isActive == true) return
        job?.cancel()
        job = scope.launch {
            val safeInterval = interval.coerceAtLeast(MIN_INTERVAL_MS)
            var nextTick = SystemClock.elapsedRealtime()

            while (isActive) {
                onPosition(readPosition())
                nextTick += safeInterval

                val remaining = nextTick - SystemClock.elapsedRealtime()
                if (remaining > 0) {
                    delay(remaining)
                } else {
                    nextTick = SystemClock.elapsedRealtime()
                    yield()
                }
            }
        }
    }

    /** 停止轮询（若未运行则为空操作）。 */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** 停止轮询并释放资源。 */
    fun close() {
        stop()
    }

    private companion object {
        private const val MIN_INTERVAL_MS = 16L
    }
}
