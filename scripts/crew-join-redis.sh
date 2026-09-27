#!/usr/bin/env bash
# Redis 선착순 가입 실험 준비 도구 (로컬 전용, public API 아님, DB 쓰기 없음)
#
#   init      <namespace> <runId> <crewId>...  DB snapshot → 크루별 Redis 상태 생성 → 직후 pre-flight
#   preflight <namespace> <runId> <crewId>...  연결 → 크루별 준비 상태 → 새 run pending 비어 있음 확인
#   cleanup   <namespace> <runId>              해당 run prefix의 key만 삭제 (FLUSHDB 안 씀)
#
# init이 여러 크루 중 중간에 실패하면 앞 크루는 초기화된 채 남는다 — 해당 run을 cleanup하고 다시 준비한다.
# 요청을 멈춘 상태에서만 실행한다. DB/Redis 접근 명령은 환경변수로 바꿀 수 있다:
#   PSQL      (기본: docker compose exec -T postgres psql -U triagain -d triagain)
#   REDIS_CLI (기본: docker compose exec -T redis redis-cli)
# 종료 코드: 0 성공 / 1 사용법·입력 / 2 CONNECTION / 3 NOT_INITIALIZED / 4 INVALID_STATE / 5 DB snapshot 불일치
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INIT_LUA="$SCRIPT_DIR/../src/main/resources/redis/crew-join/initialize-crew-join.lua"
PSQL="${PSQL:-docker compose exec -T postgres psql -U triagain -d triagain}"
REDIS_CLI="${REDIS_CLI:-docker compose exec -T redis redis-cli}"

die() { echo "$2" >&2; exit "$1"; }
# stdin을 닫는다 — docker exec -T가 호출자의 stdin(예: cleanup의 while read 입력)을 먹지 않게
redis() { $REDIS_CLI "$@" </dev/null; }
key_part() { [[ "$1" =~ ^[A-Za-z0-9_-]+$ ]] || die 1 "invalid $2: '$1' (allowed [A-Za-z0-9_-]+)"; }

# 크루 1건의 일관된 읽기 snapshot (단일 SELECT) → "max|current|count|distinct|json"
# json 배열은 user_id 바이트 순서(COLLATE "C")로 결정적이다 — score 1..N 배치 재현용. Lua는 순서를 검사하지 않는다
snapshot() {
	$PSQL -X -q -At -F '|' -v ON_ERROR_STOP=1 -v crew="$1" <<'SQL'
SELECT c.max_members,
       c.current_members,
       count(m.user_id),
       count(DISTINCT m.user_id),
       coalesce(json_agg(m.user_id ORDER BY m.user_id COLLATE "C") FILTER (WHERE m.user_id IS NOT NULL), '[]')
FROM crews c
LEFT JOIN crew_members m
   ON m.crew_id = c.id
WHERE c.id = :'crew'
GROUP BY c.id, c.max_members, c.current_members;
SQL
}

# snapshot을 읽고 검증해 전역 CAP/N/USERS를 채운다
load_snapshot() {
	local row max current count distinct
	row="$(snapshot "$1")" || die 2 "CONNECTION: DB snapshot failed for crew $1"
	[[ -n "$row" ]] || die 5 "SNAPSHOT: crew $1 not found in DB"
	IFS='|' read -r max current count distinct USERS <<<"$row"
	[[ "$count" == "$current" ]] || die 5 "SNAPSHOT: crew $1 members=$count != current_members=$current"
	[[ "$count" == "$distinct" ]] || die 5 "SNAPSHOT: crew $1 has duplicate userId"
	(( count >= 1 && count <= max )) || die 5 "SNAPSHOT: crew $1 N=$count outside 1..max_members=$max"
	CAP="$max"; N="$count"
}

check_connection() {
	[[ "$(redis PING 2>/dev/null)" == "PONG" ]] || die 2 "CONNECTION: Redis PING failed"
}

# 크루 1건 준비 상태 확인 (DB snapshot 대비)
check_crew() {
	local crew="$1" members="$PREFIX:crew:$1:members" meta="$PREFIX:crew:$1:meta"
	local mtype ztype expected actual
	load_snapshot "$crew"
	mtype="$(redis TYPE "$meta")"; ztype="$(redis TYPE "$members")"
	if [[ "$mtype" == none && "$ztype" == none ]]; then die 3 "NOT_INITIALIZED: crew $crew"; fi
	[[ "$mtype" == hash && "$ztype" == zset ]] || die 4 "INVALID_STATE: crew $crew meta=$mtype members=$ztype"
	[[ "$(redis HGET "$meta" initialized)" == 1 ]] || die 4 "INVALID_STATE: crew $crew initialized!=1"
	[[ "$(redis HGET "$meta" capacity)" == "$CAP" ]] || die 4 "INVALID_STATE: crew $crew capacity!=$CAP"
	[[ "$(redis HGET "$meta" seq)" == "$N" ]] || die 4 "INVALID_STATE: crew $crew seq!=$N"
	# members 집합·수·score 1..N을 한 번에: DB 정렬 순서의 (userId, i) 목록과 ZSET 전체가 같아야 한다
	expected="$(jq -r 'to_entries[] | .value, (.key + 1 | tostring)' <<<"$USERS")"
	actual="$(redis ZRANGE "$members" 0 -1 WITHSCORES)"
	[[ "$actual" == "$expected" ]] || die 4 "INVALID_STATE: crew $crew members/scores differ from DB snapshot"
	echo "ok crew=$crew N=$N capacity=$CAP"
}

check_pending_empty() {
	local ptype
	ptype="$(redis TYPE "$PREFIX:pending")"
	[[ "$ptype" == none || "$ptype" == list ]] || die 4 "INVALID_STATE: pending type=$ptype"
	[[ "$(redis LLEN "$PREFIX:pending")" == 0 ]] || die 4 "INVALID_STATE: pending not empty for new run"
	echo "ok pending empty"
}

cmd="${1:-}"; [[ $# -ge 3 ]] || die 1 "usage: $0 init|preflight <namespace> <runId> <crewId>... | cleanup <namespace> <runId>"
key_part "$2" namespace; key_part "$3" runId
PREFIX="triagain:crew-join:{$2:$3}"
shift 3

case "$cmd" in
	init)
		[[ $# -ge 1 ]] || die 1 "init needs crewId"
		check_connection
		check_pending_empty  # 재사용 run이면 첫 쓰기 전에 거부한다 (끝의 확인은 init 이후 상태용으로 유지)
		script="$(cat "$INIT_LUA")"
		for crew in "$@"; do
			load_snapshot "$crew"
			result="$(redis EVAL "$script" 2 "$PREFIX:crew:$crew:members" "$PREFIX:crew:$crew:meta" "$CAP" "$USERS")"
			[[ "$result" == OK ]] || die 4 "init crew $crew: $result (stopped; earlier crews stay initialized)"
			check_crew "$crew"
		done
		check_pending_empty
		;;
	preflight)
		[[ $# -ge 1 ]] || die 1 "preflight needs crewId"
		check_connection
		for crew in "$@"; do check_crew "$crew"; done
		check_pending_empty
		;;
	cleanup)
		check_connection
		keys="$(redis --scan --pattern "$PREFIX:*")"
		[[ -n "$keys" ]] || { echo "no keys for $PREFIX"; exit 0; }
		echo "$keys" | while IFS= read -r key; do echo "DEL $key -> $(redis DEL "$key")"; done
		;;
	*) die 1 "unknown command: $cmd" ;;
esac
