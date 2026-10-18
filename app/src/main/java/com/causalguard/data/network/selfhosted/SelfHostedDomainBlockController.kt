package com.causalguard.data.network.selfhosted

import com.causalguard.mitigation.DomainBlockController
import com.causalguard.mitigation.DomainBlockOutcome
import com.causalguard.net.core.block.DomainBlocklist

/**
 * 自研底座的域名阻断控制器（v2.0 阶段 1，替代 `TrackerControlDomainBlockController`）。
 *
 * 不再跨进程请求第三方底座，而是把域名写入进程内 [SelfHostedVpnRuntime.blocklist]，
 * 由自研 DNS 代理生效。**诚实原则**：只有自研 VPN 正在运行（代理已生效）时才返回
 * [DomainBlockOutcome.CONFIRMED]；未运行时写入名单也无效果，返回 [DomainBlockOutcome.UNAVAILABLE]。
 */
class SelfHostedDomainBlockController(
    private val blocklist: DomainBlocklist = SelfHostedVpnRuntime.blocklist,
) : DomainBlockController {

    override suspend fun blockDomain(packageName: String, domain: String): DomainBlockOutcome {
        if (domain.isBlank()) return DomainBlockOutcome.UNAVAILABLE
        blocklist.add(domain)
        return if (SelfHostedVpnRuntime.running) {
            DomainBlockOutcome.CONFIRMED
        } else {
            DomainBlockOutcome.UNAVAILABLE
        }
    }
}
