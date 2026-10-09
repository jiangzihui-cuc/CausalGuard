# B7-2 三分钟主演示脚本

> 状态：演示编排稿已形成
> 目标时长：2:40～3:10
> 适用版本：与录屏使用的同一 commit
> 关联：`docs/demo-scenarios.md`、`docs/offline-replay/README.md`

## 使用边界

这是一份按真实录屏顺序执行的脚本，不是产品介绍稿。画面或口播必须明确区分：

- **Runtime Ground Truth**：Demo App 自己真实执行的受控动作，或设备/API 返回的事实。
- **Observed Fact**：CausalGuard VPN、系统 API 或 UsageStats 实际观察到的元数据。
- **Synthetic Evaluation Input**：仓库中的固定离线评测输入，只用于稳定复现规则与解释链。
- **Derived Inference**：由规则、场景知识或证据链推导出的判断，不新增事实。
- **Unavailable / Unknown**：当前证据不能确认的内容，保持未知，不补写成成功或泄露。

离线回放包只登记和复用 canonical assets，不把 synthetic input 说成 Android 16 真机观测。录屏前应确认画面上能看到当前是 Runtime、Fixture/离线演示或 Unknown 状态。

## 录屏流程

| 时间 | 屏幕操作 | 预期画面 | 演示者口播 | 证据类型 | 降级/异常口径 |
|---|---|---|---|---|---|
| 0:00–0:25 | 打开 CausalGuard 首页，停留在事件/演示入口。 | 应用名、数据来源/运行模式和能力边界；不展示“已监控所有敏感 API”。 | “CausalGuard 关注的不是一次敏感访问本身，而是把场景、可追溯证据和网络行为放在一起解释为什么值得关注。” | 产品边界说明；不是事件事实。 | 没有网络或 VPN 时保持离线/Fixture 模式，说明核心分析不依赖在线 AI 或公网。 |
| 0:25–0:50 | 打开 Demo Map，进入前台导航并触发一次位置访问，在 Demo App 页面查看结果和 event 元信息。 | Demo App 显示 `DEMO-A Success`/位置访问元信息、`foreground` 和受控 Demo 来源；不暗示事件已进入 CausalGuard 主 App。 | “前台地图导航需要当前位置。这里展示的是 Demo App 自己执行的受控动作；敏感权限不等于自动高风险，场景匹配会降低误报。当前这条 runtime Demo event 没有已确认的 Demo App 到 CausalGuard Room 桥接。” | Runtime Ground Truth（Demo App 受控动作）；Derived Inference（若使用预置分析材料）。离线 `e-20260921-0001` 必须标记为 Synthetic Evaluation Input。 | 触发失败显示空态/错误态，不补造位置事件；场景资料缺失则降为 `unknown`。如需在 CausalGuard 中展示 `e-20260921-0001`，明确它是预置/显式导入的 synthetic fixture，不是刚才的 Demo App runtime event。 |
| 0:50–1:15 | 在目标设备打开 Demo Calculator，Arm `DEMO-B`，按 Home 后返回。 | `Platform Restricted`；probe 已执行；没有 clipboard PrivacyEvent、原文或外传结论。 | “真实 Android 16 结果是后台 probe 执行后被平台限制。我们不把平台拒绝包装成成功读取，也不保存或展示剪贴板原文。” | Runtime Ground Truth（probe 执行）；Observed Fact（平台限制）；Unavailable / Unknown（内容与是否外传）。 | 其他设备只口播实际状态；成功分支也只展示脱敏长度，不代表目标设备结果。不能把 `e-20260921-0003` 当作真机成功读取。 |
| 1:15–1:40 | Arm `DEMO-C`，按 Home，等待 probe 完成；查看 CausalGuard 网络事件。 | 已验证设备上显示 `com.demo.calculator → example.com:443`、`TCP`、`domainHint=example.com`。 | “这是一次最小 TCP connection attempt。VPN 观察到了连接尝试，但应用层 `Socket.connect` 最终是 `ConnectException`；被观察到不等于连接成功，更不等于 tracker 命中、隐私泄露或敏感数据外传。” | Runtime Ground Truth（发起 attempt）；Observed Fact（VPN 元数据）；Unavailable / Unknown（payload、应用层成功、泄露）。 | 没有对应 VPN 事件时只报告 Demo App attempt 和实际异常，显示 unknown，不补写 VPN 观察结果。 |
| 1:40–2:20 | 明确切换到已准备的固定离线评测材料/fixture replay 环境，选择 `e-20260921-0003` 与 `e-20260921-0004`，进入详情和证据链。 | `background`、`scenario mismatch`、E4/E2、matched rules、risk/category/confidence、证据链、因果链和本地解释。 | “下面切换到固定离线评测样例，用来稳定展示规则与解释链。它不是刚才 Android 16 现场采集出的事件。`e-0003/e-0004` 是 canonical Synthetic Evaluation Input；时间相关只能作为伴随证据，不能证明剪贴板内容已发送。” | Synthetic Evaluation Input（events、context、rules）；Derived Inference（规则、场景、证据/因果链）；Unavailable / Unknown（请求内容与真实外传）。 | 这里必须使用预置或显式导入的 synthetic dataset；clean-install production App 不会自动 seed `privacy-events` fixture，规则或场景资料缺失时展示实际 unknown/degraded，不手工补 matched rule 或风险等级。 |
| 2:20–2:45 | 打开 Recommendation、Mitigation 和 Recheck 页面；使用已准备的处置/复查回放输入，或展示当前真实执行结果。 | 分开显示 recommendation、action requested、execution result、verified effect；复查结果为 `reduced`、`no_change`、`blocked` 或 `unknown`。 | “建议不是执行结果，执行回执也不等于效果已经验证。`OPEN_SETTINGS` 只表示跳转系统设置，不能说权限已经修改；离线复查样例是确定性回放，不是现场刚采集的长期统计。” | Derived Inference（Recommendation/Recheck）；Synthetic Evaluation Input（recheck 回放）；Observed Fact（真实执行回执）。 | 没有可比较窗口、执行未确认或页面未注入回放输入时显示 unknown/unavailable，不制造成功结果。 |
| 2:45–3:00 | 回到结果页，停留在来源/证据说明。 | 运行模式、证据等级、规则版本和降级边界清晰可见。 | “三个要点：场景感知减少‘敏感访问=恶意’的误报；证据和因果链让结论可解释、可复查；AI 只做受约束的语言增强，断网时仍由本地确定性解释支撑主演示。” | 产品总结；Derived Inference 边界说明。 | 任一真机能力不可用时，展示 Unknown/Platform Restricted，并转入固定离线样例完成可重复的规则演示。 |

## 演示前检查

1. 设备、APK、脚本和仓库 commit 保持一致；真机段落不预先写入未发生的事件。
2. 离线段落使用 `docs/offline-replay/manifest-v0.1.json` 登记的 canonical assets；expected/oracle 不作为 runtime 输入。
3. DEMO-B、DEMO-C、DEMO-D 的真实结果按设备实际显示，不把 `Platform Restricted`、`ConnectException` 或 `Failure(IllegalStateException)` 改写成成功访问或成功外传。
4. clean-install CausalGuard 不会自动导入 `privacy-events` fixture；若展示 CausalGuard 中的 synthetic event，必须说明是预置或显式导入的数据环境。
5. AI 未配置或断网时使用本地 deterministic explanation fallback；不在现场调用在线模型补充事件事实。
