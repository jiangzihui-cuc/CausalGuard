package com.causalguard.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 公共枚举（阶段 2 冻结，见 docs/09-event-contract.md 第 1.1 节）。
 *
 * 约定：
 * - `wire` 为契约 JSON 值（小写 snake_case，协议除外）；
 * - 解析未知值时统一映射为 `UNKNOWN`，不抛异常；
 * - 新增枚举值必须同步 docs/09、docs/07、docs/10。
 */

@Serializable
enum class EventType(val wire: String) {
    @SerialName("clipboard")
    CLIPBOARD("clipboard"),

    @SerialName("location")
    LOCATION("location"),

    @SerialName("contacts")
    CONTACTS("contacts"),

    @SerialName("network")
    NETWORK("network"),

    @SerialName("usage_context")
    USAGE_CONTEXT("usage_context"),

    @SerialName("permission")
    PERMISSION("permission"),

    @SerialName("unknown")
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): EventType =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

@Serializable
enum class ForegroundState(val wire: String) {
    @SerialName("foreground")
    FOREGROUND("foreground"),

    @SerialName("background")
    BACKGROUND("background"),

    @SerialName("recent")
    RECENT("recent"),

    @SerialName("unused")
    UNUSED("unused"),

    @SerialName("unknown")
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): ForegroundState =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

@Serializable
enum class EventSource(val wire: String) {
    @SerialName("system_api")
    SYSTEM_API("system_api"),

    @SerialName("usage_stats")
    USAGE_STATS("usage_stats"),

    @SerialName("vpn")
    VPN("vpn"),

    @SerialName("demo")
    DEMO("demo"),

    @SerialName("mock")
    MOCK("mock"),

    @SerialName("unknown")
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): EventSource =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

@Serializable
enum class EvidenceLevel(val wire: String) {
    @SerialName("E1")
    E1("E1"),

    @SerialName("E2")
    E2("E2"),

    @SerialName("E3")
    E3("E3"),

    @SerialName("E4")
    E4("E4"),

    @SerialName("E5")
    E5("E5");

    companion object {
        fun fromWire(value: String?): EvidenceLevel =
            entries.firstOrNull { it.wire == value } ?: E5
    }
}

@Serializable
enum class RiskCategory(val wire: String) {
    @SerialName("necessary")
    NECESSARY("necessary"),

    @SerialName("analytics")
    ANALYTICS("analytics"),

    @SerialName("high_risk")
    HIGH_RISK("high_risk"),

    @SerialName("unknown")
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): RiskCategory =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

@Serializable
enum class Confidence(val wire: String) {
    @SerialName("low")
    LOW("low"),

    @SerialName("medium")
    MEDIUM("medium"),

    @SerialName("high")
    HIGH("high");

    companion object {
        fun fromWire(value: String?): Confidence =
            entries.firstOrNull { it.wire == value } ?: LOW
    }
}

@Serializable
enum class NetworkProtocol(val wire: String) {
    @SerialName("TCP")
    TCP("TCP"),

    @SerialName("UDP")
    UDP("UDP"),

    @SerialName("ICMP")
    ICMP("ICMP"),

    @SerialName("unknown")
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): NetworkProtocol =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

@Serializable
enum class RiskLevel(val wire: String) {
    @SerialName("low")
    LOW("low"),

    @SerialName("medium")
    MEDIUM("medium"),

    @SerialName("high")
    HIGH("high"),

    @SerialName("critical")
    CRITICAL("critical");

    companion object {
        fun fromWire(value: String?): RiskLevel =
            entries.firstOrNull { it.wire == value } ?: LOW
    }
}

@Serializable
enum class ScenarioMatch(val wire: String) {
    @SerialName("match")
    MATCH("match"),

    @SerialName("match_with_concern")
    MATCH_WITH_CONCERN("match_with_concern"),

    @SerialName("mismatch")
    MISMATCH("mismatch"),

    @SerialName("unknown")
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): ScenarioMatch =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}
