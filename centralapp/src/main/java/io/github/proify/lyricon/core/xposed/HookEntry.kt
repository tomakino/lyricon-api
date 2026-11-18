/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.core.xposed

import android.app.Application
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import io.github.proify.lyricon.central.BridgeCentral

@YukiHookLibXposedEntry(
    targetApiVersion = 101,
    scope = ["com.android.systemui"],
    staticScope = true,
)
class HookEntry : YukiHookXposedModule {

    companion object {
        private const val TAG = "HookEntry"
    }

    override fun onHook() {
        YukiHook.encase {
            loadApp("com.android.systemui", Hooker())
        }
    }

    class Hooker : YukiBaseHooker() {

        override fun onHook() {
            registerAppLifecycle {
                onCreate {
                    initLyriconCentral(this)
                }
            }
        }

        private fun initLyriconCentral(app: Application) {
            try {
                BridgeCentral.initialize(app)
                BridgeCentral.sendBootCompleted()
                YLog.info(tag = TAG, msg = "Lyricon BridgeCentral initialized successfully.")
            } catch (e: Throwable) {
                YLog.error(tag = TAG, e = e)
            }
        }
    }

}