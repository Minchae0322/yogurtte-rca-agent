#!/usr/bin/env bash
# 실서비스 쓰기 플로우 - 첨부 업로드 → 피드 등록 → 댓글 등록.
#
# 목적: 관측 대상인 핵심 쓰기 경로의 트레이스를 온디맨드로 생성한다.
#   - 첨부 업로드: 파일 저장 경로 (DF-01 #3 왕복 검증 겸용)
#   - 피드 등록: 트랜잭션·락 경로 (NF-04 계열)
#   - 댓글 등록: content → Kafka → chat → FCM 전체 비동기 경로 (N1 정답지 흐름)
#
# 사용법:
#   YOGURTTE_TOKEN='<액세스 토큰>' ./scripts/api-write-flow.sh [BASE_URL]
#
# 첨부파일: ./sweep-assets/ 폴더에 이미지(jpg/png/gif/webp)를 넣어두면 첫 번째 파일을
# 사용한다. 폴더가 비어 있으면 1x1 테스트 PNG를 생성해 사용한다.
#   ASSET_DIR=다른/폴더  SWEEP_USER_ID=1  SWEEP_SUBCATEGORY_ID=지정  로 덮어쓸 수 있다.
#
# 안전 규칙: 이 스크립트는 "생성"만 한다 (업로드/피드/댓글). 삭제·수정은 하지 않는다.
# 생성물에는 식별 가능한 텍스트를 넣어 나중에 수동 정리할 수 있게 한다.
set -euo pipefail

BASE="${1:-https://yogurtte.com}"
TOKEN="${YOGURTTE_TOKEN:?YOGURTTE_TOKEN env가 필요합니다 (Bearer 액세스 토큰)}"
ASSET_DIR="${ASSET_DIR:-./sweep-assets}"
USER_ID="${SWEEP_USER_ID:-1}"
OUTDIR="./reports/sweeps"
mkdir -p "$OUTDIR"
TS=$(date +%Y%m%dT%H%M%S)
OUT="$OUTDIR/write-$TS.tsv"
MARK="api-write-flow $TS"
printf "step\tstatus\ttime_s\tdetail\n" > "$OUT"

auth=(-H "Authorization: Bearer $TOKEN")
say() { echo "[$1] $2"; }
rec() { printf "%s\t%s\t%s\t%s\n" "$1" "$2" "$3" "$4" >> "$OUT"; }

json_get() { # stdin JSON에서 키 추출 (중첩 data 감안)
  python3 -c "
import json,sys
key='$1'
try: d=json.load(sys.stdin)
except Exception: sys.exit(0)
def find(o):
    if isinstance(o,dict):
        if key in o and o[key] is not None: return o[key]
        for v in o.values():
            r=find(v)
            if r is not None: return r
    elif isinstance(o,list):
        for v in o:
            r=find(v)
            if r is not None: return r
    return None
v=find(d)
print(v if v is not None else '')"
}

