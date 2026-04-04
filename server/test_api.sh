#!/bin/bash
# InfoPush API 测试脚本
# 用法: bash test_api.sh [服务器地址]
# 示例: bash test_api.sh http://localhost:8000

BASE_URL="${1:-http://localhost:8000}"
USERNAME="testuser_$$"
PASSWORD="test123456"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }

echo "=== InfoPush API Test ==="
echo "Server: $BASE_URL"
echo ""

# 1. 健康检查
echo "--- 1. 健康检查 ---"
RESP=$(curl -sf "$BASE_URL/")
echo "$RESP" | grep -q "InfoPush" && pass "根路由正常" || fail "根路由异常: $RESP"

# 2. 注册
echo ""
echo "--- 2. 注册用户 ($USERNAME) ---"
RESP=$(curl -sf -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
ACCESS_TOKEN=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['access_token'])" 2>/dev/null)
PUSH_TOKEN=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['push_token'])" 2>/dev/null)
[ -n "$ACCESS_TOKEN" ] && pass "注册成功，push_token=${PUSH_TOKEN:0:16}..." || fail "注册失败: $RESP"

# 3. 登录
echo ""
echo "--- 3. 登录 ---"
RESP=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
TOKEN2=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['access_token'])" 2>/dev/null)
[ -n "$TOKEN2" ] && pass "登录成功" || fail "登录失败: $RESP"

# 4. 获取用户信息
echo ""
echo "--- 4. 获取用户信息 ---"
RESP=$(curl -sf "$BASE_URL/api/auth/me" \
  -H "Authorization: Bearer $ACCESS_TOKEN")
echo "$RESP" | grep -q "$USERNAME" && pass "获取用户信息正常" || fail "获取用户信息失败: $RESP"

# 5. POST 推送（离线模式，消息应保存到数据库）
echo ""
echo "--- 5. POST 推送消息 ---"
RESP=$(curl -sf -X POST "$BASE_URL/push/$PUSH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"测试标题","content":"这是一条测试消息","content_type":"text"}')
echo "$RESP" | grep -q "200" && pass "POST 推送成功: $(echo $RESP | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["msg"])')" || fail "POST 推送失败: $RESP"

# 6. GET 推送
echo ""
echo "--- 6. GET 推送消息 ---"
RESP=$(curl -sf -G "$BASE_URL/push/$PUSH_TOKEN" \
  --data-urlencode "title=GET推送测试" \
  --data-urlencode "content=通过GET方式推送的消息")
echo "$RESP" | grep -q "200" && pass "GET 推送成功" || fail "GET 推送失败: $RESP"

# 7. 查询消息列表
echo ""
echo "--- 7. 查询消息列表 ---"
RESP=$(curl -sf "$BASE_URL/api/messages?page=1&page_size=10" \
  -H "Authorization: Bearer $ACCESS_TOKEN")
TOTAL=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['total'])" 2>/dev/null)
[ "$TOTAL" -ge 2 ] && pass "消息列表正常，共 $TOTAL 条" || fail "消息列表异常: $RESP"

# 8. 重置推送 token
echo ""
echo "--- 8. 重置推送 token ---"
RESP=$(curl -sf -X POST "$BASE_URL/api/auth/reset-token" \
  -H "Authorization: Bearer $ACCESS_TOKEN")
NEW_PUSH_TOKEN=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['push_token'])" 2>/dev/null)
[ -n "$NEW_PUSH_TOKEN" ] && [ "$NEW_PUSH_TOKEN" != "$PUSH_TOKEN" ] && pass "推送 token 已重置" || fail "重置 token 失败: $RESP"

echo ""
echo "=== 全部测试通过 ==="
