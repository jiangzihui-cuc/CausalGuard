# 真机截图（A8-1 回归，真实观测）

> 来源：PJW110 / Android 16（API 36）真机，APK `causalguard-1.0.0.apk`（versionName `1.0.0` / versionCode `3`），构建 commit `ad637b5`，tag `causalguard-v1.0.0`。
> 全部为 **真实观测**（`source=vpn`、`Mode: 真实观测`），非 Demo、非离线 fixture。
> 关联：[A8-1 最终设备回归记录](../a8-1-final-device-regression.md)、[B8-4 附录](../b8-4-evaluation-and-failure-appendix.md) §6。

| 文件 | 页面 | 展示目的 | 事件 ID | 数据来源 |
|---|---|---|---|---|
| `home-overview.png` | 首页/总览 | 运行模式=真实观测、风险总览、事件数 | —（总览） | 真实观测 |
| `timeline.png` | 事件时间线 | 真实事件列表（`Type: network` / `Mode: 真实观测`） | — | 真实观测 |
| `event-detail-facts.png` | 事件详情（上） | 事实字段 + 证据等级 `E2` | `n-1791595513423-10407-172.217.112.4:443`（`com.android.chrome`） | 真实观测 |
| `event-detail-chain.png` | 事件详情（下） | 因果链（事实 → 推断 → 评估） | 同上 | 真实观测 |
| `event-detail-recommendation-recheck.png` | 事件详情（下） | 建议 + 处置与复查 | 同上 | 真实观测 |
| `event-detail-unknown.png` | 事件详情 | `unknown` 归属降级（R-008），不强行归因 | 见时间线 unknown 事件 | 真实观测 |
| `home-usage-access-revoked.png` | 首页 | 异常分支证据：撤销 Usage Access 后不崩溃 | — | 真实观测 |
| `settings.png` | 设置与隐私控制 | 运行模式/数据来源/解释方式（本地模板） | — | — |

> 说明：本目录仅存放 CausalGuard 自身界面截图，不含任何个人应用或通知内容。
