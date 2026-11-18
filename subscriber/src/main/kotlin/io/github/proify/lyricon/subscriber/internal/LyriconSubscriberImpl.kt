/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber.internal

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.IRemoteService
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.subscriber.SubscriberInfo
import io.github.proify.lyricon.subscriber.SubscriberStatus
import io.github.proify.lyricon.subscriber.internal.binding.RegistrationBinder
import io.github.proify.lyricon.subscriber.internal.connection.SubscriberLink
import io.github.proify.lyricon.subscriber.internal.registration.CentralBootReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 默认订阅端实现。
 *
 * 负责发送注册广播、处理连接超时重试、维护连接状态，并把远端服务交给
 * [SubscriberLink] 管理。
 */
@RequiresApi(Build.VERSION_CODES.O_MR1)
internal class LyriconSubscriberImpl(
    private val context: Context,
    override val subscriberInfo: SubscriberInfo,
) : LyriconSubscriber {
    private val destroyed = AtomicBoolean(false)
    private val listeners = CopyOnWriteArraySet<ConnectionListener>()
    private val eventExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lyricon-subscriber-events")
    }

    @Volatile
    private var connectionGeneration: Long = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val remote =
        SubscriberLink { disconnect(remote = true, notifyRemote = false) }
    private val registration = RegistrationCoordinator()

    /** 当前连接状态（由 [RegistrationCoordinator] 维护）。 */
    val status: SubscriberStatus
        get() = registration.status

    override fun addConnectionListener(listener: ConnectionListener) {
        listeners.add(listener)
    }

    override fun removeConnectionListener(listener: ConnectionListener) {
        listeners.remove(listener)
    }

    override fun subscribeActivePlayer(listener: ActivePlayerListener): Boolean =
        remote.addActivePlayerListener(listener)

    override fun unsubscribeActivePlayer(listener: ActivePlayerListener): Boolean =
        remote.removeActivePlayerListener(listener)

    override fun register() {
        registration.start(manual = true)
    }

    override fun unregister() {
        if (destroyed.get()) return
        registration.cancel()
        disconnect(remote = false)
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        registration.close()
        disconnect(remote = false, destroy = true)
        scope.cancel()
        listeners.clear()
        eventExecutor.shutdown()
    }

    /**
     * 断开连接并通知监听器。
     *
     * @param remote 是否为远端导致的断开。
     * @param notifyRemote 是否调用远端 disconnect 接口。
     * @param destroy 是否彻底销毁连接端点。
     */
    private fun disconnect(
        remote: Boolean,
        notifyRemote: Boolean = true,
        destroy: Boolean = false
    ) {
        registration.abortAttempt()
        this.remote.disconnect(notifyRemote, destroy)
        val generation = registration.markDisconnected(remote)
        dispatchEvent(generation) { it.onDisconnected(this) }
    }

    /** 串行投递连接事件；代次失效或执行器已关闭时丢弃。 */
    private fun dispatchEvent(generation: Long, block: (ConnectionListener) -> Unit) {
        if (eventExecutor.isShutdown) return
        try {
            eventExecutor.execute {
                if (generation != connectionGeneration) return@execute
                listeners.forEach { listener ->
                    runCatching { block(listener) }
                }
            }
        } catch (_: RejectedExecutionException) {
            // 销毁竞态：事件已无接收方。
        }
    }

    /**
     * 注册协调器：发送注册广播、管理超时重试与中心重启后的恢复注册。
     *
     * 注册尝试（[io.github.proify.lyricon.core.binding.PendingAttemptSlot] + [RegistrationBinder]）保证回调至多被消费一次：
     * - 注销/销毁会结束尝试，迟到回调不再建立连接；
     * - 连接中重复注册被拒绝，避免重复广播与重复 onConnected；
     * - 超时后仍期望连接时，中心启动广播可恢复注册。
     */
    private inner class RegistrationCoordinator : CentralBootReceiver.BootListener {
        private val binder = RegistrationBinder(subscriberInfo)

        @Volatile
        private var timeoutJob: Job? = null

        @Volatile
        private var retryJob: Job? = null

        @Volatile
        private var retryDelayMs: Long = INITIAL_RETRY_DELAY_MS

        @Volatile
        private var timeoutNotified = false

        private var reconnect = false

        /** 期望保持连接（register 时置位，注销/销毁时复位）。 */
        @Volatile
        private var wantsConnection: Boolean = false

        @Volatile
        var status: SubscriberStatus = SubscriberStatus.DISCONNECTED
            private set

        /** 标记为已连接。 */
        fun markConnected() {
            status = SubscriberStatus.CONNECTED
        }

        /** 标记为断开。 */
        @Synchronized
        fun markDisconnected(byRemote: Boolean): Long {
            connectionGeneration++
            status =
                if (byRemote) SubscriberStatus.DISCONNECTED_BY_REMOTE
                else SubscriberStatus.DISCONNECTED
            return connectionGeneration
        }

        private val attempt = object : RegistrationBinder.RegistrationCallback {
            override fun onCompleted(service: IRemoteService?) {
                val result = onAttemptCompleted(service) ?: return
                notifyRegistered(result.first, result.second)
            }
        }

        /**
         * 锁内完成绑定与状态发布；返回是否需要通知监听器及是否重连。
         *
         * @return null 表示不通知；false 表示首次连接；true 表示重连
         */
        @Synchronized
        private fun onAttemptCompleted(service: IRemoteService?): Pair<Boolean, Long>? {
            cancelTimeout()
            retryDelayMs = INITIAL_RETRY_DELAY_MS
            if (!wantsConnection || destroyed.get()) return null

            if (service == null) {
                // 中心返回空服务（异常路径）：不标记连接，按失败路径继续重试。
                Log.w(TAG, "Central returned null service, registration rejected")
                status = SubscriberStatus.DISCONNECTED
                scheduleRetry()
                return null
            }

            remote.bind(service)
            status = SubscriberStatus.CONNECTED
            timeoutNotified = false
            return reconnect to ++connectionGeneration
        }

        /** 通知监听器；代次已失效时跳过。 */
        private fun notifyRegistered(reconnect: Boolean, generation: Long) {
            dispatchEvent(generation) {
                if (isConnectionCurrent(generation)) {
                    if (reconnect) it.onReconnected(this@LyriconSubscriberImpl)
                    else it.onConnected(this@LyriconSubscriberImpl)
                }
            }
        }

        private fun isConnectionCurrent(generation: Long): Boolean =
            generation == connectionGeneration &&
                    wantsConnection &&
                    !destroyed.get() &&
                    status.isConnected()

        init {
            CentralBootReceiver.addBootListener(this)
        }

        /**
         * 发起注册（用户主动或中心启动恢复）。
         *
         * @param manual 用户主动触发；否则为系统恢复路径。
         */
        @Synchronized
        fun start(manual: Boolean) {
            if (destroyed.get()) return
            if (!manual && !wantsConnection) return
            if (status == SubscriberStatus.CONNECTED || status == SubscriberStatus.CONNECTING) return

            if (manual) {
                cancelRetry()
                retryDelayMs = INITIAL_RETRY_DELAY_MS
                timeoutNotified = false
            }
            connectionGeneration++
            wantsConnection = true
            reconnect = !manual || status.isDisconnectedByRemote()
            send()
        }

        override fun onBootCompleted() {
            // 中心服务重启：仍期望连接且当前不在连接中时补一次注册。
            if (wantsConnection &&
                status != SubscriberStatus.CONNECTED &&
                status != SubscriberStatus.CONNECTING
            ) {
                start(manual = false)
            }
        }

        /** 结束当前尝试但保留期望连接（远端断开/超时等非用户路径）。 */
        @Synchronized
        fun abortAttempt() {
            cancelTimeout()
            binder.endAttempt(attempt)
        }

        /** 注销/销毁：结束尝试并清除期望连接。 */
        @Synchronized
        fun cancel() {
            wantsConnection = false
            timeoutNotified = false
            connectionGeneration++
            cancelRetry()
            abortAttempt()
        }

        /** 销毁：结束尝试并停止监听中心启动广播。 */
        fun close() {
            cancel()
            CentralBootReceiver.removeBootListener(this)
        }

        /** 取消注册超时等待。 */
        private fun cancelTimeout() {
            timeoutJob?.cancel()
            timeoutJob = null
        }

        /** 发送注册广播并启动超时等待。 */
        private fun send() {
            if (destroyed.get()) return
            status = SubscriberStatus.CONNECTING
            binder.beginAttempt(attempt)
            context.sendBroadcast(Intent(SubscriberConstants.ACTION_REGISTER_SUBSCRIBER).apply {
                setPackage(SubscriberConstants.SYSTEM_UI_PACKAGE_NAME)
                putExtra(
                    SubscriberConstants.EXTRA_BUNDLE,
                    Bundle().apply {
                        putBinder(SubscriberConstants.EXTRA_BINDER, binder)
                    }
                )
            })
            scheduleTimeout()
        }

        /** 启动注册超时等待；超时后安排指数退避重试。 */
        @Synchronized
        private fun scheduleTimeout() {
            cancelTimeout()
            timeoutJob = scope.launch {
                delay(CONNECT_TIMEOUT_MS)
                val generation = onTimeout()
                if (generation != null) {
                    dispatchEvent(generation) {
                        if (wantsConnection && !destroyed.get()) {
                            it.onConnectTimeout(this@LyriconSubscriberImpl)
                        }
                    }
                }
            }
        }

        /**
         * 处理一次超时；返回是否需要通知监听器。
         *
         * 首次超时通知一次，后续重试只更新状态与退避。
         */
        @Synchronized
        private fun onTimeout(): Long? {
            if (destroyed.get() || !status.isConnecting() || !wantsConnection) return null
            status = SubscriberStatus.DISCONNECTED
            scheduleRetry()
            if (timeoutNotified) return null
            timeoutNotified = true
            return connectionGeneration
        }

        @Synchronized
        private fun scheduleRetry() {
            if (!wantsConnection) return
            cancelRetry()
            retryJob = scope.launch {
                delay(retryDelayMs)
                if (!wantsConnection || destroyed.get()) return@launch
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                start(manual = false)
            }
        }

        @Synchronized
        private fun cancelRetry() {
            retryJob?.cancel()
            retryJob = null
        }
    }

    private companion object {
        private const val TAG = "LyriconSubscriberImpl"
        private const val CONNECT_TIMEOUT_MS = 3_000L
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
    }
}
