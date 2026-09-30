#!/usr/bin/env bash
# =============================================================================
# 订单超时自动取消 · 演示与验证脚本
#
# 用法：先以「短超时」配置启动应用，再执行本脚本
#   java -jar target/campus-market-1.0.0.jar --spring.profiles.active=local \
#        --app.order.timeout-minutes=1 \
#        --app.order.scan-interval-ms=5000 \
#        --app.order.initial-delay-ms=5000
#   bash scripts/timeout-demo.sh
#
# 为什么演示时要设 timeout-minutes=1：
#   默认配置是 30 分钟，等它超时太久了。
#   但也不要设成 0 —— 那样订单创建后一两秒内就被扫走取消，
#   「下单后应该是待卖家确认」这个中间状态根本来不及观察。
#   留 1 分钟，既能稳定看到「已锁定」的中间态，又只需等一分钟。
# =============================================================================
set -u
BASE="${BASE:-http://localhost:8080}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-150}"

PASS=0
FAIL=0
check() {
    local name="$1" expected="$2" actual="$3"
    if [ "$expected" = "$actual" ]; then
        printf '  \033[32m✓\033[0m %s  →  %s\n' "$name" "$actual"
        PASS=$((PASS + 1))
    else
        printf '  \033[31m✗\033[0m %s  →  期望 %s，实际 %s\n' "$name" "$expected" "$actual"
        FAIL=$((FAIL + 1))
    fi
}
section() { printf '\n\033[1m%s\033[0m\n' "$1"; }

code_of() { printf '%s' "$1" | sed -n 's/.*"code":\([0-9]\{1,\}\).*/\1/p' | head -1; }
str_of()  { printf '%s' "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p" | head -1; }
num_of()  { printf '%s' "$1" | sed -n "s/.*\"$2\":\([0-9]\{1,\}\).*/\1/p" | head -1; }

get()  { curl -s --max-time 15 -H "Authorization: Bearer ${2:-}" "$BASE$1"; }
post() {
    local path="$1" token="${2:-}" body="${3:-}"
    local args=(-s --max-time 15 -X POST "$BASE$path")
    [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
    [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
    curl "${args[@]}"
}
login() {
    post /api/auth/login '' "{\"username\":\"$1\",\"password\":\"$2\"}" \
        | sed -n 's/.*"token":"\([^"]*\)".*/\1/p' | head -1
}
credit_of() { num_of "$(get /api/auth/me "$1")" creditScore; }

printf '\033[1m校园二手交易平台 · 订单超时自动取消验证\033[0m\n目标地址：%s\n' "$BASE"

# 注意：不要用 curl 的退出码判断存活。Windows 版 curl 不认 Git Bash 的 /dev/null，
# `curl -o /dev/null` 会以退出码 23（写数据失败）结束，即使请求本身是成功的。
# 所以这里看 HTTP 状态码 —— 与 smoke-test.sh 的 http_of 保持一致。
HEALTH="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/api/categories")"
if [ "$HEALTH" != "200" ]; then
    printf '\033[31m应用未就绪（/api/categories 返回 %s），请先按脚本头部说明启动。\033[0m\n' "$HEALTH"
    exit 1
fi

TA="$(login alice 123456)"
TB="$(login bob 123456)"
TC="$(login carol 123456)"
if [ -z "$TA" ] || [ -z "$TB" ] || [ -z "$TC" ]; then
    printf '\033[31m登录失败，请确认演示数据已生成。\033[0m\n'
    exit 1
fi

section '1. 准备：alice 发布商品，bob 下单'

# 注意两个接口的返回结构不一样：
#   发布商品 → data 直接就是新商品的 ID（裸数字）
#   创建订单 → data 是订单对象，ID 在 data.id 里
RP="$(post /api/products "$TA" \
    '{"title":"超时验证商品","description":"用于验证订单超时自动取消","price":9.90,"categoryId":1}')"
PID="$(num_of "$RP" data)"
check '发布商品成功' yes "$([ -n "$PID" ] && printf yes || printf no)"

OID="$(num_of "$(post /api/orders "$TB" \
    "{\"productId\":$PID,\"contact\":\"13800000000\",\"remark\":\"超时验证\"}")" id)"
check '下单成功' yes "$([ -n "$OID" ] && printf yes || printf no)"

check '下单后订单为待卖家确认' CREATED "$(str_of "$(get "/api/orders/$OID" "$TB")" status)"
check '下单后商品被锁定' LOCKED "$(str_of "$(get "/api/products/$PID" "")" status)"

CREDIT_BEFORE="$(credit_of "$TB")"

section '2. 等待定时任务把超时订单清掉'
printf '  最长等待 %s 秒（扫描间隔由启动参数决定）...\n' "$MAX_WAIT_SECONDS"

WAITED=0
STATUS=""
while [ "$WAITED" -lt "$MAX_WAIT_SECONDS" ]; do
    STATUS="$(str_of "$(get "/api/orders/$OID" "$TB")" status)"
    [ "$STATUS" = "CANCELLED" ] && break
    sleep 3
    WAITED=$((WAITED + 3))
    printf '  ...已等待 %ss，当前状态 %s\n' "$WAITED" "$STATUS"
done

section '3. 结果校验'
check '订单已被自动取消' CANCELLED "$STATUS"
check '商品已释放回在售' ON_SALE "$(str_of "$(get "/api/products/$PID" "")" status)"

REASON="$(str_of "$(get "/api/orders/$OID" "$TB")" cancelReason)"
check '取消原因可识别为超时' yes "$(printf '%s' "$REASON" | grep -q '超时' && printf yes || printf no)"
printf '       取消原因：%s\n' "$REASON"

# 超时是系统行为，不该归咎于买卖任何一方，所以不扣信用分
check '买家信用分未被扣' "$CREDIT_BEFORE" "$(credit_of "$TB")"

section '4. 商品可被重新下单（验证真的释放干净了）'
# 换 carol 来买：商品是 alice 发布的，alice 买自己的商品会被 403 拦住
OID2="$(num_of "$(post /api/orders "$TC" "{\"productId\":$PID}")" id)"
check 'carol 可以买下这件商品' yes "$([ -n "$OID2" ] && printf yes || printf no)"

printf '\n\033[1m验证结果：\033[0m通过 \033[32m%d\033[0m 项，失败 \033[31m%d\033[0m 项\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] && printf '\033[32m全部通过\033[0m\n' || printf '\033[31m存在失败项\033[0m\n'
exit "$FAIL"
