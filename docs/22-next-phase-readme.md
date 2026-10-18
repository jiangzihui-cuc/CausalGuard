# 22 下一阶段 README（v2.0 产品化）

> 文档任务：下一阶段（v1.0.0 → v2.0）的入口 README
> 基线：`main` `0776457`（已合入 B8-1 设计文档、B8-5 审查记录、SOURCE_ORIGINALITY）
> 上一阶段产物：v1.0.0（竞赛提交版，P0 闭环 demo）
> 决赛答辩：2026-11-14
> 决策冻结：2026-10-14（见第 3 节，9 项决策已定）
> 阶段 0 完成：2026-10-16（真机验证 A/B/C 通过，见下方阶段 0 完成记录）
> 阶段 1 进展：2026-10-16 自研底座核心 + 去 GPL + Apache-2.0 完成；2026-10-17 真机完成 DNS 观测面端到端验证；2026-10-18 V1-7a UDP、V1-7b TCP、V1-7c IPv6/健壮性完成（TCP/UDP IPv4 真机通过，IPv6 真机待可用网络）。V1-7d 待续，见下方阶段 1 进展记录。
> 本文档回答一个问题：**从「竞赛 demo」到「成熟完整 app」，下一步做什么、先做什么、怎么验收。**

---

## 1. 一句话定位

把 v1.0.0（一个能跑通 P0 闭环、但仍是框架 + demo 的竞赛提交版）推进为 **v2.0（数据不出设备、网络底座自研、真实接入大模型、可独立安装与运维的成熟隐私产品）**。

**终点**：开源维护 + 择机海外上架（决策 D0/D4，见第 3 节）。不是一次性交付，而是持续迭代的长期项目。

上一阶段的核心价值是「**诚实**」——承认 Android 平台边界、不编造证据、把第三方底座和原创内容分得清清楚楚。下一阶段的核心价值是「**完整 + 自研**」——补齐大模型、去掉第三方底座依赖、把观测面从「VPN 元数据」拓宽到「本地 + 端侧模型的场景推理」。

---

## 2. 现状 → 差距 → 目标（对照表）

| 维度 | v1.0.0 现状（代码事实） | 差距 | v2.0 目标 |
|---|---|---|---|
| **大模型** | `RetrofitAiExplanationProvider` 是 stub：`modelName = "remote-ai"` 占位；`AI_API_KEY`/`AI_BASE_URL` 未注入时永远 `return null` 走本地模板；无任何端侧 ML 依赖（无 tflite/onnx/gguf）；真实 fact consistency = N/A | 模型从未真正接通过 | 接入真实模型（名称/版本/接口登记在案），`explain → 本地事实校验 → 回退` 全链路真实可用 |
| **网络底座** | `third_party/tracker-control-android` GPL-3.0 submodule（commit `9504d41b`），整个采集/阻断能力依赖它 | 依赖「别人的 app」，被 GPL 传染 | 自研 `VpnService` + L3/L4 报文解析 + `/proc/net` UID 归属，彻底去 GPL |
| **数据/观测面** | 纯本地 Room/SQLite，观测面只有 VPN 网络元数据 + UsageStats | 数据维度单薄 | 拓宽观测面 + 端侧模型做场景推理，本地优先不变 |
| **产品形态** | 无后端、无账号、部分页面靠 fixture 回放 | 离「成熟 app」差一个量级 | 完整 onboarding、常驻服务、真实时告警、每日摘要（P1）、导出（P1） |
| **文档/评测** | 规则评测完善，AI 评测是 synthetic | AI 真实评测空白 | 补真实 fact consistency 评测，去掉 N/A |

> 关键判断：**「数据只是真机本地数据」不是 bug，是 Android 平台的硬边界**（普通 App 无法观测别的 App 的剪贴板/定位/相机调用）。不要试图去「监控别的 App 的剪贴板」——那是死路。正确做法是把「数据不出设备 + 端侧模型 + 可追溯因果链」包装成**卖点**而非短板。

