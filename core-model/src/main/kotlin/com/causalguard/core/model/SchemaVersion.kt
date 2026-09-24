package com.causalguard.core.model

/**
 * 事件契约版本。
 *
 * 与 `docs/09-event-contract.md` 的 `schemaVersion` 一一对应；
 * 不兼容变更时递增次版本，并同步 docs/07、docs/08 与 fixture。
 */
object SchemaVersion {
    const val CURRENT: String = "0.1"
}
