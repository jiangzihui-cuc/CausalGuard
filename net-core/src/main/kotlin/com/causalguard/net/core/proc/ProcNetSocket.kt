package com.causalguard.net.core.proc

/** 解析 `/proc/net` 时区分的协议。 */
enum class ProcNetProtocol { TCP, UDP }

/**
 * 一条内核 socket 表项（`/proc/net/tcp`、`/proc/net/udp` 等）。
 *
 * `uid` 为 socket 拥有者 UID（该表项第 8 列，直接给出，无需再遍历 `/proc/<pid>/fd`）。
 * `inode` 保留用于关联进程与包名（可选增强）。
 */
data class ProcNetSocket(
    val protocol: ProcNetProtocol,
    val ipv6: Boolean,
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val state: Int,
    val uid: Int,
    val inode: Long,
) {
    /** 本地端点键（地址+端口）。 */
    fun localKey(): String = "$protocol|$ipv6|$localAddress|$localPort"

    /** 五元组键（含远端）。 */
    fun exactKey(): String = "${localKey()}|$remoteAddress|$remotePort"
}
