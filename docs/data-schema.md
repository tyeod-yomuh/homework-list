# 作业数据格式规范

本仓库所有作业数据都存放在 `data/` 目录，一天一个 JSON 文件；网站和 App 读取 `data/latest.json` 来展示当天清单。

---

## 1. 文件命名

| 用途 | 文件名 | 说明 |
|---|---|---|
| 每日存档 | `YYYY-MM-DD.json` | 如 `2026-11-08.json`，日期即作业所属日 |
| 当天清单 | `latest.json` | 内容与最新一天的存档**完全一致**（每晚更新时覆盖） |
| 空模板 | `_template.json` | 手工新建数据时复制它，用 `_` 开头不会被当成真实数据 |
| 作业照片 | `photos/YYYY-MM-DD.jpg` | 一张照片，短边缩到 1200px、质量 80，约 150KB |

> 同一晚有第二次作业时，文件名追加后缀：`2026-10-13.json`（第一份）、`2026-10-13-b.json`（第二份）。

---

## 2. 字段结构

```json
{
  "date": "2026-11-08",
  "label": "11月8日 周日",
  "key": "hw-list-2026-11-08",
  "photo": "photos/2026-11-08.jpg",
  "items": [
    { "text": "试卷一张", "subject": "化学", "done": false },
    { "text": "抄一背 Page 44", "subject": "英语", "done": false }
  ]
}
```

### 顶层字段

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `date` | string | 是 | `YYYY-MM-DD`，必须与文件名一致 |
| `label` | string | 是 | 形如 `11月8日 周日`，可加备注：`10月19日 周一 · 周末作业` |
| `key` | string | 是 | `hw-list-YYYY-MM-DD`；同日多份追加 `-b`、`-c` |
| `photo` | string | 是 | 通常为 `photos/<date>.jpg`；没有照片时为空字符串 `""` |
| `items` | array | 是 | 非空数组，每条作业一个对象 |
| `note` | string | 否 | 补充说明，如 `政治：无作业` |

### items 条目

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `text` | string | 是 | 作业内容，简短直白（建议 ≤ 20 字） |
| `subject` | string | 是 | 见下方科目表 |
| `done` | boolean | 是 | 新建数据时一律 `false`；勾选状态由客户端本地保存，**不回写仓库** |

### 允许的科目

`语文`、`数学`、`英语`、`物理`、`化学`、`生物`、`历史`、`政治`、`其他`

---

## 3. `key` 的作用

`key` 是客户端本地存档（勾选进度、跳过项）的命名空间：

- 每天一个 key，勾选进度按天独立，不会串。
- 数据修正时**保持 key 不变**，用户的勾选状态就不会因更新而丢失。
- 换成新的作业日时 key 必须随日期变化，否则会串到昨天的进度。

---

## 4. 校验

提交前运行：

```bash
python3 scripts/validate_data.py          # 基础校验
python3 scripts/validate_data.py --strict # 额外检查照片文件是否存在
```

校验规则：
- JSON 可解析、必填字段齐全
- 文件名 / `date` / `key` 三者日期一致
- `items` 非空、`subject` 合法、`done` 为布尔
- `latest.json` 与最新日期存档内容一致

---

## 5. 数据读取优先级（客户端）

App 与网页按以下顺序尝试，任一成功即渲染，全部失败才用内置兜底数据：

1. `https://cdn.jsdelivr.net/gh/tyeod-yomuh/homework-list@main/data/latest.json`
2. `https://raw.githubusercontent.com/tyeod-yomuh/homework-list/main/data/latest.json`
3. `https://tyeod-yomuh.github.io/homework-list/data/latest.json`

每个源超时 9 秒，自动切换下一个。
