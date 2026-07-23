#!/usr/bin/env bash
# 실서비스 GET 전용 API 순회 - 트레이스 생성 + 상태/지연 스모크.
#
# 용도:
#   1) 모든 읽기 API에 실제 트래픽을 만들어 Tempo에 정상 트레이스를 쌓는다 (리뷰 모드 분석용)
#   2) 상태코드/응답시간 회귀를 잡는다 (500이 새로 생기면 여기서 보인다)
#
# 사용법:
#   YOGURTTE_TOKEN='<액세스 토큰>' ./scripts/api-sweep.sh [BASE_URL]
#   BASE_URL 기본값: https://yogurtte.com
#
#   파일 업로드 왕복까지 태우려면 (선택):
#   YOGURTTE_TOKEN=... SWEEP_UPLOAD_IMG=~/Pictures/test.png ./scripts/api-sweep.sh
#
# 안전 규칙: 기본은 GET만 호출한다. 유일한 예외는 SWEEP_UPLOAD_IMG를 명시했을 때의
# 파일 업로드(생성만 하고 아무것도 지우지 않음)다. 그 외 데이터를 바꾸거나 지우는
# API(POST/PUT/DELETE)는 절대 추가하지 말 것 - 계정 삭제/채팅방 나가기 같은
# 파괴적 호출이 섞여 있다.
set -euo pipefail

BASE="${1:-https://yogurtte.com}"
TOKEN="${YOGURTTE_TOKEN:?YOGURTTE_TOKEN env가 필요합니다 (Bearer 액세스 토큰)}"
OUTDIR="./reports/sweeps"
mkdir -p "$OUTDIR"
TS=$(date +%Y%m%dT%H%M%S)
OUT="$OUTDIR/sweep-$TS.tsv"
printf "status\ttime_s\tbytes\tpath\n" > "$OUT"

hit() {
  local p="$1"
  local r
  r=$(curl -sk -o /tmp/sweep_body.$$ -w "%{http_code}\t%{time_total}\t%{size_download}" \
      -H "Authorization: Bearer $TOKEN" "$BASE$p" || echo "000\t0\t0")
  printf "%s\t%s\n" "$r" "$p" >> "$OUT"
  sleep 0.25
}

# 목록 응답에서 첫 번째 숫자 id를 뽑는다 (상세 API용)
first_id() {
  local p="$1"
  curl -sk -H "Authorization: Bearer $TOKEN" "$BASE$p" \
    | python3 -c '
import json,sys
keys={"id","battleId","productId","roomId","feedId","chatRoomId"}
found=[]
def walk(o):
    if isinstance(o,dict):
        for k,v in o.items():
            if k in keys and str(v).isdigit(): found.append(int(v))
            walk(v)
    elif isinstance(o,list):
        for x in o: walk(x)
try: walk(json.load(sys.stdin))
except Exception: pass
print(found[0] if found else "")'
}

echo "== phase 1: 고정 엔드포인트 =="
# --- content ---
hit "/api/content/feeds/scroll?page=0&size=10"
hit "/api/content/feeds/hot"
hit "/api/content/feeds/following"
hit "/api/content/battles?page=0&size=10"
hit "/api/content/battles/hot"
hit "/api/content/battles/creation/validation"
hit "/api/content/categories"
hit "/api/content/categories/popular"          # 2026-07-24 기준 500 - 수정되면 200으로 바뀌어야 함
hit "/api/content/products?page=0&size=10"
hit "/api/content/hashtags/hot"
hit "/api/content/dashboard/summary"
hit "/api/content/carriers/me"
# --- chat ---
hit "/api/chat/v1/chat/rooms"
hit "/api/chat/notifications?page=0&size=10"
hit "/api/chat/notifications/unread"
hit "/api/chat/notifications/unread/count"
# --- auth/user ---
hit "/api/auth/auth/verify"
hit "/api/auth/user/1"
hit "/api/user/1/profile"
hit "/api/auth/user/1/profile"                 # 위와 응답 크기가 다름(504B vs 6.6KB) - 라우팅 이상 추적용
hit "/api/auth/user/1/following"               # 2026-07-24 기준 500
hit "/api/auth/user/1/followers"               # 2026-07-24 기준 500
hit "/api/auth/user/1/notification-settings"
# 파라미터 필요해서 제외: /feeds/{id}/swipe/*, /categories/tree, /location
# GET 미지원(405): /user/me/fcm-tokens, /products/{id}/reviews/stats

