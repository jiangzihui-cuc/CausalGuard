# B8-5 最终材料真实性审查记录

> 任务：B8-5（阶段 8：核对最终文案、截图和视频不夸大实际能力）
> 审查基线：`origin/main` `c51755814c1f4d0a32b27b062ccddbae3f22f103`
> 发布基线：`causalguard-v1.0.0`，构建 commit `ad637b5`
> 结论：`BLOCKED`
> 事实截止点：2026-10-10。

## 1. 范围与责任边界

本记录只审查当前 `origin/main`、`docs/screenshots/` 中的 PNG、项目目录外的候选 MP4，以及最终材料相关文档。不修改业务代码、截图、视频、B8-1/B8-2/B8-3/B8-4、任务看板或其他既有文件。

真机安装、通知/Usage Access/VPN 授权、Demo 复现、VPN/网络观测、处置与复查、截图和录屏由成员 A 执行并提供原始证据。成员 B 负责最终媒体方案、截图筛选、MP4 分镜与旁白编排、`REAL`/`SANDBOX`/`Fixture`/`Unknown` 边界，以及最终真实性和版本一致性核对。本记录不把文案审查写成成员 B 已执行真机测试。

本任务未新增开源代码、第三方库、第三方数据、媒体模板或许可证条目；未执行 Android/真机测试；未提交 MP4 或临时抽帧。

## 2. 基线与文档状态

| 项目 | 当前事实 | 结论 |
|---|---|---|
| `main` | `c51755814c1f4d0a32b27b062ccddbae3f22f103` | 本次核验基线 |
| 发布版本 | `causalguard-v1.0.0`；APK 1.0.0 / versionCode 3 | 实际媒体仍须核对安装物 |
| B8-2 | main 已包含真实观测为主方案；记录约 74 秒片段，V-05/V-06 为补录项 | 不能称最终媒体完成 |
| B8-3 | 已在 main | 未发现将第三方底座/数据写成原创 |
| B8-4 | 附录及 A 提供的软件门禁、v1.0.0 真机证据已在 main | 明确为 `source=vpn` 真实观测 |
| B8-1 | main 不含 `docs/b8-1-formal-design-document.md`；源稿在 `origin/docs/b8-1-design-document` | 只核对，不修改 B8-1 |
| B7-4 | 仍有“A8-4 尚未完成”等历史表述 | 与当前 A8-4/A8-5 不一致；本任务不修改 |

## 3. 必须保留的边界

- `REAL / Observed Fact` 只表示授权后观测到的 Android/VPN 网络元数据，不表示读取请求正文、敏感内容或已经泄露。
- `SANDBOX / Demo Ground Truth` 只表示 Demo App 自身受控动作或平台限制，不代表任意第三方 App 的完整敏感访问历史。
- `Fixture / Synthetic Evaluation` 只表示固定输入、规则、解释和评测结果；fixture 不等于实时设备事件。
- `Unknown / E5` 表示证据不足、无法归属或不可观测；unknown 不等于安全，也不等于恶意。
- TCP connection attempt 不等于成功连接；连接不等于敏感数据外传；tracker 分类不等于泄露证明。
- `supports` 不得改为 `causes`；Recommendation 不等于已执行；打开系统设置不等于权限已修改。
- 无证据时不得补造事件、tracker 命中、处置回执、复查改善或外传结果。

视频抽帧可见 `source=vpn`、`Evidence: E2`、`E5 · Unable to Confirm / Unknown`、`无法确认，暂不处置` 和“这是建议，不代表已执行”等边界文案，未发现直接声称“已泄露”或“已阻断”。

## 4. 截图核验

8 张截图均为 PNG、1080×2412，大小均小于 1 MB，最终只能提交 3 张。

| 文件 | 实际核验 | 状态 |
|---|---|---|
| `home-overview.png` | CausalGuard 首页；真实观测、事件数和风险总览可见，无“实时全量监控/已泄露”表述 | 可作为 S-01 |
| `event-detail-chain.png` | CausalGuard 真实观测详情；Source=vpn、E2、E5 Unknown、证据支持链、Recommendation“无法确认，暂不处置”、尚未执行处置可见 | 可作为 S-02；不是高风险/泄露证明 |
| `event-detail-recommendation-recheck.png` | 同类真实观测详情；建议和“尚未执行处置”可见 | 补充候选，不宜重复提交 |
| `event-detail-facts.png` | low、category unknown、confidence low、scenario unknown，但仍显示 Chrome 包名和网络元数据 | 补充证据；不能单独证明无法归属 |
| `event-detail-unknown.png` | 另一套红色 TrackerControl/隐私统计界面，显示“6 联系的跟踪主机”“1 跟踪公司”“0% 已屏蔽”，不是 CausalGuard Event Detail | **排除，不得作为 S-03** |
| `home-usage-access-revoked.png` | 明显页面叠化/转场残影 | 排除 |
| `timeline.png` / `settings.png` | CausalGuard 真实观测时间线/设置页，可作视频或补充材料 | 不作为核心三张优先项 |

