# B8-2 最终截图与 MP4 拍摄方案

> 任务：B8-2（阶段 8：3 张核心截图和最终 MP4）
> 文档性质：拍摄方案，不是截图、视频或最终媒体产物
> 事实基线：`origin/main` 的 `6b1e222359d1251dbe5246083dfd117a0cf217ec`；最终发布 tag 为 `causalguard-v1.0.0`（指向发布构建 commit `ad637b5`）
> 状态：A8-1～A8-5 的发布、真机 smoke、清理和 GPL 核对记录已在当前基线完成；B8-3 和 B8-4 附录源稿已合入。B8-2 的真实截图与 MP4 尚未生成，本轮不录制媒体。

## 1. 目标、交付物与事实边界

### 1.1 目标

以不夸大能力的方式，完成竞赛要求的三张核心截图和一段最终 MP4：让评审能看见 CausalGuard 的“证据来源 → 场景/规则判断 → 人类可读解释 → 可逆建议 → 复查或无法确认”闭环，同时理解真实观测、Demo 真值和离线 fixture 的边界。

### 1.2 计划交付物

| 交付物 | 最终约束 | 本文产出 |
|---|---|---|
| 核心截图 | 恰好 3 张，JPG/PNG，每张不超过 1 MB | 截图编号、画面、操作和验收标准 |
| 演示视频 | MP4，不超过 5 分钟、不超过 150 MB | 分镜、时长、操作、旁白、失败降级和真实性检查 |
| 设计/答辩材料 | 与最终 APK、源码、截图和视频来自同一最终 tag | 拍摄前后的版本一致性清单 |

### 1.3 事实截止点与用语

本文只依据当前 `origin/main` 可证明的页面、场景和交付记录编排：主 App 具有首页、时间线、事件详情、设置与 Spike/Debug 入口；Demo App 包含 Map（DEMO-A）、Calculator（DEMO-B/DEMO-C）和 Weather（DEMO-D）场景；当前主 App 的默认产品展示明确标注为 `Fixture / 离线演示分析`。

下列内容必须按来源分开拍摄和说明：

| 标识 | 可展示的事实 | 禁止表述 |
|---|---|---|
| `REAL` / Observed Fact | Android 系统事实、授权后的 Usage/VPN 网络元数据 | 请求体、敏感内容、已发生泄露 |
| `SANDBOX` / Demo Ground Truth | Demo App 自己受控触发的操作或平台探测结果 | 普通第三方 App 的完整敏感访问历史 |
| `Fixture / 离线演示分析` | 固定 JSON 驱动的规则、证据链、解释和 UI | 设备刚刚实时发生的事件 |
| `Unknown` / E5 | 无法归属、缺证据或不可观测状态 | 强行归因、确定性处置、已改善 |

当前基线已记录 A8-1 的预演和 `v1.0.0` 真机 smoke，A8-2 的 `v1.0.0` release APK/源码包，A8-3 的提交清理、A8-4 的 GPL 核对和 A8-5 的最终 tag；这些记录不等于 B8-2 媒体已经实拍。B8-3 已合入，B8-4 的评测与失败案例附录源稿已合入但任务看板仍保留最终材料汇编状态。`origin/main` 当前未包含 `docs/b8-1-formal-design-document.md`，故本文不将 B8-1 写成已合入事实，待其分支/PR 与最终材料统一核验。截图、MP4、其媒体真实性交叉核对及 B8-5 文案核对仍为待完成事项。

## 2. 拍摄总原则

