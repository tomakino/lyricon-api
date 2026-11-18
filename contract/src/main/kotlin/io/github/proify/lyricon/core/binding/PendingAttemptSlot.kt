/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.core.binding

/**
 * 至多一个待处理项的槽位。
 *
 * [consume] 在锁内取用并清空，保证中心回调至多被消费一次：
 * 迟到的回调（尝试已结束）与重复回调都会拿到 null 而被拦截。
 */
class PendingAttemptSlot<T> {

    @Volatile
    private var pending: T? = null

    /** 进入等待状态（覆盖旧的等待项）。 */
    fun begin(attempt: T) {
        synchronized(this) {
            pending = attempt
        }
    }

    /** 结束等待：仅在 [attempt] 仍为当前项时清除。 */
    fun end(attempt: T) {
        synchronized(this) {
            if (pending === attempt) pending = null
        }
    }

    /** 原子地取用并清空当前项；没有等待项时返回 null。 */
    fun consume(): T? = synchronized(this) {
        val current = pending
        pending = null
        current
    }
}