当前只能确认 S-01=`home-overview.png`、S-02=`event-detail-chain.png`；S-03 尚无合格独立文件。`event-detail-unknown.png` 必须排除；其他详情图仍是已识别 Chrome 的真实观测，不能冒充专门的“无法归属/缺证据 unknown”截图。因此整体不能判为 `PASS` 或 `PASS_WITH_MEDIA_EDIT`。

截图中有远端 IP、域名线索、包名和 UID 等真实观测元数据；未见账号、手机号、局域网 IP、Wi-Fi SSID、设备序列号、密钥或剪贴板/位置/通讯录原文。提交前仍由 A/B 按最终政策确认是否保留非个人观测字段。

## 5. MP4 核验

候选视频为仓库父目录 `/home/liuxinyue/projects/video.mp4`，不在 Git 仓库内，未添加或提交。实测为：

| 项目 | 实测值 | 结论 |
|---|---|---|
| 编码 | H.264 视频 + AAC 音频 | 格式通过 |
| 时长 | `74.075542` 秒 | 小于 5 分钟 |
| 分辨率 | `576×1280` | 与 B8-2 历史 `858×1920` 不一致 |
| 大小 | `7,238,561` bytes（约 7.0 MiB） | 小于 150 MB |
| SHA-256 | `b61c44d62c17fb2f1856a79a5d85b9965af177f8e1ed70afbbffd13492a03563` | 已记录，未入 Git |

抽帧覆盖首页、真实观测时间线、事件详情、E2/E5 证据链、Recommendation、尚未执行处置、设置页和返回首页；没有把连接写成泄露或把 Recommendation 写成已执行。

仍需处理：左上角有录屏控件标记，页面切换有叠化/残影；未覆盖 B8-2 方案中的 Demo 段和独立 unknown 补录；不能称为完整方案成片。不得继续使用“约 858×1920、约 42 MB”的历史记录，提交材料须统一为实测值。视频能力表述可判为 `PASS_WITH_MEDIA_EDIT`，但 S-03 缺失使总体结论仍为 `BLOCKED`。

## 6. 文案与已知限制

当前文案保留：真实网络事件 `Foreground` 可能为 unknown；R-005 tracker 数据未接入 runtime；DEMO-C 可能受 ColorOS 后台限制而未被 VPN 观测；网络元数据不证明外传；Recommendation 不等于执行；unknown 不等于安全；在线 AI 是可选增强，本地确定性模板是核心降级路径。

本任务不修改的问题：`docs/b7-4-defense-brief.md` 仍有 A8-4 尚未完成的历史表述；B8-1 源稿尚未进入 main；B8-2 的 S-03 仍缺独立 unknown 边界画面。

## 7. 最终结论与门槛

**BLOCKED**：当前不能把三张截图和 MP4 作为最终材料直接提交。

阻断原因：缺少合格的 CausalGuard S-03 unknown 截图；同名 PNG 属于其他应用；MP4 需要去除录屏控件、处理叠化转场，并明确是否补录 Demo/unknown 段；B7-4 的历史 A8 状态文案也需单独处理。

成员 A 需提供真实、可复查、明确标注 `Unknown / E5` 且不强行归属/处置的 CausalGuard 截图，并完成视频编辑和版本登记后，B8-5 才能重新核验。本任务不执行后续动作。

## 8. 证据链接

- [截图目录说明](screenshots/README.md)
- [B8-2 拍摄方案](b8-2-demo-capture-plan.md)
- [B8-4 评测附录](b8-4-evaluation-and-failure-appendix.md)
- [A8-1 最终设备回归记录](a8-1-final-device-regression.md)
- [A8-2 构建说明](a8-build-and-reproducibility.md)
- [B8-3 第三方与原创边界说明](b8-3-third-party-original-boundary.md)
- [验收清单](15-acceptance-checklist.md)

本记录只报告实际审查结果，不替代 B8-1/B8-2/B8-3/B8-4 文档。
