# B8-4 评测与失败案例附录

> 状态：阶段 8 的评测附录源稿；不包含新的真机统计结果。
>
> 责任人：成员 B。
>
> 事实截止点：本文件基于 `origin/main` 的 `2b2b8510522b6f9ba9280bd1b5c5197c905c5bf7` 整理；未运行新的设备测试，也不把 fixture 结论表述为真实设备采集结果。
> 关联：[评测数据集方案](14-evaluation-dataset.md)、[验收清单](15-acceptance-checklist.md)、[任务看板](17-task-board.md)、[B6-5 评测指标](b6-5-evaluation-metrics.md)、[B6-6 失败分析](b6-6-failure-analysis.md)。

---

## 1. 目的、范围与限制

本附录为 B8-4 的最终材料源稿和真机结果填写模板，汇总当前仓库已有的自动化软件测试、确定性 fixture 规则评测、synthetic AI guardrail 与处置复查评测，以及 failure registry。其目的是让评审能复查“输入—规则—oracle—结论”的对应关系，同时清楚区分已验证的软件层事实、受控 fixture 结论和仍需补齐的真机证据。

本附录不新增评测代码、数据、依赖、媒体或真实设备结论。以下限制始终有效：

- curated fixture 的 oracle 是项目定义的契约标签；一致性是 deterministic oracle agreement，不是现实 Android 应用的泛化准确率、误报率或漏报率。
- synthetic AI guardrail 不联网、不调用真实模型，因此不能代替真实 AI 事实一致率；后者当前为 `N/A`。
- synthetic recheck 的 allowed-count 变化不是用户实际操作耗时，也不是设备上的流量下降比例。
- Demo/沙箱事件只能证明 Demo App 在受控条件下的行为；VPN、系统 API 和 UsageStats 的观测事实另有各自边界，详见 [Demo 场景](demo-scenarios.md)。
- 本轮没有执行真机回归、APK 安装、截图、视频或成员 A 的设备数据采集；所有此类字段均保留为“待 A 提供”。

## 2. 测试层次与证据边界

| 层次 | 被测对象 | 当前仓库证据 | 本附录可作出的结论 | 不能据此作出的结论 |
|---|---|---|---|---|
| 契约层 | `PrivacyEvent`、`RuleInput`、规则资产、context 与 oracle 的隔离 | [事件 fixture](fixtures/privacy-events-v0.1.json)、[预期旁路](fixtures/privacy-events-v0.1.expected.json)、`RuleFixtureContractTest`、`RuleEvaluationDatasetTest` | 输入可解析，输出 oracle 未混入规则输入，版本与 ID 对应可校验 | Android 采集一定正确或所有 App 均可归属 |
| 规则层 | `RuleEvaluator`、多规则合并、unknown 降级、处置选择 | `RuleEvaluatorTest`、`RuleEvaluationDatasetTest`、`EvaluationMetricsTest`、[规则资产](fixtures/risk-rules-v0.1.json) | 固定输入下的风险、命中规则、解释/降级相关输出与项目 oracle 一致 | 真实世界准确率、所有设备上的检出率 |
| 软件层 | `core-model`、app 数据/Room/UI、Demo 状态机、规则引擎 | 各模块 `src/test/`；[A7 RC 记录](a7-release-candidate.md) §6 | 已有单元测试和历史 RC 回归记录覆盖模块行为 | 本轮已重新完成 release 构建或真机验证 |
| 评测/复查层 | synthetic AI validator 与 `RecheckComparator` | `EvaluationMetricsTest`、`RecheckEvaluationDatasetTest`、recheck fixtures | 固定 candidate/oracle 与 pre/post 样例的确定性一致性 | 真实模型事实一致率、真人处置时长、真实网络效果 |

## 3. v0.1 fixture 与评测覆盖

### 3.1 资产与版本

| 分组 | 输入 / 上游 context / 输出 oracle | 规则版本 | case 数 | 用途 |
|---|---|---:|---:|---|
| frozen v0.1 | [privacy-events](fixtures/privacy-events-v0.1.json) / [rule-input-context](fixtures/rule-input-context-v0.1.json) / [expected](fixtures/privacy-events-v0.1.expected.json) | `rules-v0.1` | 10 | 固定事件 fixture 与页面/导入/规则回归 |
| historical extension | `evaluation-events-v0.1.json` / `evaluation-context-v0.1.json` / `evaluation-expected-v0.1.json` | `rules-v0.1` | 15 | 历史规则扩展回归 |
| current boundary | `evaluation-boundary-events-v0.1.json` / `evaluation-boundary-context-v0.1.json` / `evaluation-boundary-expected-v0.1.json` | `rules-v0.2` | 17 | 当前运行时边界、版本化行为和 unknown 降级 |

