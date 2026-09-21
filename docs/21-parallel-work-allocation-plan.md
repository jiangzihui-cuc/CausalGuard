# 21 双人并行开发分工、开源复用与 Git 协作规范

> 版本：`v0.1`
> 最后更新：2026-09-21
> 责任人：两人共同
> 依据仓库快照：`ae9ef69014a1c567a346edca7661b8589372ef3c`
> 阶段依据：[19 工作导引](19-work-guide.md)、[17 任务看板](17-task-board.md)、[16 演示与发布方案](16-demo-and-release-plan.md)、[20 开源复用接入建议](20-open-source-reuse-guide.md)
> 成员 A：平台、网络、系统能力与发布主责
> 成员 B：产品、规则、证据、UI、评测与演示主责

---

## 1. 结论：保留阶段 0～8，调整阶段内分工

仓库当前阶段顺序符合实际开发依赖：

```text
阶段 0  范围冻结
   ↓
阶段 1  高风险技术 Spike
   ↓
阶段 2  需求、架构与契约冻结
   ↓
阶段 3  模拟数据跑通 MVP
   ↓
阶段 4  真实数据替换模拟输入
   ↓
阶段 5  场景推理、因果链、处置复查
   ↓
阶段 6  AI、评测与可用性
   ↓
阶段 7  发布候选与完整演示
   ↓
阶段 8  最终提交
```

不需要重新发明阶段，但必须修正现有任务分配：

1. 阶段 1 不从零实现 VPN/TUN，改为优先构建和裁剪 TrackerControl/NetGuard。
2. 阶段 2 只冻结影响编码的核心设计和契约，不重新撰写整套文档。
3. 阶段 3 将 UI、规则和 Demo App 移交 B，避免 A 同时承担所有可运行代码。
4. 阶段 5 将证据链、因果链和复查判断交给 B；A 只负责真实阻断、设置跳转和持久化。
5. 阶段 6 的本地解释模板归 B；A 集中处理 VPN 稳定性和真机缺陷。
6. B 始终使用 fixture、Fake Repository 和 Fake Executor，不等待 A 的真实 VPN。
7. A 始终通过日志、JSON 导出、DAO/Adapter 测试验收，不等待 B 的页面。

---

## 2. 开源复用总方案

### 2.1 推荐组合

| 层 | 推荐方案 | 主责 | 使用方式 |
|---|---|---|---|
| VPN/TUN、TCP/UDP、DNS、连接记录、阻断 | TrackerControl / NetGuard | A | 固定完整 commit；优先以成熟工程为网络底座，不从零写协议栈 |
| App 信息、权限能力 | Android `PackageManager` + 官方 samples | A | 平台 API；示例仅用于正确用法参考 |
| 使用上下文 | `UsageStatsManager` + 官方 samples | A | 输出前台、后台、近期、长期未使用、unknown |
| 数据库 | AndroidX Room | A | Entity、DAO、Repository、migration 测试 |
| JSON | kotlinx.serialization | A 维护公共模型，B 维护业务数据 | 契约、规则、fixture、评测统一使用 |
| UI | Jetpack Compose + compose-samples | B | 参考状态提升和组件拆分；页面和视觉原创 |
| 规则 | 团队自研纯 Kotlin 小型规则引擎 | B | P0 约 5 条高质量规则；Easy Rules 仅作备选 |
| tracker 分类 | TrackerControl 数据管线，或 Disconnect / DuckDuckGo 二选一 | B | 固定版本并生成精简离线表 |
| 在线解释 | Retrofit + OkHttp | A 配置，B 实现解释 Provider | P1；只传白名单 DTO；失败回退本地模板 |
| 测试 | JUnit、AndroidX Test、Turbine | 各自模块主责 | 功能与测试同一 PR |
| 许可证 | AboutLibraries、Gradle License Report + 人工登记 | A 生成，B 汇总 | 自动扫描不能替代人工核对 |

### 2.2 开源使用底线

- TrackerControl/NetGuard 为 GPL-3.0 路线。复制、修改或组合进 APK 后，应保留版权和许可证、提供对应源码，并记录实际使用和修改的文件。
- DuckDuckGo、Disconnect 等 tracker 数据常见为 CC BY-NC-SA 4.0；必须记录版本、署名和非商业限制。
- 所有第三方组件必须固定 tag 或完整 commit SHA，禁止长期跟随浮动 `main`。
- 第三方源码首次导入单独一个 commit；团队修改另起 commit。
- `THIRD_PARTY_NOTICES.md` 至少记录：来源、版本、许可证、使用范围、修改内容和团队原创边界。
- 团队原创重点是统一事件、使用上下文关联、场景规则、证据等级、因果解释、处置复查、UI、Demo 和评测。

