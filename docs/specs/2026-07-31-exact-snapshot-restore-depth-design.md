# Exact snapshot restore 完整 depth 设计（事后重建）

**原始设计日期**：2026-07-29
**重建日期**：2026-07-31
**状态**：Post-hoc reconstructed design（事后重建设计记录）

## 文档定位

这份文档不是实施前保存下来的原始设计稿。它根据原始设计会话、已批准决策、Git
提交、当前合同、实现和合同测试事后重建，用于保存模块形状、设计理由、取舍和演进过程。

权威性按以下顺序理解：

1. [`docs/SNAPSHOT_NODE_KIND.md`](../SNAPSHOT_NODE_KIND.md) 是
   `SNAPSHOT`、setup、PASS 与 exact restore 行为的权威合同。
2. 本文解释为什么采用当前 module/interface、ownership 和 failure semantics；不重新定义
   上述行为合同。
3. 当前源码与测试是实现和回归证据。若它们与权威合同冲突，必须显式报告并由维护者裁决，
   不能以本文替代合同。
4. Review handoff 只记录审查进度和待核证事项，不是设计 source of truth。

## 重建证据

本设计由以下证据交叉恢复：

- 原始架构设计会话：用户选择“让 exact snapshot restore 拥有完整 depth”，随后逐项批准
  本文“原始已批准决策”中的九项决策。
- `ecba216d`：`refactor(engine): 收敛精确快照恢复架构`，实现初始 deep module。
- `aa757492`：`fix(engine): capture snapshot restore plan before commands`，补齐所有前置命令前
  capture 的合同。
- [`docs/SNAPSHOT_NODE_KIND.md`](../SNAPSHOT_NODE_KIND.md) 当前 exact restore 合同。
- [`CONTEXT.md`](../../CONTEXT.md) 中“精确快照引擎恢复”的领域定义。
- 当前 `ExactSnapshotEngineRestore`、各恢复入口和相关合同测试。
- `lizzieyzy-exact-snapshot-restore-review-handoff-2026-07-31.md` 中记录的后续 review
  结论；其中 worker 自报测试结果仍需独立 fresh 验证。

## 背景与问题

重构前，exact restore 的知识分散在 `Board`、`BoardHistoryNode`、`ReadBoard`、
`LeelazEngineCommandSink`、`Leelaz` 和一个浅层 restore helper 中。调用方需要分别知道：

- 如何从目标节点找到最近可用的静态 `SNAPSHOT` 锚点；
- 哪些后继节点是真实 `MOVE/PASS`，可以作为 tail 重放；
- 如何 materialize 临时 SGF、选择主/副引擎并安排 `loadsgf`；
- 临时 SGF 何时可以删除；
- 失败、超时、晚到响应、部分写和 engine arbitration 应如何收敛；
- ponder、komi、当前 history 和目标引擎应该在哪个时点冻结。

这使 restore helper 的 interface 很浅：调用方仍然掌握几乎全部实现知识。删除该 helper
不会消除复杂度，只会让同一复杂度继续留在多个 caller。目标是把这些知识集中到一个
拥有完整 depth 的 module，让 caller 通过小 interface 获得恢复能力和一致语义。

## 领域术语

**精确快照引擎恢复（exact snapshot restore）**：从目标之前最近可用的静态
`SNAPSHOT` 锚点恢复引擎盘面，在该锚点成功生效后，只续接锚点后的真实
`MOVE/PASS`。所有 captured target 达到模块完成边界前，恢复不算完成。

**prepared restore**：`prepare(...)` 返回的句柄，封装一次已经冻结的 immutable
restore plan。后续前置命令和 callback 不能改变该 plan 的目标、盘面、tail、komi、mirror、
ponder disposition 或 admission identity。

**未进入 exact restore**：目标祖先链没有可用静态锚点，或者只有新棋局默认的空 root
`SNAPSHOT`。此时 caller 保留原有 root replay；这不是 exact restore 的失败 fallback。

**exact restore 失败**：已经捕获并开始执行 exact restore 后，admission、`loadsgf`、tail
提交或底层协议生命周期失败。该状态禁止降级为 root replay。

## 目标

- 让 `ExactSnapshotEngineRestore` 成为 exact restore 的唯一编排 owner。
- 通过 `prepare(...) -> execute()` 的小 interface 隐藏 anchor、tail、mirror、SGF、cleanup
  和 completion sequencing。
