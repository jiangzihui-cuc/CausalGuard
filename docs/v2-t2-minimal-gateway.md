# v2 阶段 2：最小模型网关与密钥托管

> 状态：**已实现并通过本地自动化测试；未部署**
> 日期：2026-10-19
> 对应路线图：[docs/22](22-next-phase-readme.md) 阶段 2 任务 3；决策 D3
> 代码入口：[backend/README.md](../backend/README.md)、`backend/causalguard_gateway/`

---

## 1. 要解决的问题

阶段 0 为了在决赛前打通真实模型，采用 `secrets.properties` / 环境变量在**构建期**把上游密钥写入 `BuildConfig`。这条路径适合本地演示，但不适合分发 APK：

- 任何拿到 APK 的人都可以从字符串常量或运行时配置中提取密钥；
- 密钥轮换必须重新构建、重新分发；
- 无法在上游侧区分“哪个客户端用了多少请求”。

阶段 2 的最小后端只解决这一件事：**把上游模型密钥从客户端移到服务端，同时保持 App 的 OpenAI 兼容调用路径不变。**

不做：账号、订阅、计费、云端事件存储、用户行为聚合。这些都不在 D3 范围内。

---

## 2. 边界与数据流

```text
Android App
  │  Authorization: Bearer <gateway client token>
  │  POST /v1/chat/completions
  │  body: model / messages / temperature / response_format
  ▼
CausalGuard Gateway
  │  1. 常量时间校验 client token
  │  2. 每 token 滑动窗口限流
  │  3. 字段白名单校验 + 重建 payload
  │  4. 模型白名单 / max_tokens 上限
  │  5. Authorization 替换为上游密钥
  ▼
OpenAI 兼容上游（默认 DeepSeek）
  │
  ▼
Gateway 只回传 JSON；上游错误统一脱敏
```

App 侧**不需要改调用协议**：继续使用 `RetrofitAiExplanationProvider` →
`AiExplanationApi.chat`，只把 `AI_BASE_URL` 指向网关的 `/v1`，把 `AI_API_KEY`
设置为 gateway client token。

---

## 3. API 契约

### 3.1 `GET /healthz`

无需鉴权，仅返回存活状态，不暴露上游、模型或配置信息。

```json
{"status":"ok","service":"causalguard-gateway"}
```

### 3.2 `POST /v1/chat/completions`

请求头：

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <gateway client token>` |
| `Content-Type` | 是 | `application/json` |
| `X-Request-Id` | 否 | 1–64 位字母/数字/`._-`；非法值会被替换为随机 id |

请求体允许字段（**其他字段一律 400**）：

| 字段 | 约束 |
|---|---|
| `model` | 必须命中 `GATEWAY_ALLOWED_MODELS` |
| `messages` | 非空数组；仅 `role`/`content`；role ∈ system/user/assistant；条数与总字符数有上限 |
| `temperature` | 0–2 |
| `max_tokens` | 1–`GATEWAY_MAX_TOKENS` |
| `top_p` | (0, 1] |
| `response_format` | 仅 `{"type":"json_object"}` 或 `{"type":"text"}` |

明确拒绝：`stream`、`tools`、`functions`、`user`、`metadata` 等未列出的字段。

成功时透传上游 JSON，状态码保留；失败时返回统一结构：

```json
{
  "error": {"code": "invalid_request", "message": "..."},
  "request_id": "..."
}
```

错误码：`unauthorized`、`rate_limited`、`payload_too_large`、`length_required`、
`invalid_json`、`invalid_request`、`upstream_error`、`upstream_timeout`、
`upstream_unavailable`、`upstream_invalid_response`、`upstream_response_too_large`、`not_found`。

---

## 4. 安全设计

| 风险 | 处理 |
|---|---|
| APK 泄露上游密钥 | 上游密钥只从服务端环境变量读取；不写入仓库、镜像层或日志 |
| client token 被暴力猜测 | 每个 token 至少 16 字符；常量时间比较；环境变量注入；支持多 token 轮换 |
| 客户端伪造 provider 参数 | 字段白名单重建 payload，未知字段直接拒绝 |
| 客户端滥用昂贵模型 | `GATEWAY_ALLOWED_MODELS` + `max_tokens` 上限 |
| 请求风暴 | 单进程滑动窗口限流（默认 60 次/分钟/token） |
| 上游错误泄露内部信息 | 非 2xx 统一转 `upstream_error`，不回传上游正文 |
| 重定向带走密钥 | 禁用上游重定向 |
| 明文传输 | 远端上游强制 HTTPS；网关自身要求部署在 TLS 反向代理后 |
| 日志泄露内容 | 只记录 request id / method / path / status / duration_ms，不记录 header 与 body |
| 大请求/大响应耗尽内存 | 请求体与上游响应均有硬上限 |

**仍然存在的边界**：

- 限流是单进程内存实现，重启清零，不是边缘 DDoS 防护；
- 网关不做内容审计，这是有意的隐私选择；
- 没有 mTLS、设备证明或用户级配额；
- 生产环境需要额外的 TLS、反向代理、防火墙、日志留存和密钥轮换流程。

---

## 5. 实现与测试

实现使用 Python 3.10+ 标准库，无第三方依赖：

```text
backend/
├─ README.md                       配置、运行、Docker 与 App 接入说明
├─ Dockerfile                      非 root 运行 + healthcheck
├─ causalguard_gateway/
│  ├─ __init__.py
│  ├─ __main__.py
│  └─ server.py                    配置、鉴权、限流、白名单、上游转发
└─ tests/
   └─ test_server.py               22 项本地测试（假上游，不访问真实模型）
