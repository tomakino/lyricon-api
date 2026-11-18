/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.core.wire

import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/** 跨模块跨进程载荷统一使用的宽松 JSON 编解码器。 */
val wireJson: Json = Json {
    coerceInputValues = true
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = false
}

/** 使用 ZLIB 压缩字节数组。 */
fun ByteArray.deflate(): ByteArray {
    if (isEmpty()) return byteArrayOf()

    return Deflater().run {
        setInput(this@deflate)
        finish()

        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(4096)
            while (!finished()) {
                output.write(buffer, 0, deflate(buffer))
            }
            output.toByteArray()
        }.also { end() }
    }
}

/**
 * 使用 Deflate 算法解压当前字节数组。
 *
 * @return 解压后的字节数组
 * @throws DataFormatException 当压缩数据格式非法时抛出
 */
@Throws(DataFormatException::class)
fun ByteArray.inflate(): ByteArray {
    if (isEmpty()) return ByteArray(0)

    val inflater = Inflater().apply {
        setInput(this@inflate)
    }

    return ByteArrayOutputStream().also { out ->
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count > 0) {
                out.write(buffer, 0, count)
            } else if (inflater.needsInput()) {
                break
            }
        }
        inflater.end()
    }.toByteArray()
}
