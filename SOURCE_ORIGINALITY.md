# 源代码原创说明（SOURCE_ORIGINALITY）

> 作品名称：隐私因果哨兵 Privacy Sentinel（CausalGuard）
> 作品版本：v2.0.0（阶段 1：自研网络底座）
> 用途：随源码包提交，说明本作品源码中**团队原创部分**与**第三方来源边界**。
> 配套文档：`THIRD_PARTY_NOTICES.md`（详细第三方登记）、`docs/`（设计基线）、Design Doc 中的第三方清单。

## 1. 一句话总览

v2.0 阶段 1 起，本作品采用**自研网络底座 + 上层原创**的策略：**网络事实采集、UID 归属与域名阻断**由团队自研（`net-core` 模块 + `app/.../data/network/selfhosted/`，Apache-2.0），不再复用任何第三方网络底座；**统一事件模型、场景推理、风险规则、证据链/因果链、解释约束、处置复查、UI、Demo 与评测集**同样均为团队原创。仓库已移除 TrackerControl/NetGuard（GPL-3.0）submodule 及补丁，整体许可证转为 Apache-2.0。

## 2. 模块结构（本源码包内容）

```text
CausalGuard/
├─ core-model/      纯 Kotlin 契约模块（原创）
├─ rule-engine/     确定性规则与解释引擎（原创）
├─ net-core/        自研网络底座核心：L3/L4 报文解析、DNS 解析、/proc 归属、域名阻断（原创）
├─ app/             Android 主应用（原创）
├─ demo-app/        演示沙箱 App（原创）
├─ docs/            设计文档、契约、评测集与 fixtures（原创）
├─ scripts/         构建/打包/校验/数据生成脚本（原创）
├─ README.md、CONTRIBUTING.md、THIRD_PARTY_NOTICES.md、LICENSE（Apache-2.0）
└─ SOURCE_ORIGINALITY.md（本文件）
```

## 3. 团队原创内容清单

### 3.1 `core-model/` —— 统一契约（原创）

`com.causalguard.core.model` 下的 `PrivacyEvent` 统一事件模型、`RiskAssessment`、`MitigationApi`、`RuleApi`、`Repositories`、`Enums`、`Sources`、`SchemaVersion`、`ContractJson` 等契约接口与数据模型，全部为团队设计实现。

### 3.2 `rule-engine/` —— 规则与解释引擎（原创）

- `RuleEvaluator`、`RuleAssetLoader`：确定性本地规则匹配与资产加载；
- `SceneKnowledge`：场景知识库与场景一致性判断；
- `EvidenceChainBuilder`：证据链（`EvidenceLink`、E1–E5 证据等级）；
- `CausalChainBuilder`：因果链（事实节点 / 推断节点、unknown 降级）；
- `RecommendationSelector`：可逆处置建议选择；
- `RecheckComparator`：处置前后复查对比（reduced/no_change/blocked/unknown）；
- `explain/`：本地确定性解释模板、AI 输入白名单、`ExplanationFactValidator` 本地事实校验、可选 AI Provider 编排。

以上算法、规则内容、阈值、降级策略与评测口径均为团队原创。

### 3.3 `net-core/` —— 自研网络底座核心（原创）

`com.causalguard.net.core` 下的纯 Kotlin 网络核心，全部为团队自研、无第三方底座代码：

- `packet/`：IPv4/IPv6 + TCP/UDP 报文解析（含 IPv6 扩展首部、分片处理）与 IPv4/UDP 报文构造（校验和）；
- `dns/`：DNS 报文解析（含名字压缩指针）与本地 NXDOMAIN 响应构造；
- `proc/`：`/proc/net/{tcp,udp,tcp6,udp6}` 解析与 UID 归属索引（五元组 + 本地端点退化匹配）；
- `block/`：域名阻断判定（含子域匹配、规范化）。

配套 `app/.../data/network/selfhosted/` 提供 `VpnService`、DNS 代理、`/proc`+`ConnectivityManager` UID 归属与事件桥。

### 3.4 `app/` —— Android 主应用（原创）

