# 文档索引与阅读顺序

> 项目：隐私因果哨兵（Privacy Causal Sentinel）
> 文档基线版本：`v0.1`
> 最后更新：2026-09-20
> 依据：《软件工程实施规划-隐私因果哨兵》第 2 章“开发前必须先完成的文档”

本目录是编码前的设计基线。所有文档先冻结到 `v0.1`，两人确认后再进入开发。

## 依据规划的产出对照

本目录严格按《软件工程实施规划-隐私因果哨兵》第 2 章列出的产出文件建立。

| 规划中的产出文件 | 仓库对应 | 状态 |
|---|---|---|
| `docs/01-project-charter.md` | [01 项目章程](01-project-charter.md) | ✅ |
| `docs/02-android-capability-matrix.md` | [02 Android 能力边界表](02-android-capability-matrix.md) | ✅ |
| `docs/03-permission-and-consent-flow.md` | [03 权限与授权流程](03-permission-and-consent-flow.md) | ✅ |
| `docs/04-product-requirements.md` | [04 PRD](04-product-requirements.md) | ✅ |
| `docs/05-system-architecture.md` | [05 系统架构](05-system-architecture.md) | ✅ |
| `docs/06-module-design.md` | [06 模块设计](06-module-design.md) | ✅ |
| `docs/07-data-model.md` | [07 数据模型](07-data-model.md) | ✅ |
| `docs/08-database-schema.sql` | [08 数据库表结构](08-database-schema.sql) | ✅ |
| `docs/09-event-contract.md` | [09 事件契约](09-event-contract.md) | ✅ |
| `docs/10-risk-rule-contract.md` | [10 风险规则契约](10-risk-rule-contract.md) | ✅ |
| `docs/11-ai-explanation-contract.md` | [11 AI 解释契约](11-ai-explanation-contract.md) | ✅ |
| `docs/12-privacy-security-design.md` | [12 隐私与安全设计](12-privacy-security-design.md) | ✅ |
| `docs/13-test-plan.md` | [13 测试计划](13-test-plan.md) | ✅ |
| `docs/14-evaluation-dataset.md` | [14 评测数据集方案](14-evaluation-dataset.md) | ✅ |
| `docs/15-acceptance-checklist.md` | [15 验收清单](15-acceptance-checklist.md) | ✅ |
| `docs/16-demo-and-release-plan.md` | [16 演示与发布方案](16-demo-and-release-plan.md) | ✅ |

规划提到但未编号、由本仓库补充的文档：

| 仓库对应 | 来源 |
|---|---|
| [00 文档索引](00-index.md) | 规划要求“docs/ 目录 + 版本 v0.1” |
| [17 任务看板](17-task-board.md) | 规划阶段0要求“建立任务看板” |
| [18 风险清单](18-risk-register.md) | 规划阶段0要求“建立风险清单” |

## 阅读顺序

| 编号 | 文档 | 作用 | 主要责任人 |
|---|---|---|---|
| 01 | [项目章程与范围说明](01-project-charter.md) | 明确做什么、不做什么 | 成员 B |
| 02 | [Android 能力边界表](02-android-capability-matrix.md) | 每类数据的来源与可信度 | 成员 B |
| 03 | [权限与授权流程](03-permission-and-consent-flow.md) | 用户授权路径与拒绝降级 | 成员 B |
| 04 | [产品需求规格说明书（PRD）](04-product-requirements.md) | 功能需求与 P0/P1/P2 | 成员 B |
| 05 | [系统架构设计](05-system-architecture.md) | 模块边界与数据流 | 成员 A |
| 06 | [模块设计说明](06-module-design.md) | 各模块输入输出与接口 | 成员 A |
| 07 | [数据模型](07-data-model.md) | 事件与证据结构 | 成员 A |
| 08 | [数据库表结构](08-database-schema.sql) | Room/SQLite 表结构 | 成员 A |
| 09 | [事件契约](09-event-contract.md) | 采集写入事件库格式 | 成员 A |
| 10 | [风险规则契约](10-risk-rule-contract.md) | 规则输入输出格式 | 成员 A/B |
| 11 | [AI 解释契约](11-ai-explanation-contract.md) | 解释层输入输出格式 | 成员 B |
| 12 | [隐私与安全设计](12-privacy-security-design.md) | 数据最小化与合规 | 成员 B |
| 13 | [测试计划](13-test-plan.md) | 如何验证 | 成员 B |
| 14 | [评测数据集方案](14-evaluation-dataset.md) | 评测集与指标 | 成员 B |
| 15 | [验收清单](15-acceptance-checklist.md) | 完成的判定标准 | 两人共同 |
| 16 | [演示与发布方案](16-demo-and-release-plan.md) | 现场演示与提交 | 两人共同 |
| 17 | [任务看板](17-task-board.md) | 每日任务状态 | 成员 B |
| 18 | [风险清单](18-risk-register.md) | 风险与应对 | 成员 B |

