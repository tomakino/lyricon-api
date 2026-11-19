# Subscriber

用于订阅当前活跃播放器歌词、播放状态和显示配置。

## 1. 依赖

![provider version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/subscriber)

```kotlin
implementation("io.github.proify.lyricon:subscriber:<version>")
```

## 2. 创建与注册

```kotlin
val subscriber = LyriconFactory.createSubscriber(context)
subscriber.register()
```

Android 8.1 以下返回空实现。

## 3. 连接状态监听

```kotlin
val listener = object : ConnectionListener {
    override fun onConnected(subscriber: LyriconSubscriber) {}       // 首次连接
    override fun onReconnected(subscriber: LyriconSubscriber) {}    // 重连
    override fun onDisconnected(subscriber: LyriconSubscriber) {}   // 断开
    override fun onConnectTimeout(subscriber: LyriconSubscriber) {} // 超时
}

subscriber.addConnectionListener(listener)
```

| 回调               | 说明                     |
|--------------------|--------------------------|
| `onConnected`      | 用户主动注册且连接成功   |
| `onReconnected`    | 服务重启或超时重试后恢复 |
| `onDisconnected`   | 主动注销或远端断开       |
| `onConnectTimeout` | 达到最大重试次数仍未连接 |

## 4. 订阅活跃播放器

```kotlin
subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {}
    override fun onSongChanged(song: Song?) {}
    override fun onReceiveText(text: String?) {}
    override fun onPlaybackStateChanged(isPlaying: Boolean) {}
    override fun onPositionChanged(position: Long) {}
    override fun onSeekTo(position: Long) {}
    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {}
    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {}
})
```

## 回调

| 回调                          | 触发时机             | 用途                            |
|-------------------------------|----------------------|---------------------------------|
| `onActiveProviderChanged`     | 活跃播放器变化       | 更新来源；`null` 表示无         |
| `onSongChanged`               | 歌曲或结构化歌词变化 | 渲染歌曲和歌词；`null` 表示清空 |
| `onReceiveText`               | 收到纯文本           | 显示无时间轴文本                |
| `onPlaybackStateChanged`      | 播放/暂停变化        | 控制播放状态                    |
| `onPositionChanged`           | 普通进度更新         | 驱动歌词滚动                    |
| `onSeekTo`                    | 主动跳转             | 立即校准显示                    |
| `onDisplayTranslationChanged` | 翻译开关变化         | 控制翻译 UI                     |
| `onDisplayRomaChanged`        | 罗马音开关变化       | 控制罗马音 UI                   |

- `onSongChanged` 与 `onReceiveText` 互斥；
- 所有回调都应允许参数为空；
- 不要依赖固定回调顺序；
- 收到 `onActiveProviderChanged(null)` 时清空当前 UI；
- 收到 `onSeekTo` 时立即重置歌词位置。

## ProviderInfo

| 字段                  | 说明         |
|-----------------------|--------------|
| `providerPackageName` | 提供端包名   |
| `playerPackageName`   | 播放器包名   |
| `logo`                | 图标，仅展示 |
| `metadata`            | 附加元数据   |
| `processName`         | 播放器进程名 |

相等性只比较 `providerPackageName`、`playerPackageName`、`processName`。

## 生命周期建议

- Activity：在 `onStart` / `onCreate` 注册，在 `onStop` / `onDestroy` 释放；
- Service：随服务创建/销毁管理；
- 只暂停接收时用 `unregister()`，最终销毁用 `destroy()`；
- 可以同时存在多个 Subscriber，中心通过 `SubscriberInfo` 区分。

## 常见问题

**收不到回调？**

检查核心服务、`register()`、`subscribeActivePlayer()`、当前是否有活跃 Provider。

**providerInfo 为什么是 null？**

当前没有活跃播放器，或活跃 Provider 已断开。

**onPositionChanged 和 onSeekTo 的区别？**

前者是普通进度更新，后者是主动跳转，适合立即重置歌词位置。

**需要 Root/LSPosed 吗？**

Subscriber 本身不一定需要，但必须能连接 Lyricon 核心服务。