```

运行测试：

```bash
cd backend
PYTHONPATH=. python3 -m unittest discover -s tests -v
```

本轮实测结果：**22 项通过，0 失败**（2026-10-19，Python 3.10.12）。覆盖：

- 健康检查无需鉴权；
- 缺失/错误 client token 返回 401，且不调用上游；
- 正常请求转发，上游只看到上游密钥，客户端 token 不出现在上游 header；
- 未知字段、`stream`、非法 message 字段被拒绝且不调用上游；
- 模型白名单；
- 请求体上限 413；
- 限流 429 + `Retry-After`；
- 上游 401 脱敏为 502，且不泄露上游正文；
- 上游非 JSON 响应 502；
- 缺失 `Content-Length` 411；
- 配置校验：缺密钥、弱 token、远端明文 HTTP、URL 内嵌凭据。

---

## 6. App 接入方式

### 6.1 Debug 真机联调（adb reverse）

Debug 构建新增了仅对 `127.0.0.1` / `localhost` 放行明文 HTTP 的网络安全配置
（`app/src/debug/`），因此可以直接通过 `adb reverse` 连接本机网关，不需要公网地址：

```bash
# 主机：启动网关（127.0.0.1:8080）
cd backend
python3 -m causalguard_gateway

# 主机：把设备的 8080 反向转发到主机 8080
adb reverse tcp:8080 tcp:8080
```

Debug APK 的 `AI_BASE_URL` 配置为 `http://127.0.0.1:8080/v1`，
`AI_API_KEY` 配置为 gateway client token。Release 构建不包含该明文放行配置；
生产网关必须使用 HTTPS。

> `AI_API_KEY` 字段名是阶段 0 遗留。App 的 `isConfigured` 仍要求它非空，
> 语义已经变为“网关客户端凭据”。

### 6.2 生产接入

在 `secrets.properties`（已 gitignore）或构建环境中（release 使用 `AI_GATEWAY_TOKEN`）：

```properties
# 指向网关，而不是模型供应商
AI_BASE_URL=https://gateway.example.com/v1
# 这里是 gateway client token，不是上游 API key
AI_GATEWAY_TOKEN=<gateway client token>
AI_MODEL_NAME=deepseek-chat
```

接入后：

- 联网：App → 网关 → 上游模型；
- 断网或网关不可达：App 捕获异常并回退本地模板（阶段 0 行为不变）；
- 网关返回格式错误：本地事实校验与模板回退仍然生效。

---

## 7. 验收状态（诚实标注）

