# 19 工作导引（打开仓库先看这个）

> 版本：`v0.1`
> 最后更新：2026-09-21
> 用途：两人打开仓库后，30 秒内知道“现在到哪一步、我下一步做什么、改哪个文件”。
> 配套：[20 开源复用建议](20-open-source-reuse-guide.md)、[21 并行分工与协作规范](21-parallel-work-allocation-plan.md)、[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

---

## 一、30 秒看懂现状

```text
时间基线：2026-09-20 起，10/8-10/10 提交截止
当前阶段：阶段 0（范围冻结）已完成，准备进入阶段 1（技术 Spike）
主演示案例：后台读取剪贴板/位置 + 网络行为解释
首版基线：Android 10 / API 29+
```

**一句话**：设计文档已基本写完；**下一步不是写页面，也不是从零写 VPN 协议栈，而是优先构建/裁剪 TrackerControl 网络底座，同时并行验证 PackageManager 与 Usage Access**。

开源路线已定（详见 [20 开源复用建议](20-open-source-reuse-guide.md) 第 13 节）：优先走 **方案 A：TrackerControl / NetGuard（GPL-3.0）** 作为网络底座；接受 GPL 路线并明确开源与原创边界。MIT 备选路线仅在阶段 1 构建链持续失败时启用。

---

## 二、先读这 4 份（按顺序）

| 顺序 | 文档 | 看什么 |
|---|---|---|
| 1 | [00 文档索引](00-index.md) | 有哪些文档、谁负责 |
| 2 | [01 项目章程](01-project-charter.md) | 做什么、不做什么、P0 范围 |
| 3 | [04 PRD](04-product-requirements.md) | 要实现的 F1~F10 功能 |
| 4 | [17 任务看板](17-task-board.md) | 当前阶段你自己的任务编号 |
| 5 | [21 并行分工与协作规范](21-parallel-work-allocation-plan.md) | 分工、契约、分支与 PR 规范 |
| 6 | [20 开源复用建议](20-open-source-reuse-guide.md) | 开源选型、许可证与接入边界 |

其余文档遇到对应问题时再查（见第八节“文件地图”）。

---

## 三、我是谁？我下一步做什么？

### 成员 A（技术实现主责）——现在做阶段 1 Spike（9/21-9/22）

**目标**：先验证最危险的技术点，不写业务页面。

| 任务编号 | 做什么 | 改哪里（新建/编辑） | 完成标准 |
|---|---|---|---|
| A1-1 | 固定 TrackerControl commit/tag 并构建 | 第三方源码单独提交；新建 `docs/spike-results.md` 记录环境与 commit | 演示机启动 VPN 后可联网 |
| A1-2 | 定位 VPN、连接、DNS、UID、阻断入口 | 新建 `docs/network-core-map.md` | 能指出类、回调、数据流 |
| A1-3 | 验证 PackageManager 读包名/权限 | `app/.../profile/` 下新建采集类 | App、UID、版本、声明权限、授权状态可输出 |
| A1-4 | 验证 Usage Access 前后台 | `app/.../usage/` | 授权后能读到前后台或明确 unknown/失败原因 |
| A1-5 | 输出最小 NetworkEvent | `app/.../vpn/` 适配层 | 脱敏 JSON 含时间、协议、IP/域名线索、端口、UID/unknown |
| A1-6 | 最小阻断验证 | `app/.../vpn/` | 至少一个测试域名可阻断并保留尝试记录 |
| A1-7 | 开源技术登记 | [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) | NDK/JNI/Rust/数据依赖可追溯 |
| A1-8 | 记录 UID 归属成功率 | 更新 [02 能力边界表](02-android-capability-matrix.md) | 记录 IP/端口/流量/UID 成功率 |

> 优先级：TrackerControl 能否构建联网 → 能否产生连接事件 → 能否阻断 → PackageManager / UsageStats 补充验证。
> 止损：24 小时内 TrackerControl 必须能构建并联网；48 小时内必须得到连接事件或明确失败原因；失败先换固定旧 tag，仍失败则缩小网络范围。
> 建议按 [05 系统架构](05-system-architecture.md) 的模块划分建目录：`profile/`（M1）、`usage/`（M2）、`vpn/`（M3）、`event/`（M4）。

### 成员 B（产品与智能分析主责）——现在做阶段 1 设计输入（9/21-9/22）

| 任务编号 | 做什么 | 改哪里 | 完成标准 |
|---|---|---|---|
| B1-1 | 定义 v0.1 事件 fixture | 新建 `docs/fixtures/`（8～12 条 JSON） | 正常、风险、unknown、沙箱均覆盖 |
| B1-2 | 4 个 Demo 场景与预期证据链 | 新建 `docs/demo-scenarios.md` | 操作、真值、预期证据、失败降级完整 |
| B1-3 | 事实/推断/不可观测三类文案模板 | 编辑 [11 AI 解释契约](11-ai-explanation-contract.md) | 三类模板可直接用，四类不混写 |
| B1-4 | 第三方组件登记 | [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) | 仓库、版本、许可证、用途齐全 |
| B1-5 | UI 状态草图 | `docs/demo-scenarios.md` 附页面状态表 | loading/empty/unknown/demo/error 均定义 |
| B1-6 | 定稿 Android 能力边界表 | 编辑 [02-android-capability-matrix.md](02-android-capability-matrix.md) | 每行有来源、证据等级、降级 |
| B1-7 | 真实/沙箱模式产品说明 | 编辑 [01 章程](01-project-charter.md) 第 4 节 | 两种模式表述清晰 |

> B 始终使用 fixture、Fake Repository 和 Fake Executor，**不等待 A 的真实 VPN**。唯一集成点是 A 导出的真实 NetworkEvent 必须能映射到 B 的 fixture schema。

### 两人共同

- **阶段 0 收尾**：开会确认“普通第三方 App 无法可靠监控其他 App 的敏感 API”理解一致；在 [17 任务看板](17-task-board.md) 将 T0-7 表述修正为“01~16 已形成 v0.1 草案，阶段 1 Spike 后修订、阶段 2 正式冻结跨模块契约”，避免与阶段 2 重复冻结冲突。
- **每日结束**：更新任务看板状态，保存截图/日志，生成一个可复现版本。

---

## 四、文件地图：什么改动放哪里

```text
CausalGuard/
├─ README.md                    项目总览（对外）
├─ CONTRIBUTING.md              Git 分支与协作规则 → 提交前必看
├─ THIRD_PARTY_NOTICES.md       第三方源码/数据/素材来源与许可证登记 → 引入开源前必看
├─ docs/                        所有设计文档（见第二节和第八节）
│  ├─ 00~18                    设计基线，改动需同步相关契约
│  ├─ 19-work-guide.md          本文件
│  ├─ 20-open-source-reuse-guide.md        开源选型、许可证、接入与止损
│  ├─ 21-parallel-work-allocation-plan.md  分工、契约、Git 与 PR 规范
│  └─ （新）demo-scenarios.md、network-core-map.md、spike-results.md、fixtures/ 等阶段产物
├─ app/                         （待建）Android 主工程，Kotlin 代码
│  └─ src/main/java/…           按模块建包：profile / usage / vpn / event / rules / ui
└─ demo-app/                    （待建）自研 Demo App，提供演示真值
```

**开源相关约定**：第三方源码首次导入单独 commit；团队修改另起 commit；任何引入先更新 `docs/20` 与 `THIRD_PARTY_NOTICES.md`。

**改代码前先确认契约**：事件格式看 09、规则格式看 10、AI 格式看 11。契约没改，代码不能自己发明字段。

---

## 五、每天怎么工作（Git 流程）

```bash
# 开始前检查
git status
git fetch --prune origin
git switch main
git pull --ff-only origin main
# 建任务分支（任务编号入分支名）
git switch -c feature/a-t1-trackercontrol-spike
# 只做一件事并提交（Conventional Commits）
git add <文件> && git commit -m "feat(network): adapt connection event to PrivacyEvent"
git push -u origin feature/a-t1-trackercontrol-spike
# 向 main 发 PR，对方 review + CI 通过后 Squash and merge
```

规则详见 [CONTRIBUTING.md](../CONTRIBUTING.md) 与 [21 并行分工与协作规范](21-parallel-work-allocation-plan.md) 第 13 节。要点：
- `main` 只留可运行、可演示版本，禁止直接提交；
- 一次提交只解决一个问题，提交前运行受影响测试；
- 不提交密钥、真实隐私数据、原始设备日志、APK 构建产物；
- 文档/规则变更与代码同一提交；
- 第三方源码导入与团队修改分属不同 commit；
- 个人分支同步用 `rebase` + `git push --force-with-lease`，禁止 `--force`。

---

## 六、当前“不知道下一步”时的标准答案

1. 打开 [17 任务看板](17-task-board.md)，找你名字下**状态不是“已完成”的第一个任务**。
2. 看该任务对应的文档（第三节表格里有）。
3. 按第四节的“文件地图”去建/改对应文件。
4. 做完更新任务看板状态，提交推送。

---

## 七、阶段门禁（不满足不进入下一阶段）

| 门禁 | 条件 |
|---|---|
| 阶段0 → 阶段1 | 两人对“能否监控其他 App 敏感 API”理解一致；已接受 GPL-3.0 路线或明确选择宽松许可证备选 |
| 阶段1 → 阶段2 | TrackerControl 能构建联网或已收缩网络范围；能产生连接事件；UID 无法归属有降级；Usage Access 稳定性有结论 |
| 阶段2 → 阶段3 | `docs/01`~`15` 冻结 v0.1，契约完成 |
| 阶段3 → 阶段4 | 模拟事件能稳定跑通“事件→告警→解释→建议” |

完整时间表见 [16 演示与发布方案](16-demo-and-release-plan.md) 第 8 节。

---

## 八、按问题的文档速查

| 我想知道… | 看哪份 |
|---|---|
| 项目到底做什么、不做什么 | 01 项目章程 |
| 某能力能不能真实拿到 | 02 能力边界表 |
| 权限怎么申请、拒绝怎么办 | 03 权限授权流程 |
| 要实现哪些功能 | 04 PRD |
| 模块怎么分层、谁调谁 | 05 系统架构 |
| 某模块输入输出/接口 | 06 模块设计 |
| 事件字段/表结构 | 07 数据模型、08 表结构 |
| 采集写入格式 | 09 事件契约 |
| 规则怎么评分 | 10 风险规则契约 |
| AI 怎么防编造 | 11 AI 解释契约 |
| 什么数据不能存 | 12 隐私安全设计 |
| 怎么测、测哪些 | 13 测试计划 |
| 评测集和指标 | 14 评测数据集 |
| 什么算完成 | 15 验收清单 |
| 演示和提交怎么做 | 16 演示与发布方案 |
| 我今天干什么 | 17 任务看板 |
| 有哪些技术风险 | 18 风险清单 |
| 开源选型/许可证怎么判断 | 20 开源复用建议、THIRD_PARTY_NOTICES |
| 分工、契约、分支和 PR 怎么走 | 21 并行分工与协作规范 |

---

## 九、更新规则

- 本导引随阶段推进更新；阶段切换时先改第三节“下一步做什么”。
- 新增阶段产物（如 `demo-scenarios.md`、`spike-results.md`）后，在本文件第四节和 [00 索引](00-index.md) 登记。
