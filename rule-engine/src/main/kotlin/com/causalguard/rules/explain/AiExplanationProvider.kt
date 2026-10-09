package com.causalguard.rules.explain

/**
 * B6-3：可选的在线 AI 解释 Provider 契约（docs/11 §2、§3）。
 *
 * 在线 AI 是**可选增强**，不是 P0 门禁。实现只允许发送经 [AiExplanationRequest] 投影的
 * 白名单字段，返回模型原始 [ExplanationText]；**不做**任何本地事实校验——校验统一由
 * [ExplanationService] 调用 [ExplanationFactValidator] 完成，确保“AI 只改写表达、不新增事实”。
 *
 * 失败语义：未配置（[isAvailable] 为 false）、超时、HTTP 错误、解析失败等一律返回 `null`，
 * 由调用方回退本地模板；实现**不得**抛出业务异常以外的异常，更不得返回伪造的解释。
 */
interface AiExplanationProvider {

    /** 是否已配置可用（如密钥/端点已注入）。为 false 时调用方应直接走本地模板，不发起网络。 */
    val isAvailable: Boolean

    /** 模型名称与版本，用于审计（docs/11 §6）。未配置时可为占位值。 */
    val modelName: String

    /**
     * 请求在线模型生成解释。
     *
     * 只接受 [AiExplanationRequest]（12 字段白名单）；失败或不可用时返回 `null`。
     */
    suspend fun explain(request: AiExplanationRequest): ExplanationText?
}