---

## 3. 决策记录（已定版，2026-10-14）

决策分两层：**第 0 层（产品/商业/合规）先定，第 1 层（技术）随之确定**。

### 3.1 第 0 层：产品 / 商业 / 合规

| 编号 | 决策 | 结论 | 依据与影响 |
|---|---|---|---|
| **D0** | 项目终点 | **开源维护 + 择机上架** | 持续迭代；不做一次性交付。后端/账号/资质不急于短期铺开，按上架时机逐步补齐 |
| **D4** | 市场与合规 | **海外 / 开源展示** | 避开国内 VPN 类产品上架资质红线；目标 Google Play 或纯开源。注意海外上架需评估 GDPR 与数据出境 |
| **D5** | 最终许可证 | **Apache-2.0** | 与现有 AndroidX/Room 依赖一致，允许后续商业化。**注意**：仅在自研底座（D2）完成后才能整体转 Apache-2.0，去 GPL 前仍受 GPL-3.0 约束 |

### 3.2 第 1 层：技术

| 编号 | 决策 | 结论 | 依据与影响 |
|---|---|---|---|
| **D1** | 大模型接入 | **云端先跑通 + 端侧立项** | 先用云端 API（Qwen/DeepSeek/GLM，OpenAI 兼容）打通闭环，端侧小模型同步做 PoC |
| **D2** | 网络底座 | **自研去 GPL** | `VpnService` + L3/L4 报文解析 + `/proc/net` UID 归属，彻底去掉 TrackerControl/GPL 依赖 |
| **D3** | 后端范围 | **最小后端：模型网关 + 密钥托管** | 只解决「云端模型密钥不能内置 app」这一个必须问题；不做账号/订阅/聚合 |
| **D6** | AI 深度 | **受约束解释器，智能体留 v3** | 维持 `docs/11` 契约（只改写表达、不新增事实），对话式/主动洞察放到 v3 |
| **D7** | 观测面红线 | **维持红线不碰** | 不碰无障碍服务/剪贴板监控，守住 v1.0 的隐私伦理与商店审核边界 |
| **D8** | 决赛前范围 | **只做阶段 0（接真模型）** | 决赛（11/14）前不碰底座，保住已验证的稳定性；自研底座决赛后做 |

> 决策含义速记：**先接真模型稳住决赛 → 决赛后自研底座去 GPL 并整体转 Apache-2.0 → 端侧模型 PoC 作为「数据不出设备」的长期卖点 → 观测面不碰红线 → 后端只做密钥网关。**

---

## 4. 分阶段路线图

> 阶段 0 在决赛（11/14）前完成；阶段 1/2/3 在决赛后推进（决策 D8）。

### 阶段 0（决赛前，约 1 周）：真正打通大模型，去掉 stub

**目标**：补上「大模型没接入」这个最大缺口，且不破坏决赛稳定性。

任务：

1. 选定一个真实模型，写进 `THIRD_PARTY_NOTICES.md`（名称 / 版本 / 接口方式——文档里已留空位）。
2. 打通 `app/src/main/java/com/causalguard/explain/`：
   - `AiExplanationApi` 指向真实 OpenAI 兼容端点；
   - `RetrofitAiExplanationProvider.modelName` 换成真实值；
   - 密钥沿用现有 `secrets.properties` / 环境变量注入作为**过渡**（决赛演示用），正式发布前迁移到后端网关（D3）。
3. 跑通 `explain → ExplanationFactValidator 本地校验 → 模板回退` 全链路，别让 `isConfigured` 永远是 false。
4. 补真实 fact consistency 评测（现在文档里是 N/A，评审会盯）。

**验收**：真机断网走模板、联网走真实模型；模型输出被本地校验拦截时正确回退；文档里 model 名称/版本非占位符。

#### 阶段 0 完成记录（2026-10-14）

