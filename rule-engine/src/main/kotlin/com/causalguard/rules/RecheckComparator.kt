package com.causalguard.rules

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.MitigationAction
import com.causalguard.core.model.MitigationRecord
import com.causalguard.core.model.MitigationStatus
import com.causalguard.core.model.NetworkObservation
import com.causalguard.core.model.NetworkRequestPresence
import kotlinx.serialization.SerializationException
import java.util.Locale

enum class RecheckOutcome {
    REDUCED,
    NO_CHANGE,
    BLOCKED,
    UNCONFIRMABLE,
}

data class RecheckResult(
    val outcome: RecheckOutcome,
    val postResultWire: String,
    val reviewNotes: String,
    val preObservation: NetworkObservation? = null,
    val postObservation: NetworkObservation? = null,
)

/** Compares two already-collected observations without performing any I/O. */
class RecheckComparator {

    fun compare(
        record: MitigationRecord,
        postObservation: NetworkObservation?,
    ): RecheckResult {
        if (record.executionStatus != MitigationStatus.EXECUTED.wire) {
            return unconfirmable(
                "executionStatus=${record.executionStatus} 不是 executed，无法确认复查结果。",
                postObservation = postObservation,
            )
        }
        if (record.action != MitigationAction.BLOCK_DOMAIN.wire) {
            return unconfirmable(
                "action=${record.action} 当前没有可比较的网络复查语义。",
                postObservation = postObservation,
            )
        }
        val observationEnd = record.observationEnd
        if (
            record.target.isNullOrBlank() ||
            observationEnd == null ||
            record.executedAt < 0L ||
            observationEnd <= record.executedAt ||
            record.packageName.isBlank()
        ) {
            return unconfirmable("处置记录缺少可比较的目标或观察窗口。", postObservation = postObservation)
        }

        val preObservation = parsePreSnapshot(record.preSnapshot)
            ?: return unconfirmable("处置前快照缺失、为空或无法解析。", postObservation = postObservation)
        if (postObservation == null) {
            return unconfirmable("处置后观察结果缺失。", preObservation = preObservation)
        }

        val invalidObservation = when {
            !hasValidCountsAndWindow(preObservation) -> "处置前观察的计数或时间窗口非法。"
            !hasValidCountsAndWindow(postObservation) -> "处置后观察的计数或时间窗口非法。"
            preObservation.packageName != record.packageName -> "处置前观察的 App 与处置记录不一致。"
            postObservation.packageName != record.packageName -> "处置后观察的 App 与处置记录不一致。"
            !sameDomain(record.target, preObservation.domain) -> "处置前观察的域名与处置目标不一致。"
            !sameDomain(record.target, postObservation.domain) -> "处置后观察的域名与处置目标不一致。"
            !hasComparableWindows(record, observationEnd, preObservation, postObservation) ->
                "前后观察窗口未对齐或长度不同，无法直接比较。"
            else -> null
        }
        if (invalidObservation != null) {
            return unconfirmable(
                invalidObservation,
                preObservation = preObservation,
                postObservation = postObservation,
            )
        }

        val preAllowed = preObservation.allowedCount
        val postAllowed = postObservation.allowedCount
        if (
            preObservation.presence == NetworkRequestPresence.ALL_BLOCKED &&
            postObservation.presence == NetworkRequestPresence.ALL_BLOCKED
        ) {
            return result(
                outcome = RecheckOutcome.NO_CHANGE,
                notes = "前后窗口均观察到全部请求被阻断，允许通过的连接数均为 $preAllowed；未观察到状态变化。",
                preObservation = preObservation,
                postObservation = postObservation,
            )
        }
        if (
            postObservation.presence == NetworkRequestPresence.ALL_BLOCKED &&
            preObservation.presence != NetworkRequestPresence.ALL_BLOCKED
        ) {
            return result(
                outcome = RecheckOutcome.BLOCKED,
                notes = "后观察窗口中有 ${postObservation.requestCount} 次连接尝试，${postObservation.blockedCount} 次均被标记为 blocked。",
                preObservation = preObservation,
                postObservation = postObservation,
            )
        }
        if (preAllowed > 0 && postAllowed < preAllowed) {
            return result(
                outcome = RecheckOutcome.REDUCED,
                notes = "观察到允许通过的连接数从 $preAllowed 降至 $postAllowed。",
                preObservation = preObservation,
                postObservation = postObservation,
            )
        }
        if (preAllowed > 0 && postAllowed == preAllowed) {
            return result(
                outcome = RecheckOutcome.NO_CHANGE,
                notes = "前后窗口允许通过的连接数均为 $preAllowed，未观察到变化。",
                preObservation = preObservation,
                postObservation = postObservation,
            )
        }
        if (postAllowed > preAllowed) {
            return unconfirmable(
                "后窗口允许连接数为 $postAllowed，高于前窗口的 $preAllowed，无法确认减少。",
                preObservation = preObservation,
                postObservation = postObservation,
            )
        }

        return unconfirmable(
            "处置前没有允许连接基线，无法确认减少。",
            preObservation = preObservation,
            postObservation = postObservation,
        )
    }

    private fun parsePreSnapshot(snapshot: String?): NetworkObservation? {
        if (snapshot.isNullOrBlank()) return null
        return try {
            ContractJson.instance.decodeFromString(NetworkObservation.serializer(), snapshot)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun hasValidCountsAndWindow(observation: NetworkObservation): Boolean =
        observation.requestCount >= 0 &&
            observation.blockedCount >= 0 &&
            observation.blockedCount <= observation.requestCount &&
            observation.windowEnd >= observation.windowStart

    private fun hasComparableWindows(
        record: MitigationRecord,
        observationEnd: Long,
        pre: NetworkObservation,
        post: NetworkObservation,
    ): Boolean {
        val preDuration = pre.windowEnd - pre.windowStart
        val postDuration = post.windowEnd - post.windowStart
        return pre.windowEnd == record.executedAt &&
            post.windowStart == record.executedAt &&
            post.windowEnd == observationEnd &&
            preDuration > 0L &&
            postDuration > 0L &&
            preDuration == postDuration
    }

    private fun sameDomain(target: String?, observed: String?): Boolean {
        val normalizedTarget = normalizeDomain(target) ?: return false
        return normalizeDomain(observed) == normalizedTarget
    }

    private fun normalizeDomain(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    private fun result(
        outcome: RecheckOutcome,
        notes: String,
        preObservation: NetworkObservation,
        postObservation: NetworkObservation,
    ): RecheckResult = RecheckResult(
        outcome = outcome,
        postResultWire = outcome.wire,
        reviewNotes = notes,
        preObservation = preObservation,
        postObservation = postObservation,
    )

    private fun unconfirmable(
        notes: String,
        preObservation: NetworkObservation? = null,
        postObservation: NetworkObservation? = null,
    ): RecheckResult = RecheckResult(
        outcome = RecheckOutcome.UNCONFIRMABLE,
        postResultWire = RecheckOutcome.UNCONFIRMABLE.wire,
        reviewNotes = notes,
        preObservation = preObservation,
        postObservation = postObservation,
    )

    private val RecheckOutcome.wire: String
        get() = when (this) {
            RecheckOutcome.REDUCED -> "reduced"
            RecheckOutcome.NO_CHANGE -> "no_change"
            RecheckOutcome.BLOCKED -> "blocked"
            RecheckOutcome.UNCONFIRMABLE -> "unknown"
        }
}
