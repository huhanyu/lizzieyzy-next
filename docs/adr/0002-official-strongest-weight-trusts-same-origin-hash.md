---
status: accepted
---

# 官网最强权重动态获取，并信任同源哈希

官方权重列表新增一行“官网最强”，内容在运行时从 `https://katagotraining.org/api/networks/get_strongest/` 获取，校验用的大小和 SHA256 也取自同一个 API。其余权重的哈希都固定在 `katago-assets.json` 里，这一行是例外。原因是需求本身就要求自动跟随官网标注，而固定哈希就意味着每换一个网络都要发版；同源哈希仍然能发现下载损坏和截断，只是无法防御 katagotraining 本身被篡改。

## Considered Options

- 不校验抓取到的权重（和现在的 HTML 表格行一样）：拒绝，因为 API 已经提供了哈希，放弃它是无谓的退化。
- 只有官网最强恰好是 catalog 里的文件时才允许下载：拒绝，这样实际上无法跟随官网。
- 把打包内置权重或默认通用权重也改成跟随官网：拒绝，同一个版本会打出不同的包，“默认”也会和用户手里的内置文件对不上。

## Consequences

- 打包内置权重、默认通用权重、推荐卡片和默认选中行仍以 `katago-assets.json` 的 `defaultModelId` 为准；官网最强的那一行不占用这些身份。
- API 地址固定为 katagotraining.org（系统属性 `lizzie.katago.strongest.url` 只用于测试替换，和现有的 `lizzie.katago.networks.url` 一样），下载地址必须是 `https://media.katagotraining.org/uploaded/networks/models/kata1/<name>.bin.gz`；大小或 SHA256 任一不符都拒绝安装。
- 官网最强不限规模和架构。最低 KataGo 版本按名字推断：`kata1-tf*` 要求 1.17.0，经典的 `kata1-b<N>c<N>-` 没有要求，其他名字视为获取失败。
- 获取失败或字段不合法时静默不显示官网最强这一行，catalog 的固定行照常显示，不在本地缓存上一次的结果。
- 已下载的文件不沿用“官网最强”这个名字，因为官网标注会变；未收录进 catalog 的 Transformer 权重只按文件名显示为 `B<规模> · <训练标识>`（例如 `B11 · s11003M-d5973M-7gres`），离线时引擎配置名也保持稳定。