分支 `feature/v2-t0-ai-integration` 已完成阶段 0 全部任务（代码 + 评测 + 真机验证）。真实 fact consistency 实测 `deepseek-chat` 6 条样例 `6/6 = 1.0`（见 `docs/b6-5`）；真机验证三项全部通过：**A** 设置页显示「已配置密钥」、**B** 联网调用 deepseek 返回 `POST .../chat/completions -> 200`、**C** 断网走本地模板不闪退。待 PR 合入 `main`。

| 交付物 | 状态 |
|---|---|
| `explain/` 直连 OpenAI 兼容大模型（`chat/completions`） | ✅ |
| `AiPromptBuilder`：白名单 → 约束五段 JSON 提示词（rule-engine，纯函数） | ✅ |
| `BearerAuthInterceptor` + `NoBodyLoggingInterceptor`：鉴权注入且不落日志 | ✅ |
| 真实模型名 `deepseek-chat`（`AI_MODEL_NAME` 可覆盖为任意 OpenAI 兼容模型） | ✅ |
| `RealAiFactConsistencyTest`：opt-in 真实事实一致性评测（无密钥自动跳过） | ✅ |
| `THIRD_PARTY_NOTICES.md` 第 5 节 + `docs/b6-5` 登记模型服务与评测协议 | ✅ |

**配置密钥（本地，不入库）：**

```bash
# 项目根目录新建 secrets.properties（已被 .gitignore 忽略）
cat > secrets.properties <<'EOF'
AI_BASE_URL=https://api.deepseek.com/v1
AI_API_KEY=sk-xxxxxxxx
# 可选：AI_MODEL_NAME=deepseek-chat（默认即此值）
EOF
```

**验证命令：**

```bash
./gradlew :app:testDebugUnitTest --tests "com.causalguard.explain.*"   # 离线单测（无密钥时评测自动跳过）
./gradlew :app:testDebugUnitTest --tests "com.causalguard.explain.RealAiFactConsistencyTest"  # 配密钥后跑真实 fact consistency
```

- 未配置密钥时 `AiHttpClient.isConfigured=false`，在线 AI 降级本地模板（P0 行为不变）。
- 配置密钥后真机验证：断网走模板、联网走 `deepseek-chat`，模型输出被本地校验拦截时回退模板。

### 阶段 1（决赛后，约 2-4 周）：自研网络底座，去 GPL

**目标**：让 app 不再依赖 TrackerControl，成为自包含实现，并整体转 Apache-2.0（D2/D5）。

任务：

1. `VpnService` 起包，写最小 IP/TCP/UDP/DNS 头解析器（纯 Kotlin）。
2. UID 归属：`/proc/net/{tcp,udp}` inode → 进程 → 包名映射。
3. 保留 `PrivacyEvent` 契约和 `TrackerControlAdapter` 的边界，把底座换成自研实现，跑通同一条事件链（`NetworkEventSource → NetworkEventIngestor → PrivacyEventRepository`）。
4. 移除 submodule 依赖，更新 `THIRD_PARTY_NOTICES.md`、`SOURCE_ORIGINALITY.md`、`docs/20-open-source-reuse-guide.md`，并将许可证整体转为 Apache-2.0。

**验收**：真实设备上能观测到连接元数据并生成 `PrivacyEvent`；域名阻断能力可用；`git grep tracker-control` 只剩历史文档引用，无运行时依赖。

#### 阶段 1 进展记录（2026-10-16，分支 `feature/v2-t1-selfhosted-net-core`）

已完成（代码 + 单测，可离线验证）：

