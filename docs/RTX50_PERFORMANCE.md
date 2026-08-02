# RTX 50 / TensorRT 分析性能验收

这份文档用于排查“相同 KataGo、模型和配置下，LizzieYzy Next 比旧版吞吐低或功耗低”的问题。不能只比较界面上的瞬时 visits；必须固定模型、配置、TensorRT 缓存、局面和运行时，并同时记录 KataGo 原始指标与 GPU 状态。

## 本轮资源仲裁

- 未启用“预加载分析引擎”时，应用启动后不再常驻第二个隐藏 KataGo。
- 自动快速胜率曲线按需创建独立分析进程，完成后立即退出。
- 自动快速曲线运行期间不会同时恢复主棋盘分析；曲线完成后立即恢复。
- 用户主动开始主棋盘分析时，空闲的第二分析进程会被释放，自动后台分析会被抢占；用户明确启动的闪电分析、整盘精析等任务不会被静默终止。
- 诊断默认关闭，不增加正常分析 I/O。启用后会记录进程用途、PID、脱敏命令、配置哈希、动态 `kata-set-param` 和主引擎 playouts/s。

## 固定参数基准

在 Windows PowerShell 中运行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows_rtx50_analysis_benchmark.ps1 `
  -KataGoExe "D:\KataGo\katago.exe" `
  -Model "D:\KataGo\model.bin.gz" `
  -Config "D:\KataGo\gtp.cfg" `
  -OutputDirectory "$env:USERPROFILE\Desktop\lizzie-rtx50-benchmark" `
  -Runs 3 `
  -SecondsPerMove 30 `
  -VisitsPerPosition 5000
```

脚本会保存：

- KataGo 官方 `benchmark` 的完整 stdout/stderr
- GPU 型号、驱动、P-State、功耗、显存、GPU/显存控制器利用率
- 每秒计算进程 PID、名称和显存
- 模型与配置文件 SHA-256，防止比较时实际使用了不同文件

`SecondsPerMove` 对应 KataGo 官方 benchmark 的 `-time` 参数，表示典型单手思考时间，不是脚本总运行时间；完整基准会测试多个局面和线程组合。

## Next 运行诊断

复制一份 Windows 启动器旁的 `app\LizzieYzy Next*.cfg`，只在测试副本的 `[JavaOptions]` 末尾加入：

```text
java-options=-Dlizzie.analysis.diagnostics=true
java-options=-Dlizzie.analysis.diagnostics.path=C:\Users\Public\Documents\LizzieYzyNext\analysis-resource-diagnostics.jsonl
```

完成测试后删除这两行。诊断文件不会记录密码、token、cookie 或完整模型路径；远程连接参数会脱敏。不要把诊断开关作为日常设置长期保留。

## 对比矩阵

所有行必须使用同一局面、模型、配置、KataGo 二进制、已完成预热的 TensorRT 缓存和相同运行时长。

| 场景 | 自动快速曲线 | 其他 KataGo 进程 | 必须记录 |
| --- | --- | --- | --- |
| 官方 KataGo benchmark | 不适用 | 0 | nnEvals/s、batch、功耗、显存 |
| Next 主棋盘单引擎 | 关闭 | 0 | visits/s、功耗、显存、最终参数 |
| Next 自动快速曲线开启 | 开启 | 曲线完成后应为 0 | 曲线期间与完成后进程数 |
| 旧版同局面对照 | 与 Next 一致 | 明确记录 | visits/s、功耗、显存 |

每个场景至少运行三次，丢弃第一次 TensorRT 建图/缓存生成的冷启动数据。RTX 5080 真机数据未完成前，不能仅凭 CI 或其他显卡结果宣称性能问题已经解决。

## 结果判读

- 官方 benchmark 已慢：优先检查驱动、TensorRT/CUDA 版本、模型、配置和缓存，不属于 Swing UI 开销。
- 官方 benchmark 正常、Next 单引擎慢：检查诊断中的最终命令、配置哈希、动态参数和进程用途。
- Next 单引擎正常、开启自动快速曲线后持续慢：检查是否仍有第二 KataGo PID；这是资源仲裁回归。
- 首次运行慢、第二次正常：通常是 TensorRT 引擎构建或 CUDA 缓存，不应与热缓存数据混合比较。