---

## 3. 消除等待的工程契约

两人只通过三个核心结构汇合：

```text
PrivacyEvent → RiskAssessment → ExplanationResult
```

每个契约必须包含：

- Kotlin 数据类；
- JSON 示例；
- 字段说明和数据来源；
- `schemaVersion`；
- null/unknown 处理；
- 正常样例、风险样例和降级样例。

### 3.1 单一编辑者

| 公共内容 | 编辑者 | 复核者 |
|---|---|---|
| `PrivacyEvent`、事件 schema | A | B |
| `RiskAssessment`、规则 schema | B | A |
| `ExplanationResult` | B | A |
| Gradle、模块注册、版本目录 | A | B |
| Room Entity、DAO、migration | A | B |
| 风险规则、场景知识、tracker 精简表 | B | A |
| release 版本、APK、tag | A | B |
| 最终验收、视频、答辩材料 | B | A |

同一公共文件同一时间只有一个编辑者。另一人通过 issue 或 PR 评论提出需求，避免两个分支同时修改。

### 3.2 Fake/Real 双实现

| 接口 | B 的 Fake 实现 | A 的 Real 实现 |
|---|---|---|
| `EventRepository` | fixture JSON 回放 | Room + Package/Usage/VPN Adapter |
| `MitigationExecutor` | 明确标记 `DEMO` | 域名阻断、系统设置跳转 |
| `ExplanationProvider` | 本地确定性模板 | 可选在线 AI Provider |
| `TrackerClassifier` | 固定离线精简表 | 后续可替换完整数据管线 |

真实能力缺失时使用 `UNKNOWN`、`UNAVAILABLE` 或 `DEMO`，不得伪造域名、UID、权限访问或阻断结果。

---

## 4. 阶段 0：项目启动与范围冻结（9/20）

### 客观评价

阶段位置合理，仓库也已基本完成。但 `T0-7：设计基线文档 01～16 冻结 v0.1` 与阶段 2 的“再次冻结”冲突。

### 调整

将 T0-7 改为：

> 设计基线 01～16 已形成 v0.1 草案；阶段 1 Spike 后修订，阶段 2 正式冻结跨模块契约。

### 成员 A

- 确认 Android 10/API 29、演示机型号、ABI、JDK、Gradle、AGP、NDK；
- 确认 TrackerControl/NetGuard GPL 路线是否接受；
- 建立受保护的 `main`、CI 最小构建、PR 模板和 `.gitignore`；
- 固定初始第三方候选版本。

### 成员 B

- 冻结 P0/P1/P2、真实/沙箱模式和排除项；
- 修正主演示案例：其他 App 的剪贴板/位置访问只能由 Demo App 提供沙箱真值；
- 建立 `THIRD_PARTY_NOTICES.md` 模板；
- 维护任务看板和风险清单。

### 阶段门

- 两人对能力边界理解一致；
- 接受 GPL 路线，或明确选择风险更高的宽松许可证备选；
- `main` 不允许直接开发；
- P0 不包含 TLS MITM、本地大模型、PDF、每日摘要和多平台。

---

## 5. 阶段 1：技术可行性 Spike（9/21～9/22）

### 客观评价

阶段顺序正确，但当前任务 T1-4“建立最小 VpnService”不符合“不从零写网络栈”的既定方向。A 的五项技术 Spike 也需要明确优先级和止损时间。

### 成员 A：高风险真实能力

| 编号 | 任务 | 交付物 | 独立验收 |
|---|---|---|---|
| A1-1 | 固定 TrackerControl commit/tag 并构建 | commit、环境记录、APK | 演示机启动 VPN 后可联网 |
| A1-2 | 定位 VPN、连接、DNS、UID、阻断入口 | `docs/network-core-map.md` | 能指出类、回调、数据流 |
| A1-3 | PackageManager Spike | 日志/JSON | App、UID、版本、声明权限和授权状态 |
| A1-4 | UsageStats Spike | 日志/JSON | 前后台或明确 unknown/失败原因 |
| A1-5 | 输出最小 NetworkEvent | 脱敏 JSON | 时间、协议、IP/域名线索、端口、UID/unknown |
| A1-6 | 最小阻断验证 | 日志 | 至少一个测试域名可阻断并保留尝试记录 |
| A1-7 | 开源技术登记 | 第三方修改清单 | NDK/JNI/Rust/数据依赖可追溯 |

