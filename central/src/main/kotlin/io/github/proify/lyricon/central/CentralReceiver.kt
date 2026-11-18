/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.Executors

/**
 * 注册广播接收器：把注册请求交给 [CentralRuntime.registration] 处理。
 */
internal object CentralReceiver : BroadcastReceiver() {

    private const val TAG = "CentralReceiver"

    /** 注册广播串行处理，避免并发进入连接注册表。 */
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lyricon-registration")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received intent: $action")

        val pending = goAsync()
        executor.execute {
            try {
                CentralRuntime.registration.handle(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Registration handling failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