| 交付物 | 说明 |
|---|---|
| `net-core/` 自研网络核心模块 | IPv4/IPv6 + TCP/UDP 报文解析（含 IPv6 扩展首部/分片）、IPv4/UDP 报文构造（校验和）、DNS 解析（含压缩指针）与 NXDOMAIN 构造、`/proc/net/*` 解析与 UID 归属索引、域名阻断判定；JVM 单测 27 项全绿 |
| `app/.../data/network/selfhosted/` | 自研 `VpnService`（观测优先：只路由 DNS 子网，DNS 代理转发 + 域名阻断 + NXDOMAIN，不改 TCP 连通性）、`/proc`+`ConnectivityManager` UID 归属、事件桥 `SelfHostedNetworkAdapter`、`SelfHostedDomainBlockController` |
| 事件链打通 | `SelfHostedNetworkEventSource → NetworkEventCollector → NetworkEventIngestor → PrivacyEventRepository`，DI（`AppContainer`）已切换，契约（`NetworkEventSource`）不变 |
| 数据面 | `net-core` `UdpRelay`（V1-7a）+ `TcpRelay`（V1-7b/7c，IPv4+IPv6、乱序/快速重传/RTT-RTO/回绕）：2026-10-18 真机 UDP 回环、TCP HTTP 200 通过 |
| 去 GPL | 移除 `.gitmodules`、`third_party/tracker-control-android`、`third_party/patches/*`、`apply-trackercontrol-hook.sh`；运行时代码无 tracker-control 引用（`scripts/verify-no-gpl-residue.sh` 校验） |
| 许可证 | 整体转为 **Apache-2.0**（根 `LICENSE`），`THIRD_PARTY_NOTICES.md`、`SOURCE_ORIGINALITY.md`、`README.md` 已更新 |

**真机验证（2026-10-17，PJW110 / Android 16）**：已完成 DNS 观测面端到端验证——TUN 建立、DNS 转发、`PrivacyEvent` 入库、UID 归属、域名阻断（NXDOMAIN）全部通过。设备、命令与原始证据见 [v2-t1 真机验证记录](v2-t1-real-device-verification.md)。

**明确未完成（诚实标注）**：

1. **V1-7a UDP 用户态转发已完成**（2026-10-18 真机通过：捕获路由 → `UdpRelay` 转发 → 响应回写 → UID 归属入库）。
2. **V1-7b TCP 数据面已完成 IPv4 MVP**（2026-10-18 真机通过：握手 → 双向数据 → HTTP 200 → TCP 事件 UID 归属入库）。
3. **V1-7c 已完成核心项**：IPv6 构造/中继、客户端乱序缓存、快速重传、RTT-RTO、序列号回绕；未实现分片重组、SACK、窗口缩放（已标注）。
4. **剩余 V1-7d**：把默认路由切到 `0.0.0.0/0` 前，需要真机吞吐/切换/耗电验证，并提供 DNS-only 回退开关。
   **当前默认路由仍只含 DNS 子网**；`0.0.0.0/0` 需 7d 完成后再切。
5. **IPv6 真机验证**：实现与单测已通过，但测试设备 Wi-Fi 无可用全球 IPv6，端到端待补测。

> 结论：阶段 1 的**自研底座核心、DNS 观测面真机闭环、去 GPL / 转 Apache-2.0 已完成**；「真实设备上观测任意 TCP 连接元数据」这一验收项依赖后续 TCP 数据面里程碑。

### 阶段 2（与阶段 1 同步推进）：端侧模型 + 最小后端

**目标**：把「规则引擎」升级为「规则 + 模型」双层，补「数据不出设备」能力（D1/D3）。

任务：

1. 端侧小模型 PoC（Gemini Nano 或 MediaPipe LLM + 量化中文模型），做「本地解释」和「本地事实校验」。
2. 规则层管事实与降级（保持确定性、稳定输出），模型层管表达（受约束解释器，D6）。
3. 最小后端：模型网关 + 密钥托管，替换阶段 0 的构建期密钥注入（D3）。
4. 拓宽观测面：通知监听、电池/UsageStats 长期趋势、网络行为异常检测（不碰无障碍，D7）。

**验收**：端侧模型离线可用；确定性规则行为不因模型引入而改变（同版本同输入输出稳定）；评测集新增端侧模型事实一致率。

