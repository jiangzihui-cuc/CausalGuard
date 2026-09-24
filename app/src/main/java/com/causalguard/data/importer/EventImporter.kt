package com.causalguard.data.importer

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import java.io.File
import kotlinx.serialization.builtins.ListSerializer

/**
 * 事件导入器（A3-2）：把 docs/09 契约 JSON（fixture 或导出文件）批量写入事件库。
 * 解析使用 [ContractJson]，与采集层和 fixture 共用同一套契约配置。
 */
class EventImporter(
    private val repository: PrivacyEventRepository,
) {

    suspend fun import(json: String): Int {
        val events = ContractJson.instance.decodeFromString(
            ListSerializer(PrivacyEvent.serializer()),
            json,
        )
        repository.insertAll(events)
        return events.size
    }

    suspend fun import(file: File): Int = import(file.readText())
}