1. 先在全新、已清理的设备上完成安装、授权、场景 reset 和版本核对，再开始录制。
2. 任何 fixture 画面必须保留应用内的“Fixture / 离线演示分析”“内置 v0.1 fixture”或事件 `Mode` 标识；不得裁掉这些文字后当作实时监测。
3. Runtime DEMO-C 只可陈述“后台最小 TCP connection attempt”和“VPN 是否观测到连接元数据”；`ConnectException`、未命中 tracker、无 payload 都不改写为成功连接、tracker 命中或数据外传。
4. Runtime DEMO-B 的 `Platform Restricted` 与 Runtime DEMO-D 的 `Failure(IllegalStateException)` 都是能力边界结果，不为展示高风险而伪造 `PrivacyEvent`。
5. 高风险、规则命中、解释和建议可用离线 synthetic fixture 展示，但旁白和画面必须说明其是可重复的离线评测/演示输入。
6. 处置成功、处置不可用和复查 `unknown` 都是可接受的真实结果；只展示执行回执和可比较观察所支持的结论。
7. 不出现真实账号、手机号、设备序列号、局域网 IP、Wi-Fi SSID、API key、keystore 路径、原始日志、剪贴板内容、坐标、通讯录内容或未脱敏网络载荷。

## 3. 三张核心截图方案

### S-01：离线风险总览与最近告警

| 项 | 拍摄要求 |
|---|---|
| 对应场景 | 主 App 首页，使用内置 fixture 的离线分析模式 |
| 进入步骤 | 启动主 App → 等待首页完成加载；不进入 Spike/Debug 页面 |
| 必须展示的 UI | `CausalGuard`、`隐私因果哨兵`、运行模式、`当前数据集来自离线 fixture，不代表实时设备监测状态。`、风险总览、当前数据集事件数、最近告警、来源标签和“查看事件时间线”入口 |
| 对应事件/风险 | 最近告警必须是当前 fixture 中实际由规则分析得到的中高风险事件；截图保留 App 名称、事件类型、风险等级和分类 |
| 证据与解释 | 最近告警卡片必须保留解释摘要和来源（Demo/观测/fixture）文字；本图只承担概览，不把卡片摘要当作完整证据链 |
| 建议 | 首页未完整展示建议时，不在图片外另行加“已处置”字幕；建议和复查由 S-02/视频展示 |
| 禁止出现 | 把离线 fixture 写成实时设备监测；调试输出、未授权状态、系统通知、真实设备隐私信息、P1 每日摘要/PDF 功能 |
| 截图验收 | 运行模式文字可读、最近告警可读、无个人信息；单张 PNG/JPG ≤ 1 MB；版本与最终 APK/tag 一致 |

### S-02：高风险 fixture 的证据、解释与建议

| 项 | 拍摄要求 |
|---|---|
| 对应场景 | 从时间线打开一个实际存在的高风险 synthetic fixture 事件详情；优先选择当前规则资产实际输出 `high` 且具有 `matchedRules` 的事件 |
| 进入步骤 | 首页 → `查看事件时间线` → 点击目标事件 → 在详情页滚动到“Risk Assessment”“Why / Explanation”“Evidence”“证据支持链”和“Recommendation / 建议”可核验的区域；若系统支持滚动长截图，可使用不裁切来源标签的长截图 |
| 必须展示的 UI | 事件 `Source`、`Evidence`、`Mode`、风险等级、分类、置信度、场景匹配、命中规则、五段解释中的事实/理由/证据/行动/边界，至少一张证据卡片或证据支持链，建议标题与“这是建议，不代表已执行。” |
| 对应事件/风险 | 只使用当前 fixture/规则版本实际计算出的结果；若使用 `e-20260921-0003`/`0004` 的敏感行为与网络时间相关故事，必须明确它是 synthetic evaluation input，不是 Android 16 Runtime 事实 |
| 证据与解释 | 必须保留解释边界，例如未看到请求内容、不能确认数据外传；证据支持链中的 `supports` 不能裁剪成因果证明 |
| 建议与复查 | 截图可以展示 Recommendation；仅在最终真机有该事件的真实执行记录时才展示 `executed`/`blocked`/`reduced` 等复查区。否则必须保留“尚未执行处置”或“当前建议没有可安全执行的自动操作”，不能伪造成功处置 |
| 禁止出现 | 把 `R-003`/`R-005`/`R-007` 规则输出写为真实 tracker 或泄露事实；将建议写为已经执行；无法溯源的二次编辑标签 |
| 截图验收 | 从事件事实、规则到解释边界可追溯；fixture/synthetic 身份可见；建议与执行状态不混淆；无敏感原文；单张 PNG/JPG ≤ 1 MB |

