# Git 分支与协作规则

> 适用范围：隐私因果哨兵（CausalGuard）
> 团队人数：2 人（成员 A 平台/网络/系统/发布主责，成员 B 产品/规则/证据/UI/评测主责）
> 权威来源：[docs/21-parallel-work-allocation-plan.md](docs/21-parallel-work-allocation-plan.md) 第 13 节。本文件为速查，冲突时以 21 为准。

## 一、分支模型

```text
main               始终可编译、可演示；禁止直接提交
feature/<任务名>   功能开发
docs/<任务名>      独立文档变更
fix/<问题名>       缺陷修复
release/<版本>     仅最终冻结期按需创建，不长期保留
```

不建立长期个人分支，也不额外增加长期 `develop`。推荐把任务编号放入分支名：

```text
feature/a-t1-trackercontrol-spike
feature/a-t3-room-repository
feature/b-t3-rule-engine
feature/b-t5-evidence-chain
docs/b-t1-demo-scenarios
fix/a-vpn-reconnect
```

## 二、每次开始任务前必须检查

```bash
git status
git branch --show-current
git fetch --prune origin
git log --oneline --decorate -5 origin/main
git rev-list --left-right --count HEAD...origin/main

git switch main
git pull --ff-only origin main
git switch -c feature/b-t3-rule-engine
```

规则：

- 工作区不干净：先完成当前任务提交，或安全 stash；不能把旧任务改动带进新分支；
- 当前在 `main`：禁止直接修改和提交；
- 新任务必须基于最新 `origin/main`；
- `git pull --ff-only` 不能快进时，先查明分叉原因，不能用普通 `git pull` 自动制造 merge commit。

## 三、已开始分支如何同步

个人独占分支：

```bash
git fetch origin
git rebase origin/main
# 解决冲突并重新运行测试
git push --force-with-lease
```

已共享分支：

```bash
git fetch origin
git merge --no-ff origin/main
# 解决冲突并重新运行测试
git push
```

禁止 `git push --force`。更新个人分支只允许 `--force-with-lease`，并确认无人依赖。

## 四、Commit 规范

使用简化 Conventional Commits：

```text
feat(network): adapt connection event to PrivacyEvent
feat(rules): add background tracker rule
fix(vpn): recover after network switch
test(rules): add unknown attribution cases
docs(license): record TrackerControl source and changes
chore(build): pin NDK version
```

要求：

- 一个 commit 只做一个逻辑变化；
- 禁止 `update`、`fix`、`改了一下` 等无信息说明；
- 提交前运行受影响测试；
- 不提交密钥、keystore、个人数据、原始设备日志、构建产物和无关格式化；
- 开源源码导入和团队修改必须分成不同 commit；
- 不能编译的 WIP 可以留在个人分支，但不得进入 `main`；
- 规则、数据模型和文档变更必须与对应代码处于同一提交或同一版本记录。

## 五、PR 与合并

所有任务提交到短任务分支，再向 `main` 发 PR。PR 必须包含：

- 对应任务编号；
- 改了什么、为什么；
- 测试命令和结果；
- UI 截图、网络日志/fixture、schema 前后示例等证据；
- 新增/修改的第三方代码或数据；
- 已知限制、降级和回滚方式。

合并条件：

- CI 构建和测试通过；
- 另一名成员 review；
- 已同步最新 `main`；
- 契约/Room/schema 变更有兼容说明和测试；
- 新开源项已固定版本并登记许可证；
- 合并后不破坏主演示链。

默认使用 **Squash and merge**。开源底座首次导入需要保留上游历史时可例外使用 merge commit，并在 PR 说明。

## 六、第三方代码与数据

1. 引入前先更新 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，固定 tag 或完整 commit SHA；
2. 核对具体版本内的根 `LICENSE`、`NOTICE` 和被复制文件头部声明；
3. 第三方源码首次导入单独一个 commit，团队修改另起 commit；
4. 在 PR 中说明实际使用范围、修改内容和团队原创边界；
5. 选型与许可证判定见 [docs/20-open-source-reuse-guide.md](docs/20-open-source-reuse-guide.md)。

## 七、版本与一致性

- 文档基线版本统一标记为 `v0.1`，冻结后再递增；
- APK、源代码、设计文档、截图和视频展示的必须是同一版本功能；
- 版本号规则：`主版本.次版本.修订号`，如 `0.1.0`；
- 发布时生成完整 commit SHA 与 SHA-256，并在最终 commit 创建带注释 tag。

## 八、每日工作机制

- 开始前 10 分钟：确认当天唯一主任务、契约变化、目标分支和阻塞替代任务；
- 结束前 20~30 分钟：review/合并当天增量，更新 [docs/17-task-board.md](docs/17-task-board.md) 和 [docs/18-risk-register.md](docs/18-risk-register.md)；
- 每人每天最多保留：今日必须完成 1 项、完成后再做 1 项、阻塞替代 1 项。

禁止：

- “等待 A 完成 VPN”/“等待 B 完成规则”；
- 两人共同负责但无人主责；
- 三天不合并，最后集中联调；
- 在聊天中口头修改契约而不更新 schema/fixture；
- 未运行测试就把分支交给对方排错。

## 九、Definition of Done

- [ ] 在正确的短任务分支，不是直接修改 `main`；
- [ ] 开始前已 fetch 并基于最新 `origin/main`；
- [ ] 有唯一主责、明确输入、输出和验收证据；
- [ ] 受影响测试通过；
- [ ] 新增第三方代码/数据已固定版本并登记许可证；
- [ ] unknown、权限拒绝、断网和缺失数据有降级；
- [ ] PR 已由另一人 review，CI 通过；
- [ ] 合并后 `main` 可编译且主演示链未破坏。