- 在任何可能改变恢复输入或目标的前置动作之前冻结 immutable plan。
- 让所有恢复入口共享同一套 fail-closed 语义。
- 保持 `Leelaz` 对 ordinary GTP queue、response、timeout、output stream 和 engine
  arbitration 的唯一 ownership。
- 让 caller 和合同测试都通过同一个 module seam 使用恢复能力，提升 leverage 与 locality。

## 非目标

- 不改变 `MOVE`、真实 `PASS`、dummy PASS、`SNAPSHOT` 或 setup 的历史语义。
- 不新增第二条 GTP queue、通用 transaction、rollback、retry 或新的长期 ownership
  resource。
- 不把 `Leelaz` 的 response handler、timeout、outstanding retirement 或 output-stream
  invalidation 搬入 restore module。
- 不在 exact restore 失败后猜测性 root replay，也不把失败静默改写为成功。
- 不由 restore module 设置通用 `ENGINE_STATE_UNRESTORED`。
- 不让 restore module拥有或启动 ponder；它只冻结 disposition 并返回完成结果。
- 不把 ReadBoard GMA 的 final-play epoch、外部点击授权或同步状态机耦合进 restore module。
- 不为测试替身、任意 subclass 或未来可能性增加通用 adapter/DI 抽象。
- 不改变 Web trial、tracking、PK 或 ReadBoard 的产品语义；本设计只统一它们需要使用的
  exact restore handoff。

## Module seam 与 ownership

### `ExactSnapshotEngineRestore`

该 module 拥有：

- 从 history target 找到最近可用静态锚点；
- 将 removed-stone 等显式静态 seed materialize 为 snapshot；
- 捕获 snapshot stones、轮次、盘尺寸、setup metadata 与 komi；
- 捕获锚点后的真实 `MOVE/PASS` tail；
- 捕获主目标、可用 mirror、目标集合与 ponder disposition；
- 捕获本次恢复的 owner/admission 身份；
- lifecycle 入口在副作用前冻结 exact/root 路线；root 路线也捕获同一 target/mirror/owner 的 admission，并只允许一次性执行；
- 将 Board restore 所需的 exact preclear 发送到 captured target set，不通过 execution-time default mirror；
- lifecycle root replay 在 callback 前及每条 root command enqueue 时复验同一 admission，拒绝即显式失败；
- 生成并清理临时 SGF；
- 编排所有 captured target 的 `loadsgf`；
- 在 snapshot 成功消费后向所有 captured target 提交 tail；
- 返回明确完成结果，或传播原始失败。

### `Leelaz`

`Leelaz` 继续唯一拥有：

- ordinary command queue 与实际发送；
- response handler 绑定、GTP response 解析与 timeout；
- outstanding response retirement 与发送窗口推进；
- late response 隔离所需的底层协议机制；
- output-stream cleanup/invalidation；
- engine arbitration 与 restore admission gate。

`ExactSnapshotEngineRestore` 只能通过 `Leelaz` 提供的窄内部 seam 使用这些能力，不能复制、
旁路或接管它们。

### Caller / lifecycle owner

Caller 只负责：

- 选择要恢复的 history target 或显式 snapshot seed；
- 在自己的 owner/lifecycle 语境中调用 `prepare(...)`；lifecycle caller 可以提供本次操作未来
  使用的 target pairing context，但 pairing 由 restore module 校验并冻结；
- 在 plan 已冻结后执行必要的 `stop`、`name`、`komi`、`clear_board`、engine start/switch
  等前置动作；
- 调用 `execute()` 并处理成功或原始失败；
- 全部目标恢复成功后，按冻结的 disposition 和自身既有策略决定是否恢复 ponder。

Caller 不能再提供或拼装 tail，不能在 pairing capture 后重新 resolve/reselect mirror，不能持有
临时 SGF lifecycle，也不能直接调用内部 cleanup/dispatch callback。prepare 与 lifecycle
reservation 必须消费同一个 frozen target-pair context。

