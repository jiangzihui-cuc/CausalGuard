# CausalGuard：从“检测行为”到“解释风险、给出处置并复查”

> B7-4 答辩/PPT 核心页素材
> 本页压缩已有设计、评测和第三方边界事实，不替代 B8-3、B6-5 或 B6-6 原文。

## 一页答辩页正文

### 三个产品创新点

**1. 场景感知风险判断**

不是“敏感访问 = 恶意”。系统结合 `foreground/background/unused`、App scene、`scenarioMatch` 和证据等级，再给出风险与置信度。目标是减少简单权限扫描的误报；现有结果只能表述为固定 curated evaluation 上的确定性一致性，不能外推为现实世界误报变化。

**2. 证据链、因果链、Recommendation 与 Recheck**

```text
事实 → 场景判断 → 规则命中 → 证据/因果解释
    → Recommendation → 执行回执 → Recheck
```

每一步都可回到输入和证据。时间相关不是因果证明；Recommendation 不是 action executed；execution result 不是 verified effect。证据不足时降级为 `unknown`，不补写事实。

**3. AI 受约束，而不是 AI 决定事实**

AI 只接收白名单结构化事实，输出经过本地事实校验；越界、失败、未配置或断网时回退到本地 deterministic template。AI 不负责发现隐私泄露，也不替规则决定全部风险。

## 证据等级

下表沿用 `docs/02-android-capability-matrix.md` 的现有契约定义：

| 证据等级 | 来源/含义 | 可以证明什么 | 不能证明什么 |
|---|---|---|---|
| E1 · 系统事实 | 官方 API 直接返回 | 当前权限或系统状态等直接事实 | 不能证明 App 刚刚使用了该权限 |
| E2 · 授权观测 | 用户授权后通过 VPN/Usage 获得 | 观测到的网络连接元数据或使用上下文 | 不含请求内容；无法归属时不能确定具体 App |
| E3 · 规则推断 | 规则基于事实推导 | 当前规则为何得到某个分类、风险或场景判断 | 不是新的原始事件，不是因果或泄露证明 |
| E4 · 演示真值 | Demo App 提供的受控事件 | Demo App 自己执行了受控动作 | 不能外推为任意第三方 App 行为或真实数据外传 |
| E5 · 无法确认 | 证据不足或方法不可靠 | 当前证据边界和降级原因 | 不能补成安全、恶意、归因或泄露结论 |

**原则：证据不足时降级 `unknown`，而不是补事实。** `E3` 及以上的结论不使用“已发生泄露”等确定性措辞。

## 开源与原创边界

| 第三方复用 | 团队原创 | 边界与责任 |
|---|---|---|
| TrackerControl / NetGuard 网络底座；GPL-3.0 | 统一 `PrivacyEvent`、`RiskAssessment` 等业务契约中的团队设计部分 | 网络底座能力归第三方，不声称自研；最终 GPL/submodule/corresponding source/tag 核验已由 A8-4 完成，具体对应关系以 A8-4 的核对记录、固定 commit/tag 和最终源码包为准 |
| Disconnect Tracking Protection 的 tracker/domain 分类数据；CC BY-NC-SA 4.0 | 场景知识、scenario consistency、规则组合与安全 unknown degradation | 数据作者和数据许可证独立于代码许可证；tracker 命中只表示公开分类，不表示隐私泄露 |
| 其他依赖 | evidence chain、causal chain、Recommendation selection、mitigation/recheck orchestration | 具体版本、许可证和用途以 `THIRD_PARTY_NOTICES.md` 与 license report 为准 |
|  | explanation constraints、UI / Demo 编排、evaluation/failure registry | 不声称自研第三方底座、协议栈或数据集 |

## 评测数字：可说 / 不可说

### 可说

- `rules-v0.1` fixed curated historical set：25 cases；category/risk/scenario deterministic oracle agreement 均为 `25/25`，high-risk severity recall 为 `9/9`。
- `rules-v0.2` fixed curated boundary set：17 cases；category/risk/scenario deterministic oracle agreement 均为 `17/17`，high-risk severity recall 为 `6/6`；`necessary` support 为 0，recall 为 `N/A`。
- 两个版本合计 42 条 cross-version regression cases，不能跨版本合并成一个现实准确率指标。
- 12 条 synthetic AI guardrail cases：`ACCEPT=4`、`SANITIZE=1`、`REJECT=7`。
- 12 条 synthetic recheck cases：`reduced=3`、`no_change=2`、`blocked=1`、`unknown=6`；unknown 保持 unknown。
- real AI fact consistency 与真实 user mitigation duration 当前均为 `N/A`。

### 不可说

- 不能把 curated deterministic agreement 说成现实 Android 泛化准确率。
- 不能宣称现实误报、漏报已经被证明为零。
- 不能宣称 AI 事实一致性已经完成真实测量。
- 不能宣称处置后真实网络流量必然下降，或把 observation/recheck 等待时间当成用户处置耗时。

## 45～60 秒答辩口播

“CausalGuard 和普通权限检测的区别，不是多列出几个敏感权限，而是把行为放回使用场景里判断。比如前台地图定位和后台计算器联网，事实相同吗？风险含义并不相同。我们的结论还会沿着证据链和因果链回溯到事件、规则和场景，再给出处置建议，并在执行后区分回执和真正验证过的效果。AI 只处理白名单结构化事实，输出还要经过本地校验；没有网络或 AI 失败时，核心规则和本地解释仍然可用。网络底座和 tracker 数据按第三方许可证使用，团队原创的是场景判断、证据与因果链、处置复查和完整产品编排。我们展示的是可追溯、可降级的结论，而不是把一次联网直接说成隐私泄露。”

## 高频追问速答

### 1. 你们和普通权限检测工具有什么区别？

普通权限检测主要回答“声明了什么、当前是否授权”。CausalGuard 进一步结合前后台状态、App 场景、可观测网络元数据和证据等级，输出可追溯的风险解释与安全降级。敏感访问本身不会直接等于恶意。

### 2. 你们怎么证明不是 AI 在编？

AI 输入是白名单结构化事实，输出要经过本地事实校验，不能新增数字、App、事件类型或升级风险。模型未配置、断网、超时或越界时，系统回退到本地 deterministic template；核心事实不由 AI 创造。

### 3. 为什么一次后台联网不能直接判定隐私泄露？

VPN 主要提供连接、协议、域名线索和归属等元数据，不提供请求正文。时间相关或 tracker 分类只能作为伴随证据，不能证明具体敏感内容已经发送，所以系统保留证据边界并在不足时显示 unknown。

### 4. 用了 TrackerControl，那你们自己做了什么？

TrackerControl/NetGuard 提供网络底座能力；团队完成了统一事件契约、场景一致性、规则与 unknown 降级、证据链/因果链、处置复查、解释约束、UI 和评测登记。第三方代码、数据和团队原创边界分别登记在 notices 与边界文档中。

### 5. 你们的指标是不是现实准确率？

不是。25 条 `rules-v0.1` 和 17 条 `rules-v0.2` 是固定 curated set 上与项目 oracle 的 deterministic agreement，AI 和 Recheck 也都是 synthetic evaluation。它们用于回归和边界检查，不代表所有 Android App 的现实泛化结果。