### S-03：unknown 安全降级与不处置边界

| 项 | 拍摄要求 |
|---|---|
| 对应场景 | 当前 fixture 中 `packageName=unknown`、缺域名或 E5 的 unknown 事件详情；可使用 `e-20260921-0006` 对应的固定边界案例，前提是最终 APK 中该事件和当前规则版本实际可见 |
| 进入步骤 | 时间线 → 选择 unknown 事件 → Event Detail；滚动至“Risk Assessment”“Boundary / Unknown”“Recommendation / 建议”区域 |
| 必须展示的 UI | 事件来源与 `Mode`、无法归属/缺证据提示（若存在）、`riskLevel=low`、`category=unknown`、`confidence=low`、`scenarioMatch=unknown`、`Boundary / Unknown` 文本、无确定性自动操作的建议文案 |
| 对应事件/风险 | unknown 是安全降级边界，不是低风险安全证明；若缺少可靠包名/域名，不能指定目标 App 或阻断目标 |
| 证据与解释 | 必须展示“当前证据不足以确认风险。”或同义的当前产品文案；说明不强行归因是设计结果 |
| 建议 | 应显示“无法确认，暂不处置”或“当前建议没有可安全执行的自动操作”；不能出现 `BLOCK_APP` 成功、虚构复查成功或风险为零的结论 |
| 禁止出现 | 真实用户包名/UID/IP；将 unknown 说成“安全”“无风险”或“恶意 App” |
| 截图验收 | unknown 降级、低置信度和无确定性处置同时可读；可证明项目诚实处理缺失证据；单张 PNG/JPG ≤ 1 MB |

### 3.4 截图文件与命名

拍摄后以 final tag 为前缀命名，并在素材登记中记录 APK SHA-256、设备、时间和画面来源。例如：

```text
causalguard-<final-tag>-S01-fixture-overview.png
causalguard-<final-tag>-S02-fixture-evidence-recommendation.png
causalguard-<final-tag>-S03-unknown-degradation.png
```

最终只提交验收通过的三张；源文件、裁剪记录和关联版本信息留在受控的发布材料目录，不将真实设备日志或敏感数据纳入仓库。

## 4. 最终 MP4 分镜

### 4.1 时长与剪辑规则

目标总时长为 **4 分 10 秒至 4 分 45 秒**，留出片头/片尾和转场余量，严格小于 5 分钟。建议使用 12 个镜头；每段录制从真实设备开始，不使用无法复现的后期“效果图”。后期仅允许裁掉等待时间、加入不改变事实的标题卡和来源标识；不得用字幕替换实际不存在的 UI 状态。