优先级：

```text
TrackerControl 能否构建联网
→ 能否产生连接事件
→ 能否阻断
→ PackageManager / UsageStats 补充验证
```

### 成员 B：完全不等待 VPN

| 编号 | 任务 | 交付物 | 独立验收 |
|---|---|---|---|
| B1-1 | 定义 v0.1 事件 fixture | 8～12 条 JSON | 正常、风险、unknown、沙箱均覆盖 |
| B1-2 | 定义 4 个 Demo 场景 | `demo-scenarios.md` | 操作、真值、预期证据、失败降级完整 |
| B1-3 | 证据文案模板 | 模板 JSON/文档 | 事实、能力、推断、不可观测不混写 |
| B1-4 | 第三方组件登记 | `THIRD_PARTY_NOTICES.md` | 仓库、版本、许可证、用途齐全 |
| B1-5 | UI 状态草图 | 页面状态表 | loading/empty/unknown/demo/error 均定义 |

### 唯一集成点

A 导出的真实 NetworkEvent 必须可以映射到 B 的 fixture schema。不一致只修改 Adapter 或契约，不重写 VPN、规则或 UI。

### 止损

- 24 小时内 TrackerControl 必须能构建并联网；
- 48 小时内必须得到连接事件或明确失败原因；
- 失败时先尝试固定旧 tag；仍失败则缩小网络能力，停止深度裁剪；
- 不在阶段 1 引入 AI、页面视觉和复杂规则。

---

## 6. 阶段 2：需求、架构与数据设计冻结（9/23～9/24）

### 客观评价

阶段合理，但文档已经存在，不应重新从头编写 `docs/01～15`。本阶段只修订 Spike 推翻的假设，并冻结会阻塞编码的契约。

### 成员 A

- 根据 Spike 更新能力矩阵的技术事实；
- 冻结 `PrivacyEvent`、公共枚举和 `schemaVersion`；
- 建立 `core-model`、Repository/Adapter 接口骨架；
- 修订 Room schema，但实现延后到阶段 3；
- 冻结 TrackerControl Adapter 边界；
- 确定 Compose、依赖注入和模块结构，不再摇摆。

### 成员 B

- 冻结 PRD 的 P0/P1/P2；
- 冻结 `RiskAssessment`、`ExplanationResult`；
- 建立 5 条 P0 规则定义、场景知识草案和解释模板；
- 补全权限拒绝、无域名、无法归属、断网等 UI 状态；
- 建立首批 20 条评测样例。

### 共同

- 契约变更独立 PR，不与其他功能混合；
- 使用 kotlinx.serialization 验证所有 fixture；
- 冻结 `PrivacyEvent`、`RiskAssessment`、`ExplanationResult` v0.1；
- 更新 05～15 文档中被 Spike 推翻的内容。

### 阶段门

- 固定 JSON 能被 Kotlin 模型解析；
- 规则只依赖公共契约，不依赖 Room/Android；
- 页面只依赖 ViewModel/Repository，不直接调用 Android API；
- 新增功能必须修改 PRD、模型和验收标准。

---

## 7. 阶段 3：MVP 基础闭环（9/25～9/27）

### 客观评价

原看板把页面、Room、状态管理、Demo App 全分给 A，是当前最严重的效率问题。应按“基础设施 / 产品闭环”拆成两条并行链。

### 成员 A：数据基础设施

| 编号 | 任务 | 验收 |
|---|---|---|
| A3-1 | Room Entity、DAO、migration | 写入、查询、重启持久化测试通过 |
| A3-2 | `EventRepository` 与事件导入器 | 可批量导入 B 的 fixture |
| A3-3 | AppProfile/PermissionState Repository | Fake 与真实 Provider 可替换 |
| A3-4 | 导航/ViewModel 注入接口 | 不包含页面视觉和业务文案 |
| A3-5 | DAO、Adapter、Repository 单元测试 | CI 通过 |

### 成员 B：可运行产品闭环