| 验收项 | 状态 | 说明 |
|---|---|---|
| 网关代码与配置校验 | ✅ 已完成 | 标准库实现，无新增第三方依赖 |
| 本地自动化测试 | ✅ 22/22 | 假上游，不消耗真实额度 |
| Docker 构建文件 | ✅ 已提供 | 未在本机执行 `docker build`（当前环境无 Docker） |
| 真实上游联调 | ✅ 2026-10-19 | 经本机网关转发到 DeepSeek，返回 200；未部署公网 |
| App 端到端（debug） | ✅ 2026-10-19 | PJW110 / Android 16，`adb reverse`；A/B 通过 |
| 公网部署 + TLS | ⏳ 未做 | DoD 第 4 项“上线”仍未完成 |
| 移除构建期上游密钥 | ✅ 代码已切换 | release 只读取 `AI_GATEWAY_TOKEN`；仍需用 release APK 做真机 A/B |

**结论**：阶段 2 的“最小后端代码、安全边界与真机 A/B”已完成；“公网部署 + 生产密钥托管切换”尚未完成。在公网部署与 release 构建验证通过前，不把 DoD 第 4 项标记为完成。

---

## 8. 真机验证记录（2026-10-19）

环境：

- 设备：PJW110 / Android 16 / API 36；
- App：debug 构建，`AI_BASE_URL=http://127.0.0.1:8080/v1`，经 `adb reverse tcp:8080 tcp:8080` 访问主机网关；
- 上游：DeepSeek OpenAI 兼容接口（真实 key 只在网关进程环境变量中）；
- 事件库：624 条真机事件。

### A：网关在线

```text
I/CausalGuardAI: POST http://127.0.0.1:8080/v1/chat/completions -> 200 (2532ms)
I/CausalGuardAI: explanation status=SUCCEEDED source=AI_ENHANCED model=deepseek-chat reasons=
```

- 打开单条事件详情只产生 **1 次**网关请求；
- 模型输出通过本地事实校验，采用 AI 增强文本；
- Home 概览加载完成（624 条事件），期间 **0 次**在线模型调用。

### B：网关不可达

```text
I/CausalGuardAI: explanation status=FAILED source=LOCAL_TEMPLATE model=deepseek-chat reasons=
```

- App 未闪退；
- 事件详情正常展示本地确定性模板；
- 说明“在线增强失败 → 本地兜底”在真机成立。

### 真机发现并修复的两个问题

1. **Home 批量概览逐条调用模型**：624 条事件会触发数百次真实 API 请求，界面长时间停留在加载态。
   已改为 `analyzeAll()` 只走本地确定性模板；只有用户打开单条事件详情时才调用在线模型。
   新增回归测试 `batchAnalysisDoesNotCallOnlineAiButSingleEventDoes`。
2. **本地事实校验误拒绝“未命中风险规则”**：字符串包含子串“中风险”，被误判为风险等级升级，
   导致 AI 输出总是回退本地模板。已排除“命中风险 / 降低风险 / 提高风险”等安全搭配，
   同时保留对“这是中风险行为”的真实升级拦截。
   新增回归测试 `does not treat the phrase 未命中风险规则 as a medium risk claim`。
3. **审计日志**：只记录 `status/source/model/reasons`，不记录解释正文、提示词或事件事实；仅在 debug 构建输出。

### 回归测试

```text
:app:testDebugUnitTest          142 tests, 0 failures
:rule-engine:testDebugUnitTest  141 tests, 0 failures
:app:lintDebug                  BUILD SUCCESSFUL
backend unittest                22 tests, 0 failures
```

### Release 构建检查

`./gradlew :app:assembleRelease` 通过；release merged manifest 中：

- `Debug*Receiver` 数量为 0；
- 无 `networkSecurityConfig`；
- 无 `usesCleartextTraffic`。

即 debug-only 的 localhost 明文放行没有进入 release 包。但本轮**未用 release 构建重复真机 A/B**，也未验证 release 包的生产网关 HTTPS 配置。

---

## 9. 下一步

1. 在受控环境部署网关（TLS 反向代理 + 防火墙），配置真实上游密钥与 client token；
2. 用 release 构建重复 A/B，确认 debug-only 明文配置未进入正式包；
3. 通过后移除 `secrets.properties` 中的上游密钥路径，更新阶段 0 文档与构建说明；
4. 端侧模型 PoC（阶段 2 另一条线）另行推进，不在本文档范围内。