| 镜头 | 预计时长 | 屏幕状态与操作 | 旁白要点 | 验收/边界 |
|---|---:|---|---|---|
| V-01 开场 | 12–15 秒 | 标题卡 → 主 App 首页；保留“Fixture / 离线演示分析”状态 | 项目关联使用场景、敏感行为和网络元数据，提供证据约束的风险辅助研判 | 不宣称实时全量监控或自动判恶 |
| V-02 来源图例 | 15–20 秒 | 首页/设置页的来源与版本信息；必要时简短标题卡解释 REAL、SANDBOX、FIXTURE、UNKNOWN | 真实观测、Demo 真值和离线 fixture 分开显示 | 标题卡不得冒充 App 画面；AI 为可选增强 |
| V-03 DEMO-B 平台边界 | 18–25 秒 | Demo Calculator：`Arm DEMO-B` → Home → 返回，展示 `Platform Restricted` | 后台 clipboard probe 已执行，但 Android 16 拒绝访问；未读取保存内容，未生成虚假 PrivacyEvent | 这是能力边界，不是高风险 Runtime 事件 |
| V-04 DEMO-C 触发 | 20–28 秒 | Demo Calculator：`Arm DEMO-C` → Home → 返回，展示 probe 状态/结果 | Calculator 只发起最小 TCP connection attempt，无 payload | 不说连接成功、tracker 命中或数据外传 |
| V-05 真实观测核对 | 18–25 秒 | A8-1 预演与 `v1.0.0` smoke 已证明主 App 可记录真实观测；录制时仅在本次设备仍可复现时展示 VPN 事件的来源、协议和可见线索，否则展示缺失/unknown 的诚实状态 | VPN 观测是独立 Observed Fact；是否可见域名/UID 以本次实机为准 | 已有回归记录不替代本段实拍；无观测绝不补造 `source=vpn` 事件 |
| V-06 低风险对照 | 18–24 秒 | Demo Map 前台位置场景或其明确 SANDBOX fixture 详情 | 位置访问不必然危险；前台地图场景可被判为低风险/必要 | 保留 Demo 标识；不展示坐标 |
| V-07 高风险 fixture 进入 | 20–26 秒 | 首页/时间线显示 fixture 高风险事件，点击详情；画面保留 fixture 标识 | 为可重复验证规则，下面展示固定 synthetic fixture，而非刚才 Runtime probe | 与 DEMO-C/D Runtime 画面用标题卡隔开 |
| V-08 风险、证据与解释 | 32–40 秒 | Event Detail：Risk Assessment、Explanation、Evidence、证据支持链 | 规则输出可追溯到证据；时间关联只表示 supports，不证明敏感数据外传 | 读出解释边界，不使用“泄露已发生” |
| V-09 建议与执行状态 | 25–32 秒 | Recommendation 区；若最终设备已验证，则操作 `BLOCK_DOMAIN` 或 `OPEN_SETTINGS` 并展示真实回执；否则展示建议未执行/能力不可用 | 建议可逆，建议不等于执行；系统设置打开不等于用户已修改设置 | 只说最终屏幕实际显示的状态；`BLOCK_APP` 属 P1/unsupported |
| V-10 复查 | 20–28 秒 | 观察窗口结束后点击“复查”，展示 `reduced`、`no_change`、`blocked` 或 `unknown` 的真实结果 | 复查比较前后可比窗口；无法确认同样是有效、诚实输出 | `v1.0.0` 已冻结，但本镜头仍依赖本次实拍的执行回执与可比较观察；不能把等待窗口时长说成用户操作耗时 |
| V-11 unknown 安全降级 | 18–24 秒 | unknown Event Detail：E5/无法归属、`Boundary / Unknown`、无自动操作 | 归属或证据不足时不强行指向 App，也不生成确定性处置 | 不将 unknown 说成绝对安全或绝对恶意 |
| V-12 结束画面 | 12–16 秒 | 回到首页或版本/来源页，显示最终版本信息；标题卡列出能力边界 | 总结：证据可追溯、建议可复查、未知不强行归因 | final tag、APK、截图、视频版本一致后才录制定稿 |

### 4.2 拍摄顺序

为降低重复安装和状态污染，按以下顺序拍原始片段：

1. 清理设备、确认版本、授权和录屏设置；
2. 拍 V-01、V-02；
3. Reset Demo Calculator 后依次拍 V-03、V-04，再在已满足真机条件时拍 V-05；
4. Reset/启动 Demo Map，拍 V-06；
5. 回到主 App 的固定 fixture 模式，拍 V-07、V-08、V-11 和 S-01～S-03；
6. 仅在最终设备具备可靠目标、同签底座和观察条件时拍 V-09、V-10；否则拍摄当前真实的 `UNAVAILABLE`/`unknown` 分支并在旁白说明；
7. 复查每段版本、水印、来源标签和敏感信息，再剪辑 V-12。