| 编号 | 任务 | 验收 |
|---|---|---|
| B3-1 | FakeEventRepository | 无 VPN 也能播放 fixture |
| B3-2 | 首页、时间线、详情、设置四个 P0 页面 | Fake 数据可完整浏览 |
| B3-3 | 小型规则执行器与 5 条规则 | 正例、反例、unknown 测试通过 |
| B3-4 | 证据卡片和本地解释模板 | 事实/推断/不可观测显示准确 |
| B3-5 | Demo App 场景 A/B | 可单独编译、触发和复位 |
| B3-6 | 第一批评测测试 | 至少 20 条自动运行 |

### 集成

```text
fixture → FakeEventRepository
        → 规则 → 证据 → 本地解释 → UI → Fake 处置

随后只替换：
FakeEventRepository → RoomEventRepository
```

### 阶段门

模拟事件必须稳定跑通“事件→告警→证据→解释→建议”。未完成时不增加 AI、PDF、图表和额外页面。

---

## 8. 阶段 4：真实数据接入（9/28～9/30）

### 成员 A：真实 Provider

- PackageManager Provider；
- UsageStats Provider；
- TrackerControl/NetGuard Network Adapter；
- UID、包名、域名线索和时间窗关联；
- VPN 生命周期、前台服务、网络切换和异常恢复；
- 每完成一种 Provider 就提交一个脱敏 fixture，不等待全部完成。

### 成员 B：分类与降级体验

- 从 TrackerControl 已有管线或 Disconnect/DuckDuckGo 中只选一套 tracker 数据；
- 固定数据版本并生成 50～200 条精简离线表；
- 实现 TrackerClassifier 和域名归一化；
- 完成无权限、无域名、无法归属、VPN 停止和真实/沙箱标签；
- 使用 A 的真实 fixture 校准规则；
- 完成 Demo App 场景 C/D。

### 防等待规则

- A 用日志、JSON 和 DAO 验收，不等 UI；
- B 用真实 fixture 回归，不进入 A 的 native 代码调试；
- UID/域名失败时输出 unknown，不阻塞整条链；
- B 必须先支持 unknown UI，再显示成功结果。

### 阶段门

- 至少一种真实网络事件进入 Room；
- 至少一个 App 获得使用上下文；
- 无域名/UID 时诚实降级；
- 真实 Provider 替换 Fake 后，规则和 UI 无需重写。

---

## 9. 阶段 5：场景推理、因果链与处置复查（10/1～10/3）

### 客观评价

原看板把证据链交给 A，会再次形成瓶颈。证据等级、因果链和复查结论属于产品智能，应由 B 实现；A 负责真实动作和数据查询。

### 成员 A：执行层

- 实现真实 `MitigationExecutor`；
- P0 只保证域名阻断，App 级阻断视稳定性进入 P1；
- 系统设置跳转；
- 处置记录和观察窗口持久化；
- 按 App、域名、时间窗提供前后聚合查询；
- 区分“没有请求”和“有请求但已阻断”。

### 成员 B：原创分析层

- 场景知识库和场景一致性；
- `EvidenceLink` 与证据链构建器；
- 因果链节点和事实/推断等级；
- Recommendation 选择；
- RecheckComparator：减少、无变化、被阻断、无法确认；
- 因果链、处置和复查页面；
- 至少 8 条处置前后评测样例。

### 集成契约

```text
B：Recommendation
        ↓
A：MitigationExecutor
        ↓
A：MitigationRecord + 新事件
        ↓
B：RecheckResult + 页面
```

### 阶段门

- 至少一个真实域名阻断成功；
- 至少一个 Demo 场景跑通发现→解释→处置→复查；
- 每个风险结果可追溯到事件 ID；
- 不使用“已经泄露”“窃取”等超出证据的结论。

---

## 10. 阶段 6：AI 解释、评测与可用性（10/4～10/5）

### 调整原则

在线 AI 是增强项，不是 P0 门禁。本地模板、规则和证据链先稳定。

### 成员 A

- Retrofit/OkHttp 安全配置、超时和无 Body 日志；
- 密钥通过本地配置/环境注入，仓库不得出现密钥；
- 长时间 VPN、网络切换、服务回收和真机稳定性测试；
- 修复 P0 缺陷，不新增功能；
- 若时间不足，可完全不接在线 AI。

### 成员 B

- 本地解释模板和失败兜底；
- AI 输入白名单、脱敏 DTO、输出 schema 和事实校验；
- 可选 `AiExplanationProvider`；
- 扩充到 30～50 条评测样例；
- 统计规则准确率/召回率、事实一致率和处置耗时；
- 保存误报、漏报和 unknown 案例并校准规则。

### 阶段门