Lifecycle 入口通过 opaque typed handoff 一次冻结 target、可用 mirror、合法的既有 reservation endpoint
和 owner identity。任意第三方 endpoint 在 reservation 副作用前显式拒绝。Caller 只把同一个 handoff
交给 `prepare` 与现有 lifecycle reservation seam；不能分别创建 raw owner、公开 target/mirror pair，
或传入“mirror 是否归本操作”boolean。Handoff 只绑定事实，不申请、持有或关闭 reservation；
reservation lifetime 仍归 lifecycle owner，配置替换和直接 restart 必须持有它直到 frozen target 完成
restore/board fence，执行阶段不能从可变 engine catalog slot 重新选择实例。

Captured mirror 不属于合法的独立 existing lifecycle endpoint。Handoff 在 mirror 与 target 不同时拒绝把 mirror 作为 existing endpoint：secondary switch 的 existing endpoint 是被停止的 frozen previous secondary；PK start/restart 没有额外 existing endpoint，只 reserve target。Mirror 上的竞争由 execute-time admission fail-closed，不以 reservation 清退其 tracking/foreground work。

当 lifecycle `prepare(...)` 没有找到可用静态锚点时，empty 不是允许副作用后重新 prepare 的信号。Handoff 在此时冻结 root-replay 路线与 admission；caller 只能通过该 handoff 一次性执行既有 root replay。Root payload 仍遵循入口原有的 live-board/root-movelist 语义，但 target、mirror、owner identity 与路线不再变化。

## 小 interface

Module 的外部形状收敛为两阶段 handoff：

```text
prepare(engine, historyTarget, context) -> Optional<PreparedRestore>
prepare(engine, explicitCurrentPosition, context) -> PreparedRestore

prepareLifecycleHandoff(existing, target, mirror) -> LifecycleRestoreHandoff
LifecycleRestoreHandoff.prepare(historyTarget, context) -> Optional<PreparedRestore>
LifecycleRestoreHandoff.executeRootReplay(replay) -> void

PreparedRestore.execute() -> Completion
PreparedRestore.executeAfterCapturedTargetClear() -> Completion
```

- history target 没有可用静态锚点时，`prepare(...)` 返回 empty，表示本次路线已确定为 root replay。普通 caller 保留原有 root replay；lifecycle caller 必须使用同一个 handoff 的 one-shot root execution，不能在副作用后重新 prepare。
- explicit current position 必须包含可物化的有效盘面状态；无效输入显式失败。
- explicit current position 可以是现有 `SNAPSHOT` 或已重建的当前 `BoardData`；module 内部负责
  clone/materialize 为静态 snapshot，caller 不再调用公开 snapshot conversion helper。
- `PreparedRestore` 隐藏 immutable plan，不暴露 tail、mirror、SGF 路径、dispatch 或 cleanup
  callback。
- `PreparedRestore` 是 one-shot；第二次 `execute()` 在任何文件或引擎副作用前显式失败。
- Board restore 需要的 preclear 与 exact execute 通过 `executeAfterCapturedTargetClear()` 形成一个 one-shot 操作；`clear_board` 只发给 plan 的 captured target set。普通 `execute()` 不凭空增加 caller 未请求的 preclear。
- 成功返回 `Completion`；失败通过原始异常/失败原因向上传递。没有额外的“失败后重试或
  fallback”结果。
- `Completion` 只提供 caller 真正需要的完成信息，例如冻结的 ponder disposition；不暴露
  `Leelaz` 内部协议生命周期。

## Immutable restore plan

`prepare(...)` 一次性冻结：

- 最近可用静态锚点及其 snapshot clone；
- stones、side-to-play、盘尺寸、setup properties/metadata、手数与 captures 等恢复所需状态；
- 当前棋局 komi，而不是稍后可能变化的目标引擎默认/cache komi；
- 锚点到目标之间的真实 `MOVE/PASS` tail；
- module 校验并冻结的 authority engine、captured mirror 和最终 target 集合；
- 入口时的 ponder disposition；
- restore owner、owner identity 与 mirror admission 条件。
- lifecycle root 路线的 target、captured mirror、owner/admission identity 与 exact/root 决策；root payload 本身继续由入口的既有 live-board/root-movelist 语义提供。

后续 callback 不得重新读取以下 mutable state 来修改同一个 plan：

- `Lizzie.board` 或当前/display history node；
- `Lizzie.leelaz`、`Lizzie.leelaz2` 或双引擎配置；
- engine cache 中可能已被前置命令改写的 komi；
- 当前 ponder 状态；
- 已被替换的新 lifecycle/GMA reservation。

## Capture handoff

