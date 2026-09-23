package io.causalguard.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleEvaluatorTest {
    private val evaluator = RuleEvaluator(FixtureRules.rules)

    @Test
    fun `background clipboard matches sensitive access and correlated network rules`() {
        val event = FixtureEvents.calculatorClipboard
        val result = evaluator.assess(
            event,
            EvaluationContext(
                appProfile = AppProfile("com.demo.calculator", "calculator"),
                relatedEvents = listOf(FixtureEvents.calculatorNetwork)
            )
        )

        assertEquals(listOf("R-007", "R-002"), result.matchedRules)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertEquals(RiskCategory.HIGH_RISK, result.category)
        assertEquals("limit_background_network", result.recommendation.action)
    }

    @Test
    fun `background contacts matches sensitive access rule`() {
        val result = evaluator.assess(FixtureEvents.notesContacts)

        assertEquals(listOf("R-002"), result.matchedRules)
        assertEquals(RiskLevel.MEDIUM, result.riskLevel)
        assertEquals(RiskCategory.HIGH_RISK, result.category)
        assertEquals("review_permission", result.recommendation.action)
    }

    @Test
    fun `unused app network matches tracker and long-unused rules`() {
        val result = evaluator.assess(FixtureEvents.flashlightNetwork)

        assertEquals(listOf("R-006", "R-005"), result.matchedRules)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertEquals(RiskCategory.ANALYTICS, result.category)
        assertEquals("limit_background_network", result.recommendation.action)
    }

    @Test
    fun `post-revocation background location matches permission rule`() {
        val result = evaluator.assess(
            FixtureEvents.weatherLocation,
            EvaluationContext(priorEvents = listOf(FixtureEvents.weatherPermission))
        )

        assertEquals(listOf("R-002", "R-009"), result.matchedRules)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertEquals(RiskCategory.HIGH_RISK, result.category)
        assertEquals("review_permission", result.recommendation.action)
    }

    @Test
    fun `foreground location is normal low risk`() {
        val result = evaluator.assess(
            FixtureEvents.mapLocation,
            EvaluationContext(
                appProfile = AppProfile("com.demo.map", "map"),
                scenarioMatch = ScenarioMatch.MATCH
            )
        )

        assertEquals(listOf("R-001"), result.matchedRules)
        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertEquals(RiskCategory.NECESSARY, result.category)
        assertEquals("none", result.recommendation.action)
        assertFalse(result.shouldShowUnknownDegradation)
    }

    @Test
    fun `unknown network stays low confidence and has no deterministic action`() {
        val result = evaluator.assess(FixtureEvents.unknownNetwork)

        assertEquals(listOf("R-008", "R-010"), result.matchedRules)
        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertEquals(RiskCategory.UNKNOWN, result.category)
        assertEquals(ScenarioMatch.UNKNOWN, result.scenarioMatch)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals("none", result.recommendation.action)
        assertTrue(result.shouldShowUnknownDegradation)
    }

    @Test
    fun `multi-rule matching is priority sorted and stable`() {
        val first = evaluator.assess(
            FixtureEvents.calculatorNetwork,
            EvaluationContext(
                appProfile = AppProfile("com.demo.calculator", "calculator"),
                relatedEvents = listOf(FixtureEvents.calculatorClipboard)
            )
        )
        val second = evaluator.assess(
            FixtureEvents.calculatorNetwork,
            EvaluationContext(
                appProfile = AppProfile("com.demo.calculator", "calculator"),
                relatedEvents = listOf(FixtureEvents.calculatorClipboard)
            )
        )

        assertEquals(listOf("R-007", "R-003", "R-005"), first.matchedRules)
        assertEquals(first.matchedRules, second.matchedRules)
        assertEquals(RiskLevel.HIGH, first.riskLevel)
    }

    @Test
    fun `event with no matching rule returns safe low risk result`() {
        val result = evaluator.assess(FixtureEvents.readerUsage)

        assertEquals(emptyList(), result.matchedRules)
        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertEquals(RiskCategory.UNKNOWN, result.category)
        assertEquals("none", result.recommendation.action)
    }
}