三组共 42 条；其中 `rules-v0.1` 的 25 条仅作 historical regression，`rules-v0.2` 的 17 条才是当前边界集。因规则版本不同，42 条只能称为 cross-version regression health，**不得**合并为一个规则版本的准确率。

### 3.2 场景覆盖矩阵

| 覆盖目标 | 当前固定资产中的证据 | 评测边界 |
|---|---|---|
| normal | 三组 oracle 合计 10 条 `kind=normal`；包括前台地图定位/网络等低风险或仅上下文事实 | “normal” 不表示真实应用一定安全，只表示当前受控输入未命中风险规则 |
| risk | 22 条 `kind=abnormal`，另有 2 条 `kind=scenario_mismatch`；42 条中 15 条预期风险为 `high` 或 `critical` | 只检验项目规则与人工定义 oracle 的一致性，不断言已发生泄露或恶意行为 |
| unknown | 8 条 `unknown_boundary`，6 条 `context_only_unknown`；unknown category/scenario support 共 14 条 | unknown 是合法安全降级；不得强行归属、推断数据流向或生成确定性处置 |
| Demo | frozen v0.1 有 4 条 `isDemo=true`：`e-20260921-0001`、`0003`、`0007`、`0009` | 仅为受控 Demo 真值/离线 fixture；展示时须保留 Demo/沙箱标识 |

`RuleEvaluationDatasetTest` 还覆盖 normal、abnormal、scenario mismatch 与 unknown 类别存在性；boundary 集包含 R-003 独立正例、R-006 `unused` 正/负例、R-007 的 59,999/60,000/60,001 ms 边界、R-008 unknown attribution、R-009 prior-event 边界、E5 和显式 unknown，详见 [评测数据集方案](14-evaluation-dataset.md) §2。

### 3.3 frozen v0.1 事件—规则—解释对应

下表只摘录当前 10 条 frozen fixture 的测试 oracle；事件正文、上游 context 与 output oracle 保持物理分离，不能把右侧预期字段写入 `PrivacyEvent`。

| eventId | 场景/类型 | 预期风险与分类 | 命中规则 | 解释模板 / 降级要求 |
|---|---|---|---|---|
| `e-20260921-0001` | Demo Map 前台 `location` | `low` / `necessary` | R-001 | 前台地图场景；保留 Demo 标识 |
| `e-20260921-0002` | 地图前台 `network` | `low` / `necessary` | R-001 | 仅 VPN 元数据，不读取请求内容 |
| `e-20260921-0003` | Demo Calculator 后台 `clipboard` | `high` / `high_risk` | R-007、R-002 | 时间相关不证明内容外传；Demo 标识 |
| `e-20260921-0004` | Calculator 后台 `network` | `high` / `high_risk` | R-007、R-003、R-005 | 场景不匹配/分析域名线索，不声称泄露 |
| `e-20260921-0005` | Flashlight `unused` 下 `network` | `high` / `analytics` | R-006、R-005 | 仅元数据和分类线索，不读取通信内容 |
| `e-20260921-0006` | 无可靠归属的 `network` | `low` / `unknown` | R-008、R-010 | 展示 unknown 降级；无确定性处置 |
| `e-20260921-0007` | Demo Notes 后台 `contacts` | `medium` / `high_risk` | R-002 | 只记录计数/事件类型；保留 Demo 标识 |
| `e-20260921-0008` | Weather permission context | `low` / `unknown` | 无 | 上下文事实，不单独报警 |
| `e-20260921-0009` | Demo Weather 撤权后 `location` | `high` / `high_risk` | R-002、R-009 | 不宣称已泄露；保留 Demo 标识 |
| `e-20260921-0010` | Reader `usage_context` | `low` / `unknown` | 无 | supporting context，不单独报警 |

上述事件的事件专属/本地解释素材位于 [explanation-templates-v0.1.json](fixtures/explanation-templates-v0.1.json)；解释仍受 [AI 解释契约](11-ai-explanation-contract.md) 与本地事实校验约束，不能把时间相关改写成外传证明。

## 4. 可复现自动化命令与已确认记录

### 4.1 当前 main 中的可复现命令