## 5. 录制前置条件

### 5.1 版本与设备

- A8-2/A8-5 已记录 `v1.0.0` release APK、源码包和 `causalguard-v1.0.0` tag 的对应关系；B8-2 实拍前仍须取得受控归档中的 APK，并逐项登记本次安装的 APK SHA-256、Demo App/底座版本与 tag，不能以文档记录代替设备核对。
- A8-1 已记录 PJW110 / Android 16 / API 36 的预演和 `v1.0.0` smoke；如本次实拍换机、重装或出现运行差异，仍须记录型号、系统和影响，并按实际结果拍摄或降级。
- 安装顺序为底座 → 主 App → Demo App；需演示域名阻断时，底座和主 App 必须同一 release keystore 签名。
- 录屏前关闭其他应用通知、悬浮窗、个人账户、浏览器标签、蓝牙设备名和网络名称。

### 5.2 权限与运行状态

- 通知、Usage Access 和 VPN 授权按 [A7 安装·授权·清理·故障恢复](a7-install-auth-recovery.md)逐项检查；任何拒绝状态都按降级口径拍摄，不临时绕过。
- 需要真实网络观测时，确认 `tun0`、主 App 监测状态和底座可用；不能确认时转入离线 fixture 说明，不将其伪装为实时观测。
- DEMO-A/B/C/D 拍摄前点击各自的 `Reset Scenario`；DEMO-D 还需确认 baseline/session 已清除并按状态机重新执行。
- 在线 AI 不作为拍摄前置条件；关闭、未配置或超时时使用本地确定性解释模板。

### 5.3 测试数据与素材卫生

- fixture 截图仅使用仓库当前随 APK 加载的内置资产和当前规则版本；不手工编辑事件、规则、界面文本或风险结果。
- 真实网络镜头只使用 `example.com:443` 的最小 TCP probe 或已批准的无个人数据测试流量；不登录真实账号，不使用真实聊天、通讯录、位置或剪贴板内容。
- 拍摄前清空主 App 演示数据和不需要的设备数据；拍摄后按 A7 清理/卸载流程清除本地事件、SharedPreferences、VPN 和 Demo 状态。
- 输出文件不含 `.env`、密钥、keystore、原始 logcat、设备数据库或未脱敏截图。

## 6. 失败时的降级方案

| 失败或限制 | 不可做的事 | 允许的降级拍法/表述 |
|---|---|---|
| VPN 未授权、被回收或未观察到 DEMO-C | 不补造 VPN 网络事件 | 录 Demo 的 probe 结果；主 App 录制“监测已暂停”/unknown 或离线 fixture，并说明两者不同 |
| `domainHint` 或 UID 不可见 | 不猜测域名或包名 | 显示 IP/unknown 和低置信度边界；拍 V-11 |
| DEMO-B 显示 `Platform Restricted` | 不伪造 clipboard 成功事件 | 保留平台限制、未生成 PrivacyEvent、未保存原文的画面 |
| DEMO-D 显示 `Failure(IllegalStateException)` | 不改写为 Platform Restricted、成功定位或权限绕过 | 保留真实 Failure、无坐标、无 PrivacyEvent 的画面 |
| 阻断控制通道无法确认 | 不写“已阻断” | 展示 `UNAVAILABLE`/`FAILED`/`unknown`；移除成功阻断的旁白 |
| 观察窗口尚未结束或不可比较 | 不伪造 `reduced` | 展示“观察中”或“当前证据无法确认改善”；视频 V-10 仍可作为 unknown 复查镜头 |
| 在线 AI 不可用 | 不截取外部模型生成文本当核心能力 | 用本地模板解释；说明离线核心不依赖 AI |
| 本次实拍所需 APK、设备状态、评测/真机证据或媒体交叉核对不完整 | 不把 A8 已有记录或 fixture 当作本次实拍证明，也不提交最终成片 | 使用已冻结 `v1.0.0` 记录作为版本基线；只保留拍摄脚本和可重录原始步骤，等待实际 APK/设备材料与 B8-4/B8-5 最终核对 |

