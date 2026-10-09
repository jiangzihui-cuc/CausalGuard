# B6-3 可选 AiExplanationProvider

> 状态：已完成
> 实现：`rule-engine/src/main/kotlin/com/causalguard/rules/explain/AiExplanationProvider.kt`、`ExplanationService.kt`；`app/src/main/java/com/causalguard/explain/AiExplanationApi.kt`、`RetrofitAiExplanationProvider.kt`
> 契约：[11 AI 解释契约](11-ai-explanation-contract.md) §1、§2、§3、§6；网络层 [A6-1/A6-2](spike-results.md) §A6-1

## 职责

在线 AI 是**可选增强**，不是 P0 门禁。B6-3 提供可插拔的 `AiExplanationProvider` 与确定性编排 `ExplanationService`：无论在线 AI 是否可用，解释文本始终可用。

## 契约分层

| 层 | 位置 | 职责 |
|---|---|---|
| `AiExplanationProvider` | rule-engine（纯 Kotlin） | 只发送白名单 `AiExplanationRequest`，返回模型原始 `ExplanationText`，不做本地校验 |
| `ExplanationService` | rule-engine（纯 Kotlin） | 渲染本地兜底 → 可选调用 Provider → 本地事实校验 → 采用或回退 |
| `AiExplanationApi` / `RetrofitAiExplanationProvider` | app（Android） | 基于 A6-1 安全网络层（Retrofit/OkHttp）实现 Provider，处理超时/异常 |

## 编排顺序（`ExplanationService.explain`）

1. 用 [LocalExplanationProvider](b6-1-local-explanation.md) 渲染确定性本地兜底；
2. 未注入或 Provider `isAvailable=false` → `aiStatus=UNAVAILABLE`，**不发起网络**；
3. 经 `AiExplanationRequest.from(context)` 投影 12 字段白名单后请求；
4. 模型输出一律经 [ExplanationFactValidator](b6-2-ai-explanation-validation.md) 校验：
   - 通过 → `source=AI_ENHANCED`、`aiStatus=SUCCEEDED`（`sanitized` 时可携带清洗后文本）；
   - 拒绝 → 回退本地模板，`aiStatus=REJECTED`；
5. 超时/网络错误/解析失败/返回 null → 回退本地模板，`aiStatus=FAILED`。

`CancellationException` 原样抛出，不吞掉，保持结构化并发语义。

## 运行时接入

- `FixtureEventAnalysisService` 注入可选 `AiExplanationProvider`；**事件专属模板仍优先**（B6-1），缺失模板的事件才走 `ExplanationService`（可能 AI 增强）；
- `AppContainer` 仅在 `AI_API_KEY`/`AI_BASE_URL` 均已注入（`AiHttpClient.isConfigured`）时构造 `RetrofitAiExplanationProvider`，否则为 `null`；
- `SettingsViewModel` 依据 `EventAnalysisService.aiExplanationAvailable` 诚实展示“AI 云端解释”是否已配置，不再写死“未接入”。

## 边界

- Provider 只接收白名单 DTO，禁止发送剪贴板原文、通讯录、精确坐标、IP 全量列表、设备标识；
- 本地事实校验始终在设备侧执行，不依赖模型自检；
- 服务端需暴露 `POST {AI_BASE_URL}/v1/explain`（相对路径 `AiExplanationApi.EXPLAIN_PATH`），请求/响应体均为契约 §2/§3 JSON；
- 未配置密钥时整体不可用，P0 主演示完全离线可跑；事实一致率评测属 B6-5。
