# 作业清单 · Homework List

每天自动更新的初中作业管理工具：网页 + Android App（含桌面小组件），作业写一项划一项。

- 🌐 网页：<https://tyeod-yomuh.github.io/homework-list/>
- 📱 App：Android 原生壳 + WebView 界面，见 [`apk/`](apk/)

---

## 功能

| 功能 | 说明 |
|---|---|
| 科目归类 | 语文 / 数学 / 英语 / 物理 / 化学 / 生物 / 历史 / 政治，同科作业排在一起并带科目标签 |
| 勾选划掉 | 每条带复选框，点一下划掉，顶部实时统计「已完成 X / Y」 |
| 推荐下一个 | 优先推荐同科未完成项，可切「随机」或指定某一科 |
| 随机抽一项 | 一键随机挑一条未完成作业 |
| 增删条目 | 手机上可手动添加、编辑、删除作业 |
| 作业原图 | 当天便签照片直接显示，防止文字整理出错 |
| 每日存档 | 一天一个 JSON，历史只增不改，勾选进度按天独立 |
| 桌面小组件 | 2×2 小组件直接看剩余作业 / 随机抽一项，点击可刷新 |
| 三源取数 | jsDelivr → GitHub Raw → Pages 自动切换，国内网络也能拉到数据 |
| 深色模式 | 纯深色界面，夜间不刺眼 |

---

## 目录结构

```
homework-list/
├── index.html              # 网页应用入口（GitHub Pages），不含数据，运行时读 data/latest.json
├── favicon.png             # 站点图标
├── .nojekyll               # 关闭 Jekyll 处理，保证文件按原样发布
├── LICENSE                 # MIT
├── data/                   # ★ 全部作业数据
│   ├── latest.json         # 当天清单（网页 / App 只读它）
│   ├── _template.json      # 空模板（下划线开头，不参与校验）
│   └── YYYY-MM-DD.json     # 每日存档，同日第二次作业用 -b 后缀
├── photos/                 # 作业原图，YYYY-MM-DD.jpg
├── scripts/
│   └── validate_data.py    # 数据校验脚本
├── docs/
│   ├── data-schema.md      # 数据格式规范
│   └── archive/            # 历史遗留页面归档
└── apk/                    # Android 源码（无需 Gradle，纯命令行构建）
    ├── AndroidManifest.xml
    ├── assets/index.html   # 壳内界面（含离线兜底数据）
    ├── res/                # 图标、布局、小组件资源
    ├── src/                # MainActivity + WidgetProvider
    └── build.sh            # aapt2 + javac + d8 + apksigner 一键打包
```

---

## 数据规范

完整格式见 [`docs/data-schema.md`](docs/data-schema.md)。核心约定：

- 一天一个文件 `data/YYYY-MM-DD.json`，`data/latest.json` 与最新一天内容保持一致
- 必填字段 `date` / `label` / `key` / `photo` / `items`
- `key = hw-list-YYYY-MM-DD`（同日多份加 `-b`），是客户端勾选进度的命名空间，**修正数据时不要改 key**
- 提交前跑校验：

```bash
python3 scripts/validate_data.py --strict
```

---

## 每日更新流程

1. 收到当天作业（文字或照片）
2. 新建 `data/YYYY-MM-DD.json`（参照 `_template.json`），`items` 的 `done` 一律 `false`
3. 照片压缩后存为 `photos/YYYY-MM-DD.jpg`（短边 1200px、JPEG 质量 80）
4. `cp data/YYYY-MM-DD.json data/latest.json`
5. 运行 `python3 scripts/validate_data.py --strict`
6. 提交推送：

```bash
git add -A && git commit -m "update: 作业清单 M/D 周X" && git push origin main
```

7. GitHub Pages 约 1–2 分钟自动更新

---

## 数据读取

网页与 App 按顺序尝试，任一成功即渲染，全部失败才用内置兜底数据：

1. `https://cdn.jsdelivr.net/gh/tyeod-yomuh/homework-list@main/data/latest.json`
2. `https://raw.githubusercontent.com/tyeod-yomuh/homework-list/main/data/latest.json`
3. `https://tyeod-yomuh.github.io/homework-list/data/latest.json`

每个源超时 9 秒后自动切换下一个。

---

## 构建 APK

```bash
cd apk
bash build.sh
```

依赖：Android SDK（build-tools 34、platform android-34）、JDK 17。产物为 `apk/作业清单.apk`，签名使用固定 keystore，可覆盖升级安装。

---

## 许可

[MIT](LICENSE)
