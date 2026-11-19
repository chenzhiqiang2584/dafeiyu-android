# 二次开发指南

想改这个 App？从这里开始。

---

## 先搞清楚一件事

**这个 App 没有后端。** 它就是一个原生 Android 客户端，直接给 DeepSeek 发 HTTP 请求。

```
MainActivity（界面）
      ↓
    Chat（拼 prompt + 管对话）
      ↓
  DeepSeek（HTTP 流式请求）
```

**"她的智能"全在 DeepSeek 那边。** 这个 App 只做三件事：

1. 把人格 + 记忆 + 历史拼成一段 prompt
2. 发给 DeepSeek
3. 把回复逐字显示成气泡

所以你想改她的**性格**，改 `assets/persona/persona.md` 就行，不用动代码。
想改**她能记住什么**，看 `Memory.java` 和 `Chat.buildSystemPrompt()`。

---

## 源码结构

```
src/com/dafeiyu/app/
```

### 核心链路（改功能必看）

| 文件 | 行数 | 职责 |
|---|---|---|
| `MainActivity.java` | ~2900 | 主界面，所有 UI 逻辑的总入口 |
| `Chat.java` | ~500 | 拼 prompt、管对话历史、压缩上下文 |
| `DeepSeek.java` | ~250 | HTTP 调用，SSE 流式解析 |
| `Memory.java` | ~700 | 记忆的存取、检索、去重 |
| `Session.java` | ~250 | 多会话管理（增删改查、切换） |
| `ChatArchive.java` | ~1300 | 聊天记录存档（写 markdown、读取、导出） |

### 界面组件

| 文件 | 职责 |
|---|---|
| `SettingsDrawer.java` | 设置抽屉（右侧滑出），菜单项都在这里 |
| `MDialog.java` | 统一弹窗（卡片 / 输入框 / 底部 sheet / 胶囊按钮） |
| `MemoryPage.java` | 记忆页面（全屏） |
| `MemGraphPage.java` | 记忆图谱页面（力导向图） |
| `ChatSearch.java` | 搜索聊天记录 |
| `BgPicker.java` | 背景选择器 |
| `PersonaLibrary.java` | 人格库 |
| `StickerPanel.java` | 表情面板 |
| `SetupActivity.java` | 首次启动引导 |

### 自定义 View / Drawable

| 文件 | 职责 |
|---|---|
| `GifView.java` | GIF 播放（用 android.graphics.Movie） |
| `MemGraphView.java` | 力导向图的绘制和动画 |
| `LiquidGlass.java` | 液态玻璃背景（弹窗用的） |
| `BgDrawable.java` | 图片背景（含裁切） |
| `CircleImageDrawable.java` | 圆形头像 |
| `LineIconDrawable.java` | 线性图标（代码画的，没有图片资源） |

### 工具

| 文件 | 职责 |
|---|---|
| `Theme.java` | **所有颜色都在这里**，改配色只改这个文件 |
| `FontKit.java` | 自定义字体（遍历 View 树套 Typeface） |
| `Sanitize.java` | 清理模型输出、切分长句、识别表情标记 |
| `Sanitize.splitLines()` | 把长回复切成多条气泡 |
| `StickerCatalog.java` | 表情名字和内容描述（喂给模型） |
| `StickerVision.java` | 调视觉模型识别新导入的表情 |
| `AvatarCache.java` | 头像缓存 |
| `BalanceChecker.java` | 查 DeepSeek 余额 |
| `Filer.java` | 文件读写小工具 |
| `SimpleDialog.java` | 极简提示框 |

---

## 常见改动指南

### 改她的性格

改 `assets/persona/persona.md`，纯文本，改完直接编译。

**不用改代码。** 这个文件会原样拼进 system prompt。

想在 App 里放多份人格，看 `PersonaLibrary.java`。

### 改配色

`Theme.java` 里有所有颜色常量：

```java
public static final int BUBBLE_MINE_LIGHT = 0xFF8EC9F0;   // 我的气泡（浅色）
public static final int BUBBLE_HER_LIGHT  = 0xFFE8EAED;   // 她的气泡
```

改完深浅两套都要看一眼。

### 改 prompt 里带什么

`Chat.buildSystemPrompt()` 决定每轮发给模型的内容：

```java
sb.append("
【" + persona + "】
");           // 人格
sb.append("
【记忆】
").append(recall);       // 检索到的记忆
sb.append("
【表情】
").append(stickerHint);  // 可用表情
sb.append("
【时间】
").append(nowDate);      // 当前时间
```

**想加东西就 append，想省钱就删。** prompt 越长每轮越贵。

### 加一个设置菜单项