private object FixtureEvents {
    val mapLocation = PrivacyEvent(
        eventId = "e-20260921-0001",
        appId = "com.demo.map",
        appName = "Demo Map",
        eventType = EventType.LOCATION,
        timestamp = 1789920000000,
        foregroundState = ForegroundState.FOREGROUND,
        source = "demo",
        evidenceLevel = EvidenceLevel.E4,
        evidenceSummary = "前台导航时访问位置，调用 1 次，未保存坐标",
        isDemo = true,
        dedupKey = "com.demo.map|location|foreground|1789920000"
    )

    val calculatorClipboard = PrivacyEvent(
        eventId = "e-20260921-0003",
        appId = "com.demo.calculator",
        appName = "Demo Calculator",
        eventType = EventType.CLIPBOARD,
        timestamp = 1789920300000,
        foregroundState = ForegroundState.BACKGROUND,
        source = "demo",
        evidenceLevel = EvidenceLevel.E4,
        evidenceSummary = "后台读取剪贴板，长度 8，仅记录长度与事件类型",
        isDemo = true,
        dedupKey = "com.demo.calculator|clipboard|background|1789920300"
    )

    val calculatorNetwork = PrivacyEvent(
        eventId = "e-20260921-0004",
        appId = "com.demo.calculator",
        appName = "Demo Calculator",
        eventType = EventType.NETWORK,
        timestamp = 1789920303000,
        foregroundState = ForegroundState.BACKGROUND,
        source = "vpn",
        evidenceLevel = EvidenceLevel.E2,
        evidenceSummary = "后台连接分析域名，未读取请求内容",
        isDemo = false,
        dedupKey = "com.demo.calculator|network|background|1789920303",
        network = NetworkEvent(
            protocol = "TCP",
            remoteIp = "203.0.113.30",
            remotePort = 443,
            domainHint = "analytics.example.test",
            uid = 10123,
            packageName = "com.demo.calculator",
            bytesIn = 1536,
            bytesOut = 512,
            blocked = false
        )
    )

    val flashlightNetwork = PrivacyEvent(
        eventId = "e-20260921-0005",
        appId = "com.demo.flashlight",
        appName = "Demo Flashlight",
        eventType = EventType.NETWORK,
        timestamp = 1789920600000,
        foregroundState = ForegroundState.UNUSED,
        source = "vpn",
        evidenceLevel = EvidenceLevel.E2,
        evidenceSummary = "长期未使用状态下连接追踪域名，未读取通信内容",
        isDemo = false,
        dedupKey = "com.demo.flashlight|network|unused|1789920600",
        network = NetworkEvent(
            protocol = "TCP",
            remoteIp = "203.0.113.40",
            remotePort = 443,
            domainHint = "tracker.example.test",
            uid = 10131,
            packageName = "com.demo.flashlight",
            bytesIn = 512,
            bytesOut = 384,
            blocked = false
        )
    )

    val unknownNetwork = PrivacyEvent(
        eventId = "e-20260921-0006",
        appId = "unknown",
        appName = "Unknown App",
        eventType = EventType.NETWORK,
        timestamp = 1789920900000,
        foregroundState = ForegroundState.UNKNOWN,
        source = "vpn",
        evidenceLevel = EvidenceLevel.E5,
        evidenceSummary = "观测到网络连接，但 UID 与包名无法可靠归属",
        isDemo = false,
        dedupKey = "unknown|network|unknown|1789920900",
        network = NetworkEvent(
            protocol = "UDP",
            remoteIp = "203.0.113.50",
            remotePort = 443,
            domainHint = "",
            uid = -1,
            packageName = "unknown",
            bytesIn = 0,
            bytesOut = 128,
            blocked = false
        )
    )

    val notesContacts = PrivacyEvent(
        eventId = "e-20260921-0007",
        appId = "com.demo.notes",
        appName = "Demo Notes",
        eventType = EventType.CONTACTS,
        timestamp = 1789921200000,
        foregroundState = ForegroundState.BACKGROUND,
        source = "demo",
        evidenceLevel = EvidenceLevel.E4,
        evidenceSummary = "后台访问通讯录，读取 3 条记录，仅保存计数",
        isDemo = true,
        dedupKey = "com.demo.notes|contacts|background|1789921200"
    )

    val weatherPermission = PrivacyEvent(
        eventId = "e-20260921-0008",
        appId = "com.demo.weather",
        appName = "Demo Weather",
        eventType = EventType.PERMISSION,
        timestamp = 1789921500000,
        foregroundState = ForegroundState.RECENT,
        source = "system_api",
        evidenceLevel = EvidenceLevel.E1,
        evidenceSummary = "位置权限状态由 granted 变为 revoked",
        isDemo = false,
        dedupKey = "com.demo.weather|permission|recent|1789921500"
    )

