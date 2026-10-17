package com.causalguard.net.core

/**
 * 自研网络底座的字节与地址工具（v2.0 阶段 1）。
 *
 * 纯函数、无 Android 依赖、无可变全局状态，便于 JVM 单测。
 * 所有 `u8/u16/u32` 均按网络字节序（大端）读取。
 */

internal fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

internal fun ByteArray.u16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)

internal fun ByteArray.u32(offset: Int): Long =
    (u8(offset).toLong() shl 24) or
        (u8(offset + 1).toLong() shl 16) or
        (u8(offset + 2).toLong() shl 8) or
        u8(offset + 3).toLong()

/** 4 字节 → 点分十进制 IPv4。 */
internal fun formatIpv4(bytes: ByteArray, offset: Int): String =
    "${bytes.u8(offset)}.${bytes.u8(offset + 1)}.${bytes.u8(offset + 2)}.${bytes.u8(offset + 3)}"

/**
 * 16 字节 → 压缩 IPv6（RFC 5952：小写、最长零段用 `::` 压缩，长度需 ≥ 2）。
 */
internal fun formatIpv6(bytes: ByteArray, offset: Int): String {
    val groups = IntArray(8) { (bytes.u8(offset + it * 2) shl 8) or bytes.u8(offset + it * 2 + 1) }
    var bestStart = -1
    var bestLength = 0
    var i = 0
    while (i < 8) {
        if (groups[i] == 0) {
            var j = i
            while (j < 8 && groups[j] == 0) j++
            val length = j - i
            if (length > bestLength) {
                bestLength = length
                bestStart = i
            }
            i = j
        } else {
            i++
        }
    }
    if (bestLength < 2) return groups.joinToString(":") { it.toString(16) }
    val head = (0 until bestStart).joinToString(":") { groups[it].toString(16) }
    val tail = (bestStart + bestLength until 8).joinToString(":") { groups[it].toString(16) }
    return "$head::$tail"
}
