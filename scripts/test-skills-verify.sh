#!/bin/bash
BASE_URL="http://localhost:8080"

TOKEN=$(curl -s -X POST "$BASE_URL/admins/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['access_token'])")

AUTH="Authorization: Bearer $TOKEN"

# 再下载一次验证计数递增
curl -s "$BASE_URL/skills/product-6993bfdcbdb2a4b37a8dc0e0/download" \
  -H "$AUTH" > /dev/null

echo "=== 下载计数验证 ==="
curl -s "$BASE_URL/products/product-6993bfdcbdb2a4b37a8dc0e0" \
  -H "$AUTH" | python3 -c "
import json,sys
d = json.load(sys.stdin)['data']
sc = d.get('skillConfig') or {}
print(f'DownloadCount: {sc.get(\"downloadCount\", 0)} (预期: 2)')
"

echo ""
echo "=== 搜索测试 (name=Debug) ==="
curl -s "$BASE_URL/products?type=AGENT_SKILL&name=Debug" \
  -H "$AUTH" | python3 -c "
import json,sys
data = json.load(sys.stdin)['data']
print(f'搜索结果数: {data[\"totalElements\"]}')
for p in data['content']:
    print(f'  - {p[\"name\"]}')
"

echo ""
echo "=== 搜索测试 (name=Playwright) ==="
curl -s "$BASE_URL/products?type=AGENT_SKILL&name=Playwright" \
  -H "$AUTH" | python3 -c "
import json,sys
data = json.load(sys.stdin)['data']
print(f'搜索结果数: {data[\"totalElements\"]}')
for p in data['content']:
    print(f'  - {p[\"name\"]}')
"

echo ""
echo "=== 404 测试 (不存在的技能) ==="
RESP=$(curl -s -w "\n%{http_code}" "$BASE_URL/skills/product-nonexistent/download" \
  -H "$AUTH")
HTTP_CODE=$(echo "$RESP" | tail -1)
echo "HTTP Status: $HTTP_CODE (预期: 404)"

echo ""
echo "=== 无认证下载测试 ==="
RESP2=$(curl -s -w "\n%{http_code}" "$BASE_URL/skills/product-6993bfdcbdb2a4b37a8dc0e0/download")
HTTP_CODE2=$(echo "$RESP2" | tail -1)
echo "HTTP Status: $HTTP_CODE2 (预期: 200，当前因未重启服务可能为 403)"
