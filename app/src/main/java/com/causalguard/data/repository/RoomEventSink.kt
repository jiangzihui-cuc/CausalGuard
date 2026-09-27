package com.causalguard.data.repository

import com.causalguard.core.model.EventSink
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository

/**
 * 唯一事件写入口（docs/09 §3，M4）：把契约事件幂等写入事件库。
 * 采集层、Adapter 与导入器只依赖 [EventSink]，不直接接触 Room。
 */
class RoomEventSink(
    private val repository: PrivacyEventRepository,
) : EventSink {

    override suspend fun emit(event: PrivacyEvent) = repository.insert(event)

    override suspend fun emitAll(events: List<PrivacyEvent>) = repository.insertAll(events)
}
