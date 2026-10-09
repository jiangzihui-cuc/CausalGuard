# A8-3 提交前清理核对

> 版本：`v0.2.0-rc1`
> 最后更新：2026-10-09
> 责任人：成员 A（协作：成员 B）
> 关联任务：[17 任务看板](17-task-board.md) A8-3；[A8-2 构建说明与源码包](a8-build-and-reproducibility.md)；[A8-4 GPL 核对](a8-gpl-compliance-verification.md)；[12 隐私与安全设计](12-privacy-security-design.md)；[16 演示与发布方案](16-demo-and-release-plan.md) 第 6 节。
> 目标：提交前确认仓库**不含**密钥、签名、账号、真实隐私数据、原始设备日志和临时文件，且这些内容也不在 git 历史中。

---

## 1. 检查方式（可复现）

```bash
scripts/verify-clean-submission.sh
```

脚本执行四项检查，任一失败即非零退出：

| # | 检查 | 说明 |
|---|---|---|
| 1 | 工作区受控文件 | `git ls-files` 不含 `*.jks`/`*.keystore`/`keystore.properties`/`secrets.properties`/`.env*`/`*.pem`/`*.key`/`*.p12`/`*.apk`/`*.aab`/`*.pdf`/`*.mp4`/`*.log`/`*.hprof`/`*.tmp` |
| 2 | git 历史新增文件 | `git log --all --diff-filter=A --name-only` 中从未新增过上述敏感/大文件 |
| 3 | 非忽略未跟踪文件 | 工作区无 `??` 文件（忽略的构建产物、`local.properties` 不计） |
| 4 | `.gitignore` 覆盖 | 含 `*.jks`/`*.keystore`/`keystore.properties`/`secrets.properties`/`*.apk`/`*.aab`/`*.pdf`/`*.mp4`/`*.log` |

## 2. 本次结果（2026-10-09）

| 检查 | 结果 |
|---|---|
| 工作区受控文件 | ✅ 无敏感/大文件 |
| git 历史新增文件 | ✅ 从未提交敏感/大文件 |
| 非忽略未跟踪文件 | ✅ 无（脚本自身提交前为待跟踪状态，属预期） |
| `.gitignore` 覆盖 | ✅ 9 项关键模式齐全 |

结论：**当前仓库满足提交清理要求。**

## 3. 数据边界（与 [12 隐私与安全设计](12-privacy-security-design.md) 一致）

- 不入库：密钥与签名（`*.jks`/`*.keystore`/`keystore.properties`/`secrets.properties`）、真实设备原始数据库与日志、真实隐私数据。
- 脱敏离线 fixture（如 `docs/fixtures/real-network-events-a4-v0.1*.json`）为去标识化产物，可入库；原始采集数据不得入库。
- 构建产物（APK/AAB/源码包）通过 Release 流程单独归档，不直接入库。
- 提交材料中的视频/PDF 通过归档目录管理，不放入仓库。

## 4. 门禁与复跑

- A8-3 在**最终 tag 建立前**必须**以最终冻结 commit 重跑一次**，保证与 A8-2 最终产物、A8-4 核对、A8-5 tag 版本一致。
- 若检查 3 出现实际未跟踪文件，提交前需确认其中无密钥/真实数据，或加入 `.gitignore`。
- 若检查 2 命中，则需按 [16 演示与发布方案](16-demo-and-release-plan.md) 第 6 节从 git 历史清理后再提交。
