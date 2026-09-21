# 20 开源代码复用调研与接入建议

> 版本：`v0.1`
> 最后更新：2026-09-21
> 责任人：成员 A（协作：成员 B）
> 适用项目：CausalGuard / 隐私因果哨兵
> 适用团队：2 人，Android 10 / API 29+，竞赛 MVP
> 目标：在不牺牲原创性、技术真实性和许可证合规的前提下，复用成熟底层能力，避免从零重复实现高风险基础设施。
> 关联：[21 并行分工与协作规范](21-parallel-work-allocation-plan.md)、[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)、[12 隐私与安全设计](12-privacy-security-design.md)

> 引入前必须固定版本，并再次核对具体 commit/tag 内的 LICENSE、NOTICE 和单文件头部声明。

## 1. 结论先行

项目应当使用开源代码，但复用范围应集中在以下部分：

1. Android VPN/TUN 转发、DNS 解析和网络阻断；
2. Android 官方 API 示例与通用应用架构；
3. Room、Compose、JSON、HTTP、测试等通用基础库；
4. tracker/域名分类数据；
5. 许可证清单、截图、构建和发布工具。

以下部分应由团队原创：

- `PrivacyEvent` 统一事件模型；
- UsageStats 与网络行为的时间关联；
- 场景知识库；
- 场景一致性判断；
- 证据等级；
- 风险规则内容与阈值；
- 因果链展示；
- 处置建议与复查算法；
- Ground Truth Demo 场景；
- 产品 UI、解释文案和评测集。

最适合本项目的技术路线是：

```text
成熟开源网络底座（TrackerControl / NetGuard）
                   ↓
        标准化 NetworkEvent 适配器
                   ↓
团队原创：统一事件库 + 使用上下文 + 证据关联
                   ↓
团队原创：场景规则 + 因果解释 + 处置复查 + UI
```

## 2. 许可证等级与“能否挪用”

| 许可证 | 能否复制/修改 | 项目中的处理方式 |
|---|---|---|
| Apache-2.0 | 可以 | 保留版权、LICENSE 和 NOTICE；修改文件应注明修改 |
| MIT | 可以 | 保留版权和许可证文本 |
| BSD-2/3-Clause | 可以 | 保留版权、许可证和免责声明 |
| MPL-2.0 | 可以 | 修改过的 MPL 文件需要继续以 MPL 提供源码；其他文件可使用不同许可证 |
| GPL-3.0 | 可以，但为强 copyleft | 若代码被整合为衍生应用，应按 GPL-3.0 提供对应源代码、保留声明，并使用兼容许可证 |
| AGPL-3.0 | 可以，但义务更强 | 包含网络交互场景下的源码提供要求；本项目通常不建议引入 |
| CC BY-NC-SA 4.0 | 可以用于符合条件的非商业用途 | 必须署名、非商业、相同方式共享；未来商业化必须替换或另行授权 |

> 本文不是法律意见。最终应以所使用**具体 commit/tag 内的 LICENSE、NOTICE 和单文件头部声明**为准。引入前必须固定版本并再次核对许可证。

---

# 3. 阶段 0：项目启动、工程骨架与架构基线

## 3.1 Android Architecture Templates

