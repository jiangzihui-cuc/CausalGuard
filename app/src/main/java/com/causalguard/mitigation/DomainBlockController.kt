package com.causalguard.mitigation

/**
 * 域名阻断执行结果（A5-1/A5-2）。
 *
 * `CONFIRMED` 仅当底座确认阻断已生效时才返回；依赖缺失、未接线、超时或无法确认
 * 一律 [UNAVAILABLE]，绝不谎报已阻断。
 */
enum class DomainBlockOutcome {
    /** 底座确认阻断已生效。 */
    CONFIRMED,

    /** 底座未安装/未接线/无法确认（默认降级）。 */
    UNAVAILABLE,

    /** 已发起但执行失败。 */
    FAILED,
}

/** 按域名请求底座阻断的控制器（A5-1）。 */
interface DomainBlockController {
    suspend fun blockDomain(packageName: String, domain: String): DomainBlockOutcome
}

/** 无底座/未接线时的默认实现：诚实返回 [DomainBlockOutcome.UNAVAILABLE]，不伪造。 */
class UnavailableDomainBlockController : DomainBlockController {
    override suspend fun blockDomain(packageName: String, domain: String): DomainBlockOutcome =
        DomainBlockOutcome.UNAVAILABLE
}

/**
 * v2.0 阶段 1 起，真实实现为自研底座的 [com.causalguard.data.network.selfhosted.SelfHostedDomainBlockController]：
 * 不再跨进程请求第三方（TrackerControl）底座，改为写入进程内阻断名单由自研 DNS 代理生效。
 */
