package com.causalguard.core.model

import kotlinx.serialization.json.Json

/**
 * 契约 JSON 编解码配置（docs/09-event-contract.md）。
 *
 * - `schemaVersion` 由 [SchemaVersion.CURRENT] 提供默认值，旧 fixture 未带该字段也能解析；
 * - 忽略未知字段，保证契约向后兼容；
 * - 编码时输出默认值，便于 fixture 比对与调试。
 */
object ContractJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}
