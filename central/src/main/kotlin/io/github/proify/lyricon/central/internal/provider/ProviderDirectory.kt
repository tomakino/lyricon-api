/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.provider

import io.github.proify.lyricon.central.internal.connection.ConnectionRegistry
import io.github.proify.lyricon.central.internal.player.ActivePlayerHub
import io.github.proify.lyricon.provider.IProviderBinder
import io.github.proify.lyricon.provider.ProviderInfo

/**
 * 提供端目录：按 [ProviderInfo] 身份维护提供端连接。
 *
 * @property activePlayers 活跃播放器中枢。
 */
internal class ProviderDirectory(
    private val activePlayers: ActivePlayerHub
) {
    private val registry = ConnectionRegistry<ProviderInfo, ProviderConnection>()

    /**
     * 获取或创建提供端连接。
     *
     * 复用已有连接时换绑死亡监听；已关闭的残留连接会被清理并新建。
     *
     * @param binder 本次注册的 Binder。
     * @param info 提供端身份。
     * @return 连接与是否本次新建；false 表示复用了已有连接。
     */
    fun getOrCreate(
        binder: IProviderBinder,
        info: ProviderInfo,
    ): Pair<ProviderConnection, Boolean> {
        val existing = registry.get(info)
        if (existing != null) {
            // 提供端以新 Binder 重新注册：换绑死亡监听；已关闭的残留连接会被
            // 清除并由下方新建连接取代。
            if (existing.reattach(binder)) return existing to false
            registry.unregister(existing)
        }
        val candidate = ProviderConnection(binder, info, activePlayers)
        val registered = registry.register(candidate)
        return registered to (registered === candidate)
    }

    /** 注销提供端连接。 */
    fun unregister(connection: ProviderConnection) {
        registry.unregister(connection)
    }
}
