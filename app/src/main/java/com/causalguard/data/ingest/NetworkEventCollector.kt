package com.causalguard.data.ingest

import com.causalguard.core.model.NetworkEventSource
import com.causalguard.core.model.PrivacyEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * 网络事件端到端采集器（A4-4）：把 [NetworkEventSource] 的事件持续转成契约事件并写入事件库。
 *
 * ```text
 * NetworkEventSource.events() → NetworkEventIngestor.ingest() → PrivacyEventRepository
 * ```
 *
 * 与具体来源解耦：真实底座用 [com.causalguard.data.network.trackercontrol.TrackerControlEventSource]，
 * 无 VPN 时用 [com.causalguard.data.network.ReplayNetworkEventSource]（fixture 回放），下游规则与 UI 不变。
 *
 * [start]/[stop] 成对调用；重复 [start] 不重复订阅，[stop] 先取消收集再停来源，避免泄漏。
 */
class NetworkEventCollector(
    private val source: NetworkEventSource,
    private val ingestor: NetworkEventIngestor,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** 每成功入库一条事件后的观察回调（默认 no-op，便于真机用 logcat 观察而不污染纯 JVM 测试）。 */
    private val onIngested: (PrivacyEvent) -> Unit = {},
) {

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    /** 是否正在采集。 */
    val isCollecting: Boolean
        get() = job?.isActive == true

    suspend fun start() {
        if (isCollecting) return
        source.start()
        val collectorScope = CoroutineScope(SupervisorJob() + dispatcher)
        scope = collectorScope
        job = collectorScope.launch {
            source.events().collect { raw -> onIngested(ingestor.ingest(raw)) }
        }
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        scope?.cancel()
        scope = null
        source.stop()
    }
}
