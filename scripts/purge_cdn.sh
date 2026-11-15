#!/usr/bin/env bash
# 推送数据后立即刷新 jsDelivr CDN 缓存，避免 App 拉到旧数据
# 用法：bash scripts/purge_cdn.sh
set -e
URL="https://purge.jsdelivr.net/gh/tyeod-yomuh/homework-list@main/data/latest.json"
echo "刷新 jsDelivr 缓存…"
curl -s -m 20 "$URL" | grep -o '"status": "[^"]*"' || true
echo "验证最新数据："
curl -s -m 10 "https://cdn.jsdelivr.net/gh/tyeod-yomuh/homework-list@main/data/latest.json?t=$(date +%s)" | head -c 200
echo ""
