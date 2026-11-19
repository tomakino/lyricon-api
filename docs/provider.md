# Provider

用于发送歌词、播放状态和显示配置。

## 1. 添加依赖

![provider version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/provider)

在 `build.gradle.kts` 添加以下依赖：

```kotlin
implementation("io.github.proify.lyricon:provider:<version>")
```

## 2. 配置 AndroidManifest

在 `application` 节点添加：

```xml

<meta-data android:name="lyricon_module" android:value="true" />

<meta-data android:name="lyricon_module_author" android:value="your name" />

<meta-data android:name="lyricon_module_description" android:value="module description" />
```

| 字段                         | 说明                        |
|------------------------------|-----------------------------|
| `lyricon_module`             | 标记当前应用为 Lyricon 模块 |
| `lyricon_module_author`      | 模块作者                    |
| `lyricon_module_description` | 模块说明                    |

可选能力标签（仅用于展示）

```xml

<meta-data android:name="lyricon_module_tags" android:resource="@array/lyricon_module_tags" />
```

```xml

<string-array name="lyricon_module_tags">
    <item>$syllable</item>
    <item>$translation</item>
</string-array>
```

| 标签           | 含义                |
|----------------|---------------------|
| `$syllable`    | 支持逐字 / 动态歌词 |
| `$translation` | 支持歌词翻译显示    |

## 3. 创建

```kotlin
val provider = LyriconFactory.createProvider(
    context = context,
    providerPackageName = context.packageName,
    playerPackageName = context.packageName,
    logo = ProviderLogo.fromSvg(""),
)
```

注意：内部实现要求至少 Android 8.1，Android 8.1 以下会返回空实现。

### 注册、注销、销毁

```kotlin
provider.register()   // 发送注册请求
provider.unregister() // 断开，实例仍可再次 register()
provider.destroy()    // 释放资源，之后不可复用
```

### 连接状态监听

```kotlin
provider.service.addConnectionListener {
    onConnected { }      // 首次连接成功
    onReconnected { }    // 断线后重连成功
    onDisconnected { }   // 断开
    onConnectTimeout { } // 注册超时
}
```

使用 `autoSync` 控制连接或重连后是否补发最近一次缓存的播放器状态，默认开启。

## 4. 发送歌词

### 4.1 发送普通文本

```kotlin
provider.player.sendText("我无法只是普通朋友")
```

`sendText` 用于没有时间轴的文本，会清除之前设置的歌曲，进入纯文本模式。

### 4.2 发送歌曲

`Song` 字段：

| 字段       | 说明                       |
|------------|----------------------------|
| `id`       | 歌曲唯一标识，建议稳定复用 |
| `name`     | 歌曲名                     |
| `artist`   | 歌手                       |
| `duration` | 总时长，毫秒               |
| `lyrics`   | 歌词行列表                 |

当歌词还没有准备好时，可以先发送占位：

```kotlin
provider.player.setSong(
    Song(
        name = "普通朋友",
        artist = "陶喆",
    )
)
```

发送行级歌词：

```kotlin
provider.player.setSong(
    Song(
        id = "song-id",
        name = "普通朋友",
        artist = "陶喆",
        duration = 2000,
        lyrics = listOf(
            RichLyricLine(begin = 0, end = 1000, text = "我无法只是普通朋友"),
            RichLyricLine(begin = 1000, end = 2000, text = "不想做普通朋友")
        )
    )
)
```

发送逐字歌词：

```kotlin
provider.player.setSong(
    Song(
        id = "song-id",
        name = "普通朋友",
        artist = "陶喆",
        duration = 1000,
        lyrics = listOf(
            RichLyricLine(
                begin = 0,
                end = 1000,
                text = "我无法只是普通朋友",
                words = listOf(
                    LyricWord(text = "我", end = 200),
                    LyricWord(text = "无法", begin = 200, end = 400),
                    LyricWord(text = "只是", begin = 400, end = 600),
                    LyricWord(text = "普通", begin = 600, end = 800),
                    LyricWord(text = "朋友", begin = 800, end = 1000)
                )
            )
        )
    )
)
```

翻译、次要文本(背景人声)、罗马音：

```kotlin
RichLyricLine(
    begin = 0,
    end = 1000,
    text = "我无法只是普通朋友",
    secondary = "（不想做普通朋友）",
    translation = "I can't just be a normal friend",
    roma = "..."
)
```

## 播放状态与位置

```kotlin
// 只同步播放/暂停
provider.player.setPlaybackState(true)

// 播放中持续同步当前位置（毫秒）
provider.player.setPosition(positionMs)

// 用户拖动进度条、切歌恢复进度
provider.player.seekTo(positionMs)

// 显示开关
provider.player.setDisplayTranslation(true)
provider.player.setDisplayRoma(true)
```

## 推荐调用顺序

```kotlin
player.setPlaybackState(isPlaying)
player.setSong(song)
player.setPosition(currentPosition)
player.setDisplayTranslation(displayTranslation)
player.setDisplayRoma(displayRoma)
```

## 本地测试

没有 LSPosed 时，可以使用 [LocalCentralService](https://github.com/tomakino/lyricon/releases/tag/localcentral)：

1. 安装 LocalCentralService；
2. 打开并激活服务；
3. 授予悬浮窗权限；
4. 创建 Provider 时指定本地包名：

```kotlin
val provider = LyriconFactory.createProvider(
    context,
    centralPackageName = "io.github.lyricon.localcentralapp"
)
```

正式发布前请删除 `centralPackageName` 字段。

## 常见问题

**注册后没有歌词？**

检查核心服务是否运行、LSPosed 作用域是否正确、是否调用 `register()`、是否调用了 `sendText()` 或
`setSong()`。

**sendText 和 setSong 的区别？**

`sendText` 是无时间轴文本，会清除歌曲；`setSong` 是结构化歌曲和歌词。

**什么时候用 setPosition / seekTo？**

`setPosition` 用于播放中的持续进度；`seekTo` 用于主动跳转。

**翻译/罗马音不显示？**

需要歌词行里有对应数据、Provider 调用了显示开关、展示端允许显示。

**支持 Java 吗？**

Java 调用方式未完整验证，建议使用 Kotlin。
