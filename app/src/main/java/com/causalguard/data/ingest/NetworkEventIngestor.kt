package com.causalguard.data.ingest

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.NetworkEvent
import com.causalguard.core.model.NetworkInfo
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.core.model.RiskCategory

/**
 * 网络事件关联入库（A4-4）：把 Adapter 产出的 [NetworkEvent] 转成事件契约
 * [PrivacyEvent]（docs/09 §2.1），补齐 `schemaVersion`、`dedupKey` 与时间窗关联后写入事件库。
 *
 * 只搬运底座已脱敏的连接元数据，不读通信内容；无法归属时诚实保留 `uid=-1`、`packageName=unknown`。
 * 前后台状态由 [ForegroundStateResolver] 提供，默认 `UNKNOWN`，A4-3 可接 UsageStats。
 */
class NetworkEventIngestor(
    private val repository: PrivacyEventRepository,
    private val foregroundStates: ForegroundStateResolver = ForegroundStateResolver { _, _ -> ForegroundState.UNKNOWN },
    private val clock: () -> Long = System::currentTimeMillis,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {

    suspend fun ingest(raw: NetworkEvent): PrivacyEvent {
        val event = toPrivacyEvent(raw)
        repository.insert(event)
        return event
    }

    suspend fun ingestAll(raws: List<NetworkEvent>): List<PrivacyEvent> {
        val events = raws.map { toPrivacyEvent(it) }
        repository.insertAll(events)
        return events
    }

    suspend fun toPrivacyEvent(raw: NetworkEvent): PrivacyEvent = PrivacyEvent(
        eventId = raw.eventId.ifBlank { fallbackEventId(raw) },
        appId = raw.packageName,
        appName = null,
        eventType = EventType.NETWORK,
        timestamp = raw.timestamp,
        foregroundState = foregroundStates.resolve(raw.packageName, raw.timestamp),
        source = raw.source,
        evidenceLevel = EvidenceLevel.E2,
        evidenceSummary = null,
        category = RiskCategory.UNKNOWN,
        isDemo = raw.source == EventSource.DEMO,
        dedupKey = buildDedupKey(raw),
        createdAt = clock(),
        network = NetworkInfo(
            protocol = raw.protocol,
            remoteIp = raw.remoteIp,
            remotePort = raw.remotePort,
            domainHint = raw.domainHint,
            uid = raw.uid,
            packageName = raw.packageName,
            bytesIn = raw.bytesIn,
            bytesOut = raw.bytesOut,
            blocked = raw.blocked,
        ),
    )

    private fun fallbackEventId(raw: NetworkEvent): String =
        "n-${raw.timestamp}-${raw.uid}-${raw.remoteIp}-${raw.remotePort}"

    private fun buildDedupKey(raw: NetworkEvent): String {
        val destination = "${raw.remoteIp}:${raw.remotePort}"
        val bucket = raw.timestamp / windowMs
        return "${raw.packageName}|network|$destination|$bucket"
    }

    companion object {
        /** docs/09 §4：默认聚合时间窗 60 秒。 */
        const val DEFAULT_WINDOW_MS: Long = 60_000L
    }
}

/** 前后台状态解析（可由 UsageContextRepository.lastState 实现）。 */
fun interface ForegroundStateResolver {
    suspend fun resolve(packageName: String, timestamp: Long): ForegroundState
}
