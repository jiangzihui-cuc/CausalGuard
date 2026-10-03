package com.causalguard.mitigation

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.MitigationAction
import com.causalguard.core.model.MitigationExecution
import com.causalguard.core.model.MitigationExecutor
import com.causalguard.core.model.MitigationRecord
import com.causalguard.core.model.MitigationRepository
import com.causalguard.core.model.MitigationRequest
import com.causalguard.core.model.MitigationStatus
import com.causalguard.core.model.NetworkObservation
import com.causalguard.core.model.NetworkObservationRepository

/**
 * A5-1 真实处置执行器：把 B 的 `MitigationRequest` 落成真实动作 + 持久化记录。
 *
 * 分派（A5-2）：
 * - `BLOCK_DOMAIN`：经 [DomainBlockController] 请求底座阻断，仅回执确认才 `EXECUTED`；
 * - `OPEN_SETTINGS`：经 [AppSettingsLauncher] 跳转系统页（A5-3）；
 * - `BLOCK_APP`：P0 明确 `UNSUPPORTED`（待 P1）；
 * - `NONE`：无动作。
 *
 * 持久化（A5-4）：对 `BLOCK_DOMAIN`/`OPEN_SETTINGS` 记录 `MitigationRecord`，
 * 写入处置前窗口快照与 `observationEnd`（观察窗口结束），`postResult` 初始 `unknown`，
 * 由 B 的 `RecheckComparator` 复查后通过
 * [MitigationRepository.updateOutcome] 回填。`NONE`/`BLOCK_APP` 不落库。
 *
 * 诚实原则：`UNAVAILABLE`/`UNSUPPORTED`/`FAILED` 一律如实上报，绝不改写为成功。
 */
class DeviceMitigationExecutor(
    private val observationRepository: NetworkObservationRepository,
    private val mitigationRepository: MitigationRepository,
    private val domainBlockController: DomainBlockController,
    private val appSettingsLauncher: AppSettingsLauncher,
    private val clock: () -> Long = System::currentTimeMillis,
) : MitigationExecutor {

    override suspend fun execute(request: MitigationRequest): MitigationExecution =
        when (request.action) {
            MitigationAction.NONE -> unsupported(request, "无可执行的处置动作")
            MitigationAction.BLOCK_APP -> unsupported(request, "P0 不支持 App 级阻断，待 P1")
            MitigationAction.OPEN_SETTINGS -> openSettings(request)
            MitigationAction.BLOCK_DOMAIN -> blockDomain(request)
        }

    private fun unsupported(request: MitigationRequest, message: String): MitigationExecution =
        MitigationExecution(
            status = MitigationStatus.UNSUPPORTED,
            action = request.action,
            packageName = request.packageName,
            target = request.target,
            message = message,
        )

    private suspend fun openSettings(request: MitigationRequest): MitigationExecution {
        val now = clock()
        val windowMs = request.observationWindowMs.coerceAtLeast(0L)
        val observationEnd = now + windowMs
        val recordId = persist(request, now, target = request.target, snapshotDomain = null, observationEnd, windowMs)
        return try {
            appSettingsLauncher.open(request.packageName)
            MitigationExecution(
                status = MitigationStatus.EXECUTED,
                action = request.action,
                packageName = request.packageName,
                target = request.target,
                message = "已打开系统应用设置页",
                recordId = recordId,
                observationEnd = observationEnd,
            )
        } catch (throwable: Throwable) {
            MitigationExecution(
                status = MitigationStatus.FAILED,
                action = request.action,
                packageName = request.packageName,
                target = request.target,
                message = "打开系统设置失败: ${throwable.message}",
                recordId = recordId,
                observationEnd = observationEnd,
            )
        }
    }

    private suspend fun blockDomain(request: MitigationRequest): MitigationExecution {
        val domain = request.target?.takeIf { it.isNotBlank() }
            ?: return unsupported(request, "缺少可阻断的域名目标")
        val now = clock()
        val windowMs = request.observationWindowMs.coerceAtLeast(0L)
        val observationEnd = now + windowMs
        val recordId = persist(request, now, target = domain, snapshotDomain = domain, observationEnd, windowMs)
        val status = when (domainBlockController.blockDomain(request.packageName, domain)) {
            DomainBlockOutcome.CONFIRMED -> MitigationStatus.EXECUTED
            DomainBlockOutcome.FAILED -> MitigationStatus.FAILED
            DomainBlockOutcome.UNAVAILABLE -> MitigationStatus.UNAVAILABLE
        }
        val message = when (status) {
            MitigationStatus.EXECUTED -> "底座已确认阻断域名 $domain"
            MitigationStatus.FAILED -> "域名阻断执行失败: $domain"
            else -> "域名阻断能力不可用，未执行: $domain"
        }
        return MitigationExecution(
            status = status,
            action = request.action,
            packageName = request.packageName,
            target = domain,
            message = message,
            recordId = recordId,
            observationEnd = observationEnd,
        )
    }

    private suspend fun persist(
        request: MitigationRequest,
        now: Long,
        target: String?,
        snapshotDomain: String?,
        observationEnd: Long,
        windowMs: Long,
    ): Long {
        val pre = observationRepository.observeWindow(
            packageName = request.packageName,
            domain = snapshotDomain,
            start = now - windowMs,
            end = now,
        )
        return mitigationRepository.record(
            MitigationRecord(
                packageName = request.packageName,
                recommendationId = request.recommendationId,
                action = request.action.wire,
                target = target,
                executedAt = now,
                ruleVersion = request.ruleVersion,
                preSnapshot = ContractJson.instance.encodeToString(NetworkObservation.serializer(), pre),
                postResult = "unknown",
                observationEnd = observationEnd,
            ),
        )
    }
}
