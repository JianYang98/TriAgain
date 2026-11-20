#!/usr/bin/env bash
# N2 — nstat 전·후 스냅샷의 증가량 표 (measurement-contract §2-N). 앱 EC2·맥 어디서나.
set -euo pipefail
USAGE='사용법: tcp-delta.sh <run 폴더>
  읽기: nstat-before.txt·nstat-after.txt(컨테이너 netns) · nstat-host-before.txt·nstat-host-after.txt(호스트) · tcp-listen.log
  쓰기: <run 폴더>/tcp-delta.txt — 카운터별 "이름 before after delta [표시]", 1차 3개 → 참고 → 나머지 전체.
        표시: MISSING(한쪽에 없음) · DECREASED(after < before). 머리말에 관측한 Send-Q, 맨 아래 §2-N 한계 6개.
  종료 코드: 0 · 1 사용법 · 3 입력 파일 일부 없음(표는 있는 만큼 쓴다, 비차단)'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

[[ $# -eq 1 && -d "$1" ]] || usage
RUN_DIR=$1
OUT=$RUN_DIR/tcp-delta.txt
PRIMARY="TcpExtListenOverflows TcpExtListenDrops TcpRetransSegs"
REF="TcpExtTCPReqQFullDoCookies TcpExtTCPReqQFullDrop TcpExtTCPSynRetrans TcpAttemptFails TcpOutRsts TcpEstabResets TcpExtTCPBacklogDrop"
rc=0

table() { # $1 before $2 after — nstat 형식: "#kernel" 줄, 이후 "이름 값 비율"
	if [[ ! -f "$1" || ! -f "$2" ]]; then
		echo "(파일 없음: $(basename "$1") 또는 $(basename "$2"))"
		rc=3
		return 0
	fi
	awk -v bf="$1" -v primary="$PRIMARY" -v ref="$REF" '
		function row(k,   d, fl) {
			if ((k in b) && (k in a)) { d = sprintf("%.0f", a[k] - b[k]); fl = (a[k] - b[k] < 0) ? "DECREASED" : "" }
			else { d = "-"; fl = "MISSING" }
			printf "%-34s %20s %20s %14s %s\n", k, ((k in b) ? b[k] : "MISSING"), ((k in a) ? a[k] : "MISSING"), d, fl
		}
		/^#/ || NF < 2 { next }
		{ if (!($1 in seen)) { seen[$1] = 1; ord[++n] = $1 } }
		FILENAME == bf { b[$1] = $2; next }
		{ a[$1] = $2 }
		END {
			printf "%-34s %20s %20s %14s %s\n", "name", "before", "after", "delta", "flag"
			np = split(primary, P, " "); nr = split(ref, R, " ")
			print "### 1차 판정"; for (i = 1; i <= np; i++) { row(P[i]); done[P[i]] = 1 }
			print "### 참고 (과거 캡처키트 목록)"; for (i = 1; i <= nr; i++) { row(R[i]); done[R[i]] = 1 }
			print "### 나머지 전체"; for (i = 1; i <= n; i++) if (!(ord[i] in done)) row(ord[i])
		}' "$1" "$2"
}

listen_summary() { # tcp-listen.log: "<ms> <run-id> acceptQ=R/S|NA"
	local f=$RUN_DIR/tcp-listen.log
	[[ -f "$f" ]] || { echo "# listen: tcp-listen.log 없음"; rc=3; return 0; }
	awk '$3 ~ /^acceptQ=/ { n++; v = substr($3, 9); if (v == "NA") { na++; next }
	                         split(v, q, "/"); if (q[1] + 0 > mr) mr = q[1] + 0; if (!(q[2] in s)) { s[q[2]] = 1; sl = sl (sl == "" ? "" : ",") q[2] } }
	     END { printf "# listen (1초 표본): observed_send_q=%s max_recv_q=%d samples=%d na_samples=%d\n", (sl == "" ? "NA" : sl), mr, n, na }' "$f"
}

{
	echo "# tcp-delta — $(basename "$RUN_DIR") · 생성 ts_ms=$(ts_ms)"
	listen_summary
	echo "# Send-Q = 리슨 큐 한도(관측값). run card의 '관측한 리슨 큐 한도'에 옮긴다 — 값을 가정하지 않는다."
	echo
	echo "## 컨테이너 netns (nstat-before.txt → nstat-after.txt)"
	table "$RUN_DIR/nstat-before.txt" "$RUN_DIR/nstat-after.txt"
	echo
	echo "## 호스트 netns (nstat-host-before.txt → nstat-host-after.txt)"
	table "$RUN_DIR/nstat-host-before.txt" "$RUN_DIR/nstat-host-after.txt"
	echo
	cat <<'EOF'
## 한계 (measurement-contract.md §2-N — 결과 해석에 그대로 인용)
1. 누적 카운터의 run 전후 차이다. 같은 네임스페이스에서 그 시간창에 일어난 **모든** TCP 이벤트가 섞인다 (k6 요청뿐 아니라 Prometheus scrape, 폴러 연결, 헬스체크 등). 호스트 네임스페이스에서 읽으면 **호스트 전체**, 컨테이너 네임스페이스에서 읽으면 **그 컨테이너 네임스페이스 전체**이며, 어느 쪽도 "앱 요청만"이 아니다.
2. 특정 요청이나 원인과 1:1로 대응하지 않는다. 증가 시점은 run 전후로만 좁힐 수 있다.
3. `ss -lnt` 1초 샘플은 순간값이라 1초 미만의 큐 피크를 놓칠 수 있다 (과거 캡처키트는 0.3초 간격을 썼다).
4. 증가량 0은 TCP 연결 단계가 무문제라는 증명이 아니다.
5. 앱 EC2 시계 기준이다. 맥(k6)과의 정렬은 G6의 오프셋을 쓴다.
6. Redis 서버 박스의 TCP는 이번 범위 밖이다 (필요하면 같은 방식).
EOF
} >"$OUT"
echo "tcp-delta: $OUT"
exit "$rc"
