package com.causalguard.core.model

/**
 * 公共枚举（阶段 2 冻结，见 docs/09-event-contract.md 第 1.1 节）。
 *
 * 约定：
 * - `wire` 为契约 JSON 值（小写 snake_case，协议除外）；
 * - 解析未知值时统一映射为 `UNKNOWN`，不抛异常；
 * - 新增枚举值必须同步 docs/09、docs/07、docs/10。
 */

enum class EventType(val wire: String) {
    CLIPBOARD("clipboard"),
    LOCATION("location"),
    CONTACTS("contacts"),
    NETWORK("network"),
    USAGE_CONTEXT("usage_context"),
    PERMISSION("permission"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): EventType =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

enum class ForegroundState(val wire: String) {
    FOREGROUND("foreground"),
    BACKGROUND("background"),
    RECENT("recent"),
    UNUSED("unused"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): ForegroundState =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

enum class EventSource(val wire: String) {
    SYSTEM_API("system_api"),
    USAGE_STATS("usage_stats"),
    VPN("vpn"),
    DEMO("demo"),
    MOCK("mock"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): EventSource =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

enum class EvidenceLevel(val wire: String) {
    E1("E1"),
    E2("E2"),
    E3("E3"),
    E4("E4"),
    E5("E5");

    companion object {
        fun fromWire(value: String?): EvidenceLevel =
            entries.firstOrNull { it.wire == value } ?: E5
    }
}

enum class RiskCategory(val wire: String) {
    NECESSARY("necessary"),
    ANALYTICS("analytics"),
    HIGH_RISK("high_risk"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): RiskCategory =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

enum class Confidence(val wire: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        fun fromWire(value: String?): Confidence =
            entries.firstOrNull { it.wire == value } ?: LOW
    }
}

enum class NetworkProtocol(val wire: String) {
    TCP("TCP"),
    UDP("UDP"),
    ICMP("ICMP"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): NetworkProtocol =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class RiskLevel(val wire: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    companion object {
        fun fromWire(value: String?): RiskLevel =
            entries.firstOrNull { it.wire == value } ?: LOW
    }
}

enum class ScenarioMatch(val wire: String) {
    MATCH("match"),
    MATCH_WITH_CONCERN("match_with_concern"),
    MISMATCH("mismatch"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): ScenarioMatch =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}
