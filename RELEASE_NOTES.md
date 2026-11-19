## 大肥鱼 Android v10.1

装在手机上的 AI 伴侣 —— 不需要电脑、不需要服务器，填个 API Key 就能聊。

### 安装

1. 下载下面的 `dafeiyu-v10.1.apk`
2. 允许「安装未知来源应用」
3. 首次启动填入 [DeepSeek API Key](https://platform.deepseek.com)
4. 开始聊天

> **注意**：如果手机上装过签名不同的旧版本，需要先卸载再安装。

### 这个版本有什么

- **微信风格聊天界面** —— 左右气泡、头像、6 种皮肤
- **表情包** —— 44 张内置表情，支持 GIF 动图，可从相册导入
- **长期记忆** —— AI 分析对话提取偏好，聊到相关话题自动带上
- **多会话** —— 每个聊天独立记录，可切换 / 改名 / 批量删除
- **历史聊天记录** —— 按会话查看，可导出为 txt
- **人格库** —— 存多份人格随时切换
- **自定义** —— 深浅主题、聊天背景、历史记录背景、自定义字体
- **数据导出** —— 备份到 `Download/大肥鱼备份/`，文件管理器可见

### 技术信息

| | |
|---|---|
| 包名 | `com.dafeiyu.app` |
| 版本 | 10.1 (142) |
| 最低系统 | Android 7.0 (API 24) |
| 目标系统 | Android 14 (API 34) |
| 大小 | 6.7 MB |
| 签名 | v2 + v3 |
| 依赖 | 无（纯原生 Android，零第三方库） |

### 数据存储

所有数据都在手机本地：

| 内容 | 位置 |
|---|---|
| API Key | SharedPreferences（不上传） |
| 对话历史 | `files/sessions/<会话>/history.json` |
| 聊天记录 | `files/sessions/<会话>/大肥鱼聊天记录/聊天记录.md` |
| 记忆 | `files/memory/*.md` |
| 备份 | `Download/大肥鱼备份/`（可导出） |

**不上传任何服务器。**

### 已知限制

- 需要自备 DeepSeek API Key（按 token 计费，日常聊天约 0.003 元/轮）
- 不同会话的记忆是共享的
- 卸载 App 会清空本地数据（先「导出全部数据」备份）

### 致谢

感谢原作者 **[SKSD-RGB](https://github.com/SKSD-RGB)** —— 他写的
[eat-rice-big-fat-fish-mobile-phone](https://github.com/SKSD-RGB/eat-rice-big-fat-fish-mobile-phone)
是这一切的基础。没有他的框架，就没有这个修改版。

如果这个 App 对你有用，请先去原项目点一个 ⭐。

### 校验

```
SHA-256: 08f368a34f83287999fc908da5e54e7ee0df8f439dcaf2f28e39dadf7cdcd9d9
```

签名证书 SHA-256：
```
29e0132a42c1c5642bc002f46a6af614c4bbea430baa44709c478320b9a2e0ec
```

### 想自己编译 / 二次开发

看 [BUILD.md](BUILD.md)，需要 JDK 17 + Android SDK 34。
