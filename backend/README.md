# CausalGuard 最小模型网关

这是 v2.0 阶段 2 的**最小后端**：只做两件事——**模型网关**与**密钥托管**。它不提供账号、订阅、数据聚合或云端存储。

```text
Android App
  │  Authorization: Bearer <gateway client token>
  │  POST /v1/chat/completions（只含白名单字段）
  ▼
CausalGuard Gateway（本目录）
  │  Authorization: Bearer <upstream api key>   ← 只存在服务端
  ▼
DeepSeek / OpenAI 兼容模型服务
```

## 为什么需要它

阶段 0 通过 `secrets.properties` 把上游模型密钥编进 APK，只适合本地演示。任何拿到 APK 的人都能提取密钥。网关把上游密钥留在服务端，App 只持有可随时轮换的 client token。

## 安全边界

- **上游密钥不进入仓库、不进入 APK、不写入日志**；
- client token 使用常量时间比较；每个 token 至少 16 字符；
- 请求字段采用**白名单重建**，未知字段直接拒绝（`stream`、`tools` 等不会透传）；
- 模型名必须命中 `GATEWAY_ALLOWED_MODELS`，`max_tokens` 有上限；
- 默认限制请求体 64 KiB、上游响应 1 MiB、每 token 每分钟 60 次；
- 上游远端必须使用 HTTPS；仅 localhost 允许明文 HTTP；
- 不跟随上游重定向（避免密钥被带到其他主机）；
- 不记录请求/响应正文和 `Authorization`，只记录 request id、路径、状态码、耗时；
- 上游错误统一脱敏为 `upstream_error`，不把供应商错误正文回传给客户端。

> 这不是通用代理，也不是完整认证系统。生产部署必须放在 TLS 反向代理之后，并配合边缘限流、防火墙、日志留存与 token 轮换。

## 配置

| 环境变量 | 必填 | 默认值 | 说明 |
|---|---|---|---|
| `GATEWAY_UPSTREAM_BASE_URL` | 是 | - | OpenAI 兼容端点根地址，需包含 `/v1`，如 `https://api.deepseek.com/v1` |
| `GATEWAY_UPSTREAM_API_KEY` | 是 | - | 上游模型密钥，仅服务端可见 |
| `GATEWAY_CLIENT_TOKENS` | 是 | - | 逗号分隔的客户端 token，每个至少 16 字符 |
| `GATEWAY_ALLOWED_MODELS` | 否 | `deepseek-chat` | 逗号分隔的模型白名单 |
| `GATEWAY_HOST` | 否 | `127.0.0.1` | 监听地址；容器内使用 `0.0.0.0` |
| `GATEWAY_PORT` | 否 | `8080` | 监听端口 |
| `GATEWAY_UPSTREAM_TIMEOUT_SECONDS` | 否 | `30` | 上游超时（秒） |
| `GATEWAY_MAX_BODY_BYTES` | 否 | `65536` | 请求体上限 |
| `GATEWAY_MAX_UPSTREAM_BYTES` | 否 | `1048576` | 上游响应上限 |
| `GATEWAY_MAX_REQUESTS_PER_MINUTE` | 否 | `60` | 每 token 每分钟请求数；`0` 表示关闭 |
| `GATEWAY_MAX_TOKENS` | 否 | `2048` | 单次请求 `max_tokens` 上限 |
| `GATEWAY_MAX_MESSAGES` | 否 | `40` | 单次请求消息条数上限 |
| `GATEWAY_MAX_MESSAGE_CHARS` | 否 | `30000` | 所有消息正文总字符上限 |
| `GATEWAY_LOG_LEVEL` | 否 | `INFO` | `DEBUG` / `INFO` / `WARNING` / `ERROR` / `CRITICAL` |

## 本地运行

```bash
export GATEWAY_UPSTREAM_BASE_URL=https://api.deepseek.com/v1
export GATEWAY_UPSTREAM_API_KEY=sk-xxxxxxxx
export GATEWAY_CLIENT_TOKENS=$(openssl rand -hex 24)
export GATEWAY_ALLOWED_MODELS=deepseek-chat

cd backend
python3 -m causalguard_gateway
```

健康检查：

```bash
curl -sS http://127.0.0.1:8080/healthz
# {"status":"ok","service":"causalguard-gateway"}
```

调用：

```bash
curl -sS http://127.0.0.1:8080/v1/chat/completions \
  -H "Authorization: Bearer $GATEWAY_CLIENT_TOKENS" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "system", "content": "只输出 JSON。"},
      {"role": "user", "content": "用一句话解释这条事件。"}
    ],
    "temperature": 0.0,
    "response_format": {"type": "json_object"}
  }'
```

## Docker

```bash
docker build -t causalguard-gateway backend
docker run --rm -p 8080:8080 \
  -e GATEWAY_HOST=0.0.0.0 \
  -e GATEWAY_UPSTREAM_BASE_URL=https://api.deepseek.com/v1 \
  -e GATEWAY_UPSTREAM_API_KEY=sk-xxxxxxxx \
  -e GATEWAY_CLIENT_TOKENS="$(openssl rand -hex 24)" \
  -e GATEWAY_ALLOWED_MODELS=deepseek-chat \
  causalguard-gateway
```

容器以非 root 用户运行。生产环境建议由 Caddy/Nginx 终止 TLS，只暴露反向代理端口。

## Android App 接入

把 App 的 `AI_BASE_URL` 指向网关的 `/v1`，把 `AI_GATEWAY_TOKEN` 设为 **gateway client token**（不是上游密钥）：

```properties
AI_BASE_URL=https://gateway.example.com/v1
AI_GATEWAY_TOKEN=<gateway client token>
AI_MODEL_NAME=deepseek-chat
```

Release 构建只读取 `AI_GATEWAY_TOKEN`，不会读取阶段 0 的 `AI_API_KEY`。Debug 构建仍兼容
`AI_API_KEY`，便于本地演示和 `adb reverse` 联调；上游密钥只应配置在网关进程。

## 测试

```bash
cd backend
PYTHONPATH=. python3 -m unittest discover -s tests -v
```

测试使用本地假上游，覆盖鉴权、白名单、模型限制、请求体上限、限流、上游错误脱敏、非 JSON 响应与配置校验，不访问真实模型服务。真实上游联调与真机 A/B 记录见 [docs/v2-t2-minimal-gateway.md](../docs/v2-t2-minimal-gateway.md)。

## 已知限制（诚实标注）

- 未部署到公网；DoD 第 4 项“最小后端上线”仍未完成；
- 限流是单进程内存实现，重启即清零，不能替代边缘限流；
- 不支持流式响应（`stream` 会被拒绝）；当前 App 也不需要；
- 没有多租户、持久化审计、token 管理后台；client token 通过环境变量轮换；
- 没有请求内容审计，这是有意的隐私选择；排障只能依赖 request id 与状态码。