恢复入口必须先 `prepare(...)`，再执行可能影响恢复输入、目标或 ownership 的动作，最后
`execute()`：

```text
resolve target
    -> prepare immutable exact plan or freeze root-replay route/admission
    -> lifecycle reservation / stop / name / komi / clear_board / engine start or switch
    -> execute captured exact plan or the same handoff's one-shot root replay
    -> caller applies completion disposition
```

这里的 handoff 边界是“第一个可能产生外部效果或改变恢复事实的动作”。在同一 arbitration
lock 内设置一个不发命令、不公开 reservation、也不改变 board/history 的内部互斥 flag，
不构成 plan 必须已经捕获的外部边界；但 public reservation 返回、tracking release `stop`、
engine replacement 或任何 GTP 前置命令都必须位于 capture 之后。

## 执行顺序

1. `execute()` 使用 plan 中的 snapshot 生成临时 SGF。
2. 在发送前复验 captured admission 与目标身份。
3. 通过 `Leelaz` 的既有 queue/response seam 向所有 captured target dispatch `loadsgf`。
4. 等待每个已 dispatch 的 `loadsgf` 成功消费，或收敛为明确失败。
5. 只有所有目标的 snapshot 都成功后，才向所有 captured target 提交 tail。
6. 模块完成边界是所有 tail command 已被对应 `Leelaz` ordinary queue 接受；tail 的逐命令
   GTP response 继续由 `Leelaz` 管理。
7. 临时 SGF lifecycle 覆盖所有已 dispatch target 的消费、retirement 和本次 tail 提交；达到
   完成或失败 cleanup 边界后才删除。
8. 成功时返回 `Completion`；caller 再按冻结 disposition 决定是否恢复 ponder。

原设计讨论曾把 sequencing 描述为需要新的异步方案。核对代码后确认既有
`Leelaz.loadSgf(Path, Runnable)` 已等待 dispatch completion，因此最终决策是移动 sequencing
ownership、隐藏 lifecycle，而不是新增公共异步接口或第二套调度机制。

## Failure semantics

Exact restore 一旦开始即 fail-closed：

| 场景 | 结果 |
|---|---|
| 没有可用静态锚点 | 未进入 exact restore；普通 caller 保留原有 root replay，lifecycle caller 已冻结 root 路线与 admission |
| Capture 时 owner/admission 冲突 | 零 restore 命令；显式失败 |
| Execute 前 admission 已失效 | `loadsgf` 前失败并清理已生成的临时 SGF |
| Root callback 前 admission 已失效 | 不调用 replay；显式失败 |
| Root replay 中途 reservation 关闭或 ABA replacement | 下一条 `komi` / `clear_board` / `play` enqueue 显式失败；不能静默成功 |
| Captured-target preclear 被 arbitration 拒绝 | `loadsgf` 前显式失败；不重选 execution-time mirror |
| `loadsgf` enqueue/send/write/flush 失败 | 不发 tail；退休对应协议状态并清理；传播原始失败 |
| GTP `?` 或无响应超时 | 不发 tail；隔离晚到响应并清理；传播原始失败 |
| 部分写导致 output stream 污染 | 不发 tail；由 `Leelaz` 失效该 stream；传播原始失败 |
| 一侧已 dispatch、另一侧失败 | 已发出侧完成消费或 retirement 后再清理；所有目标都不发 tail |
| Tail 被 engine arbitration 拒绝 | 显式失败；不 fallback 到 root replay |

Restore module 不把这些失败改写为 `ENGINE_STATE_UNRESTORED`，也不自行决定上层 UI、engine
replacement 或 retry 策略。具体 caller boundary 按各自既有产品语义处理原始失败。

配置替换、engine switch 与直接/自动 restart 的 board synchronization 若抛错，既有 lifecycle owner 必须把 frozen replacement/target 标为 unavailable，并在 completion boundary 释放 reservation。该 caller-level 收敛不因 restore failure 新建通用 `ENGINE_STATE_UNRESTORED`，也不引入 retry 或 root fallback；入口若正在恢复既有 ReadBoard GMA quarantine，失败后仍保留该既有 quarantine。

## Mirror 语义

- Capture 时由 restore module 校验并一次性冻结主/副 target；caller 即使提供未来 lifecycle 的
  pairing context，也不得在 prepare 与 reservation 之间重新读取全局 engine 字段或重新选 mirror。