## 7. 媒体真实性核查清单

拍摄前、剪辑后和提交前分别核对：

- [ ] 3 张截图均为最终 APK 实拍，且恰好三张、每张为 PNG/JPG、每张 ≤ 1 MB。
- [ ] MP4 时长 ≤ 5 分钟、文件 ≤ 150 MB，含背景、核心流程、技术亮点和实际运行画面。
- [ ] 每个真实、Demo、fixture 和 unknown 片段均保留对应来源标识或口播说明。
- [ ] 没有把 Runtime DEMO-C 的连接尝试表述为成功连接、tracker 命中或数据外传。
- [ ] 没有把 synthetic fixture 的高风险规则故事表述为 Android 16 Runtime 事实。
- [ ] 没有把 `Platform Restricted`/`Failure` 改写为成功敏感访问。
- [ ] 没有把 Recommendation 写成已执行，或把打开设置页写成用户已改权限。
- [ ] 复查结论有实际执行状态、目标、观察窗口和可比较证据支持；否则使用 `unknown`。
- [ ] unknown 画面没有强行归属 App、域名、tracker 或确定性处置。
- [ ] 不含真实账号、个人信息、密钥、keystore、原始日志、局域网信息、剪贴板/位置/通讯录原文。
- [ ] 版本号、APK SHA-256、设备信息、最终 tag、截图、MP4、设计文档和源码包已登记且一致。
- [ ] 由成员 A/B 按 [验收清单](15-acceptance-checklist.md)第 6～8 节交叉复核；任何材料与最终 tag 不一致时不提交。

## 8. 与已有计划和验收的映射

| 本计划内容 | 对应依据 | 最终验收动作 |
|---|---|---|
| 三张截图、MP4 的数量/格式/大小 | [验收清单](15-acceptance-checklist.md)第 6～7 节 | 逐文件检查格式、数量、大小、播放和可读性 |
| “异常 → 判断 → 解释 → 处置 → 复查”故事线 | [演示与发布方案](16-demo-and-release-plan.md)第 3 节 | V-03～V-10 覆盖完整闭环或其诚实降级 |
| 真实、Demo、synthetic 分离 | [Demo 场景](demo-scenarios.md)第 2～8 节 | 逐镜头标记来源，核对口播不混写 |
| 断网、VPN、UID、域名降级 | [演示与发布方案](16-demo-and-release-plan.md)第 4 节、[A7 安装文档](a7-install-auth-recovery.md)第 8 节 | 至少排练并保留一种 unknown/降级片段 |
| 安装、授权、reset、清理 | [A7 安装·授权·清理·故障恢复](a7-install-auth-recovery.md)第 3～9 节 | 拍摄前后执行检查和清理 |
| 最终版本、真机与发布基线 | [A8-1 最终设备回归记录](a8-1-final-device-regression.md)、[A8-2 构建说明](a8-build-and-reproducibility.md)第 8 节、[A8-4 GPL 核对](a8-gpl-compliance-verification.md) | 以 `causalguard-v1.0.0` 与已登记 APK/源码包为版本基线；录制前重新核对本次实拍安装物和设备状态 |
| 阶段 8 待完成事项 | [任务看板](17-task-board.md)阶段 8、[工作导引](19-work-guide.md) | A8-1～A8-5 已完成；B8-2 媒体实拍、B8-4 最终材料汇编和 B8-5 文案/媒体交叉核对完成前，不将本方案当作最终媒体验收 |

## 9. 本轮范围声明

本任务只新增本拍摄方案。未生成或编辑真实截图、MP4、APK、代码、评测数据或媒体模板；未新增开源代码、第三方依赖、第三方数据或第三方模板。