# ---------- 0) 첨부파일 선택 ----------
mkdir -p "$ASSET_DIR"
IMG=$(find "$ASSET_DIR" -maxdepth 1 -type f \( -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.png" -o -iname "*.gif" -o -iname "*.webp" \) | sort | head -1)
if [ -z "$IMG" ]; then
  IMG="$ASSET_DIR/.generated-test.png"
  python3 -c "
import base64
open('$IMG','wb').write(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='))"
  say 0 "sweep-assets/ 비어 있음 → 1x1 테스트 PNG 생성"
else
  say 0 "첨부파일: $IMG"
fi

# ---------- 1) 파일 업로드 ----------
R=$(curl -sk -w "\n%{http_code}\t%{time_total}" "${auth[@]}" \
    -F "file=@$IMG" -F "fileExplain=$MARK" "$BASE/api/auth/files/upload")
BODY=$(echo "$R" | sed '$d'); META=$(echo "$R" | tail -1)
CODE=${META%%$'\t'*}
rec upload "$CODE" "${META#*$'\t'}" "$(echo "$BODY" | head -c 120)"
[ "$CODE" = "200" ] || { say 1 "업로드 실패($CODE): $BODY"; exit 1; }
FILE_ID=$(echo "$BODY" | json_get id)
STORED=$(echo "$BODY" | json_get fileUrl)
ORIGIN=$(echo "$BODY" | json_get orgFileNm)
say 1 "업로드 성공: fileId=$FILE_ID storedPath=$STORED"

# ---------- 2) subCategoryId 결정 ----------
SUB_ID="${SWEEP_SUBCATEGORY_ID:-}"
if [ -z "$SUB_ID" ]; then
  SUB_ID=$(curl -sk "${auth[@]}" "$BASE/api/content/categories" | json_get categoryId)
fi
say 2 "subCategoryId=$SUB_ID"

# ---------- 3) 피드 등록 (첨부 연결) ----------
FEED_PAYLOAD=$(python3 - "$USER_ID" "$SUB_ID" "$FILE_ID" "$STORED" "$ORIGIN" "$MARK" <<'EOF'
import json,sys
uid,sub,fid,stored,origin,mark=sys.argv[1:7]
att={"fileId":int(fid),"storedPath":stored,"originName":origin or "sweep.png"}
print(json.dumps({
  "userId":int(uid),
  "productId":None,
  "productNameCustom":f"sweep-test",
  "subCategoryId":int(sub),
  "review":f"{mark} - 자동 생성 테스트 게시글입니다.",
  "buyPlace":"api-sweep",
  "buyPrice":None,"price":None,
  "evaluation":"GOOD",
  "thumbnailAttachmentInfo":att,
  "attachmentFileInfos":[att],
  "hashtags":["sweeptest"]
}, ensure_ascii=False))
EOF
)
R=$(curl -sk -w "\n%{http_code}\t%{time_total}" "${auth[@]}" \
    -H "Content-Type: application/json" -d "$FEED_PAYLOAD" "$BASE/api/content/feeds")
BODY=$(echo "$R" | sed '$d'); META=$(echo "$R" | tail -1)
CODE=${META%%$'\t'*}
rec create-feed "$CODE" "${META#*$'\t'}" "$(echo "$BODY" | head -c 120)"
[ "$CODE" = "200" ] || { say 3 "피드 등록 실패($CODE): $BODY"; exit 1; }
FEED_ID=$(echo "$BODY" | json_get feedId)
say 3 "피드 등록 성공: feedId=$FEED_ID"

# ---------- 4) 댓글 등록 → content→Kafka→chat→FCM 비동기 경로 발화 (N1) ----------
R=$(curl -sk -w "\n%{http_code}\t%{time_total}" "${auth[@]}" \
    -H "Content-Type: application/json" \
    -d "{\"content\":\"$MARK - 자동 생성 테스트 댓글\"}" \
    "$BASE/api/content/feeds/$FEED_ID/comments")
BODY=$(echo "$R" | sed '$d'); META=$(echo "$R" | tail -1)
CODE=${META%%$'\t'*}
rec create-comment "$CODE" "${META#*$'\t'}" "$(echo "$BODY" | head -c 120)"
say 4 "댓글 등록: $CODE (이 요청이 N1 트레이스를 생성함: content→Kafka→chat→FCM)"

# ---------- 5) 읽기 왕복 확인 ----------
for p in "/api/content/feeds/$FEED_ID" "/api/content/feeds/$FEED_ID/comments?page=0&size=5" "/api/auth/files/download/$FILE_ID"; do
  R=$(curl -sk -o /dev/null -w "%{http_code}\t%{time_total}" "${auth[@]}" "$BASE$p")
  rec "readback $p" "${R%%$'\t'*}" "${R#*$'\t'}" ""
done

echo
echo "== 결과: $OUT =="
column -t -s$'\t' "$OUT"
echo
echo "생성물: fileId=$FILE_ID feedId=$FEED_ID (표시 텍스트: '$MARK')"
echo "다음: Grafana Tempo에서 방금 시각의 'http post /feeds/{feedId}/comments' 트레이스를 찾아"
echo "      rca-agent 리뷰 모드(/investigate mode=review)에 넣으면 N1 흐름 분석이 재현된다."