- 从主引擎或副引擎入口发起时，只要调用实例属于当前主/副配对，双方使用同一 snapshot、
  tail 和临时 SGF lifecycle。
- 第三实例或临时 engine 不属于主/副配对，只恢复自身。
- 任一 captured target 的 `loadsgf` 失败时，所有 target 都不提交 tail。
- 任一侧已经 dispatch 后，另一侧发送失败或任一侧返回 `?`，其余已发出侧仍必须完成消费、
  timeout retirement 或 fallback cleanup；失败不能主动退休 peer consumer，也不能提前删除临时 SGF。
- Mirror 本身不获得新的 queue 或 ownership；两侧仍各自通过自己的 `Leelaz` 协议生命周期
  完成请求。

## Ponder ownership

- `prepare(...)` 在任何可能停止 ponder 的命令之前冻结 disposition。
- Restore module 不主动启动或停止 ponder。
- 全部 captured target 成功后，caller/lifecycle owner 才能按冻结 disposition 执行既有 ponder
  策略。
- 自动/直接 restart 捕获 exact plan 时 module disposition 固定为不自行 resume；restart owner 保留原始 ponder 意图，exact/root 都只在同一个 board fence 成功后恢复，失败或 fence 前不启动分析。
- 任一目标失败时，本次 restore 不恢复 ponder。
- Execute 期间若已进入 PLAY_MODE 或其他使旧 disposition 不再合法的 owner 状态，现有 owner
  仍可拒绝 ponder；冻结 disposition 是恢复意图，不是越过 arbitration 的授权。

## Owner 与 admission（后续合同演进）

以下规则是在初始 deep-module 设计实施后，为覆盖完整 lifecycle handoff 和 review finding
加入的合同演进；它们不是原始九项问答中已经明说的细节。

Restore plan 按入口语境捕获 owner，例如 ordinary、lifecycle、foreground 或 ReadBoard GMA。
Capture、mirror accept 和 execute-time validity 复用 `Leelaz` 的 engine arbitration，不增加
新的长期 owner。

ReadBoard GMA 需要额外防止 reservation ABA：

1. reservation A 下 prepare 的 plan 必须冻结 A 的具体 identity；
2. A 退休、reservation B 建立后，A 的旧 plan 不能借 B 的存在通过 admission；
3. execute-time validity 必须要求 authority 仍持有同一个 identity；
4. mirror 只验证 authority 的同一 reservation 仍有效，再应用原有 mirror conflict gate；mirror
   自身不需要持有 GMA reservation；
5. 失败必须发生在 `loadsgf` dispatch 前，并清理临时 SGF；
6. 该 identity 只保护 restore ownership，不引入 GMA final-play epoch、外部点击授权或同步状态。

## 恢复入口覆盖

完整 depth 不只指 module 内部逻辑，还要求所有会恢复同一局面的入口在自己的第一条有影响
动作之前完成 handoff。

| 入口类别 | Capture 要求 | 主要合同测试 |
|---|---|---|
| 普通 resync、导航、clear/restore | 在 `stop/name/komi/clear_board` 前冻结 history、komi、tail、targets 和 ponder | `ExactSnapshotEngineRestoreContractTest`、`BoardMovelistExportTest` |
| 前台/副引擎切换与配置更新 | 在 lifecycle reservation、停止旧 engine、目标 `name/komi/clear_board` 前冻结目标实例、棋局状态与 exact/root 路线；reservation 到 frozen target restore 完成或失败收敛后才释放 | `EngineManagerLifecycleReservationTest` |
| 自动/直接 restart | 对外发布 reservation 或执行 engine start/stop 前冻结 target 与 exact/root 路线；直接入口自行建立同 identity handoff/reservation 并持有到 board fence；exact/root 共用该 fence 与 caller-owned ponder resume，执行 frozen target 时不能因 `Lizzie.leelaz` 变化静默跳过；root payload 保留既有 live-board 语义但不能重新 prepare | `EngineManagerLifecycleReservationTest`、`LeelazExclusiveRemoteGtpSessionTest`、`LeelazReadBoardGmaTest` |
| OpenCL recovery | 在 lifecycle reservation 和 engine start 前冻结 target 与 exact/root 路线 | `LeelazOpenClRecoveryTest` |
| Benchmark recovery | 在 ponder/start 前冻结 exact restore plan；无 lifecycle handoff 的既有 root 路径保持原产品语义 | `KataGoRuntimeHelperBenchmarkLeaseTest` |
| PK start/restart | 在 engine start、clear 及其他前置命令前冻结 target 与 exact/root 路线；clear 使用 frozen target，不重读 catalog | `EngineManagerLifecycleReservationTest` |
| Foreground lease release/retry | 每次 attempt 都是独立的 `prepare -> execute`；旧 plan 失效后只能重新 prepare | `LeelazExclusiveRemoteGtpSessionTest` |
| ReadBoard/GMA restore | 捕获具体 GMA reservation identity，并在 clear/restore 命令前完成 handoff | `ExactSnapshotEngineRestoreContractTest`、`LeelazReadBoardGmaTest`、`ReadBoardSyncDecisionTest` |