## 按成员职责的文档对照

**成员 A（技术实现主责）负责看/改的文档**

| 文件 | 一句话说明 |
|---|---|
| [05 系统架构](05-system-architecture.md) | 分层、调用关系、数据流；你定模块边界 |
| [06 模块设计](06-module-design.md) | 每模块输入输出、接口、权限、测试；你写主要类/接口 |
| [07 数据模型](07-data-model.md) | 事件和证据的字段；你冻结数据结构 |
| [08 数据库表结构](08-database-schema.sql) | Room/SQLite 建表 SQL；你写 |
| [09 事件契约](09-event-contract.md) | 采集→事件库写入 JSON 格式；你定，让页面和规则可并行 |
| [10 风险规则契约](10-risk-rule-contract.md) | 规则引擎输入输出格式；你与成员 B 一起定 |
| [13 测试计划](13-test-plan.md) | 怎么测（VPN、权限拒绝、断网）；技术侧你验证 |
| [16 演示与发布](16-demo-and-release-plan.md) | 固定设备、版本一致性、打包；APK 归你 |

**成员 A 负责理解、照着做（成员 B 设计的输入）**

| 文件 | 一句话说明 |
|---|---|
| [01 项目章程](01-project-charter.md) | 做啥不做啥、P0 范围；你按它的范围做 |
| [02 能力边界表](02-android-capability-matrix.md) | 哪些能力真实可拿、哪些只能演示/标“无法确认”；你实现 |
| [03 权限授权流程](03-permission-and-consent-flow.md) | 申请哪些权限、拒绝怎么办；你写代码处理 |
| [04 PRD](04-product-requirements.md) | 功能需求 F1~F10；你要实现的功能清单 |
| [11 AI 解释契约](11-ai-explanation-contract.md) | AI 输入/输出、防编造；你实现接口与本地兜底 |
| [12 隐私安全](12-privacy-security-design.md) | 不存什么、怎么脱敏；你遵守的底线 |
| [14 评测集](14-evaluation-dataset.md) | 30-50 条样例与指标；你跑规则出结果 |
| [15 验收清单](15-acceptance-checklist.md) | 完成/提交判定标准；你对照自查 |
| [17 任务看板](17-task-board.md) | 你 T 开头的任务与状态 |
| [18 风险清单](18-risk-register.md) | 技术风险（VPN/UID/域名）；你重点关注 |

**成员 B（产品与智能分析主责）主写、成员 A 复核**：01、02、03、04、11、12、14（需求、场景、隐私、AI）——成员 A 按设计落地。

**两人共同复核**：15 验收清单、16 演示与发布、17 任务看板、18 风险清单。

> 核心区分：**架构和数据A定（05/06/07/08/09/10/13/16），需求、场景、隐私、AI 按成员 B 的设计落地（01/02/03/04/11/12/14）。**

## 契约冻结原则

以下三类契约必须先冻结，成员 A 和成员 B 才能并行工作：

1. 采集模块向事件库写入的事件格式（文档 09）；
2. 规则引擎读取事件并输出风险结果的格式（文档 10）；
3. 解释层读取结构化结果并输出文案的格式（文档 11）。

## 变更规则

- 任何新增功能必须先修改 01、04、07 和 15，再进入代码。
- 规则、数据模型和文档变更要与代码同一提交或同一版本记录。
- 文档版本统一以 `v0.x` 标记，冻结后在页首注明。
