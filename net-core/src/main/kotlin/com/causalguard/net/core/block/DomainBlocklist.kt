package com.causalguard.net.core.block

/**
 * 基于域名的阻断判定（v2.0 阶段 1 自研底座，替代底座 `isAddressAllowed`）。
 *
 * 只按域名证据阻断（不做 IP-only 阻断，避免共享 IP 误伤，见 docs/network-core-map.md 第 7 节）。
 * 支持子域匹配：阻断 `example.com` 同时命中的 `a.b.example.com`。线程安全（同步方法）。
 */
class DomainBlocklist(domains: Collection<String> = emptyList()) {

    private val blocked = LinkedHashSet<String>()

    init {
        domains.forEach(::add)
    }

    val size: Int get() = blocked.size

    @Synchronized
    fun add(domain: String): Boolean {
        val normalized = normalize(domain) ?: return false
        return blocked.add(normalized)
    }

    @Synchronized
    fun remove(domain: String): Boolean {
        val normalized = normalize(domain) ?: return false
        return blocked.remove(normalized)
    }

    @Synchronized
    fun snapshot(): Set<String> = LinkedHashSet(blocked)

    /** 判定域名（或其任一父域）是否被阻断。null/空/非法一律不阻断。 */
    fun shouldBlock(domain: String?): Boolean {
        val normalized = normalize(domain) ?: return false
        synchronized(this) {
            if (blocked.contains(normalized)) return true
            var index = normalized.indexOf('.')
            while (index in 0 until normalized.length - 1) {
                if (blocked.contains(normalized.substring(index + 1))) return true
                index = normalized.indexOf('.', index + 1)
            }
        }
        return false
    }

    companion object {
        /** 规范化：去空白、转小写、去 `*.`/`.` 前缀与结尾点；空或含空格返回 null。 */
        fun normalize(domain: String?): String? {
            var value = domain?.trim()?.lowercase() ?: return null
            value = value.removePrefix("*.").removePrefix(".").trimEnd('.')
            if (value.isEmpty() || value.contains(' ')) return null
            return value
        }
    }
}
