# A8-1 最终设备回归记录

> 版本：`v0.2.0-rc1`（预演版）
> 最后更新：2026-10-10
> 责任人：成员 A（协作：成员 B）
> 关联任务：[17 任务看板](17-task-board.md) A8-1；[A7 安装·授权·清理·故障恢复](a7-install-auth-recovery.md) §8；[16 演示与发布方案](16-demo-and-release-plan.md) §4；[15 验收清单](15-acceptance-checklist.md)。
> 说明：本记录为**预演版回归**（B 侧尚未最终冻结）。最终回归应在 A8-2 生成冻结 APK 后重跑并替换本表。

---

## 1. 环境

| 项 | 值 |
|---|---|
| 设备 | PJW110（Android 16 / API 36），HTTP 无线调试 `10.203.58.26:36015` |
| 主 App | `com.causalguard` versionName `0.2.0-rc1`，签名 `b3ed3f7d…` |
| 网络底座 | `net.kollnig.missioncontrol.fdroid.test`（fdroid debug + A4-3/A5-1 补丁），同签名 `b3ed3f7d…` |
| 源码 commit | `2b2b851`（`main`，含 #41/#43 合并） |
| APK SHA-256 | `00d80e7f82f053488fa87d9ad5705eeef7edd33c14da611ecff44402db82c12b` |
| 授权 | 通知、Usage Access、VPN 三项已授权 |

## 2. 主演示链回归

| 步骤 | 结果 | 证据 |
|---|---|---|
| 底座 VPN 起 `tun0` | ✅ `tun0` 存在 | `a8-1/01-home.png` |
| 主 App 启动，首页运行模式 | ✅ `运行模式：真实观测`；事件数持续增长（回归期间 1483） | `a8-1/01-home.png` |
| 生成真实流量（Chrome 访问 baidu/qq/taobao 等） | ✅ 观测并入库（单轮 426 条 ingest，`source=vpn`/`evidenceLevel=E2`） | logcat `CausalGuardNet` |
| 时间线展示真实事件 | ✅ 逐条 `Type: network` / `Mode: 真实观测` | `a8-1/02-timeline.png` |
| 事件详情：事实 + 证据等级 | ✅ Event ID/UID/包名/协议/IP/端口/域名线索、`Evidence: E2`、`Foreground: unknown` | `a8-1/03-detail-top.png` |
| 事件详情：因果链 + 解释 | ✅ 事件事实(E2) → 规则推断(E3，命中时) → 最终评估(E5)；`Why/Explanation` | `a8-1/04-detail-chain.png` |
| 建议 + 处置复查区块 | ✅ `Recommendation / 建议`、`处置与复查：尚未执行处置`（当前无中高风险命中） | `a8-1/05-detail-chain.png` |

## 3. 异常与降级分支

| 分支 | 操作 | 期望 | 结果 |
|---|---|---|---|
| VPN 被回收 | `am force-stop` 底座 | 记录 `vpn-down`，服务尝试恢复，不崩溃 | ✅ logcat：`network changed: vpn-down` → `network restart done: active=true collecting=true`；App 无崩溃 |
| VPN 恢复 | 重新拉起底座 | `tun0` 恢复，事件继续入库 | ✅ 底座自动起 `tun0`；恢复后单轮 194 条 ingest |
| 拒绝/撤销 Usage Access | `appops set ... GET_USAGE_STATS deny` | 不崩溃；使用上下文降级 | ✅ 无崩溃。**但见 §4 缺陷①**：前后台结论本就恒为 `unknown`，撤销后无额外可观察差异 |
| 断网 | — | 核心本地分析与解释可用，AI 走模板兜底 | ⚠️ **未做设备级断网**：`adb` 走 Wi‑Fi，切断网络会断连。以“AI 未配置 → 本地确定性模板、核心分析不依赖网络”间接佐证，登记为遗留 |
| UID 无法归属 | 观测 ICMP/IPv6 事件 | 保留 `packageName=unknown`，不强行归因 | ✅ 时间线存在 `unknown` 事件；详情显示 `UID: -1`、`Package: 无法归属到具体应用`、`Matched rules: R-008`、`Scenario match: unknown` |
| 域名不可见 | 无 `dnsResolved` 的事件 | 只显示 IP，不给域名结论 | ✅ 详情 `Domain hint: 域名不可见`，不生成域名结论 |

## 4. 发现（登记，不在本任务修复）

- **缺陷①（前台状态未知）**：真实网络事件 `Foreground` 恒为 `unknown`（`AppContainer` 构造 `NetworkEventIngestor` 未注入 `ForegroundStateResolver`）。导致依赖前后台状态的规则（R-003/R-006）不触发，且撤销 Usage Access 无可观察降级差异。建议单独排期修复并补测。
- **缺陷②（规则与数据集未打通）**：`R-005` 只匹配演示域名，未接 B4 的 tracker 数据集；真实流量基本只命中 `R-008`，回退 LOW。故“执行处置 → 复查减少”在真实流量下暂无可复现样例（见 A 侧截图方案 §A）。

## 5. 结论

- 主演示链在真机可完整跑通：采集 → 时间线 → 事件详情（证据/因果链/解释/建议/复查）。
- VPN 回收/恢复、Usage Access 撤销、unknown/无域名等降级均**不崩溃**且诚实表达。
- 无阻断性缺陷；缺陷①②与“设备级断网”列为后续项。
- 本回归为预演；最终以 A8-2 冻结 APK 重跑为准。

## 6. 证据文件

真机截图（未入库，作为提交材料候选）：
`a8-1/01-home.png`、`a8-1/02-timeline.png`、`a8-1/03-detail-top.png`、`a8-1/04-detail-chain.png`、`a8-1/05-detail-chain.png`、`a8-1/06-usage-denied-home.png`。
logcat 证据：tag `CausalGuardNet`（`monitor started` / `ingested` / `network changed: vpn-down` / `network restart done`）。
