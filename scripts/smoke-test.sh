#!/usr/bin/env bash
# =============================================================================
# 接口冒烟测试
#
# 用法：先启动应用，再执行
#   bash scripts/smoke-test.sh
#   BASE=http://localhost:8080 bash scripts/smoke-test.sh
#
# 覆盖：静态资源、鉴权、权限隔离、参数校验、发布、下单、并发防超卖、
#       订单状态机、信用分结算、商品状态流转、逻辑删除、收藏、留言、搜索排序
# =============================================================================
set -u

BASE="${BASE:-http://localhost:8080}"

# Windows 版 curl 只认 Windows 风格路径，而 Git Bash 的 $PWD 是 /c/Users/... 形式，
# 所以这里显式转一下，否则并发测试的响应文件会写不出来。
to_win() {
    local p="$1"
    if [[ "$p" =~ ^/([a-zA-Z])/(.*)$ ]]; then
        printf '%s:/%s' "${BASH_REMATCH[1]^^}" "${BASH_REMATCH[2]}"
    else
        printf '%s' "$p"
    fi
}
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(to_win "$ROOT")/target/smoke"
mkdir -p "$WORK"

PASS=0
FAIL=0

# ---------------------------------------------------------------- 工具函数

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
bool_of() { printf '%s' "$1" | sed -n 's/.*"data":\(true\|false\).*/\1/p' | head -1; }
count_of(){ printf '%s' "$1" | grep -o "\"$2\":" | wc -l | tr -d ' '; }
has()     { printf '%s' "$1" | grep -q "$2" && printf 'yes' || printf 'no'; }

get() { curl -s --max-time 15 -H "Authorization: Bearer ${2:-}" "$BASE$1"; }

