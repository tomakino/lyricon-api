/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.provider

import io.github.proify.lyricon.provider.service.RemoteService

/**
 * Lyricon 提供端入口。
 *
 * 提供端负责把播放器状态、歌曲和歌词显示配置发送给中心服务。实例通常由
 * [LyriconFactory.createProvider] 创建。
 *
 * @since 0.1.80
 */
interface LyriconProvider {
    /** 注册到中心服务时上报的提供端信息。 */
    val providerInfo: ProviderInfo

    /** 与中心服务的远端连接入口，可读取连接状态并注册连接监听。 */
    val service: RemoteService

    /** 播放器状态发送入口。 */
    val player: RemotePlayer

    /** 连接或重连成功后是否自动同步最近一次缓存的播放器状态。 */
    var autoSync: Boolean

    /** 暴露给中心服务调用的本地命令处理器。 */
    var providerService: ProviderService?

    /**
     * 向中心服务发送注册请求。
     *
     * @return 是否发起了新的注册；已连接、正在连接、已销毁或中心包名为空时为 `false`
     */
    fun register(): Boolean

    /**
     * 主动断开当前中心服务连接。
     *
     * @return 是否执行了断开；已销毁时为 `false`
     */
    fun unregister(): Boolean

    /**
     * 释放监听器、注册回调和远端连接资源。
     *
     * @return 首次销毁返回 `true`；重复调用返回 `false`
     */
    fun destroy(): Boolean
}
