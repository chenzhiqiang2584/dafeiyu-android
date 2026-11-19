# 致谢

## 原作者

### SKSD-RGB

- GitHub：**[github.com/SKSD-RGB](https://github.com/SKSD-RGB)**
- 原项目：**[eat-rice-big-fat-fish-mobile-phone](https://github.com/SKSD-RGB/eat-rice-big-fat-fish-mobile-phone)**

**他做了什么**

从零写出了一个**不依赖电脑、不需要网桥、手机上直接跑**的 AI 聊天 App：

- 直连 DeepSeek API，流式回复
- 五轨分层记忆系统（记忆 / 用户 / 关键 / 项目 / 日记），带权重和遗忘曲线
- 人格提示词体系
- 上下文压缩
- 余额查询、Token 统计
- 一套完整可用的 Android 工程

「手机上自己养一个 AI 伴侣」这件事，是他先做出来的。

**没有他的代码，就没有这个修改版。**

如果你从这个项目里得到了什么，先去他的仓库点一个 ⭐。

---

## 本修改版

在原项目基础上做了界面重构、多会话、GIF 表情包、背景自定义等改动。

完整改动列表见 [CHANGELOG.md](CHANGELOG.md)。

---

## 第三方

| 项目 | 用途 | 许可 |
|---|---|---|
| [DeepSeek API](https://platform.deepseek.com) | 模型推理 | 商业 API |
| 行楷字体（`assets/fonts/xinwei.ttf`） | 界面字体 | 见字体文件附带说明 |

---

## 表情包

`assets/stickers/` 里 44 张表情图为**本修改版自有**，与原项目代码分开授权。

---

## 如果你是二开者

基于这个项目继续开发时，请保留上述署名。

**MIT 许可证要求保留版权声明** —— 不只是法律要求，也是对原创者的基本尊重。
