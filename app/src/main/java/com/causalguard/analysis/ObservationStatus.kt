package com.causalguard.analysis

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.tracker.DomainNormalizer

enum class EventProvenance(val label: String) {
    LIVE_OBSERVED("真实观测"),
    SANDBOX_GROUND_TRUTH("演示真值"),
    FIXTURE("离线 Fixture"),
    UNKNOWN("来源未知"),
}

enum class ObservationIssue(val label: String) {
    USAGE_ACCESS_UNAVAILABLE("使用上下文不可用"),
    DOMAIN_UNAVAILABLE("域名不可见"),
    APP_ATTRIBUTION_UNAVAILABLE("无法归属到具体应用"),
    NETWORK_MONITOR_PAUSED("监测已暂停"),
}

data class ObservationStatus(
    val provenance: EventProvenance,
    val issues: Set<ObservationIssue> = emptySet(),
) {
    fun hasIssue(issue: ObservationIssue): Boolean = issue in issues
}

enum class NetworkObservationState(val label: String) {
    OBSERVING("网络观测中"),
    PAUSED("监测已暂停"),
    UNAVAILABLE("网络观测暂不可用"),
}

data class RuntimeObservationStatus(
    val usageAccessAvailable: Boolean,
    val networkState: NetworkObservationState,
) {
    val issues: Set<ObservationIssue>
        get() = buildSet {
            if (!usageAccessAvailable) add(ObservationIssue.USAGE_ACCESS_UNAVAILABLE)
            if (networkState == NetworkObservationState.PAUSED) {
                add(ObservationIssue.NETWORK_MONITOR_PAUSED)
            }
        }
}

object ObservationStatusResolver {

    fun forEvent(event: PrivacyEvent): ObservationStatus {
        val provenance = when {
            event.isDemo || event.source == EventSource.DEMO -> EventProvenance.SANDBOX_GROUND_TRUTH
            event.source == EventSource.VPN ||
                event.source == EventSource.USAGE_STATS ||
                event.source == EventSource.SYSTEM_API -> EventProvenance.LIVE_OBSERVED
            event.source == EventSource.MOCK -> EventProvenance.FIXTURE
            event.source == EventSource.UNKNOWN -> EventProvenance.UNKNOWN
            else -> EventProvenance.UNKNOWN
        }

        if (event.eventType != EventType.NETWORK) {
            return ObservationStatus(provenance)
        }

        val network = event.network
        val domainAvailable = network?.domainHint?.let { DomainNormalizer.normalize(it) != null } == true
        val attributionAvailable = network?.let {
            it.uid >= 0 && it.packageName.isMeaningfulAttribution()
        } ?: false
        val issues = buildSet {
            if (!domainAvailable) add(ObservationIssue.DOMAIN_UNAVAILABLE)
            if (!attributionAvailable) add(ObservationIssue.APP_ATTRIBUTION_UNAVAILABLE)
        }
        return ObservationStatus(provenance, issues)
    }

    fun forRuntime(
        usageAccessAvailable: Boolean,
        isActive: Boolean,
        isCollecting: Boolean,
    ): RuntimeObservationStatus = RuntimeObservationStatus(
        usageAccessAvailable = usageAccessAvailable,
        networkState = when {
            !isActive -> NetworkObservationState.PAUSED
            isCollecting -> NetworkObservationState.OBSERVING
            else -> NetworkObservationState.UNAVAILABLE
        },
    )

    private fun String.isMeaningfulAttribution(): Boolean =
        isNotBlank() && !equals("unknown", ignoreCase = true)
}