| 命令 | 覆盖范围 | 证据位置 | 本轮执行状态 |
|---|---|---|---|
| `./gradlew :rule-engine:testDebugUnitTest` | 规则、fixture、评测指标、failure registry、解释和复查比较器 | [B6-5](b6-5-evaluation-metrics.md)、[B6-6](b6-6-failure-analysis.md)、[任务看板](17-task-board.md) B6-4～B6-6 | 未在本轮重新执行；已有 main 记录为通过 |
| `./gradlew test` | `app`、`core-model`、`demo-app`、`rule-engine` 的 Gradle 单元测试任务 | [A8 构建说明](a8-build-and-reproducibility.md) §2、§4；[CI 配置](../.github/workflows/ci.yml) | 未在本轮重新执行；命令可复现 |
| `./gradlew :app:lintDebug` | app 静态检查 | [A8 构建说明](a8-build-and-reproducibility.md) §2、§4 | 未在本轮重新执行；命令可复现 |
| `gradle build --stacktrace` | CI 的构建与单元测试门禁 | [CI 配置](../.github/workflows/ci.yml) | 未在本轮重新执行；CI 使用官方 Gradle 9.6.1 |

这些均为软件层命令，不需要连接真机；执行仍依赖 JDK 17、Android SDK platform 37 / build-tools 37.0.0 等环境，完整前置见 [A8 构建说明](a8-build-and-reproducibility.md) §1～§2。

### 4.2 已确认通过的历史记录（非本轮重跑）

| 记录来源 | 已确认的结论 | 适用边界 |
|---|---|---|
| [B6-5](b6-5-evaluation-metrics.md) | `rules-v0.1`：category/risk/scenario exact-match 均为 25/25，高风险 severity recall 9/9；`rules-v0.2`：三项 exact-match 均为 17/17，高风险 severity recall 6/6 | 固定 curated oracle agreement；`rules-v0.2` 的 necessary support 为 0，recall 为 `N/A` |
| [B6-5](b6-5-evaluation-metrics.md) | 12 条 synthetic AI guardrail：`ACCEPT=4`、`SANITIZE=1`、`REJECT=7`，与独立 oracle 一致 | 仅 validator disposition；真实 AI fact consistency 为 `N/A` |
| [B6-5](b6-5-evaluation-metrics.md) | 12 条 synthetic recheck：`reduced=3`、`no_change=2`、`blocked=1`、`unknown=6`；6 条 confirmed case 用于 allowed-count 变化 | 不等同于真实网络流量或用户处置耗时；后者为 `N/A` |
| [B6-6](b6-6-failure-analysis.md) | `FailureCaseAnalysisTest` 动态分析 42 条规则样例，failure registry 与分类一致 | 未观察到 curated severity over/under disagreement，不可写为“系统零误报/零漏报” |
| [A7 RC 记录](a7-release-candidate.md) §6 | 2026-10-07 的 RC 历史记录：`./gradlew test` 为 261 通过 / 0 失败 / 0 错误；`./gradlew :app:lintDebug` 为 0 error / 4 warning | 历史 RC 记录，不是本轮或最终冻结 commit 的新结果；最终提交前须按 [验收清单](15-acceptance-checklist.md) 重跑 |

## 5. 失败案例、unknown 边界与校准

### 5.1 当前 failure registry 的可复查状态

机器可读登记为 [evaluation-failure-cases-v0.1.json](fixtures/evaluation-failure-cases-v0.1.json)。`FailureCaseAnalysisTest` 真实加载三套资产、构造 `RuleInput`、运行 `RuleEvaluator`，再与各自 oracle 比对；登记文件只保存 case/event 引用和分类，不复制完整敏感事件。

| 案例类别 | 当前数量 | 输入与预期 | 实际 / 原因 | 修复状态与复现 |
|---|---:|---|---|---|
| `false_positive` | 0 | 没有当前 curated severity over-escalation 案例 | 当前 42 条固定样例中未观察到 | 非“现实误报率 0%”；运行 `./gradlew :rule-engine:testDebugUnitTest` 复查 |
| `false_negative` | 0 | 没有当前 curated severity under-escalation 案例 | 当前 42 条固定样例中未观察到 | 非“现实漏报率 0%”；同上 |
| `classification_disagreement` | 0 | 没有 severity 相同但 category/scenario 不同的当前案例 | 当前 42 条固定样例中未观察到 | 不因追求分数而修改规则；同上 |
| `unknown_boundary` | 8 | 缺归属、无域名、窗口越界、E5 或场景不足等显式边界 | 实际保持 `unknown` 降级，符合 oracle | `NO_CHANGE`；见 registry 的 `fixture-e-...0006`、`eval-case-0008` 与 6 个 `boundary-case-*` |
| `context_only_unknown` | 6 | permission / usage 等支持性上下文 | 实际不单独生成风险规则结论，符合 oracle | `NO_CHANGE`；见 registry 的 6 个 context-only 条目 |