- 完全断网仍能完成主演示；
- AI 不能修改 App、事件、数字、证据类型或风险等级；
- AI 不达标时关闭在线 Provider，不影响 P0；
- 评测可复现，失败案例保留。

---

## 11. 阶段 7：发布候选与完整演示（10/6～10/7）

### 成员 A

- 冻结设备、系统和构建环境；
- 生成 RC APK、commit SHA 和 SHA-256；
- 编写安装、授权、清理和故障恢复步骤；
- 生成依赖许可证报告并人工核对；
- 准备真实网络数据来源、日志和第三方修改证据；
- 只修阻断性缺陷。

### 成员 B

- 完成 Demo App 场景重置流程；
- 3 分钟演示脚本和离线回放包；
- 录制候选视频、生成核心截图；
- 完成产品创新、证据等级、开源/原创边界答辩页；
- 独立按 A 的说明安装、授权和运行 RC。

### 交叉验收

- B 不依赖 A 口头帮助即可安装、重置和演示；
- A 不看额外说明即可按 B 的脚本完成流程；
- 截图、视频、文档和 APK 来自同一 commit；
- 授权失败、断网、VPN 停止和无法归属均完成演练。

---

## 12. 阶段 8：最终提交准备（10/8～10/10）

### 成员 A

- 最终设备回归；
- release APK、源码包、构建说明和依赖版本；
- 清理密钥、账号、真实数据、原始日志和临时文件；
- 核对 GPL 对应源码、许可证与 tag；
- 在 `main` 创建最终版本 tag。

### 成员 B

- 正式设计文档 PDF；
- 3 张核心截图和最终 MP4；
- 第三方与原创边界说明；
- 评测结果和失败案例附录；
- 核对所有文案、截图和视频不夸大实际能力。

### 最终交叉检查

| 检查 | 执行人 |
|---|---|
| 在干净环境按 README 编译源码 | B |
| 按最终脚本完整演示 APK | A |
| 检查技术陈述和能力边界 | A |
| 检查 APK/文档/视频/截图版本一致 | B |
| 检查许可证和署名 | 两人各检查一次 |

任何材料与最终 tag 的功能不一致，不能提交。

---

## 13. Git 协作规范（按当前仓库的轻量 GitHub Flow）

### 13.1 分支模型

```text
main               始终可编译、可演示；禁止直接提交
feature/<任务名>   功能开发
docs/<任务名>      独立文档变更
fix/<问题名>       缺陷修复
release/<版本>     仅最终冻结期按需创建，不长期保留
```

不建立长期成员 A/B 个人分支，也不额外增加长期 `develop`。对两人短周期项目，受保护的 `main` + 短任务分支 + PR 能减少无意义的分支同步。

推荐把任务编号放入分支名：

```text
feature/a-t1-trackercontrol-spike
feature/a-t3-room-repository
feature/b-t3-rule-engine
feature/b-t5-evidence-chain
docs/b-t1-demo-scenarios
fix/a-vpn-reconnect
```

### 13.2 每次开始任务前必须检查

**必须确认本地工作区、当前分支和远端 main 状态。**

```bash
git status
git branch --show-current
git fetch --prune origin
git log --oneline --decorate -5 origin/main
git rev-list --left-right --count HEAD...origin/main
```

创建新任务分支：

```bash
git switch main
git pull --ff-only origin main
git switch -c feature/b-t3-rule-engine
```

处理规则：

- 工作区不干净：先完成当前任务提交，或安全 stash；不能把旧任务改动带进新分支。
- 当前在 `main`：禁止直接修改和提交。
- 新任务必须基于最新 `origin/main`。
- `git pull --ff-only` 不能快进时，先查明分叉原因，不能用普通 `git pull` 自动制造 merge commit。

### 13.3 已开始分支如何同步

个人独占且无人基于该分支继续开发：

```bash
git fetch origin
git rebase origin/main
# 解决冲突并重新运行测试
git push --force-with-lease
```

若分支已共享：

```bash
git fetch origin
git merge --no-ff origin/main
# 解决冲突并重新运行测试
git push
```

禁止使用 `git push --force`。需要更新个人分支时只允许 `--force-with-lease`，并确认无人依赖。

### 13.4 Commit 规范

使用简化 Conventional Commits：

```text
feat(network): adapt connection event to PrivacyEvent
feat(rules): add background tracker rule
fix(vpn): recover after network switch
test(rules): add unknown attribution cases
docs(license): record TrackerControl source and changes
chore(build): pin NDK version
```