Board-size mismatch 表示创建新棋盘，不是重开同一局面。该分支刻意 clear live board，不把旧尺寸
snapshot exact restore 到新尺寸 engine。

## 原始已批准决策

原始会话逐项确认了以下决策：

1. `ExactSnapshotEngineRestore` 拥有 anchor、`loadsgf`、真实 tail、mirror 和临时 SGF
   cleanup；`Leelaz` 保留 queue、response 和 arbitration。
2. Module 统一拥有 sequencing，caller 不再调用 `finishTailReplay()` 或拼装 lifecycle。
3. `loadsgf` 发送失败、`?`、超时或部分写统一 fail-closed，不发 tail、不 root replay。
4. 入口立即冻结 immutable plan，callback 不重读 mutable globals/history。
5. Mirror 在 capture 时固定；第三实例只恢复自身；任一侧失败时所有目标不发 tail。
6. Plan 冻结 ponder disposition；restore module 不拥有 ponder。
7. Interface 只接收恢复目标和 engine context，不接收 caller 拼装的 tail。
8. Completion 不暴露 dispatch、response binding、timeout retirement 或 cleanup callback。
9. 先通过新 module interface 锁定失败不发 tail、双引擎 mirror、第三实例和 tail-only 四类
   合同测试，再实施迁移。

用户随后明确确认 shared understanding，可以开始实现。

## 后续演进记录

### 完整 capture handoff

初始实现后发现部分入口先发送 `stop/name/komi/clear_board`，再进入 restore module。这会允许
前置命令改变 history、komi、ponder 或 engine identity，违反 immutable plan 的本意。

`aa757492` 将 interface 明确拆为 `prepare(...) -> execute()`，并把 capture 移到这些命令及
可产生等价外部效果的 lifecycle 操作之前。后续未提交 repair 又继续覆盖 engine update/switch、
automatic restart、OpenCL、benchmark、PK 与 foreground lease 等入口。

### Restore admission

完整入口覆盖引出了不同 owner 共享同一 GTP stream 时的 admission 问题。解决方案是在 plan
中捕获窄 owner/admission，而不是新增 generic transaction 或第二套 queue。

### ReadBoard GMA reservation identity

最终 review 发现仅记录“存在某个 GMA reservation”不足以防止 A 退休、B 建立后的 ABA。
后续窄修固定具体 reservation identity，并增加 stale plan 在 dispatch 前失败的合同测试。

这些演进补强原始 immutable handoff，不改变 module 与 `Leelaz` 的 ownership 分工。

### Opaque lifecycle restore handoff

后续完整复审确认，`LifecycleTargetPair`、caller 创建的 raw owner、
`mirrorLifecycleOwnedByOperation` primitive 与 caller 独立推导的 reservation target 仍把同一个
ownership 决策拆在 module seam 两侧，并曾直接引出 mirror self-conflict。窄修将这些事实收敛为
一个 `LifecycleRestoreHandoff`：module 校验/freeze pairing 并内部推导 mirror ownership，
`Leelaz` 只允许 handoff 在其冻结的 lifecycle endpoint 上申请既有 reservation。Handoff 还在
`prepare(...)` 返回 empty 时冻结 root-replay route/admission，避免副作用后 generic re-prepare；
exact 与 root 都使用同一 target/mirror/identity 完成本次 board synchronization。Handoff 本身不
新增 reservation/state machine，也不接管 start/stop、root payload、reservation lifetime 或 ponder
等 caller 产品语义。

### Captured precommands 与 restart fence

