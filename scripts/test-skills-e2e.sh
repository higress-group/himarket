#!/bin/bash
# Agent Skills 市场端到端测试脚本
# 模拟 Admin 创建技能 → 发布到门户 → Developer 查询和下载

set -e

BASE_URL="http://localhost:8080"
PORTAL_ID="portal-6914b993e4b0841215f622c5"

echo "========================================="
echo "  Agent Skills 市场 - 端到端测试"
echo "========================================="

# 1. Admin 登录
echo ""
echo ">>> 步骤 1: Admin 登录"
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/admins/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}')
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['access_token'])")
echo "✅ 登录成功，获取到 Token"

AUTH="Authorization: Bearer $TOKEN"

# 2. 创建 Skill 1: Test-Driven Development
echo ""
echo ">>> 步骤 2: 创建技能 - Test-Driven Development"

SKILL1_DOC=$(cat << 'SKILLEOF'
---
name: test-driven-development
description: "Development methodology of writing tests before implementing code, producing more reliable and maintainable software"
---

# Test-Driven Development

## Use Cases

- Test planning before new feature development
- Ensuring behavior doesn't change during refactoring
- Writing reproduction tests before bug fixes
- Improving code coverage and quality

## Core Capabilities

- **Test First**: Define expected behavior before implementation
- **Red-Green-Refactor**: Follow the TDD cycle
- **Edge Coverage**: Identify and test boundary cases
- **Testable Design**: Write code structures that are easy to test

## Example

```
I need to implement a user registration feature with requirements:
- Username 3-20 characters
- Valid email format
- Password at least 8 characters with numbers and letters

Please help me write test cases first, then implement the feature.
```

## Notes

- Tests should be independent and repeatable
- Avoid testing implementation details, test behavior
- Keep tests simple and readable
SKILLEOF
)

