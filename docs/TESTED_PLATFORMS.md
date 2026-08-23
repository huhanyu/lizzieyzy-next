# 已验证平台

这份文档记录当前主发布资产的已知验证状态。

目的不是假装“所有平台都完全测过”，而是明确告诉用户：

- 哪些已经实机验证过
- 哪些已经完成构建与发布验证，但还缺少真实机器反馈
- 哪些仍然主要依赖社区回报

状态说明：

- `Maintainer tested`：维护者在真实机器上完成了安装或启动验证
- `Build verified`：资产命名、内容结构、工作流和公开 release 已验证，但还缺少真实机器反馈
- `Needs report`：目前仍缺少足够反馈

当前主推荐列表覆盖 15 个用户向资产；历史兼容包不再放进主推荐区。

## 当前状态

| 包 | 平台 | 当前状态 | 已确认内容 | 备注 |
| --- | --- | --- | --- | --- |
| `windows64.opencl.portable.zip` | Windows x64 | `Build verified` | 默认推荐免安装包已纳入正式发布矩阵 | 当前面向大多数 Windows 用户的首推下载 |
| `windows64.opencl.installer.exe` | Windows x64 | `Build verified` | OpenCL 安装器已纳入正式发布矩阵 | 面向更偏好安装流程的 Windows 用户 |
| `windows64.with-katago.portable.zip` | Windows x64 | `Build verified` | CPU 兜底便携包工作流和公开 release 资产已验证 | OpenCL 不稳定时的兼容兜底 |
| `windows64.with-katago.installer.exe` | Windows x64 | `Build verified` | CPU 兜底安装器工作流和公开 release 资产已验证 | 面向想安装的 CPU 兜底用户 |
| `windows64.nvidia.portable.zip` | Windows x64 + NVIDIA | `Build verified` | NVIDIA CUDA 便携包已纳入正式发布矩阵 | 面向不想安装的 NVIDIA 用户 |
| `windows64.nvidia.installer.exe` | Windows x64 + NVIDIA | `Build verified` | NVIDIA CUDA 整合安装器已纳入正式发布矩阵 | 需要真实 NVIDIA Windows 反馈 |
| `windows64.nvidia50.cuda.portable.zip` | Windows x64 + RTX 50 | `Build verified` | CUDA 12.8/cuDNN 9 便携包已纳入发布矩阵 | RTX 5070/5080/5090 用户优先反馈 |
| `windows64.nvidia50.cuda.installer.exe` | Windows x64 + RTX 50 | `Build verified` | CUDA 12.8/cuDNN 9 安装器已纳入发布矩阵 | RTX 5070/5080/5090 用户优先反馈 |
| `windows64.without.engine.portable.zip` | Windows x64 | `Build verified` | 无引擎便携包已纳入正式发布矩阵 | 面向进阶用户 |
| `windows64.without.engine.installer.exe` | Windows x64 | `Build verified` | 无引擎安装器已纳入正式发布矩阵 | 面向想安装但自己配引擎的用户 |
| `mac-apple-silicon.with-katago.dmg` | macOS Apple Silicon | `Maintainer tested` | 安装、启动、界面打开、野狐昵称抓谱入口可见 | 当前最完整的实机验证链路 |
| `mac-intel.with-katago.dmg` | macOS Intel | `Build verified` | 已纳入独立发布流程 | 需要真实 Intel Mac 反馈 |
| `linux64.with-katago.zip` | Linux x64 | `Build verified` | 整合包继续提供 | 需要真实 Linux 桌面反馈 |
| `linux64.opencl.zip` | Linux x64 + OpenCL | `Build verified` | OpenCL 包继续提供 | 需要真实 Linux OpenCL 反馈 |
| `linux64.nvidia.zip` | Linux x64 + NVIDIA | `Build verified` | NVIDIA CUDA 包继续提供 | 需要真实 Linux NVIDIA 反馈 |

说明：TensorRT 加速的普通用户路径是软件内按需安装；GitHub Release 同时保留高级可选离线分卷，并校验分卷数量、清单、大小和 SHA-256。真实吞吐、功耗和驱动兼容仍需在对应 RTX 20/30/40/50 Windows 硬件上验证，RTX 50 仍优先验证 CUDA 主包。

### RTX 5090 NVRTC A/B 验证（2026-08-22）

- 环境：Windows、RTX 5090、驱动 `591.86`、Compute Capability `12.0`
- 原始 `next-2026-08-19.4` RTX 50 CUDA 便携包缺少 NVRTC：`katago version` 等待 30 秒无输出，benchmark 等待 100 秒无输出，未创建 GPU context；停止后无残留进程
- 从 NVIDIA CUDA 12.8.0 redistrib manifest 下载并以 SHA-256 `e43603b09f8a52d681ceb814c00b655af19da53692ab91671dabbf8071c8f93d` 验证 `12.8.61` NVRTC compiler 与 builtins，复制到同一包后：`katago version` 约 0.6 秒返回 CUDA `12.8.61`，`nvrtcVersion` 为 `12.8`，benchmark 约 6.2 秒完成，识别 RTX 5090 / CC `12.0`、成功加载 b10 模型、约 `212.77 visits/s`，退出无残留进程
- 这组结果证明缺失 NVRTC 是该包在 RTX 5090 上无法启动的直接原因；新的完整发布资产仍须重新执行同一矩阵，未完成前保持 `Build verified`，不提前标记为完整实机通过

## 我们重点关心什么

如果你帮忙验证，最有价值的是这些信息：

- 包能不能正常下载、安装、解压或挂载
- 首次启动是否被系统安全策略拦截
- 程序能不能进入主界面
- `with-katago` 包里引擎是否正常加载
- “野狐棋谱（输入野狐昵称获取）”是否能抓到公开棋谱

## 如何补充反馈

1. 去 GitHub Issues 里选择 `Installation Report`
2. 写清楚安装包文件名、系统版本、结果和额外步骤
3. 如果有截图或报错，一起附上

相关入口：

- [获取帮助](../SUPPORT.md)
- [发布包说明](PACKAGES.md)
- [安装指南](INSTALL.md)
- [常见问题与排错](TROUBLESHOOTING.md)
