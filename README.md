# 隐私因果哨兵 Privacy Causal Sentinel

> 项目名称：**隐私因果哨兵 Privacy Sentinel**
> 参赛方向：智能网联与智能终端赛道——移动互联网人工智能应用程序开发
> 当前阶段：**P0 设计基线（v0.1）**
> 首版基线：Android 10 / API 29+

> **新上手必读** → [docs/19-work-guide.md](docs/19-work-guide.md)：30 秒看清现状、下一步做什么、改哪个文件。

隐私因果哨兵是一款运行在 Android 移动设备上的**隐私行为解释与处置智能体**：它结合 App 使用场景、敏感数据访问、网络元数据和用户授权状态，判断行为是否合理，构建可追溯的隐私因果链，并指导用户完成处置和复查。

产品核心闭环：

```text
采集行为证据
  -> 判断是否符合使用场景
  -> 构建隐私因果链
  -> 解释风险和不确定性
  -> 给出可逆处置建议
  -> 复查处置后的行为变化
```

## 仓库结构

```text
CausalGuard/
├─ README.md                    本文件
├─ CONTRIBUTING.md              Git 分支与协作规则
├─ THIRD_PARTY_NOTICES.md       第三方源码/数据来源与许可证登记
├─ docs/                        设计基线文档（v0.1）
│  ├─ 00-index.md               文档索引与阅读顺序
│  ├─ 01-project-charter.md     项目章程与范围说明
│  ├─ 02-android-capability-matrix.md  Android 能力边界表
│  ├─ 03-permission-and-consent-flow.md 权限与授权流程
│  ├─ 04-product-requirements.md PRD（含 P0/P1/P2 优先级）
│  ├─ 05-system-architecture.md 系统架构设计
│  ├─ 06-module-design.md       模块设计说明
│  ├─ 07-data-model.md          数据模型
│  ├─ 08-database-schema.sql    Room/SQLite 表结构
│  ├─ 09-event-contract.md      事件契约
│  ├─ 10-risk-rule-contract.md  风险规则契约
│  ├─ 11-ai-explanation-contract.md AI 解释契约
│  ├─ 12-privacy-security-design.md 隐私与安全设计
│  ├─ 13-test-plan.md           测试计划
│  ├─ 14-evaluation-dataset.md  评测数据集方案
│  ├─ 15-acceptance-checklist.md 验收清单
│  ├─ 16-demo-and-release-plan.md 演示与发布方案
│  ├─ 17-task-board.md          任务看板
│  ├─ 18-risk-register.md       风险清单
│  ├─ 19-work-guide.md          工作导引（打开仓库先看）
│  ├─ 20-open-source-reuse-guide.md        开源代码复用调研与接入建议
│  └─ 21-parallel-work-allocation-plan.md  双人并行分工与 Git 协作规范
└─ （后续）app/                 Android 工程（Kotlin + Room + Jetpack Compose）
    （后续）demo-app/           自研 Demo App / 演示沙箱
```

## 文档阅读顺序

0. `docs/19-work-guide.md`（先看这个，知道下一步做什么）
1. `docs/00-index.md`
2. `docs/01-project-charter.md`（先看做什么、不做什么）
3. `docs/04-product-requirements.md`（P0 功能范围）
4. `docs/20-open-source-reuse-guide.md` 与 `docs/21-parallel-work-allocation-plan.md`（开源选型、分工与 Git 规范）
5. 其余按索引顺序阅读。

## 首版绝对不做

- 不做 TLS 中间人解密；
- 不做无障碍服务监控其他 App 页面；
- 不承诺监控其他 App 每一次定位、相机、麦克风和通讯录调用；
- 不依赖 `AppOpsManager` 获取其他 App 的全局访问历史；
- 不自动撤销其他 App 的权限；
- 不从零开发完整 TCP/IP 协议栈；
- 不让大模型直接读取原始网络流量并自行判定是否泄露；
- 不在没有证据时使用“窃取”“恶意上传”等确定性措辞；
- 不同时适配多个移动平台。

## 时间基线

以 2026 年 9 月 20 日为起点，按 10 月 8 日至 10 月 10 日完成作品调试、运行和最终提交倒排。正式执行前再次核对大赛官网时间。详见 `docs/16-demo-and-release-plan.md`。

## 第三方与许可

本项目采用“成熟开源底座 + 上层原创”的策略：

- **网络底座**优先采用 TrackerControl / NetGuard（GPL-3.0），解决 VPN/TUN、DNS 和阻断等高风险基础设施；接受 GPL 路线并明确开源与原创边界。构建链持续失败时评估 MIT 的 hev-socks5-tunnel / tun2socks 备选。
- **通用能力**使用 Android 官方模板/示例与宽松许可证库（Room、Compose、kotlinx.serialization、Retrofit/OkHttp、AndroidX Test、Turbine 等）。
- **团队原创**：统一事件模型、使用上下文关联、场景知识库、证据等级、因果解释、处置复查、UI、Demo 与评测集。

任何第三方源码、二进制或数据引入前，必须固定 tag/完整 commit、核对具体版本内的 LICENSE/NOTICE 与文件头声明，并在 `THIRD_PARTY_NOTICES.md` 登记来源、版本、许可证、使用范围、修改内容与原创边界。选型与许可证判定见 `docs/20-open-source-reuse-guide.md`，合规底线见 `docs/12-privacy-security-design.md`。

> 所有第三方组件必须固定版本，禁止长期跟随浮动 `main`；第三方源码首次导入与团队修改分属不同 commit。