### 阶段 3（产品化）：从 demo 到 app

**目标**：达到「成熟完整 app」标准，支撑「择机海外上架」（D0/D4）。

任务：

1. 完整 onboarding（逐项授权说明 + 拒绝降级 + 暂停监测）。
2. 后台常驻服务 + 真实实时告警（替换 fixture 回放）。
3. 每日摘要（原 P1）、PDF 导出（原 P1）。
4. 隐私审计与合规：端侧模型 + 去 GPL + Apache-2.0 之后，正大光明宣称「数据不出设备」；上架前评估 GDPR 与数据出境。

**验收**：冷启动即可完成授权并进入监测；断网/拒绝授权/VPN 被回收均不闪退；无 fixture 也能演示完整闭环。

---

## 5. 与决赛时间线（11/14）的关系

按决策 D8，决赛前只做阶段 0。理由：

1. **接真模型**（阶段 0，1-2 天）——立刻补上「大模型」缺口，答辩时「AI 智能体」才立得住。
2. **决赛前不碰底座**——自研 VPN 底座虽含金量最高，但 2-4 周工期与决赛稳定性冲突；完不成就别冒险换底座。

> 决赛时若被问到「是否基于 TrackerControl」，诚实回答：「当前底座为 GPL 开源复用，自研底座已列入路线图（D2）」。不要交一个跑不起来的版本，也不要谎称已自研。

决赛后按阶段 1 → 2 → 3 顺序推进，自研底座（阶段 1）是最高优先级。

---

## 6. 风险清单

| 风险 | 影响 | 应对 |
|---|---|---|
| 自研底座在真机上观测不到连接/UID 归属失败 | 阶段 1 验收不达标 | 先做 1 个设备 PoC 再铺开；失败则保留 GPL 底座兜底，延期转 Apache-2.0 |
| 端侧模型中文解释质量差 | 「智能体」观感弱 | 模型只做表达，事实/降级仍由确定性规则保证（D6） |
| 云端模型密钥泄露 / 数据出境 | 隐私与合规风险 | 密钥走后端网关（D3），不内置 app；请求只发白名单字段 |
| 阶段 1 影响决赛稳定性 | 现场翻车 | 决赛前冻结 v1.0.0 可演示版本，自研底座走独立分支（D8） |
| 海外上架的 GDPR / 数据出境合规 | 上架受阻 | 上架前专项评估，纳入阶段 3 任务（D4） |
| 转 Apache-2.0 前仍有 GPL 残留 | 许可证不干净 | 去 GPL 完成并全量扫描后才改许可证声明（D5） |

---

## 7. 分工建议

延续现有「A 技术 / B 产品与智能」分工：

- **成员 A（技术实现）**：自研 VPN 底座、`explain/` 网络层、最小后端网关、产品化（onboarding/服务/告警）。
- **成员 B（产品与智能）**：模型选型与评测、端侧模型 PoC、观测面扩展（不碰红线）、文案与事实一致性。

---

## 8. 本阶段完成定义（Definition of Done）

当以下全部满足时，本阶段（v2.0）视为完成：

1. `explain/` 接入真实模型，model 名称/版本在 `THIRD_PARTY_NOTICES.md` 登记，真实 fact consistency 有数值（非 N/A）。
2. 网络底座为自研实现，运行时无 TrackerControl/GPL 依赖，许可证整体转为 Apache-2.0，`THIRD_PARTY_NOTICES.md` 与 `SOURCE_ORIGINALITY.md` 已更新。
3. 端侧模型离线可用，且确定性规则行为不因模型引入而改变。
4. 最小后端（模型网关 + 密钥托管）上线，密钥不再构建期注入。
5. 完整 onboarding + 常驻服务 + 真实告警，断网/拒绝授权/VPN 被回收不闪退。
6. 无 fixture 也能演示完整闭环，`docs/15-acceptance-checklist.md` 全项通过。
