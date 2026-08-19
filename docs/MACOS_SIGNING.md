# macOS 代码签名与公证

## 需要的 GitHub Secrets

在仓库的 `Settings → Secrets and variables → Actions` 里添加以下 secrets。四个必需 secret 全部未配置时，脚本会明确打印“跳过签名”，方便本地构建；只配置了一部分时会直接失败，避免误把未签名 DMG 当成正式发布资产。

| Secret | 作用 | 获取方式 |
|---|---|---|
| `APPLE_CERT_P12` | Developer ID Application 证书 (.p12) 的 base64 字符串 | 在 Keychain Access 导出 `.p12`，然后 `base64 -i cert.p12 -o cert.b64` |
| `APPLE_CERT_PASSWORD` | 上面 `.p12` 的密码（可为空） | 导出时设置的密码 |
| `APPLE_ID` | Apple ID 邮箱（用于 notarytool） | 你的开发者账号邮箱 |
| `APPLE_APP_PASSWORD` | app-specific password | https://appleid.apple.com/account/manage → App 专用密码 |
| `APPLE_TEAM_ID` | 10 字符的 Team ID | https://developer.apple.com/account → Membership Details |
| `APPLE_SIGN_IDENTITY` | 可选覆盖，例如 `Developer ID Application: Your Name (TEAMID)` | 有多个证书时可以手工指定 |

## 本地准备

1. 登录 https://developer.apple.com/account 确认你的 Team ID。
2. 到 `Certificates, Identifiers & Profiles → Certificates` 创建一个 `Developer ID Application` 证书（如果还没有）：
   - 在 Keychain Access → 证书助理 → 从证书颁发机构请求证书，保存到磁盘
   - 上传 CSR 到 Apple
   - 下载并双击 `.cer` 把它装到 Keychain Access
3. 在 Keychain Access 找到这个证书 + 对应私钥，右键 `导出 2 项...`，选 `.p12` 保存
4. 命令行把 p12 编码：
   ```bash
   base64 -i DeveloperID.p12 -o DeveloperID.p12.b64
   pbcopy < DeveloperID.p12.b64
   ```
   把结果粘贴到 `APPLE_CERT_P12` secret。
5. 到 https://appleid.apple.com/account/manage 创建 App 专用密码，放到 `APPLE_APP_PASSWORD`。

## Workflow 行为

`build-macos-arm64-release.yml` 和 `build-macos-amd64-release.yml` 在 jpackage 生成 DMG 之后，如果 `APPLE_CERT_P12`、`APPLE_ID`、`APPLE_APP_PASSWORD`、`APPLE_TEAM_ID` 四个必需 secret 都已配置，会调用 `scripts/sign_macos_release.sh`：

- 在导入证书前安装清理 trap；证书写入随机 `0700` 临时目录中的 `certificate.p12`（文件权限 `0600`），并通过 `security import -f pkcs12` 显式按 PKCS#12 格式导入；同时创建仅本次运行使用的随机临时 keychain
- 把 DMG 里的 `.app` 解出，按“内层原生库/辅助程序 → framework → 主程序 → 外层 `.app`”逐层执行 `codesign --options runtime --timestamp`；`--deep` 只用于签名后的递归验证，不用于签名
- 重新打包成 DMG，再次签名 DMG
- 用 `xcrun notarytool submit --wait` 提交公证
- 用 `xcrun stapler staple` 附着公证票据
- 用 `spctl --assess` 复查通过

四个必需 secret 全部未配置时，脚本打印一行后 `exit 0`，本地构建不受影响。只要其中任意一个已配置，其余必需 secret 就必须全部存在；部分配置会 fail closed。`APPLE_CERT_PASSWORD` 可以为空，`APPLE_SIGN_IDENTITY` 仍是可选覆盖。

## 验证

下载签名后的 DMG，在另一台 Mac 上直接双击。应该能直接打开，不再出现 "无法验证开发者" 的拦截。

命令行验证：

```bash
spctl --assess --type open --context context:primary-signature -vvv path/to/LizzieYzy-Next.dmg
# 应该显示:
#   path: accepted
#   source=Notarized Developer ID
```

## 失败兜底

如果证书导入、逐层签名、公证、票据附着、布局校验或最终 `spctl` Gatekeeper 评估失败，workflow 会在替换原始 DMG 和上传 Release asset 之前终止，因此不会把本次未通过完整校验的 DMG 上传到 GitHub Release。清理 trap 会在任何早期失败时删除 `certificate.p12` 及其随机临时目录、随机 keychain、挂载点和工作目录；即使失败发生在临时路径或原 keychain 列表尚为空时也会安全完成清理。只有全部校验成功后，工作目录中的已签名 DMG 才会替换原文件并进入 provenance 与 Release 上传步骤。

常见问题：
- `notarytool` 超时 → 重跑 workflow 即可
- `errSecInternalComponent` → p12 密码错误
- `No Developer ID Application identity` → 证书没能导入 keychain，检查 `APPLE_CERT_P12` 是否是 base64 后的完整字符串