`unknown_boundary` 与 `context_only_unknown` 不是失败率，也不自动计入 false negative。当前生产规则校准决定为 `NO_CHANGE`：R-006 的 `lastUsedAgoMs` 是语义/契约缺口，R-009 没有已定义的时间阈值；在没有 independent evidence 或 blind label 前，不发明阈值、不为了提高 curated 指标修改生产规则。

### 5.2 后续失败案例填写模板

发现独立证据支持的 disagreement 后，按下表登记，并保留输入资产、ruleVersion、复现命令和结论边界；不得覆盖既有 oracle 或删除不利案例。

| 案例编号 | 输入（脱敏 caseId/eventId、ruleVersion） | 预期（独立依据） | 实际（规则输出） | 原因分析 | 是否修复 | 复现方法 |
|---|---|---|---|---|---|---|
| `待登记` | `待提供` | `待独立标注` | `待运行记录` | `待分析` | `待决定` | `./gradlew :rule-engine:testDebugUnitTest` 或冻结的专用测试命令 |

## 6. 真机评测待补表（成员 A 提供）

本表故意不填写任何设备、APK、网络或真实采集数字。A7 的 RC 历史记录不能替代最终冻结 tag 上的回归；待 A8-1、A8-2、A8-5 完成后，再将可核验原始证据链接到下表。

| 必填项 | 当前状态 | 最终需提供的可复查证据 |
|---|---|---|
| 设备型号与 Android/API 版本 | **待 A 提供** | 设备标识、系统版本、测试日期与权限状态 |
| APK 版本、构建 commit 与 SHA-256 | **待 A 提供** | 最终 release APK、versionName/versionCode、commit/tag、哈希 |
| 网络/VPN 状态 | **待 A 提供** | 网络类型、VPN 启停、Usage Access、必要的无敏感日志 |
| 真实采集结果 | **待 A 提供** | 脱敏 `NetworkEvent` / `PrivacyEvent`、归属/域名可用性与 unknown 降级记录 |
| 失败日志与恢复结果 | **待 A 提供** | 可脱敏失败日志、复现步骤、恢复操作与结果 |
| 真实 AI/处置测量（如启用） | **待 A 提供** | 固定模型/version 输出快照与人工标签；真人开始/结束时间，不用 observation window 代替 |

## 7. 与最终验收的映射

| [验收清单](15-acceptance-checklist.md) 项 | 本附录提供的证据 | 最终仍需完成的工作 |
|---|---|---|
| §3：合理/不匹配场景、处置后的可观察复查、事实/推断/不可观测分离 | fixture、规则 oracle、unknown 降级与 synthetic recheck | 在最终 APK / 真机上按脚本复验，不能以 fixture 替代 |
| §4：APK 可安装、核心流程、断网基础摘要、Demo/真实数据区分 | 软件层测试入口、Demo/fixture 标识规则 | A8-1 / A8-2 最终设备与 APK 核验 |
| §5：测试结果与全生命周期材料 | 本附录、B6-5、B6-6、可复现命令 | 以最终 tag 重跑并填入实际证据 |
| §7：源码可编译、APK/源码版本一致 | CI/Gradle 命令与 A7 历史基线 | A8-2 以最终 tag 重新构建、打包和记录哈希 |
| §8：提交材料与 APK 实际功能一致 | 本文明确区分 fixture、Demo、历史 RC 与待 A 数据 | B8-5 对文案、截图、视频和最终 APK 逐项核对 |

## 8. 提交前更新规则

1. 只在最终冻结 commit 上重跑软件层命令；将命令、日期、commit 和原始结果填回最终提交材料，不能复用本轮写作日期代替执行证据。
2. 真机表只接受成员 A 提供的脱敏可复查材料；无数据即保留“待 A 提供”，不填估算值。
3. 规则版本、事件输入、context、oracle 和解释模板必须一同记录，防止把 `rules-v0.1` 历史回归误写为 `rules-v0.2` 当前结果。
4. 在截图、视频、设计 PDF 和答辩中，使用“固定 curated oracle agreement”“synthetic recheck”等限定语；不得使用“真实准确率”“零误报”“已证明泄露”等夸大表述。

## 9. 开源复用声明

**本任务未新增开源代码、第三方依赖、第三方数据或第三方模板。**

本文仅整理仓库现有的项目 fixture、测试和评测登记；其第三方来源、许可证与原创边界仍以 [第三方与原创边界说明](b8-3-third-party-original-boundary.md)、[第三方声明](../THIRD_PARTY_NOTICES.md) 和 A8-4 的最终核对材料为准。