- `profile/`：App 能力画像（PackageManager）；
- `usage/`：使用上下文（UsageStatsManager）；
- `network/`、`data/network/selfhosted/`：自研网络底座适配层（`SelfHostedVpnService`、DNS 代理、UID 归属、事件桥）与事件映射（`NetworkEvent` → `PrivacyEvent`）；
- `data/`：Room 数据库、DAO、Entity/Mapper、Repository、迁移（含 v2→v3）；
- `data/tracker/`：`DomainNormalizer`、`TrackerClassifier`、tracker 数据集加载；
- `analysis/`：事件分析服务与观测状态；
- `mitigation/`：`DeviceMitigationExecutor`、`DomainBlockController`、`SelfHostedDomainBlockController`、`AppSettingsLauncher`（可逆处置与诚实回执）；
- `explain/`：在线 AI 安全网络层（Retrofit/OkHttp、无 Body 日志、密钥注入）；
- `service/`：网络监测前台服务与连通性监听；
- `di/AppContainer`：手工依赖注入；
- `ui/`：Compose + Material3 页面、ViewModel、状态与文案。

UI、业务逻辑、状态管理、事件关联与处置编排均为团队原创。

### 3.5 `demo-app/` —— 演示沙箱（原创）

提供可重复、带真值的 4 类受控场景（前台合理访问位置、后台延迟访问位置、访问敏感数据但不联网、不联网但后台联网），用于现场演示完整闭环；所有演示事件在界面显式标注"演示数据"。

### 3.6 文档、资产与脚本（原创）

- `docs/`：项目章程、PRD、架构、数据模型、事件/规则/AI 契约、隐私安全设计、测试与评测计划、验收清单、第三方边界说明等；
- `docs/fixtures/` 与 `app/src/main/assets/`：风险规则（`risk-rules-v0.1/v0.2`）、场景知识（`scene-knowledge-v0.1`）、解释模板、评测事件与预期 oracle（42 条规则评测、12 条 AI 护栏、12 条复查样例）等，除 tracker 域名分类数据外均为团队原创；
- `scripts/`：`package-source.sh`、`build-release-rc.sh`、`verify-clean-submission.sh`、`verify-no-gpl-residue.sh`、`generate-tracker-dataset.py` 等构建、打包、合规校验与数据生成脚本。

## 4. 团队原创边界声明

以下为团队原创，非第三方能力：

1. `PrivacyEvent` 统一事件模型与事件适配层；
2. UsageStats 与网络行为的时间关联；
3. 场景知识库与场景一致性判断；
4. 证据等级（E1–E5）与证据链构建；
5. 风险规则内容、阈值与因果解释；
6. 处置建议与处置后复查算法；
7. Ground Truth Demo 场景；
8. 产品 UI、解释文案、评测数据集；
9. 受约束的 AI 解释管线（白名单输入 + 本地事实校验 + 本地模板兜底）。

## 5. 第三方来源边界（摘要）

> 完整来源、版本、许可证与用途见随附的第三方清单与 `THIRD_PARTY_NOTICES.md`。本源码包**不包含**任何第三方网络底座源码。

| 类别 | 第三方 | 许可证 | 边界 |
|---|---|---|---|
| 网络底座 | —（团队自研 `net-core`） | Apache-2.0（本作品） | 自研 `VpnService` + L3/L4 解析 + `/proc` 归属 + DNS 阻断；非第三方能力 |
| tracker 分类数据 | Disconnect Tracking Protection | CC BY-NC-SA 4.0 | 仅公开域名分类；非商业、保留 ShareAlike |
| 平台与库依赖 | AndroidX、Compose、Room、kotlinx、Retrofit/OkHttp 等 | Apache-2.0 等 | 通过构建系统引入，未复制改写源码 |

v2.0 阶段 1（2026-10-16）已移除 TrackerControl/NetGuard（GPL-3.0）网络底座 submodule、团队补丁与相关脚本，运行时代码不再引用任何第三方底座组件，仓库整体许可证转为 Apache-2.0。

## 6. 声明

本源码包中的代码除上述第三方来源外，均为团队原创。答辩与设计文档中不将第三方网络底座声称为团队原创。最终第三方来源、版本、许可证以各版本内 `LICENSE`/`NOTICE` 及 `THIRD_PARTY_NOTICES.md` 为准。