post() {
    local path="$1" token="${2:-}" body="${3:-}"
    local args=(-s --max-time 15 -X POST "$BASE$path")
    [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
    [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
    curl "${args[@]}"
}

del() {
    local path="$1" token="${2:-}"
    curl -s --max-time 15 -X DELETE "$BASE$path" -H "Authorization: Bearer $token"
}

http_of() { curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$BASE$1"; }

login() {
    post /api/auth/login '' "{\"username\":\"$1\",\"password\":\"$2\"}" \
        | sed -n 's/.*"token":"\([^"]*\)".*/\1/p' | head -1
}

credit_of() { num_of "$(get /api/auth/me "$1")" creditScore; }

# ---------------------------------------------------------------- 开始

printf '\033[1m校园二手交易平台 · 接口冒烟测试\033[0m\n目标地址：%s\n' "$BASE"

section '1. 静态资源与公开接口'
check 'GET /api/categories' 200 "$(http_of /api/categories)"
check 'GET /api/products' 200 "$(http_of /api/products)"
check 'GET /index.html' 200 "$(http_of /index.html)"
check 'GET /login.html' 200 "$(http_of /login.html)"
check 'GET /detail.html' 200 "$(http_of /detail.html)"
check 'GET /center.html' 200 "$(http_of /center.html)"
check 'GET /publish.html' 200 "$(http_of /publish.html)"
check 'GET /admin.html' 200 "$(http_of /admin.html)"
check 'GET /css/style.css' 200 "$(http_of /css/style.css)"
check 'GET /js/api.js' 200 "$(http_of /js/api.js)"
check '分类数量为 8' 8 "$(count_of "$(get /api/categories '')" id)"

section '2. 登录与鉴权'
TOKEN_A="$(login alice 123456)"
TOKEN_B="$(login bob 123456)"
TOKEN_C="$(login carol 123456)"
TOKEN_ADMIN="$(login admin 123456)"
check 'alice 登录成功' yes "$([ -n "$TOKEN_A" ] && echo yes || echo no)"
check 'admin 登录成功' yes "$([ -n "$TOKEN_ADMIN" ] && echo yes || echo no)"

check '密码错误 → 401' 401 "$(code_of "$(post /api/auth/login '' '{"username":"alice","password":"wrong"}')")"
check '用户名不存在 → 401（与密码错误同提示，防账号枚举）' 401 \
    "$(code_of "$(post /api/auth/login '' '{"username":"nobody-here","password":"wrong"}')")"
check '未携带 Token 访问订单 → 401' 401 "$(code_of "$(get /api/orders/bought '')")"
check 'Token 被篡改 → 401' 401 "$(code_of "$(get /api/orders/bought "${TOKEN_B%?}X")")"
check '普通用户访问管理端 → 403' 403 "$(code_of "$(get /api/admin/users "$TOKEN_A")")"
check '管理员访问管理端 → 200' 200 "$(code_of "$(get /api/admin/users "$TOKEN_ADMIN")")"

section '3. 参数校验与唯一约束'
SMOKE_USER="smoke$(date +%H%M%S)"
check '用户名过短 → 400' 400 "$(code_of "$(post /api/auth/register '' '{"username":"ab","password":"123456","nickname":"x"}')")"
check '密码过短 → 400' 400 "$(code_of "$(post /api/auth/register '' '{"username":"newuser001","password":"123","nickname":"x"}')")"
check '手机号格式错误 → 400' 400 "$(code_of "$(post /api/auth/register '' '{"username":"newuser002","password":"123456","nickname":"x","phone":"12345"}')")"
check '重复用户名 → 409' 409 "$(code_of "$(post /api/auth/register '' '{"username":"alice","password":"123456","nickname":"重复"}')")"
check '正常注册 → 200' 200 "$(code_of "$(post /api/auth/register '' "{\"username\":\"$SMOKE_USER\",\"password\":\"123456\",\"nickname\":\"冒烟用户\",\"campus\":\"南校区\"}")")"
check '再次注册同名 → 409' 409 "$(code_of "$(post /api/auth/register '' "{\"username\":\"$SMOKE_USER\",\"password\":\"123456\",\"nickname\":\"冒烟用户\"}")")"

section '4. 发布商品'
R="$(post /api/products "$TOKEN_A" '{"title":"冒烟测试商品","description":"用于接口自动化测试","price":99.50,"originalPrice":199.00,"categoryId":2,"conditionLevel":"九成新"}')"
PID="$(num_of "$R" data)"
check 'alice 发布商品 → 200' 200 "$(code_of "$R")"
check '返回商品 ID' yes "$([ -n "$PID" ] && echo yes || echo no)"
check '空标题 → 400' 400 "$(code_of "$(post /api/products "$TOKEN_A" '{"title":"","price":10,"categoryId":1}')")"
check '价格为 0 → 400' 400 "$(code_of "$(post /api/products "$TOKEN_A" '{"title":"测试","price":0,"categoryId":1}')")"
check '未登录发布 → 401' 401 "$(code_of "$(post /api/products '' '{"title":"测试","price":10,"categoryId":1}')")"

section '5. 下单与防超卖'
check '卖家购买自己的商品 → 403' 403 "$(code_of "$(post /api/orders "$TOKEN_A" "{\"productId\":$PID}")")"
R="$(post /api/orders "$TOKEN_B" "{\"productId\":$PID,\"contact\":\"13800000001\",\"remark\":\"冒烟测试下单\"}")"
OID="$(num_of "$R" id)"
check 'bob 下单 → 200' 200 "$(code_of "$R")"
check '订单初始状态为 CREATED' CREATED "$(str_of "$R" status)"
check '买家在 CREATED 状态下只有「取消订单」动作' no "$(has "$R" '确认订单')"
check '商品已被锁定，carol 下单 → 409' 409 "$(code_of "$(post /api/orders "$TOKEN_C" "{\"productId\":$PID}")")"
check 'bob 重复下单 → 409' 409 "$(code_of "$(post /api/orders "$TOKEN_B" "{\"productId\":$PID}")")"

section '6. 并发抢单：10 个请求同时下单同一件商品'
R="$(post /api/products "$TOKEN_A" '{"title":"并发测试商品","price":1.00,"categoryId":1}')"
CPID="$(num_of "$R" data)"
for i in 1 2 3 4 5 6 7 8 9 10; do
    curl -s --max-time 20 -X POST "$BASE/api/orders" \
        -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN_B" \
        -d "{\"productId\":$CPID,\"remark\":\"并发 $i\"}" -o "$WORK/conc_$i.json" &
done
wait
SUCCESS=0
for i in 1 2 3 4 5 6 7 8 9 10; do
    [ "$(code_of "$(cat "$WORK/conc_$i.json")")" = "200" ] && SUCCESS=$((SUCCESS + 1))
done
check '10 个并发请求中恰好 1 个成功（防超卖生效）' 1 "$SUCCESS"

section '7. 订单状态机'
check '无关用户（carol）确认订单 → 403' 403 "$(code_of "$(post "/api/orders/$OID/confirm" "$TOKEN_C")")"
check '买家不能确认订单 → 403' 403 "$(code_of "$(post "/api/orders/$OID/confirm" "$TOKEN_B")")"
check 'CREATED 不能直接跳到 FINISHED → 422' 422 "$(code_of "$(post "/api/orders/$OID/finish" "$TOKEN_B")")"
check '卖家确认订单 → 200' 200 "$(code_of "$(post "/api/orders/$OID/confirm" "$TOKEN_A")")"
check '重复确认 → 422（状态机拦截）' 422 "$(code_of "$(post "/api/orders/$OID/confirm" "$TOKEN_A")")"

R="$(get "/api/orders/$OID" "$TOKEN_A")"
check '卖家视角 viewerRole = SELLER' SELLER "$(str_of "$R" viewerRole)"
check '卖家在 CONFIRMED 状态看到「取消订单」' yes "$(has "$R" '取消订单')"

section '8. 信用分结算'
BA="$(credit_of "$TOKEN_A")"
BB="$(credit_of "$TOKEN_B")"
check '买家确认完成交易 → 200' 200 "$(code_of "$(post "/api/orders/$OID/finish" "$TOKEN_B")")"
check '卖家信用分 +2' "$((BA + 2))" "$(credit_of "$TOKEN_A")"
check '买家信用分 +2' "$((BB + 2))" "$(credit_of "$TOKEN_B")"
check '信用分流水有「完成交易」记录' yes "$(has "$(get /api/users/me/credit-logs "$TOKEN_B")" '完成交易')"
check '已完成订单不能取消 → 422' 422 "$(code_of "$(post "/api/orders/$OID/cancel" "$TOKEN_B" '{"reason":"非法流转"}')")"

section '9. 取消订单：商品回滚上架 + 违约扣分'
R="$(post /api/products "$TOKEN_A" '{"title":"取消测试商品","price":8.00,"categoryId":1}')"
XPID="$(num_of "$R" data)"
XOID="$(num_of "$(post /api/orders "$TOKEN_B" "{\"productId\":$XPID}")" id)"
post "/api/orders/$XOID/confirm" "$TOKEN_A" > /dev/null
BC="$(credit_of "$TOKEN_B")"
check '卖家确认后买家取消 → 200' 200 "$(code_of "$(post "/api/orders/$XOID/cancel" "$TOKEN_B" '{"reason":"临时不需要了"}')")"
check '买家违约信用分 -3' "$((BC - 3))" "$(credit_of "$TOKEN_B")"
check '取消后商品回到 ON_SALE' ON_SALE "$(str_of "$(get "/api/products/$XPID" '')" status)"
check '取消后商品可被重新下单' 200 "$(code_of "$(post /api/orders "$TOKEN_C" "{\"productId\":$XPID}")")"

section '10. 商品状态与权限隔离'
check '交易完成后商品状态为 SOLD' SOLD "$(str_of "$(get "/api/products/$PID" '')" status)"
check '非卖家下架他人商品 → 403' 403 "$(code_of "$(post "/api/products/$PID/off-shelf" "$TOKEN_B")")"

R="$(post /api/products "$TOKEN_A" '{"title":"删除测试商品","price":5.00,"categoryId":1}')"
DPID="$(num_of "$R" data)"
check '非卖家删除他人商品 → 403' 403 "$(code_of "$(del "/api/products/$DPID" "$TOKEN_B")")"
check '卖家删除自己的商品 → 200' 200 "$(code_of "$(del "/api/products/$DPID" "$TOKEN_A")")"
check '已删除商品查询 → 404（逻辑删除生效）' 404 "$(code_of "$(get "/api/products/$DPID" '')")"

R="$(post /api/products "$TOKEN_A" '{"title":"下架测试商品","price":6.00,"categoryId":1}')"
SPID="$(num_of "$R" data)"
check '卖家下架商品 → 200' 200 "$(code_of "$(post "/api/products/$SPID/off-shelf" "$TOKEN_A")")"
check '下架后状态为 OFF_SHELF' OFF_SHELF "$(str_of "$(get "/api/products/$SPID" '')" status)"
check '下架商品不能被下单 → 409' 409 "$(code_of "$(post /api/orders "$TOKEN_B" "{\"productId\":$SPID}")")"
check '卖家重新上架 → 200' 200 "$(code_of "$(post "/api/products/$SPID/on-shelf" "$TOKEN_A")")"
check '重新上架后状态为 ON_SALE' ON_SALE "$(str_of "$(get "/api/products/$SPID" '')" status)"

section '11. 收藏与留言'
check '收藏 → data=true' true "$(bool_of "$(post /api/favorites/1 "$TOKEN_C")")"
check '再次调用为取消收藏 → data=false' false "$(bool_of "$(post /api/favorites/1 "$TOKEN_C")")"
check '未登录收藏 → 401' 401 "$(code_of "$(post /api/favorites/1 '')")"
check '收藏列表可查询' 200 "$(code_of "$(get /api/favorites/mine "$TOKEN_C")")"
check '发表留言 → 200' 200 "$(code_of "$(post /api/products/1/comments "$TOKEN_C" '{"content":"请问可以小刀吗","parentId":0}')")"
check '留言列表可查询' yes "$(has "$(get /api/products/1/comments '')" '可以小刀')"
check '空留言 → 400' 400 "$(code_of "$(post /api/products/1/comments "$TOKEN_C" '{"content":""}')")"
check '未登录留言 → 401' 401 "$(code_of "$(post /api/products/1/comments '' '{"content":"x"}')")"

section '12. 搜索、筛选与排序'
check '关键词搜索（中文）' yes "$(has "$(curl -s --max-time 15 --get --data-urlencode 'keyword=高等数学' "$BASE/api/products")" '高等数学')"
check '分类筛选' 200 "$(code_of "$(get '/api/products?categoryId=2&page=1&size=5' '')")"
check '价格区间筛选' 200 "$(code_of "$(get '/api/products?minPrice=100&maxPrice=3000' '')")"
check '价格升序排序' 200 "$(code_of "$(get '/api/products?sort=priceAsc' '')")"
check '浏览量排序' 200 "$(code_of "$(get '/api/products?sort=hot' '')")"
check '非法排序参数回落默认（ORDER BY 白名单防注入）' 200 "$(code_of "$(get '/api/products?sort=DROP%20TABLE%20product' '')")"
check '超范围分页返回空列表而非报错' 0 "$(count_of "$(get '/api/products?page=9999&size=10' '')" title)"

section '13. 浏览量与收藏计数'
V1="$(num_of "$(get "/api/products/2" '')" viewCount)"
get "/api/products/2" '' > /dev/null
V2="$(num_of "$(get "/api/products/2" '')" viewCount)"
check '浏览量递增' yes "$([ "$V2" -gt "$V1" ] && echo yes || echo no)"

section '14. 文件上传'
# 用 node 生成一张合法的 1x1 PNG 和一份伪装成图片的文本文件
node -e "require('fs').writeFileSync(process.argv[1],Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=','base64'))" "$WORK/pixel.png"
printf 'not an image at all\n' > "$WORK/evil.txt"

check '未登录上传 → 401' 401 "$(code_of "$(curl -s -X POST "$BASE/api/files/upload" -F "file=@$WORK/pixel.png")")"

R="$(curl -s --max-time 20 -X POST "$BASE/api/files/upload" -H "Authorization: Bearer $TOKEN_A" -F "file=@$WORK/pixel.png")"
UPURL="$(str_of "$R" data)"
check '上传 PNG → 200' 200 "$(code_of "$R")"
check '返回 /uploads/ 开头的访问路径' yes "$(printf '%s' "$UPURL" | grep -q '^/uploads/' && echo yes || echo no)"
check '上传的图片可通过 URL 访问' 200 "$(http_of "$UPURL")"

R="$(curl -s --max-time 20 -X POST "$BASE/api/files/upload" -H "Authorization: Bearer $TOKEN_A" -F "file=@$WORK/evil.txt")"
check '非图片扩展名被白名单拒绝 → 422' 422 "$(code_of "$R")"

section '15. 个人资料修改'
CID="$(num_of "$(post /api/auth/login '' '{"username":"carol","password":"123456"}')" id)"
AID="$(num_of "$(post /api/auth/login '' '{"username":"admin","password":"123456"}')" id)"
ORIG_NICK="$(str_of "$(get /api/auth/me "$TOKEN_C")" nickname)"

check '修改昵称与校区 → 200' 200 "$(code_of "$(curl -s -X PUT "$BASE/api/users/me" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN_C" -d '{"nickname":"改名测试中","campus":"北校区"}')")"
check '昵称已生效' 改名测试中 "$(str_of "$(get /api/auth/me "$TOKEN_C")" nickname)"
check '校区已生效' 北校区 "$(str_of "$(get /api/auth/me "$TOKEN_C")" campus)"
check '手机号格式错误 → 400' 400 "$(code_of "$(curl -s -X PUT "$BASE/api/users/me" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN_C" -d '{"phone":"abc"}')")"
check '未登录修改资料 → 401' 401 "$(code_of "$(curl -s -X PUT "$BASE/api/users/me" -H 'Content-Type: application/json' -d '{"nickname":"x"}')")"
check '他人主页不暴露手机号' no "$(has "$(get "/api/users/$CID" '')" 'phone')"

# 还原昵称，保证脚本可重复执行
curl -s -X PUT "$BASE/api/users/me" -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN_C" -d "{\"nickname\":\"$ORIG_NICK\"}" > /dev/null

section '16. 管理员封禁 / 解封'
check '封禁 carol → 200' 200 "$(code_of "$(curl -s -X PUT "$BASE/api/admin/users/$CID/status?status=0" -H "Authorization: Bearer $TOKEN_ADMIN")")"
check '被封禁用户无法登录 → 403' 403 "$(code_of "$(post /api/auth/login '' '{"username":"carol","password":"123456"}')")"
check '解封 carol → 200' 200 "$(code_of "$(curl -s -X PUT "$BASE/api/admin/users/$CID/status?status=1" -H "Authorization: Bearer $TOKEN_ADMIN")")"
check '解封后可正常登录 → 200' 200 "$(code_of "$(post /api/auth/login '' '{"username":"carol","password":"123456"}')")"
check '封禁管理员账号 → 422' 422 "$(code_of "$(curl -s -X PUT "$BASE/api/admin/users/$AID/status?status=0" -H "Authorization: Bearer $TOKEN_ADMIN")")"
check '封禁不存在的用户 → 404' 404 "$(code_of "$(curl -s -X PUT "$BASE/api/admin/users/999999/status?status=0" -H "Authorization: Bearer $TOKEN_ADMIN")")"

section '17. 商品编辑'
R="$(post /api/products "$TOKEN_A" '{"title":"编辑前标题","price":10.00,"categoryId":1}')"
EPID="$(num_of "$R" data)"
check '非卖家编辑他人商品 → 403' 403 "$(code_of "$(curl -s -X PUT "$BASE/api/products/$EPID" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN_B" -d '{"title":"恶意修改","price":1,"categoryId":1}')")"
check '卖家编辑自己的商品 → 200' 200 "$(code_of "$(curl -s -X PUT "$BASE/api/products/$EPID" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN_A" -d '{"title":"编辑后标题","description":"已更新描述","price":12.50,"categoryId":2}')")"
R="$(get "/api/products/$EPID" '')"
check '标题修改已生效' 编辑后标题 "$(str_of "$R" title)"
check '价格修改已生效' 12.50 "$(printf '%s' "$R" | sed -n 's/.*"price":\([0-9.]*\).*/\1/p' | head -1)"
check '分类修改已生效' 数码电子 "$(str_of "$R" categoryName)"
check '修改已锁定商品 → 422' 422 "$(code_of "$(curl -s -X PUT "$BASE/api/products/$PID" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN_A" -d '{"title":"改已售出的","price":1,"categoryId":1}')")"

section '18. 订单详情与越权访问'
check '买家查看自己的订单 → 200' 200 "$(code_of "$(get "/api/orders/$OID" "$TOKEN_B")")"
check '卖家查看该订单 → 200' 200 "$(code_of "$(get "/api/orders/$OID" "$TOKEN_A")")"
check '无关用户查看订单 → 403' 403 "$(code_of "$(get "/api/orders/$OID" "$TOKEN_C")")"
check '不存在的订单 → 404' 404 "$(code_of "$(get '/api/orders/999999' "$TOKEN_B")")"
check '不存在的商品 → 404' 404 "$(code_of "$(get '/api/products/999999' '')")"
check '不存在的用户 → 404' 404 "$(code_of "$(get '/api/users/999999' '')")"

# ---------------------------------------------------------------- 汇总

printf '\n\033[1m测试结果：\033[0m通过 \033[32m%d\033[0m 项，失败 \033[31m%d\033[0m 项\n' "$PASS" "$FAIL"
if [ "$FAIL" -eq 0 ]; then
    printf '\033[32m全部通过\033[0m\n'
else
    printf '\033[31m存在失败项，请检查上方输出\033[0m\n'
fi
exit "$FAIL"