1. `SettingsDrawer.java` 的接口里加方法：
   ```java
   void onMyFeature();
   ```

2. 在同一文件里加菜单项：
   ```java
   itemCard("🎯", "我的功能", "说明文字", () -> host.onMyFeature());
   ```

3. `MainActivity.java` 里实现：
   ```java
   public void onMyFeature() { /* 你的逻辑 */ }
   ```

### 加一个页面

参考 `MemoryPage.java` 的写法：

```java
public static void open(final Activity act) {
    final Dialog dlg = new Dialog(act, Theme_DeviceDefault_NoActionBar);
    FrameLayout root = new FrameLayout(act);
    // ... 构建界面
    dlg.setContentView(root);
    dlg.show();
    FontKit.applyTo(act, root);   // ← 别忘这行，否则字体不对
}
```

**⚠️ 坑**：动态创建的 View 不会自动套自定义字体，**必须在 `show()` 之后调 `FontKit.applyTo()`**。如果之后又重建了列表，要再调一次。

### 换表情包

把图片丢进 `assets/stickers/`，文件名就是表情名（中文可以）。

**格式支持** PNG / JPG / GIF。GIF 会动。

模型怎么知道每张图是什么？看 `StickerCatalog.java` 里的描述表，**新加的图要在这里登记描述**，否则模型不知道怎么用。

### 用视觉模型自动识别表情

`StickerVision.java` 会调 `deepseek-flash` 的视觉能力，给表情生成描述。

新导入表情时如果开了这个功能，会自动调一次（约 0.0009 元/张）。

---

## 数据在哪

```
App 私有目录
├── sessions/                     每个会话一个目录
│   └── <sessionId>/
│       ├── history.json          对话历史（发给模型的原文）
│       ├── summary.md            压缩后的摘要
│       └── 大肥鱼聊天记录/
│           └── 聊天记录.md        给人看的记录
├── memory/
│   ├── user.md                   用户偏好（AI 分析出来的）
│   ├── key.md                    关键记忆
│   ├── memory.md                 长期记忆
│   ├── project.md                正在忙的事
│   └── daily/                    每天的日记
└── ...

外部可见
└── Download/大肥鱼备份/           导出时生成
```

**记忆格式**（`user.md`）：

```markdown
## 他喜欢被称呼为老大
§ 2026-11-19 21:43 | 0.70 | 
他喜欢被称呼为老大
---
```

`##` 标题，`§` 后面是时间 | 权重 | 标签。

---

## 省钱技巧

**每轮对话都按 prompt 长度计费。** prompt 由这些组成：

| 部分 | 大概 token |
|---|---|
| 人格 | 1000–2000 |
| 记忆（6 条） | 300–600 |
| 表情提示 | 100 |
| 时间 | 20 |
| 历史（最近 20 轮） | 500–2000 |
| **合计** | **2000–4700** |

按 DeepSeek 价格（输入 ¥2/M，命中缓存 ¥0.5/M）：

- **不命中缓存**：约 0.004 元/轮
- **命中缓存**（同一段 prompt 前缀）：约 0.001 元/轮

**省钱的办法：**

1. **缩短人格** —— 人格是固定的，每次都发
2. **减少历史轮数** —— `Chat` 里有个常量控制带多少轮
3. **少带记忆** —— `Memory.recall(query, prev, limit)` 的 limit 从 6 改小

---

## 构建

见 [BUILD.md](BUILD.md)。

**关键**：需要 **JDK 17**，AGP 8.x 的硬性要求。

---

## 项目为什么不是标准结构

Android 默认要求 `app/src/main/java/`，但本项目放在根目录的 `src/`。

`app/build.gradle` 里用 `sourceSets` 映射：

```gradle
sourceSets {
    main {
        manifest.srcFile '../AndroidManifest.xml'
        java.srcDirs = ['../src']
        res.srcDirs = ['../res']
        assets.srcDirs = ['../assets']
    }
}
```

**改成标准结构也可以**，把四个目录移进 `app/src/main/`，删掉这段就行。

---

## 已知的坑

| 现象 | 原因 |
|---|---|
| 字体不是自定义的 | 忘了在 `show()` 后调 `FontKit.applyTo()` |
| 列表重建后字体变了 | 重建后要再调一次 `applyTo` |
| 会话条数对不上 | 检查 `ChatArchive.appendTo()` 有没有传对 sessionId |
| 装不上（vivo/iQOO） | 签名冲突，先卸载旧版 |
| `resources.arsc` 相关崩溃 | 需要 `noCompress 'arsc'` |

---

## 许可

MIT。改了随便发，保留原作者署名就行。
