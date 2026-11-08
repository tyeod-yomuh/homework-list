#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""作业清单数据校验脚本

用法：
    python3 scripts/validate_data.py            # 校验全部数据
    python3 scripts/validate_data.py --strict   # 额外检查照片文件是否存在

校验内容：
    1. data/*.json 均为合法 JSON
    2. 必有字段：date / label / key / photo / items
    3. 文件名、date、key 三者日期一致（latest.json 除外）
    4. key 规则：hw-list-YYYY-MM-DD，同一天多份作业用 -b / -c 后缀
    5. items 非空，每条含 text / subject / done，subject 在允许科目内，done 为布尔
    6. label 形如「11月8日 周日」
    7. 最近一份数据（latest.json）应与最新日期存档内容一致
"""
import json
import re
import sys
import os
from datetime import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(ROOT, "data")
PHOTO_DIR = os.path.join(ROOT, "photos")

SUBJECTS = {"语文", "数学", "英语", "物理", "化学", "生物", "历史", "政治", "其他"}
DATE_RE = re.compile(r"^20\d{2}-\d{2}-\d{2}$")
LABEL_RE = re.compile(r"^\d{1,2}月\d{1,2}日 周[一二三四五六日天](?: · .+)?$")
KEY_RE = re.compile(r"^hw-list-(20\d{2}-\d{2}-\d{2})(?:-[bc])?$")

errors = []
warnings = []
checked = 0


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def check_file(path, strict):
    global checked
    name = os.path.basename(path)
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        err(f"{name}: JSON 解析失败 — {e}")
        return
    checked += 1

    for field in ("date", "label", "key", "photo", "items"):
        if field not in data:
            err(f"{name}: 缺少必填字段 {field}")
    if "date" not in data or "items" not in data:
        return

    date = data["date"]
    is_latest = name == "latest.json"

    if not DATE_RE.match(str(date)):
        err(f"{name}: date 格式应为 YYYY-MM-DD，实际 {date!r}")

    if not is_latest:
        stem = name[:-5]
        if DATE_RE.match(stem) and stem != date:
            err(f"{name}: 文件名与 date 不一致（date={date}）")

    key = str(data.get("key", ""))
    m = KEY_RE.match(key)
    if not m:
        err(f"{name}: key 不符合规范 hw-list-YYYY-MM-DD[-b|-c]，实际 {key!r}")
    elif m.group(1) != date:
        err(f"{name}: key 日期 {m.group(1)} 与 date {date} 不一致")

    label = str(data.get("label", ""))
    if not LABEL_RE.match(label):
        warn(f"{name}: label 建议写成「M月D日 周X」，实际 {label!r}")

    photo = str(data.get("photo", ""))
    if photo and photo != f"photos/{date}.jpg":
        warn(f"{name}: photo 建议为 photos/{date}.jpg，实际 {photo!r}")
    if photo and strict and not os.path.exists(os.path.join(ROOT, photo)):
        warn(f"{name}: 照片文件不存在 {photo}")

    items = data.get("items")
    if not isinstance(items, list) or not items:
        err(f"{name}: items 必须是非空数组")
        return
    for i, item in enumerate(items):
        if not isinstance(item, dict):
            err(f"{name}: items[{i}] 不是对象")
            continue
        text = item.get("text")
        if not isinstance(text, str) or not text.strip():
            err(f"{name}: items[{i}] 缺少有效 text")
        subj = item.get("subject")
        if subj not in SUBJECTS:
            err(f"{name}: items[{i}] subject 不合法 {subj!r}（允许：{'/'.join(sorted(SUBJECTS))}）")
        if not isinstance(item.get("done"), bool):
            err(f"{name}: items[{i}] done 必须是 true/false")


def main():
    strict = "--strict" in sys.argv
    files = sorted(
        f for f in os.listdir(DATA_DIR)
        if f.endswith(".json") and not f.startswith("_")
    )
    if not files:
        print("✗ data/ 目录下没有数据文件")
        return 1

    for f in files:
        check_file(os.path.join(DATA_DIR, f), strict)

    # latest.json 与最新日期存档一致性
    archives = [f for f in files if f != "latest.json"]
    if archives and os.path.exists(os.path.join(DATA_DIR, "latest.json")):
        latest = json.load(open(os.path.join(DATA_DIR, "latest.json"), encoding="utf-8"))
        newest_name = max(
            archives,
            key=lambda x: json.load(open(os.path.join(DATA_DIR, x), encoding="utf-8")).get("date", ""),
        )
        newest = json.load(open(os.path.join(DATA_DIR, newest_name), encoding="utf-8"))
        if latest != newest:
            err(f"latest.json 与最新的 {newest_name} 内容不一致（应保持一致）")

    print(f"检查文件：{checked} 个")
    for w in warnings:
        print(f"⚠ {w}")
    for e in errors:
        print(f"✗ {e}")
    if errors:
        print(f"\n校验失败：{len(errors)} 个错误")
        return 1
    print(f"\n✓ 全部通过（{checked} 个文件{('，' + str(len(warnings)) + ' 条提示') if warnings else ''}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