    val weatherLocation = PrivacyEvent(
        eventId = "e-20260921-0009",
        appId = "com.demo.weather",
        appName = "Demo Weather",
        eventType = EventType.LOCATION,
        timestamp = 1789921510000,
        foregroundState = ForegroundState.BACKGROUND,
        source = "demo",
        evidenceLevel = EvidenceLevel.E4,
        evidenceSummary = "权限撤销后仍触发后台位置访问演示事件，调用 1 次，未保存坐标",
        isDemo = true,
        dedupKey = "com.demo.weather|location|background|1789921510"
    )

    val readerUsage = PrivacyEvent(
        eventId = "e-20260921-0010",
        appId = "com.demo.reader",
        appName = "Demo Reader",
        eventType = EventType.USAGE_CONTEXT,
        timestamp = 1789921800000,
        foregroundState = ForegroundState.RECENT,
        source = "usage_stats",
        evidenceLevel = EvidenceLevel.E2,
        evidenceSummary = "UsageStats 显示应用近期使用后进入后台，screenOn=true",
        isDemo = false,
        dedupKey = "com.demo.reader|usage_context|recent|1789921800",
        usage = UsageContextEvent("com.demo.reader", ForegroundState.RECENT, screenOn = true)
    )
}

private object FixtureRules {
    val rules = listOf(
        RiskRule(
            id = "R-001",
            ruleVersion = "rules-v0.1",
            name = "前台合理访问",
            priority = 10,
            condition = RuleCondition(
                eventTypes = setOf(EventType.LOCATION, EventType.NETWORK),
                foregroundStates = setOf(ForegroundState.FOREGROUND),
                scenarioMatchRequired = ScenarioMatch.MATCH
            ),
            output = RuleOutput(RiskLevel.LOW, RiskCategory.NECESSARY, ScenarioMatch.MATCH, Confidence.MEDIUM),
            explanationBoundary = "该行为与前台使用场景匹配；仍不读取通信内容或敏感原文。",
            recommendation = Recommendation("none", "无需处置"),
            degradation = Degradation(false, "not_applicable")
        ),
        RiskRule(
            id = "R-002",
            ruleVersion = "rules-v0.1",
            name = "后台访问敏感数据",
            priority = 80,
            condition = RuleCondition(
                eventTypes = setOf(EventType.CLIPBOARD, EventType.LOCATION, EventType.CONTACTS),
                foregroundStates = setOf(ForegroundState.BACKGROUND)
            ),
            output = RuleOutput(RiskLevel.MEDIUM, RiskCategory.HIGH_RISK, ScenarioMatch.MISMATCH, Confidence.MEDIUM),
            explanationBoundary = "只能说明发生了后台敏感访问，不能据此确认数据泄露。",
            recommendation = Recommendation("review_permission", "检查权限和后台活动"),
            degradation = Degradation(false, "not_applicable")
        ),
        RiskRule(
            id = "R-003",
            ruleVersion = "rules-v0.1",
            name = "场景不匹配后台联网",
            priority = 75,
            condition = RuleCondition(
                eventTypes = setOf(EventType.NETWORK),
                foregroundStates = setOf(ForegroundState.BACKGROUND),
                sceneTypes = setOf("calculator")
            ),
            output = RuleOutput(RiskLevel.HIGH, RiskCategory.HIGH_RISK, ScenarioMatch.MISMATCH, Confidence.MEDIUM),
            explanationBoundary = "只能确认后台联网与场景不匹配，不能确认请求内容或数据泄露。",
            recommendation = Recommendation("limit_background_network", "限制后台网络"),
            degradation = Degradation(false, "not_applicable")
        ),
        RiskRule(
            id = "R-005",
            ruleVersion = "rules-v0.1",
            name = "已知分析追踪器",
            priority = 70,
            condition = RuleCondition(
                eventTypes = setOf(EventType.NETWORK),
                domainHints = setOf("analytics.example.test", "tracker.example.test")
            ),
            output = RuleOutput(RiskLevel.HIGH, RiskCategory.ANALYTICS, ScenarioMatch.MISMATCH, Confidence.MEDIUM),
            explanationBoundary = "域名命中公开或演示分类只能说明服务类别，不代表已发生隐私泄露。",
            recommendation = Recommendation("limit_background_network", "限制分析追踪连接"),
            degradation = Degradation(false, "If domainHint is empty, do not infer tracker category from IP alone.")
        ),
        RiskRule(
            id = "R-006",
            ruleVersion = "rules-v0.1",
            name = "长期未使用仍联网",
            priority = 75,
            condition = RuleCondition(
                eventTypes = setOf(EventType.NETWORK),
                foregroundStates = setOf(ForegroundState.UNUSED)
            ),
            output = RuleOutput(RiskLevel.HIGH, RiskCategory.ANALYTICS, ScenarioMatch.MISMATCH, Confidence.MEDIUM),
            explanationBoundary = "只能说明长期未使用状态下仍有联网元数据，不能确认请求内容。",
            recommendation = Recommendation("limit_background_network", "限制长期未使用应用联网"),
            degradation = Degradation(false, "If last-used context is unavailable, do not apply this rule.")
        ),
        RiskRule(
            id = "R-007",
            ruleVersion = "rules-v0.1",
            name = "敏感数据伴随网络",
            priority = 85,
            condition = RuleCondition(
                eventTypes = setOf(EventType.CLIPBOARD, EventType.NETWORK),
                timeWindowMs = 60_000,
                relatedEventTypes = setOf(EventType.CLIPBOARD, EventType.LOCATION, EventType.CONTACTS, EventType.NETWORK)
            ),
            output = RuleOutput(RiskLevel.HIGH, RiskCategory.HIGH_RISK, ScenarioMatch.MISMATCH, Confidence.MEDIUM),
            explanationBoundary = "时间相关只能作为伴随证据，不能证明敏感内容被发送。",
            recommendation = Recommendation("limit_background_network", "结合敏感访问检查联网行为"),
            degradation = Degradation(false, "If app attribution is unknown, do not correlate sensitive access and network traffic.")
        ),
        RiskRule(
            id = "R-008",
            ruleVersion = "rules-v0.1",
            name = "无法归属网络",
            priority = 40,
            condition = RuleCondition(
                eventTypes = setOf(EventType.NETWORK),
                packageName = "unknown",
                uid = -1
            ),
            output = RuleOutput(RiskLevel.LOW, RiskCategory.UNKNOWN, ScenarioMatch.UNKNOWN, Confidence.LOW),
            explanationBoundary = "无法可靠归属到具体 App，不得生成确定性归因、数据流向结论或处置建议。",
            recommendation = Recommendation("none", "仅展示未知网络事件"),
            degradation = Degradation(true, "Show unknown attribution and avoid deterministic mitigation.")
        ),
        RiskRule(
            id = "R-009",
            ruleVersion = "rules-v0.1",
            name = "权限已撤销仍观测",
            priority = 80,
            condition = RuleCondition(
                eventTypes = setOf(EventType.LOCATION),
                foregroundStates = setOf(ForegroundState.BACKGROUND),
                requiresPriorEvents = listOf(
                    PriorEventCondition(EventType.PERMISSION, evidenceSummaryContains = "revoked")
                )
            ),
            output = RuleOutput(RiskLevel.HIGH, RiskCategory.HIGH_RISK, ScenarioMatch.MISMATCH, Confidence.MEDIUM),
            explanationBoundary = "演示事件说明权限撤销后的异常场景，但仍不能声称真实系统权限被绕过。",
            recommendation = Recommendation("review_permission", "复查权限与演示状态"),
            degradation = Degradation(false, "If prior permission state is missing, do not apply this rule.")
        ),
        RiskRule(
            id = "R-010",
            ruleVersion = "rules-v0.1",
            name = "证据不足",
            priority = 30,
            condition = RuleCondition(evidenceLevels = setOf(EvidenceLevel.E5)),
            output = RuleOutput(RiskLevel.LOW, RiskCategory.UNKNOWN, ScenarioMatch.UNKNOWN, Confidence.LOW),
            explanationBoundary = "关键字段缺失，只能展示无法确认结论。",
            recommendation = Recommendation("none", "无法确认，暂不处置"),
            degradation = Degradation(true, "Keep the event visible as unknown evidence without app attribution.")
        )
    )
}
