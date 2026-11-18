/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.binding

import io.github.proify.lyricon.central.CentralRegistry
import io.github.proify.lyricon.central.internal.provider.ProviderConnection
import io.github.proify.lyricon.provider.IRemoteService

/**
 * 提供端远端服务桩：向提供端暴露播放命令与断开能力。
 *
 * 持有连接对应的 [RemotePlayerStub]，由 [ProviderConnection] 创建与关闭。
 *
 * @property connection 所属提供端连接。
 */
internal class ProviderRemoteServiceStub(
    private var connection: ProviderConnection? = null
) : IRemoteService.Stub() {

    /** 提供端的播放命令桩。 */
    private var player: RemotePlayerStub? = connection?.let {
        RemotePlayerStub(it.providerInfo, CentralRegistry.activePlayers)
    }

    override fun getPlayer(): RemotePlayerStub? = player

    /** 关闭播放命令桩并释放连接引用。 */
    fun close() {
        player?.close()
        player = null
        connection = null
    }

    override fun disconnect() {
        connection?.let { CentralRegistry.providers.unregister(it) }
    }
}