echo "== phase 2: 목록에서 발견한 id로 상세 =="
FEED_ID=$(first_id "/api/content/feeds/scroll?page=0&size=5");    FEED_ID=${FEED_ID:-146}
BATTLE_ID=$(first_id "/api/content/battles?page=0&size=5");       BATTLE_ID=${BATTLE_ID:-12}
PRODUCT_ID=$(first_id "/api/content/products?page=0&size=5");     PRODUCT_ID=${PRODUCT_ID:-1}
ROOM_ID=$(first_id "/api/chat/v1/chat/rooms")
echo "   feed=$FEED_ID battle=$BATTLE_ID product=$PRODUCT_ID room=${ROOM_ID:-없음}"

hit "/api/content/feeds/$FEED_ID"
hit "/api/content/feeds/$FEED_ID/comments?page=0&size=20"
hit "/api/content/battles/$BATTLE_ID"
hit "/api/content/battles/$BATTLE_ID/comments?page=0&size=10"
hit "/api/content/battles/$BATTLE_ID/items"
hit "/api/content/products/$PRODUCT_ID"
hit "/api/content/products/$PRODUCT_ID/reviews?page=0&size=10"
hit "/api/content/products/$PRODUCT_ID/feeds"
hit "/api/content/products/$PRODUCT_ID/battles"
if [ -n "${ROOM_ID:-}" ]; then
  hit "/api/chat/chats/$ROOM_ID/messages"
fi

# phase 3 (선택): 파일 업로드 -> 다운로드 왕복. SWEEP_UPLOAD_IMG 지정 시에만 실행.
if [ -n "${SWEEP_UPLOAD_IMG:-}" ]; then
  IMG="${SWEEP_UPLOAD_IMG/#\~/$HOME}"
  if [ -f "$IMG" ]; then
    echo "== phase 3: 파일 업로드 왕복 ($IMG) =="
    UP=$(curl -sk -w "\n%{http_code}\t%{time_total}" \
        -H "Authorization: Bearer $TOKEN" \
        -F "file=@$IMG" -F "fileExplain=api-sweep test" \
        "$BASE/api/auth/files/upload")
    UP_BODY=$(echo "$UP" | sed '$d')
    UP_META=$(echo "$UP" | tail -1)
    printf "%s\t%s\tPOST /api/auth/files/upload\n" "$UP_META" "${#UP_BODY}" >> "$OUT"
    FILE_ID=$(echo "$UP_BODY" | python3 -c '
import json,sys
try:
    d=json.load(sys.stdin)
    for k in ("fileId","id","attachmentFileId"):
        v=d.get(k) if isinstance(d,dict) else None
        if v is not None: print(v); break
except Exception: pass')
    if [ -n "$FILE_ID" ]; then
      echo "   업로드된 fileId=$FILE_ID"
      hit "/api/auth/files/download/$FILE_ID"
      hit "/api/auth/files/$FILE_ID/presigned/download"
    else
      echo "   업로드 응답에서 fileId를 못 찾음: $UP_BODY" | head -c 300
    fi
  else
    echo "SWEEP_UPLOAD_IMG 파일이 없음: $IMG (업로드 건너뜀)"
  fi
fi

echo
echo "== 결과: $OUT =="
column -t "$OUT"
echo
echo "== 요약 =="
awk -F'\t' 'NR>1 {n++; s[$1]++} END {printf "총 %d건 | ", n; for (c in s) printf "%s:%d ", c, s[c]; print ""}' "$OUT"
echo "-- 200 아닌 응답 --"
awk -F'\t' 'NR>1 && $1!=200 {print "  "$1"  "$4}' "$OUT"
echo "-- 느린 순 상위 5 --"
awk -F'\t' 'NR>1' "$OUT" | sort -t$'\t' -k2 -rn | head -5 | awk -F'\t' '{printf "  %ss  %s  %s\n",$2,$1,$4}'
rm -f /tmp/sweep_body.$$