要求：

- 一个 commit 只做一个逻辑变化；
- 禁止 `update`、`fix`、`改了一下` 等无信息说明；
- 提交前运行受影响测试；
- 不提交密钥、keystore、个人数据、原始设备日志、构建产物和无关格式化；
- 开源源码导入和团队修改必须分成不同 commit；
- 不能编译的 WIP 可以留在个人分支，但不得进入 `main`。

### 13.5 PR 与合并

所有任务提交到自己的短任务分支，再向 `main` 发 PR。PR 必须包含：

- 对应任务编号；
- 改了什么、为什么；
- 测试命令和结果；
- UI 截图、网络日志/fixture、schema 前后示例等证据；
- 新增/修改的第三方代码或数据；
- 已知限制、降级和回滚方式。

合并条件：

- CI 构建和测试通过；
- 另一名成员 review；
- 已同步最新 `main`；
- 契约/Room/schema 变更有兼容说明和测试；
- 新开源项已固定版本并登记许可证；
- 合并后不破坏主演示链。

默认使用 **Squash and merge**，保持一个 PR 对应一个清晰主线提交。开源底座首次导入需要保留上游历史时可例外使用 merge commit，并在 PR 说明。

### 13.6 发布

1. 从通过阶段门的 `main` 生成 RC；
2. 若冻结期仍需并行修复，创建短期 `release/x.y.z`；
3. A 生成 APK、完整 commit SHA 和 SHA-256；
4. B 按最终脚本独立验收；
5. 修复经 PR 合回 `main`；
6. 在最终 commit 创建带注释 tag，如 `v0.1.0`；
7. APK、源码、截图、视频和 PDF 全部对应该 tag。

---

## 14. 每日工作机制

每天只设置两个短同步点：

- 开始前 10 分钟：确认当天唯一主任务、契约变化、目标分支和阻塞替代任务；
- 结束前 20～30 分钟：review/合并当天增量，更新 `17-task-board.md` 和 `18-risk-register.md`。

每人每天最多保留：

| 类型 | 数量 | 要求 |
|---|---:|---|
| 今日必须完成 | 1 | 独立、可验证、可提交 |
| 完成后再做 | 1 | 不影响阶段门 |
| 阻塞替代任务 | 1 | 不依赖对方未完成代码 |

禁止：

- “等待 A 完成 VPN”；
- “等待 B 完成规则”；
- 两人共同负责但无人主责；
- 三天不合并，最后集中联调；
- 在聊天中口头修改契约而不更新 schema/fixture；
- 未运行测试就把分支交给对方排错。

---

## 15. 范围缩减触发条件

| 时间点 | 触发条件 | 立即处理 |
|---|---|---|
| 9/22 晚 | TrackerControl 不能稳定构建/联网 | 换固定旧 tag；仍失败则缩小网络范围 |
| 9/27 晚 | 模拟闭环未跑通 | 删除在线 AI、PDF、图表和次要页面 |
| 9/30 晚 | 域名/UID 不稳定 | 展示 IP/unknown；主演示使用 Demo 真值 |
| 10/3 晚 | 阻断不稳定 | 仅保留单域名阻断，不做 App 级阻断 |
| 10/5 晚 | AI 事实一致性不足 | 关闭在线 AI，只保留本地模板 |
| RC 生成后 | 非阻断性新需求 | 进入 backlog，不加入本次版本 |

---

## 16. Definition of Done

任何任务完成前必须满足：

- [ ] 在正确的短任务分支，不是直接修改 `main`；
- [ ] 开始前已 fetch 并基于最新 `origin/main`；
- [ ] 有唯一主责、明确输入、输出和验收证据；
- [ ] 受影响测试通过；
- [ ] 新增第三方代码/数据已固定版本并登记许可证；
- [ ] unknown、权限拒绝、断网和缺失数据有降级；
- [ ] PR 已由另一人 review，CI 通过；
- [ ] 合并后 `main` 可编译且主演示链未破坏。

---

## 17. 最终责任边界

> A 保证“真实信号尽可能可靠、网络底座稳定、处置动作可执行、版本能发布”；B 保证“规则可验证、证据可理解、UI 可使用、结果可评测、演示能讲清”。

本方案保留仓库阶段 0～8 的真实执行顺序，修正现有看板中的工作量失衡，并把已经调研的开源复用方案落实到每一阶段。两人通过固定契约、Fake/Real 双实现、短任务分支和每日小集成并行推进。
