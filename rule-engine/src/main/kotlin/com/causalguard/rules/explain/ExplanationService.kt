package com.causalguard.rules.explain

import kotlinx.coroutines.CancellationException

/** B6-3：解释文本来源。 */
enum class ExplanationSource {
    /** 本地确定性模板（兜底，始终可用）。 */
    LOCAL_TEMPLATE,

    /** 在线 AI 增强（已通过本地事实校验）。 */
    AI_ENHANCED,
}

/** B6-3：在线 AI 处理的最终状态，用于审计与展示（docs/11 §6）。 */
enum class ExplanationAiStatus {
    /** 未配置或 Provider 不可用，未发起网络。 */
    UNAVAILABLE,

    /** 模型输出通过本地事实校验，采用 AI 增强文本。 */
    SUCCEEDED,

    /** 模型输出未通过本地事实校验，已回退本地模板。 */
    REJECTED,

    /** 请求超时/网络错误/解析失败，已回退本地模板。 */
    FAILED,
}

/**
 * B6-3：解释结果。无论在线 AI 是否可用，[text] 始终是可直接展示的可用文本。
 */
data class ExplanationResult(
    val text: ExplanationText,
    val source: ExplanationSource,
    val aiStatus: ExplanationAiStatus,
    /** 实际模型名称；未发起在线调用时为 null。 */
    val modelName: String? = null,
    /** 审计用：实际发送的字段白名单。 */
    val inputFields: List<String> = emptyList(),
)

/**
 * B6-3：解释编排（docs/11）。
 *
 * 严格顺序：
 * 1. 先用 [LocalExplanationProvider] 渲染确定性本地兜底（始终可用）；
 * 2. 若注入了可用的 [AiExplanationProvider]，经 [AiExplanationRequest.from] 投影白名单后请求；
 * 3. 模型输出一律经 [ExplanationFactValidator] 本地事实校验，通过才采用，否则回退本地模板。
 *
 * 超时/异常/未配置/校验不通过都只回退本地模板，绝不抛出到调用方，保证 P0 断网可用。
 */
class ExplanationService(
    private val localProvider: LocalExplanationProvider = LocalExplanationProvider(),
    private val validator: ExplanationFactValidator = ExplanationFactValidator(),
    private val aiProvider: AiExplanationProvider? = null,
) {

    /** 在线 AI 是否实际可用（已配置且 Provider 可用）。 */
    val isAiAvailable: Boolean
        get() = aiProvider?.isAvailable == true

    suspend fun explain(context: ExplanationContext): ExplanationResult {
        val provider = aiProvider
        if (provider == null || !provider.isAvailable) {
            return localResult(context, ExplanationAiStatus.UNAVAILABLE)
        }

        val response = try {
            provider.explain(AiExplanationRequest.from(context))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return localResult(context, ExplanationAiStatus.FAILED, provider.modelName)
        } ?: return localResult(context, ExplanationAiStatus.FAILED, provider.modelName)

        return when (val validation = validator.validate(context, response)) {
            is ExplanationValidation.Accepted -> ExplanationResult(
                text = validation.text,
                source = ExplanationSource.AI_ENHANCED,
                aiStatus = ExplanationAiStatus.SUCCEEDED,
                modelName = provider.modelName,
                inputFields = AiExplanationRequest.WHITELIST_FIELDS,
            )

            is ExplanationValidation.Rejected -> localResult(
                context = context,
                status = ExplanationAiStatus.REJECTED,
                modelName = provider.modelName,
            )
        }
    }

    private fun localResult(
        context: ExplanationContext,
        status: ExplanationAiStatus,
        modelName: String? = null,
    ): ExplanationResult = ExplanationResult(
        text = localProvider.render(context),
        source = ExplanationSource.LOCAL_TEMPLATE,
        aiStatus = status,
        modelName = modelName,
        inputFields = emptyList(),
    )
}