- 仓库：[android/architecture-templates](https://github.com/android/architecture-templates)
- 许可证：Apache-2.0
- 推荐级别：**推荐参考，部分直接采用**
- 用途：建立绿色项目的分层目录、数据层、领域层、UI 层和依赖方向。
- 推荐原因：Android 官方维护，面向新项目，比直接从大型成品 App 拆架构更适合。
- 使用方式：
  1. 参考模板创建 `app` 工程；
  2. 保留单 Activity、Compose、ViewModel、Repository 的最小架构；
  3. 按本项目拆为 `profile`、`usage`、`network`、`event`、`rules`、`insight`、`ui`；
  4. 不要原样复制模板中的业务示例。

## 3.2 Android Architecture Samples

- 仓库：[android/architecture-samples](https://github.com/android/architecture-samples)
- 许可证：Apache-2.0
- 推荐级别：**推荐参考**
- 用途：Repository、ViewModel、UI State、测试替身和分层依赖。
- 推荐原因：官方示例，结构清楚，适合解决“页面不能直接依赖 Android API”的问题。
- 使用方式：参考其状态管理和数据仓库接口；不要为了追求完整 Clean Architecture 引入过多抽象。

## 3.3 Android Platform Samples

- 仓库：[android/platform-samples](https://github.com/android/platform-samples)
- 许可证：Apache-2.0
- 推荐级别：**推荐参考**
- 用途：查询最新 Android 平台 API 的官方示例，包括权限、后台行为、服务等。
- 推荐原因：比已经归档的旧 `googlesamples` 更适合作为新 API 用法基线。
- 使用方式：遇到具体 Android API 时，从对应 sample 提取最小调用与权限处理方式，不复制其整套导航和 UI。

### 阶段 0 建议

本阶段不需要引入第三方业务框架。工程骨架以 Android 官方模板为主，避免同时引入 MVI、Redux、复杂多模块脚手架。

---

# 4. 阶段 1：技术可行性 Spike

## 4.1 TrackerControl Android

- 仓库：[TrackerControl/tracker-control-android](https://github.com/TrackerControl/tracker-control-android)
- 许可证：GPL-3.0（部分第三方组件和数据有独立许可证）
- 推荐级别：**网络底座首选，但需要接受 GPL 路线**
- 可复用能力：
  - 本地 `VpnService`；
  - TUN 网络转发；
  - TCP/UDP 网络处理；
  - DNS 观测与域名关联；
  - App/网络通信记录；
  - tracker 分类与阻断；
  - VPN 状态和网络变化处理；
  - 不进行 TLS 中间人解密的元数据分析方案。
- 推荐原因：与本项目目标最接近，已经在真实 Android 设备上长期使用，底层基于 NetGuard。
- 使用方式：
  1. Fork 仓库并固定一个可构建 commit/tag；
  2. 首先原样构建运行，不立即删代码；
  3. 找到 VPN 启动、连接记录、DNS 映射和阻断入口；
  4. 增加适配层，将网络结果转换为本项目 `NetworkEvent`；
  5. 保留网络核心，逐步替换原有 UI 和业务层；
  6. 在 `THIRD_PARTY_NOTICES.md` 记录来源、commit、文件与修改。
- 主要风险：
  - 工程较重，包含 NDK/JNI；当前版本还涉及可选 WireGuard/Rust 构建链；
  - 不能把原 UI 和业务简单换名参赛；
  - 整合为衍生应用时需要遵守 GPL-3.0；
  - 不同数据源可能存在独立的非商业或署名要求。

## 4.2 NetGuard

- 仓库：[M66B/NetGuard](https://github.com/M66B/NetGuard)
- 许可证：GPL-3.0
- 推荐级别：**网络核心研究首选；TrackerControl 方案的上游参考**
- 可复用能力：
  - 无 root 的本地 VPN 防火墙；
  - 用户态网络转发；
  - JNI/C 网络处理；
  - App/IP/地址允许或拒绝；
  - VPN 重建和网络切换；
  - 日志与流量记录。
- 推荐原因：成熟度高，TrackerControl 的网络底层来自该项目。
- 使用方式：
  - 如果以 TrackerControl 为底座，通过 TrackerControl 间接使用，不要再同时复制第二份 NetGuard；
  - 如果只选择 NetGuard，则在其网络事件出口处增加本项目适配器；
  - 优先保留 native 核心，不修改底层协议处理，减少引入新 bug。
- 风险：GPL-3.0；代码历史较长、Java/JNI 较复杂；裁剪成本仍然较高。

## 4.3 Android AppUsageStatistics Sample

- 仓库：[googlesamples/android-AppUsageStatistics](https://github.com/googlesamples/android-AppUsageStatistics)
- 状态：已归档
- 许可证：Apache-2.0
- 推荐级别：**仅参考 API 调用，不直接作为工程底座**
- 可借鉴能力：
  - `UsageStatsManager` 获取；
  - 使用访问权限检查；
  - 查询时间区间内 App 使用统计；
  - 展示 UsageStats 的基本流程。
- 使用方式：提取权限检测与查询思路，改写为 Kotlin；本项目还需要自行补充 `queryEvents()`、状态机和版本兼容处理。
- 风险：示例较老且已归档，不能直接认为适配最新 Android。

## 4.4 Android ToyVPN

- 官方代码：[AOSP ToyVpn](https://android.googlesource.com/platform/development/+/refs/heads/main/samples/ToyVpn/)
- 说明：[Android VPN 官方文档](https://developer.android.com/develop/connectivity/vpn)
- 许可证：AOSP 示例通常为 Apache-2.0，以具体文件头为准
- 推荐级别：**仅用于理解 VpnService 生命周期，不作为本地防火墙底座**
- 可借鉴能力：
  - `VpnService.prepare()`；
  - `Builder` 配置；
  - 建立 TUN 接口；
  - 保护外部 socket 避免回环；
  - 服务启动与关闭。
- 不适合直接使用的原因：ToyVPN 是 VPN 客户端/服务器教学样例，不提供本项目需要的成熟本地 TCP/UDP 转发和 tracker 阻断。

## 4.5 hev-socks5-tunnel

- 仓库：[heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- 许可证：MIT
- 推荐级别：**许可友好的网络转发备选，需要额外架构**
- 能力：IPv4/IPv6、TCP、UDP、Android 支持、TUN 到 SOCKS5 转发。
- 推荐原因：轻量、性能较好、MIT 许可，Android 发布包覆盖多个 ABI。
- 使用方式：
  1. Android `VpnService` 建立 TUN；
  2. 将 TUN fd 交给 native tun2socks；
  3. 配置受保护的本地/远程 SOCKS5 出口；
  4. 在进入转发前或 DNS 路径上添加观测与阻断。
- 风险：它解决的是 TUN→SOCKS5，不自动提供完整的本地 tracker 防火墙；仍需 SOCKS5 出口、JNI 封装和事件归属方案。

## 4.6 xjasonlyu/tun2socks

- 仓库：[xjasonlyu/tun2socks](https://github.com/xjasonlyu/tun2socks)
- 许可证：新版本已切换为 MIT；必须固定并核对具体版本
- 推荐级别：**备选，不作为首选**
- 能力：基于 gVisor 网络栈进行透明代理，支持多平台和多种代理协议。
- 使用方式：编译为 Android 可用二进制或库，通过 fd 接入 `VpnService`。
- 风险：官方重点并非 Android App 集成；Go 运行时和 Android 打包会增加体积与集成成本；仍需要代理出口和 App/UID 归属。

### 阶段 1 决策门

建议在 1 天内并行验证：

| 路线 | 验证项 | 通过条件 |
|---|---|---|
| GPL 快速路线 | 原样构建 TrackerControl | 演示机可运行、联网、记录 DNS/连接并阻断 |
| MIT 备选路线 | hev-socks5-tunnel 最小 Android 接入 | 能稳定转发 TCP/UDP，且有可实现的事件观测入口 |

若 TrackerControl 能在一天内成功构建，比赛 MVP 优先走 TrackerControl/NetGuard 底座；若构建链持续失败，再评估 MIT tun2socks 路线。

---

# 5. 阶段 2：数据模型、数据库与契约

## 5.1 Room / Architecture Components Samples

- 仓库：[android/architecture-components-samples](https://github.com/android/architecture-components-samples)
- 许可证：Apache-2.0
- 推荐级别：**推荐参考并使用 Room 官方库**
- 可借鉴能力：Room Entity、DAO、Migration、Repository、数据库测试以及 WorkManager 示例。
- 使用方式：
  - 使用 Room 官方依赖；
  - 参考 sample 的 DAO 与 migration 测试；
  - 数据模型仍按照项目自己的 `AppProfile`、`PrivacyEvent`、`RiskAssessment`、`MitigationRecord` 设计。
- 不建议：复制 sample 的 Todo/用户业务模型。

## 5.2 kotlinx.serialization

- 仓库：[Kotlin/kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
- 许可证：Apache-2.0
- 推荐级别：**推荐直接依赖**
- 用途：统一事件 JSON、规则配置、Demo 场景、评测样例和 AI 输入输出序列化。
- 使用方式：
  - 给契约数据类添加 `@Serializable`；
  - 配置 `ignoreUnknownKeys = true`；
  - 为事件与规则增加 `schemaVersion`；
  - 把 B 成员的规则和评测成果保存为 JSON，直接进入测试与 App assets。

## 5.3 Public Suffix List

- 仓库：[publicsuffix/list](https://github.com/publicsuffix/list)
- 许可证：MPL-2.0
- 推荐级别：**推荐作为数据依赖**
- 用途：把 `a.b.example.co.uk` 正确归一化为可注册域，避免简单按最后两个标签截断。
- 使用方式：
  - 通过已有 Java/Android 域名库间接使用，或将固定版本作为资源；
  - 保存 PSL 版本与更新时间；
  - 修改 PSL 文件时遵守 MPL-2.0 文件级要求。

---

# 6. 阶段 3：模拟事件闭环、规则和 UI

## 6.1 Jetpack Compose Samples

- 仓库：[android/compose-samples](https://github.com/android/compose-samples)
- 许可证：Apache-2.0
- 推荐级别：**推荐参考，少量采用通用组件结构**
- 用途：列表、详情、卡片、导航、响应式状态和主题。
- 推荐使用位置：
  - 首页风险概览；
  - 事件时间线；
  - 证据卡片；
  - App 画像；
  - 设置页。
- 使用方式：参考状态提升、导航和组件拆分；视觉设计、文案和信息架构保持原创。

## 6.2 Easy Rules

- 仓库：[j-easy/easy-rules](https://github.com/j-easy/easy-rules)
- 许可证：MIT
- 推荐级别：**可选；规则复杂后再使用**
- 能力：轻量 Java 规则抽象、Facts、优先级、组合规则。
- 使用方式：
  - 将 `PrivacyEvent`、App 画像和场景状态映射为 Facts；
  - 每条规则只输出结构化 `RiskAssessment`；
  - 规则不得直接修改原始事件。
- 风险：项目只有 5～10 条规则时，引入第三方引擎可能比自己写 `Rule` 接口更复杂。
- 当前建议：P0 先自研约 100～200 行的纯 Kotlin 规则接口与执行器；Easy Rules 作为规则扩展备选。

## 6.3 Android Runtime Permission Samples

- 当前入口：[android/platform-samples](https://github.com/android/platform-samples)
- 旧样例：[android/permissions-samples](https://github.com/android/permissions-samples)
- 许可证：Apache-2.0
- 推荐级别：**使用新平台示例；旧样例仅参考**
- 用途：授权说明、拒绝、重复申请、设置页返回后刷新状态。
- 注意：Usage Access 与 VPN 授权不是普通 runtime permission，不能直接套普通权限库。

## 6.4 Demo App 的 WorkManager 参考

- 来源：[android/architecture-components-samples](https://github.com/android/architecture-components-samples)
- 许可证：Apache-2.0
- 推荐级别：**按需参考**
- 用途：Demo App 的延迟任务、可取消工作和受控后台演示。
- 注意：Android 后台限制会影响精确触发时间。竞赛演示应允许前台服务或明确的倒计时触发，不能承诺精确 30 秒后台执行。

---

# 7. 阶段 4：真实数据、DNS 与 tracker 分类

## 7.1 dnsjava

- 仓库：[dnsjava/dnsjava](https://github.com/dnsjava/dnsjava)
- 许可证：当前版本 BSD-3-Clause；历史版本为 BSD-2-Clause
- 推荐级别：**按需使用，不与 TrackerControl 自带 DNS 重复引入**
- 能力：DNS 消息、常见记录类型、EDNS、缓存、DNSSEC 等。
- 使用方式：
  - 如果采用 TrackerControl/NetGuard，优先使用其现有 DNS 解析链；
  - 如果选择 tun2socks 自建方案，可用 dnsjava 解析 DNS 查询/响应；
  - Android API 29 基线仍需验证依赖的 Java API/desugaring。
- 风险：安全公告和 Android 兼容性必须核对；本项目只需要有限 DNS 记录时，完整库可能偏重。

## 7.2 DuckDuckGo Tracker Radar

- 仓库：[duckduckgo/tracker-radar](https://github.com/duckduckgo/tracker-radar)
- 许可证：CC BY-NC-SA 4.0
- 推荐级别：**竞赛非商业场景可用；必须署名并隔离数据许可**
- 数据内容：域名、所属实体、流行度、跟踪行为等元数据。
- 使用方式：
  1. 不把完整仓库塞入 APK；
  2. 根据主演示场景离线生成精简 JSON；
  3. 保存数据版本和筛选脚本；
  4. 在应用“开源与数据来源”页面署名；
  5. 将数据文件与项目代码许可证分开说明。
- 风险：非商业限制；未来商业化时需要替换或申请授权。

## 7.3 DuckDuckGo Tracker Blocklists

- 仓库：[duckduckgo/tracker-blocklists](https://github.com/duckduckgo/tracker-blocklists)
- 许可证：CC BY-NC-SA 4.0
- 推荐级别：**可作为阻断数据备选**
- 用途：Android tracker 域名阻断列表。
- 使用方式：同样建议构建固定版本的精简离线列表，并记录来源和生成过程。
- 风险：非商业、署名、ShareAlike；不宜与代码许可证混写。

## 7.4 Disconnect Tracking Protection

- 仓库：[disconnectme/disconnect-tracking-protection](https://github.com/disconnectme/disconnect-tracking-protection)
- 许可证：CC BY-NC-SA 4.0
- 推荐级别：**tracker 分类数据备选**
- 用途：域名到服务、公司或 tracker 类别的映射。
- 使用方式：
  - 固定 `services.json` 版本；
  - 编写构建脚本转成项目所需的最小结构；
  - 保留许可证和署名；
  - 只把输出数据用于域名分类，不将“在列表中”解释为已泄露数据。

## 7.5 Exodus Privacy

- 核心仓库：[Exodus-Privacy/exodus-core](https://github.com/Exodus-Privacy/exodus-core)
- 许可证：AGPL-3.0
- 推荐级别：**不建议把核心代码嵌入 Android App；可以研究其 tracker 定义或使用合规 API/导出数据**
- 原因：项目主要用于静态分析 APK 中的 tracker SDK，技术栈和本项目运行时网络观测不同；AGPL 义务也比 GPL 更复杂。
- 合理用法：
  - 参考 tracker 分类与签名字段；
  - 如果使用其 API/数据，单独核对 API 条款和数据许可证；
  - 不在 P0 中加入 APK 静态反编译分析。

### tracker 数据源选择建议

P0 不应同时集成三套完整列表。建议优先级：

1. 如果以 TrackerControl 为底座：沿用其已集成的数据管线，明确记录每个数据源许可证；
2. 如果自建分类：选择 Disconnect 或 DuckDuckGo 中的一套，生成精简离线表；
3. 用人工审核的 50～200 个高价值演示域名作为稳定兜底；
4. 任何命中只表述为“该域名被公开列表分类为分析/广告服务”，不表述为“发生隐私泄露”。

---

# 8. 阶段 5：场景推理、阻断与处置复查

## 8.1 阻断执行

- 首选来源：TrackerControl / NetGuard
- 可直接借鉴：域名/IP/App 规则的数据结构、阻断判断、连接尝试日志和 VPN 重建。
- 必须原创：
  - 为什么建议阻断；
  - 阻断动作与哪条风险结论关联；
  - 处置前快照；
  - 处置后的观察窗口；
  - “已减少 / 仍有尝试但被拦截 / 无变化 / 无法确认”的判断。

## 8.2 规则引擎

- 备选来源：Easy Rules（MIT）
- 当前推荐：只复用规则接口思想，自己实现小型确定性执行器。
- 原因：场景推理是本项目核心原创点；若完全交给第三方规则 DSL，答辩难以突出原创性，而且调试成本可能更高。

## 8.3 因果图展示

P0 不建议引入大型图数据库或图可视化引擎。因果链通常只有 4～6 个节点，可直接使用 Compose 自定义布局或纵向时间线：

```text
用户状态 → App 状态 → 网络事实 → 分类事实 → 风险推断 → 处置结果
```

如果后续确实需要图算法，优先在纯 Kotlin 中使用简单邻接表；不要为了展示引入 Neo4j 或复杂图框架。

---

# 9. 阶段 6：AI 解释、网络调用与评测

## 9.1 Retrofit

- 仓库：[square/retrofit](https://github.com/square/retrofit)
- 许可证：Apache-2.0
- 推荐级别：**需要在线 AI 时推荐直接依赖**
- 用途：定义受约束的解释 API、超时、错误处理与结果解析。
- 使用方式：
  - 只发送白名单字段；
  - 使用独立 DTO，不直接序列化数据库 Entity；
  - 连接超时后立即回退本地模板；
  - 不向模型发送原始网络 payload、剪贴板或通讯录内容。

## 9.2 OkHttp

- 仓库：[square/okhttp](https://github.com/square/okhttp)
- 许可证：Apache-2.0
- 推荐级别：**配合 Retrofit 使用**
- 用途：HTTP 客户端、超时、证书、日志拦截器。
- 注意：正式构建禁止启用可能打印提示词或敏感字段的完整 Body 日志。

## 9.3 kotlinx.serialization

- 与阶段 2 使用同一依赖，不再增加 Gson/Moshi 等第二套 JSON 框架。
- 用途：AI 输入输出 DTO、评测样例和规则配置。

## 9.4 本地大模型框架

本项目 P0 **不建议**为了“本地 AI”集成 llama.cpp、MLC LLM 等大型推理框架：

- 增加 APK 体积、ABI、内存和设备兼容风险；
- 与 VPN 稳定性竞争开发时间；
- 比赛核心不依赖生成式模型；
- 本地确定性模板已经能覆盖断网兜底。

如果后续扩展，可独立做 P2 技术验证，不进入主演示链。

---

# 10. 阶段 6～7：测试、质量与演示

## 10.1 AndroidX Test

- 仓库：[android/android-test](https://github.com/android/android-test)
- 许可证：Apache-2.0
- 推荐级别：**推荐直接依赖**
- 用途：Instrumentation、Espresso、权限与页面流程测试。
- 重点测试：
  - 首次授权与拒绝；
  - 返回设置页后状态刷新；
  - VPN 启停；
  - Demo 场景复位；
  - 证据卡片展开；
  - 真实/沙箱标签不混淆。

## 10.2 Turbine

- 仓库：[cashapp/turbine](https://github.com/cashapp/turbine)
- 许可证：Apache-2.0
- 推荐级别：**推荐用于 Flow 测试**
- 用途：测试事件流、ViewModel StateFlow、风险结果和复查状态的发射顺序。
- 使用方式：用固定 JSON 生成事件，断言 `PrivacyEvent → RiskAssessment → UiState`。

## 10.3 detekt

- 仓库：[detekt/detekt](https://github.com/detekt/detekt)
- 许可证：Apache-2.0
- 推荐级别：**可选但推荐**
- 用途：Kotlin 静态检查，提前发现复杂函数、未处理异常和不一致风格。
- 使用方式：使用轻量默认规则；比赛冲刺阶段不要花时间制定几十条自定义规则。

## 10.4 Fastlane / Screengrab

- 仓库：[fastlane/fastlane](https://github.com/fastlane/fastlane)
- Android 截图说明：[Fastlane Android Screenshots](https://docs.fastlane.tools/getting-started/android/screenshots/)
- 许可证：MIT（以具体版本 LICENSE 为准）
- 推荐级别：**截图较多时使用；只有 3 张截图时可手工完成**
- 用途：自动运行 UI 测试并生成固定页面截图。
- 风险：Windows 与新 Android 版本上可能存在截图拉取问题；不要在提交前一天首次接入。

---

# 11. 阶段 7～8：报告、许可证与发布

## 11.1 PdfBox-Android

- 仓库：[TomRoush/PdfBox-Android](https://github.com/TomRoush/PdfBox-Android)
- 许可证：Apache-2.0
- 推荐级别：**P1，可用于 App 内导出 PDF**
- 用途：将事件、证据和处置结果导出为 PDF。
- 使用方式：只导出脱敏后的摘要；不要导出剪贴板、通讯录或精确位置原文。
- 当前建议：如果竞赛只需要提交设计文档 PDF，不必在 App 内集成；使用外部文档流程生成即可。

## 11.2 AboutLibraries

- 仓库：[mikepenz/AboutLibraries](https://github.com/mikepenz/AboutLibraries)
- 许可证：Apache-2.0
- 推荐级别：**推荐直接依赖或使用其 Gradle 插件**
- 用途：自动收集 Gradle 依赖及许可证，并在 App 中展示“开源许可”页面。
- 使用方式：
  - 自动生成依赖清单；
  - 手动补充 TrackerControl、NetGuard、数据列表等非普通 Maven 依赖；
  - 核对自动识别结果，不能完全依赖工具判断许可证。

## 11.3 Gradle License Report

- 仓库：[jk1/Gradle-License-Report](https://github.com/jk1/Gradle-License-Report)
- 推荐级别：**推荐用于提交前许可证审计**
- 用途：生成第三方依赖许可证报告。
- 使用方式：在 CI 或本地生成 HTML/JSON；人工检查 Unknown、GPL、AGPL 和非商业许可证。
- 注意：工具输出不是法律结论，手工复制的源码和数据集仍需自行登记。

## 11.4 Gradle GitHub Actions

- 仓库：[gradle/actions](https://github.com/gradle/actions)
- 推荐级别：**推荐用于每次 push 构建和测试**
- 用途：配置 Gradle、执行 `assembleDebug`、单元测试和依赖提交。
- 注意：2026 年新版缓存组件存在不同许可/服务模式，应选择明确允许的 basic cache 或关闭非必要增强缓存，并固定 action 主版本。

## 11.5 Uber APK Signer

- 仓库：[patrickfav/uber-apk-signer](https://github.com/patrickfav/uber-apk-signer)
- 推荐级别：**可选**
- 用途：批量签名、zipalign 和验证 APK 签名。
- 当前建议：优先使用 Android Gradle Plugin 和官方 `apksigner`；只有需要独立批处理时使用该工具。

---

# 12. 不建议采用或只能有限参考的项目

| 项目/类型 | 结论 | 原因 |
|---|---|---|
| 随机个人 VpnService Demo | 不建议作为底座 | 通常缺少 IPv6、UDP、异常恢复、网络切换和长期测试 |
| ToyVPN | 仅参考 | 需要 VPN server，不是本地透明防火墙 |
| 完整 DuckDuckGo Android App | 不建议 Fork | 工程过大，目标和本项目不一致，裁剪成本高 |
| Exodus Core | 不嵌入 App | Python/服务端静态分析方向，AGPL-3.0，和运行时网络观测不同 |
| 多套 tracker 列表同时完整打包 | 不建议 | 许可证、体积、冲突分类和更新逻辑复杂 |
| 大型通用规则 DSL | P0 不建议 | 规则少，自研小型确定性执行器更易测试与答辩 |
| 本地大模型运行时 | P0 不建议 | 体积、内存、ABI 和设备适配成本高 |
| TLS MITM 项目 | 明确排除 | 安全、合规、证书安装和技术边界风险过高 |

---

# 13. 推荐的最终开源组合

## 方案 A：比赛最快路线（推荐）

| 层 | 选择 |
|---|---|
| 网络底座 | TrackerControl / NetGuard（GPL-3.0） |
| 工程架构 | Android Architecture Templates / Samples |
| UI | Jetpack Compose + 官方 samples |
| 数据库 | Room |
| JSON | kotlinx.serialization |
| tracker 数据 | 优先沿用 TrackerControl 管线，或精简一套 CC BY-NC-SA 数据 |
| 规则 | 团队原创纯 Kotlin 小型规则引擎 |
| HTTP/AI | Retrofit + OkHttp，本地模板兜底 |
| 测试 | JUnit + AndroidX Test + Turbine |
| 许可证 | AboutLibraries + Gradle License Report + 人工清单 |

优点：最可能按期获得稳定 VPN、域名和阻断能力。  
代价：需要接受 GPL-3.0 路线并明确开源与原创边界。

## 方案 B：宽松许可证路线

| 层 | 选择 |
|---|---|
| VPN API | AOSP ToyVPN 仅参考 |
| 转发 | hev-socks5-tunnel（MIT）或固定 MIT 版本 tun2socks |
| DNS | dnsjava 或自行解析必要记录 |
| 其余 | 与方案 A 相同 |

优点：整体许可证更宽松。  
代价：需要自行解决 SOCKS5 出口、Android/JNI 集成、UID 归属、事件观测和阻断，工期风险明显更高。

## 推荐决策

对当前两人团队和比赛工期，选择**方案 A**更现实。许可证不是不能使用代码的理由，而是决定你们应该如何发布、署名和说明。

---

# 14. 每个阶段的实际接入计划

| 日期/阶段 | 本项目任务 | 开源使用动作 | 交付物 |
|---|---|---|---|
| 阶段 1 | 技术 Spike | 构建 TrackerControl；定位 NetGuard 网络入口；参考 UsageStats sample | `spike-results.md`、固定 commit、构建成功 APK |
| 阶段 2 | 契约冻结 | 使用 kotlinx.serialization；参考 Room sample | `PrivacyEvent` 等数据类、Room schema、JSON fixture |
| 阶段 3 | 模拟闭环 | 参考 Compose samples；可选 Easy Rules | 模拟事件→规则→证据卡片完整链 |
| 阶段 4 | 真实接入 | 复用 VPN/DNS/阻断；接入一套 tracker 数据 | 真实 `NetworkEvent`、分类结果、未知降级 |
| 阶段 5 | 因果与处置 | 复用底层阻断执行；原创证据关联和复查 | 处置前后可比较的证据链 |
| 阶段 6 | AI 与评测 | Retrofit/OkHttp；Turbine | 模板兜底、事实校验、30～50 条评测 |
| 阶段 7 | 演示 | AndroidX Test；按需 Fastlane | 稳定的三分钟流程、固定截图 |
| 阶段 8 | 提交 | AboutLibraries、License Report、官方签名工具 | APK、源码、许可证、第三方与原创边界表 |

---

# 15. 开源引入登记模板

每引入一个项目，在仓库 `THIRD_PARTY_NOTICES.md` 中增加：

```markdown
## TrackerControl Android

- Repository: https://github.com/TrackerControl/tracker-control-android
- Commit/Tag: <固定完整 SHA 或 tag>
- License: GPL-3.0
- Used files/modules: <实际使用范围>
- Local modifications: <修改说明>
- Purpose: VPN/TUN forwarding, DNS observation and blocking
- Included license file: YES
- Source availability: <对应源码目录或发布方式>
- Team-original boundary: event adaptation, context correlation, causal rules,
  evidence grading, mitigation recheck and UI
```

数据集单独登记：

```markdown
## Tracker classification dataset

- Source: <URL>
- Version/date: <版本或下载日期>
- Data license: <许可证>
- Transform script: <脚本位置>
- Fields retained: <字段>
- Attribution shown in app: YES
- Commercial-use restriction: <有/无>
```

---

# 16. 引入开源代码前的验收清单

- [ ] 仓库来自官方组织或可信维护者；
- [ ] 最近版本、issue 和 release 状态可接受；
- [ ] 已固定 commit/tag，不直接依赖浮动 `main`；
- [ ] 已读取根 LICENSE、NOTICE 和被复制文件的头部许可证；
- [ ] 许可证与本项目发布方式兼容；
- [ ] 能在固定演示机离线构建或有可靠缓存；
- [ ] 已记录实际复制/修改的文件；
- [ ] 已明确第三方能力和团队原创能力；
- [ ] 不因引入组件扩大 P0；
- [ ] 有失败时的降级或移除方案；
- [ ] 自动生成的许可证报告经过人工复核；
- [ ] 答辩材料没有把第三方网络底座声称为团队原创。

---

# 17. 最终建议

项目不应该“所有东西从零写”，也不应该“Fork 一个成熟项目后改名”。最合适的做法是：

> 使用 TrackerControl/NetGuard 解决成熟而高风险的网络基础设施；使用 Android 官方和宽松许可证库解决通用工程问题；把有限开发时间投入到统一事件、场景知识、证据等级、因果解释、处置复查和评测上。

在竞赛答辩中，应主动展示第三方与原创模块边界。合理使用开源代码不会削弱作品，反而能够说明团队具有工程选型、许可证治理和系统集成能力；真正影响原创性的，是是否能清楚证明团队在开源底座之上实现了新的、可验证的核心价值。