最终耦合复审又发现两个会绕过 immutable handoff 的路径：Board prepared restore 用普通 `sendCommand("clear_board")` 在执行时重新解析全局 secondary；automatic/direct restart 的 exact 分支在 `loadsgf` 后提前 return，跳过 board fence，并按 plan 过早恢复 ponder。修复后，Board preclear 由 `PreparedRestore` 发送到 captured target set；lifecycle root 的每条命令通过 active captured admission 显式 enqueue。Restart plan 不自行 resume，exact/root 都恢复 frozen target 并等待同一 fence 后才完成 reservation 与 ponder 策略。

## 被拒绝的方案

- **只统一 `Board` 与 `LeelazEngineCommandSink`**：风险较小，但 ReadBoard/GMA 等 caller 仍会
  保留第二套 exact restore 语义，无法获得 locality。
- **Caller 提供 tail 或 cleanup callback**：把 anchor/tail/lifecycle 知识重新泄漏到 interface，
  module 仍然浅。
- **Restore module 接管 GTP queue/response/arbitration**：复制 `Leelaz` 的底层协议 owner，
  形成第二套状态机和新的交错风险。
- **Callback 动态读取当前 history 或全局 engine**：执行期间导航、同步或 engine replacement
  会把一次 restore 拼成多个时点的状态。
- **Exact 失败后 root replay**：掩盖真实恢复失败，并可能把不可证明的静态局面拆成错误手顺。
- **通用 transaction、rollback 或 retry**：超出当前问题；增加长期 owner 和状态，而不是深化
  现有 module。
- **把 GMA epoch/final-play authorization 放进 restore module**：混合两个领域状态机，降低
  depth 与 locality。
- **为了 sequencing 新建公共异步 interface**：既有 `Leelaz` dispatch 已具备等待和完成机制，
  只需移动 ownership。

## 测试策略

测试通过 module interface 和真实 caller handoff 锁定以下不变量：

- 最近静态锚点恢复后只重放真实 `MOVE/PASS`；
- 默认空 root 不误进入 exact restore；
- prepare 后修改 history、komi、ponder 或全局 engine 不改变已捕获 plan；
- 所有前置命令与 lifecycle 入口都在 capture 之后；
- 主/副入口 mirror 对称，第三实例只恢复自身；
- enqueue/send/write/flush、GTP `?`、timeout、晚到响应和 stream pollution 均 fail-closed；
- tail 被 arbitration 拒绝时显式失败；
- 临时 SGF 生命周期覆盖所有已 dispatch target；
- foreground retry 使用新的 attempt/plan，不修改旧 plan；
- stale ReadBoard GMA reservation identity 在 `loadsgf` 前失败且完成 cleanup。

主要证据文件：

- `src/test/java/featurecat/lizzie/rules/ExactSnapshotEngineRestoreContractTest.java`
- `src/test/java/featurecat/lizzie/analysis/EngineManagerLifecycleReservationTest.java`
- `src/test/java/featurecat/lizzie/analysis/LeelazExclusiveRemoteGtpSessionTest.java`
- `src/test/java/featurecat/lizzie/analysis/LeelazOpenClRecoveryTest.java`
- `src/test/java/featurecat/lizzie/analysis/LeelazReadBoardGmaTest.java`
- `src/test/java/featurecat/lizzie/analysis/ReadBoardSyncDecisionTest.java`
- `src/test/java/featurecat/lizzie/util/KataGoRuntimeHelperBenchmarkLeaseTest.java`

本文只描述测试意图，不把历史或 worker 自报数字当作当前 fresh 验证。最终完成声明仍必须按
review handoff 重新运行定向与全量验证。

## 相关合同

- [`SNAPSHOT_NODE_KIND.md`](../SNAPSHOT_NODE_KIND.md)
- [`Web 试下模式 - 引擎跟随分析`](2026-04-30-web-trial-engine-follow-design.md)
- [`ReadBoard 引擎决策自动落子`](2026-06-24-readboard-gma-engine-decision-design.md)

## 重建时的 review 状态

设计层面没有遗留待确认决策。本文重建时，handoff 中尚未完成的是实现复审、异步测试 NPE
的归因、fresh 定向/全量验证以及 Windows GUI/真实 engine 验收。这些属于 review 与
verification，不通过滚动修改架构合同记录完成状态。
