# A8-4 GPL 对应源码、许可证与 tag 核对

> 状态：已完成（对应源码/许可证核对；最终发布 tag 一致性由 A8-5 创建 tag 后以脚本复核）
> 责任人：成员 A（核对）／成员 B（复核）
> 关联任务：[17 任务看板](17-task-board.md) A8-4；[15 验收清单](15-acceptance-checklist.md) §5、§7；[20 开源复用建议](20-open-source-reuse-guide.md) §7、§11；[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
> 自动核对脚本：`scripts/verify-gpl-compliance.sh`（失败非零退出，可重复运行）。

---

## 1. 范围

GPL-3.0 网络底座的**对应源码可提供性**、许可证与登记一致性、以及发布 tag 可追溯性核对。结论：**GPL 对应源码可提供、许可证登记一致，无合规阻断项**。

---

## 2. 上游组件与对应源码

| 项 | 值 |
|---|---|
| 组件 | TrackerControl Android（网络底座） |
| Repository | https://github.com/TrackerControl/tracker-control-android |
| 固定版本 | tag `2026080501` / commit `9504d41b9f6fa1509d784e5503c084d4b428307d` |
| 许可证 | GPL-3.0（根 `LICENSE`，随 submodule 保持原样） |
| 引入方式 | git submodule `third_party/tracker-control-android`（非 vendored 进主仓历史） |
| 对应源码 | submodule 固定 commit + 团队两补丁（`git submodule update --init --recursive` 后 `scripts/apply-trackercontrol-hook.sh`） |
| 交付方式 | [源码包](a8-build-and-reproducibility.md) §5：主仓 + 底座源码 + 补丁 + `SOURCE_MANIFEST.txt` |

---

## 3. 团队对底座的修改（GPL 衍生）

以补丁形式交付，非 fork；登记于 `THIRD_PARTY_NOTICES.md` 第 1 节。

| 补丁 | 用途 | SHA-256 |
|---|---|---|
| `third_party/patches/a4-3-serversinkhole-network-hook.patch` | A4-3 网络事件广播桥接 | `97dec53c117a36da615672068e5aacc6e31570200d442bce02046bd463fbe024` |
| `third_party/patches/a5-1-domain-block-receiver.patch` | A5-1 域名阻断控制通道 | `28b7ef52d68ae25c2a5496d839fbe139e0e607c21cc45b5c60d58d22e329d3ad` |
| `scripts/apply-trackercontrol-hook.sh` | 应用/回滚补丁 | `6d259922f6d072e4786983c419192a8b1b15d7ffd64ea6b94f77c3ab068da5b3` |

---

## 4. 许可证清单核对

完整清单与人工结论见 [A7 依赖许可证报告](a7-license-report.md)。要点：

| 类别 | 结论 |
|---|---|
| 分发 APK 内 Maven 依赖 | 全部宽松许可证（AndroidX/Kotlin/kotlinx/Retrofit/OkHttp = Apache-2.0 等），无 GPL/AGPL 传染链路 |
| GPL-3.0 | 仅限独立进程网络底座（跨进程广播交互，不静态链接进 `:app`） |
| CC BY-NC-SA 4.0 | Disconnect tracker 数据，非商业（竞赛）用途，已署名并保持同许可 |
| MPL-2.0 | 底座内 gotatun，未修改其文件 |
| 测试依赖 | JUnit/Robolectric 等仅 `testImplementation`，不进入 release 类路径 |
| Submodule/数据集/工具链 | 非 Maven 引入，已在 `THIRD_PARTY_NOTICES.md` 人工登记 |
| 原创边界 | 底座能力不声称为团队原创；团队原创为事件契约/规则/证据/解释/UI/评测 |

---

## 5. 自动核对结果

```bash
scripts/verify-gpl-compliance.sh          # 检查 1~6
scripts/verify-gpl-compliance.sh --tag <最终 tag>   # A8-5 后追加检查 7
```

2026-10-09 本地运行结果（feature 分支，最终 tag 尚未建立）：

```text
== 1. 网络底座 submodule ==
  [ok]   submodule 存在：third_party/tracker-control-android
  [ok]   HEAD == 固定 commit 9504d41b9f6fa1509d784e5503c084d4b428307d
  [ok]   上游 tag == 2026080501
== 2. 底座许可证 ==
  [ok]   third_party/tracker-control-android/LICENSE 为 GPL-3.0
== 3. 团队补丁与 apply 脚本校验和 ==
  [ok]   a5-1-domain-block-receiver.patch SHA-256 一致
  [ok]   a4-3-serversinkhole-network-hook.patch SHA-256 一致
  [ok]   scripts/apply-trackercontrol-hook.sh SHA-256 一致
== 4. 补丁可干净应用到固定 commit ==
  [ok]   a5-1-domain-block-receiver.patch git apply --check 通过
  [ok]   a4-3-serversinkhole-network-hook.patch git apply --check 通过
== 5. THIRD_PARTY_NOTICES 登记 ==
  [ok]   包含：TrackerControl Android / GPL-3.0 / <pin commit> / 两补丁名
== 6. 不跟踪密钥/敏感大文件 ==
  [ok]   未跟踪密钥/APK/PDF/MP4 等文件
== 7. 最终发布 tag（可选） ==
  [skip] 未传 --tag；最终 tag 由 A8-5 创建后复核
全部检查通过。
```

---

## 6. 最终发布 tag 一致性（依赖 A8-5）

A8-5 在 `main` 创建最终 tag 后，运行：

```bash
scripts/verify-gpl-compliance.sh --tag causalguard-v<version>
```

脚本校验：

- tag 存在且指向当前 `HEAD`；
- tag 名称 == `causalguard-v<versionName>`（`app/build.gradle`）；
- 配合 [源码包](a8-build-and-reproducibility.md) §5 以该 tag 重新生成，确保 tag/源码/APK/`THIRD_PARTY_NOTICES` 四者一致。

> 凡材料与最终 tag 功能不一致，不得提交（[15 验收清单](15-acceptance-checklist.md) §8）。

---

## 7. 人工复核与签署

| 检查 | 执行人 | 结论 |
|---|---|---|
| GPL 对应源码可提供（commit + 补丁 + 脚本） | 成员 A | 通过 |
| 许可证清单无 Unknown/传染阻断 | 成员 A | 通过 |
| `THIRD_PARTY_NOTICES` 与最终 tag 一致 | 成员 B | 待最终 tag 后复核 |

> 复核人 / 日期：成员 B / ____（A8-5 建 tag 后完成）。
