# CausalGuard 离线回放包

本目录是 B7-2 的离线材料索引，不复制一套新的事件或规则数据。机器可读清单见 [`manifest-v0.1.json`](manifest-v0.1.json)；清单中的路径均指向仓库现有 canonical asset。

## 目的

核心规则分析、场景判断、证据链和本地解释可以在无公网、无在线 AI、无真实 VPN 时复现，因为固定 fixture 与规则/模板资产已经在仓库中，并由确定性 evaluator 消费。离线回放用于稳定展示 synthetic evaluation input，不替代设备上的 Runtime Ground Truth。

## 当前加载边界

`RuntimeFixtureLoader` 当前从 Android assets 加载：

- `privacy-events-v0.1.json`
- `risk-rules-v0.2.json`
- `rule-input-context-v0.1.json`
- `scene-knowledge-v0.1.json`
- `explanation-templates-v0.2.json`

`RuntimeFixtureAssetTest` 校验这些 runtime asset 与 `docs/fixtures/` 的 canonical 内容一致，并确认 expected oracle 不被复制到 runtime assets。`recheck-cases` 和 `recheck-expected` 是评测/回放材料；它们不被 `RuntimeFixtureLoader` 当作普通 runtime 输入自动加载。

`ReplayNetworkEventSource` 是与 `NetworkEventSource` 相同接口的可替换事件源，供显式的无 VPN 回放或测试使用。它接收调用方提供的 `NetworkEvent` 列表，不会自动读取本 manifest，也不会把 `PrivacyEvent` fixture 伪装成网络观测。当前默认 `AppContainer` 的 network source 仍是 `TrackerControlEventSource`；只有显式替换依赖或测试 harness 时，才使用 `ReplayNetworkEventSource`。

## 当前未提供什么

- 本目录不是 standalone executable replay app。
- manifest 只是资产索引，不会自动导入数据，也不会驱动 replay。
- clean-install CausalGuard 当前不会自动 seed `privacy-events-v0.1.json` 到默认 Room；`RuntimeFixtureLoader.loadPrivacyEvents()` 虽可读取 asset，但没有接入普通启动链路。
- `EventImporter` 可以批量导入契约 JSON，但当前只是 `AppContainer` 暴露的依赖，没有接到普通用户 UI 或 startup wiring。
- `ReplayNetworkEventSource` 不读取 manifest，也不是默认 production source。
- Settings/Home 中的 Fixture/离线文案描述能力边界，不等于已经完成 fixture seeding 或存在一键 replay UI。

因此，B7-2 离线包的真实定位是 **canonical asset index + deterministic evaluation/replay material**。如果录屏需要在 CausalGuard 页面展示 synthetic event，必须使用已准备好的预置或显式导入 dataset，并在画面和口播中标注 Synthetic Evaluation Input。

## 无网展示步骤

1. 使用与 APK 相同的仓库 commit，确认 manifest 中的路径存在。
2. 准备预置或显式导入的 synthetic dataset；不要把 clean-install 后的默认 Room 说成已自动载入 fixture。
3. 在已准备的 fixture replay/evaluation 环境中，使用 runtime fixture 或显式注入的 Fake Repository 展示事件、规则结果、场景匹配、证据链和本地解释。
4. 选择 `e-20260921-0003` 与 `e-20260921-0004` 作为后台敏感访问与网络伴随的 synthetic evaluation input，并显示来源标签。
5. 需要网络事件源接口回放时，在测试或专用 harness 中显式注入 `ReplayNetworkEventSource`；不要把该替换描述成默认生产配置。
6. 处置复查只使用 manifest 登记的 deterministic recheck cases/oracle 做评测说明。若当前页面没有注入回放输入，显示 unavailable/unknown，不手工制造执行结果。
7. AI 未配置或断网时，使用已有本地 deterministic explanation fallback；不得为了演示调用在线模型或让模型补充事件事实。

## manifest 字段语义

`runtimeCopied=true` 表示这个 canonical source 在 `app/src/main/assets/` 中存在 runtime copy；它不表示该 manifest 项会被 runtime 自动加载，也不表示 clean-install 会自动 seed Room。`runtimeCopied=false` 表示该条目本身是 runtime asset、评测输入或 oracle，不应被 manifest 当作复制指令。

## 真机内容不能由本包替代

- DEMO-B 的 Android 16 平台限制是目标设备上的实际 Runtime Ground Truth；离线 `e-20260921-0003` 不是该真机结果。
- DEMO-C 的 `com.demo.calculator → example.com:443` 是 PJW110 / Android 16 上 VPN 观察到的 Observed Fact；应用层最终 `ConnectException`，不由离线包改写为连接成功。
- DEMO-D 的 revoked-location probe 真实结果是目标设备上的平台边界/失败事实；离线 synthetic permission-revoked fixture 不表示绕过权限并成功取得位置。
- VPN 授权、底座链路、Activity 生命周期、后台限制和真实系统设置往返仍须按真机验收材料单独执行。