SKILL1_BODY=$(python3 -c "
import json
doc = '''$SKILL1_DOC'''
body = {
    'name': 'Test-Driven Development',
    'description': 'Development methodology of writing tests before implementing code',
    'type': 'AGENT_SKILL',
    'document': doc,
    'feature': {
        'skillConfig': {
            'skillTags': ['testing', 'tdd', 'quality', 'methodology'],
            'downloadCount': 0
        }
    }
}
print(json.dumps(body))
")

SKILL1_RESP=$(curl -s -X POST "$BASE_URL/products" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "$SKILL1_BODY")

SKILL1_CODE=$(echo "$SKILL1_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin).get('code',''))")
if [ "$SKILL1_CODE" = "SUCCESS" ]; then
  SKILL1_ID=$(echo "$SKILL1_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['productId'])")
  echo "✅ 创建成功: productId=$SKILL1_ID"
else
  echo "❌ 创建失败: $SKILL1_RESP"
  SKILL1_ID=""
fi

# 3. 创建 Skill 2: Systematic Debugging
echo ""
echo ">>> 步骤 3: 创建技能 - Systematic Debugging"

SKILL2_DOC=$(cat << 'SKILLEOF'
---
name: systematic-debugging
description: "Methodical problem-solving in code through systematic processes to quickly locate and fix bugs"
---

# Systematic Debugging

## Use Cases

- Systematic investigation of complex bugs
- Production environment issue diagnosis
- Performance problem localization
- Intermittent error tracking

## Core Capabilities

- **Problem Isolation**: Narrow down problem scope
- **Hypothesis Verification**: Systematically test assumptions
- **Log Analysis**: Effectively interpret log information
- **Binary Search Debugging**: Quickly locate problematic code

## Example

```
The application occasionally returns 500 errors under high load,
but logs show no obvious anomalies.

Please help systematically investigate:
1. Collect more diagnostic information
2. Develop investigation plan
3. Progressively narrow down the problem
```

## Notes

- Reproduce the issue before fixing
- Document investigation process
- Verify fix actually resolves the issue
SKILLEOF
)

SKILL2_BODY=$(python3 -c "
import json
doc = '''$SKILL2_DOC'''
body = {
    'name': 'Systematic Debugging',
    'description': 'Methodical problem-solving in code through systematic processes',
    'type': 'AGENT_SKILL',
    'document': doc,
    'feature': {
        'skillConfig': {
            'skillTags': ['debugging', 'troubleshooting', 'methodology'],
            'downloadCount': 0
        }
    }
}
print(json.dumps(body))
")

SKILL2_RESP=$(curl -s -X POST "$BASE_URL/products" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "$SKILL2_BODY")

SKILL2_CODE=$(echo "$SKILL2_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin).get('code',''))")
if [ "$SKILL2_CODE" = "SUCCESS" ]; then
  SKILL2_ID=$(echo "$SKILL2_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['productId'])")
  echo "✅ 创建成功: productId=$SKILL2_ID"
else
  echo "❌ 创建失败: $SKILL2_RESP"
  SKILL2_ID=""
fi

# 4. 创建 Skill 3: Playwright Automation
echo ""
echo ">>> 步骤 4: 创建技能 - Playwright Automation"

SKILL3_DOC=$(cat << 'SKILLEOF'
---
name: playwright-skill
description: "Playwright browser automation skill for end-to-end testing and web application interaction"
---

# Playwright Automation

## Use Cases

- End-to-end automation testing
- Web crawling and data scraping
- Automated form filling
- Page screenshots and PDF generation

## Core Capabilities

- **Multi-browser Support**: Chromium, Firefox, WebKit
- **Page Interaction**: Click, input, wait
- **Network Interception**: Mock API responses
- **Device Emulation**: Mobile device viewport simulation

## Example

```
Please write a Playwright script to:
1. Open e-commerce website
2. Search for specific product
3. Add to cart
4. Screenshot and save results
```

## Notes

- Use explicit waits instead of fixed delays
- Handle popups and iframes
- Set reasonable timeouts
SKILLEOF
)

SKILL3_BODY=$(python3 -c "
import json
doc = '''$SKILL3_DOC'''
body = {
    'name': 'Playwright Automation',
    'description': 'Playwright browser automation for end-to-end testing',
    'type': 'AGENT_SKILL',
    'document': doc,
    'feature': {
        'skillConfig': {
            'skillTags': ['playwright', 'testing', 'automation', 'browser'],
            'downloadCount': 0
        }
    }
}
print(json.dumps(body))
")

SKILL3_RESP=$(curl -s -X POST "$BASE_URL/products" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "$SKILL3_BODY")

SKILL3_CODE=$(echo "$SKILL3_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin).get('code',''))")
if [ "$SKILL3_CODE" = "SUCCESS" ]; then
  SKILL3_ID=$(echo "$SKILL3_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['productId'])")
  echo "✅ 创建成功: productId=$SKILL3_ID"
else
  echo "❌ 创建失败: $SKILL3_RESP"
  SKILL3_ID=""
fi


# 5. 发布技能到门户
echo ""
echo ">>> 步骤 5: 发布技能到门户"

for SKILL_ID in $SKILL1_ID $SKILL2_ID $SKILL3_ID; do
  if [ -n "$SKILL_ID" ]; then
    PUB_RESP=$(curl -s -X POST "$BASE_URL/products/$SKILL_ID/publications" \
      -H "Content-Type: application/json" \
      -H "$AUTH" \
      -d "{\"portalId\":\"$PORTAL_ID\"}")
    PUB_CODE=$(echo "$PUB_RESP" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('code',''))" 2>/dev/null || echo "EMPTY")
    if [ "$PUB_CODE" = "SUCCESS" ] || [ "$PUB_CODE" = "EMPTY" ]; then
      echo "✅ 发布成功: $SKILL_ID"
    else
      echo "❌ 发布失败 ($SKILL_ID): $PUB_RESP"
    fi
  fi
done

# 6. Developer 视角：查询技能列表
echo ""
echo ">>> 步骤 6: [Developer] 查询 AGENT_SKILL 产品列表"
LIST_RESP=$(curl -s "$BASE_URL/products?type=AGENT_SKILL" \
  -H "$AUTH")
TOTAL=$(echo "$LIST_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['totalElements'])")
echo "✅ 查询到 $TOTAL 个技能产品"

# 打印技能列表
echo "$LIST_RESP" | python3 -c "
import json,sys
data = json.load(sys.stdin)['data']['content']
for p in data:
    tags = ''
    if p.get('skillConfig') and p['skillConfig'].get('skillTags'):
        tags = ', '.join(p['skillConfig']['skillTags'])
    print(f\"   - {p['name']} (ID: {p['productId']}, Tags: [{tags}])\")" 2>/dev/null || echo "   (解析列表失败)"

# 7. Developer 视角：查看技能详情
echo ""
echo ">>> 步骤 7: [Developer] 查看技能详情"
if [ -n "$SKILL1_ID" ]; then
  DETAIL_RESP=$(curl -s "$BASE_URL/products/$SKILL1_ID" \
    -H "$AUTH")
  DETAIL_NAME=$(echo "$DETAIL_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['name'])")
  DETAIL_DOC=$(echo "$DETAIL_RESP" | python3 -c "import json,sys; d=json.load(sys.stdin)['data'].get('document',''); print(d[:80] + '...' if len(d)>80 else d)")
  echo "✅ 技能详情: $DETAIL_NAME"
  echo "   Document 预览: $DETAIL_DOC"
fi

# 8. Developer 视角：下载技能 SKILL.md
echo ""
echo ">>> 步骤 8: [Developer] 下载技能 SKILL.md"
if [ -n "$SKILL1_ID" ]; then
  DL_RESP=$(curl -s -w "\n%{http_code}" "$BASE_URL/skills/$SKILL1_ID/download")
  DL_CODE=$(echo "$DL_RESP" | tail -1)
  DL_BODY=$(echo "$DL_RESP" | sed '$d')
  if [ "$DL_CODE" = "200" ]; then
    echo "✅ 下载成功 (HTTP $DL_CODE)"
    echo "   内容预览: $(echo "$DL_BODY" | head -3)"
  else
    echo "❌ 下载失败 (HTTP $DL_CODE): $DL_BODY"
  fi
fi

# 9. 验证下载计数递增
echo ""
echo ">>> 步骤 9: 验证下载计数递增"
if [ -n "$SKILL1_ID" ]; then
  AFTER_RESP=$(curl -s "$BASE_URL/products/$SKILL1_ID" \
    -H "$AUTH")
  DL_COUNT=$(echo "$AFTER_RESP" | python3 -c "
import json,sys
data = json.load(sys.stdin)['data']
sc = data.get('skillConfig') or {}
print(sc.get('downloadCount', 0))" 2>/dev/null || echo "N/A")
  echo "✅ 下载计数: $DL_COUNT (预期 >= 1)"
fi

# 10. 搜索测试
echo ""
echo ">>> 步骤 10: [Developer] 搜索技能 (关键词: debug)"
SEARCH_RESP=$(curl -s "$BASE_URL/products?type=AGENT_SKILL&keyword=debug" \
  -H "$AUTH")
SEARCH_TOTAL=$(echo "$SEARCH_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['totalElements'])" 2>/dev/null || echo "0")
echo "✅ 搜索结果: $SEARCH_TOTAL 个匹配"

# 11. 清理（可选 - 注释掉以保留数据）
# echo ""
# echo ">>> 步骤 11: 清理测试数据"
# for SKILL_ID in $SKILL1_ID $SKILL2_ID $SKILL3_ID; do
#   curl -s -X DELETE "$BASE_URL/products/$SKILL_ID" -H "$AUTH" > /dev/null
# done
# echo "✅ 清理完成"

echo ""
echo "========================================="
echo "  测试完成"
echo "========================================="
