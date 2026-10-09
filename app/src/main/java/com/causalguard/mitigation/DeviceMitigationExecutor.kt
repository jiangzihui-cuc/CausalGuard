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
 * 持久化（A5-4，PR #18 review 修正）：**先执行动作、再落库**，并显式持久化
 * [MitigationRecord.executionStatus]。`preSnapshot` 仍在动作前采集（处置前窗口快照），
 * 但 `executionStatus` 记录真实结论——`EXECUTED`/`FAILED`/`UNAVAILABLE` 严格区分，
 * 绝不把失败/不可用伪装成已执行。`UNSUPPORTED`/`NONE` 不落库（无动作发生）。
 * `postResult` 初始 `unknown`，由 B 的 `RecheckComparator` 复查后通过
 * [MitigationRepository.updateOutcome] 回填。
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
        // 处置前快照必须在动作前采集。
        val preSnapshot = capturePreSnapshot(request, now, snapshotDomain = null, windowMs)
        return try {
            appSettingsLauncher.open(request.packageName)
            val status = MitigationStatus.EXECUTED
            val recordId = persist(request, now, target = request.target, preSnapshot, observationEnd, status)
            MitigationExecution(
                status = status,
                action = request.action,
                packageName = request.packageName,
                target = request.target,
                message = "已打开系统应用设置页",
                recordId = recordId,
                observationEnd = observationEnd,
            )
        } catch (throwable: Throwable) {
            val status = MitigationStatus.FAILED
            val recordId = persist(request, now, target = request.target, preSnapshot, observationEnd, status)
            MitigationExecution(
                status = status,
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
        // 处置前快照必须在动作前采集。
        val preSnapshot = capturePreSnapshot(request, now, snapshotDomain = domain, windowMs)
        val status = when (domainBlockController.blockDomain(request.packageName, domain)) {
            DomainBlockOutcome.CONFIRMED -> MitigationStatus.EXECUTED
            DomainBlockOutcome.FAILED -> MitigationStatus.FAILED
            DomainBlockOutcome.UNAVAILABLE -> MitigationStatus.UNAVAILABLE
        }
        val recordId = persist(request, now, target = domain, preSnapshot, observationEnd, status)
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

    private suspend fun capturePreSnapshot(
        request: MitigationRequest,
        now: Long,
        snapshotDomain: String?,
        windowMs: Long,
    ): String {
        val pre = observationRepository.observeWindow(
            packageName = request.packageName,
            domain = snapshotDomain,
            start = now - windowMs,
            end = now,
        )
        return ContractJson.instance.encodeToString(NetworkObservation.serializer(), pre)
    }

    private suspend fun persist(
        request: MitigationRequest,
        now: Long,
        target: String?,
        preSnapshot: String,
        observationEnd: Long,
        status: MitigationStatus,
    ): Long = mitigationRepository.record(
        MitigationRecord(
            packageName = request.packageName,
            recommendationId = request.recommendationId,
            action = request.action.wire,
            target = target,
            executedAt = now,
            ruleVersion = request.ruleVersion,
            preSnapshot = preSnapshot,
            executionStatus = status.wire,
            postResult = "unknown",
            observationEnd = observationEnd,
        ),
    )
}
