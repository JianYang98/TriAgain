0721



---
0714

# 꼭 참고 사항 triagain-back/load-test/CREW-RUSH-DOCKER-환경-셋업-런북.md


# 0.1  caffeinate -i
ßß
# 0 빌드하기

cd /Users/jian/Projects/triagain/triagain-back

# 브랜치 확인 — 반드시 feat/load-test!
git switch feat/load-test
git status

1) 빌드

docker buildx build \
  --platform linux/amd64 \
  -t devjian/triagain:loadtest \
  --load \
  --progress=plain .

2)
docker buildx build --platform linux/amd64 -t devjian/triagain:loadtest --push .


 docker ps                                   # triagain 프로드 컨테이너 살아있는지 확인
  docker stop triagain-loadtest && docker rm triagain-loadtest

# 1. 이미지 받기
docker pull devjian/triagain:loadtest

# 2. 혹시 남은 컨테이너 제거 (처음이라 없겠지만 습관)
docker rm -f triagain-loadtest 2>/dev/null || true

# 3. 기동 — PESSIMISTIC + accept-count 256 (07-13 결정 반영)
docker run -d --name triagain-loadtest \
  -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://triagain-test-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432/triagain" \
  -e DB_USERNAME="triagain" \
  -e DB_PASSWORD="da6412^^Adb" \
  -e JWT_SECRET="dGVzdC1zZWNyZXQta2V5LWZvci1sb2NhbC1kZXZlbG9wbWVudC1vbmx5LXRyaWFnYWlu" \
  devjian/triagain:loadtest \
  --spring.profiles.active=prod,loadtest \
  --triagain.crew.lock-strategy=PESSIMISTIC \
  --server.tomcat.accept-count=256 \
  '--spring.flyway.ignore-migration-patterns=*:missing,*:future'

# G1
docker run -d --name triagain-loadtest \
  -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://triagain-test-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432/triagain" \
  -e JAVA_TOOL_OPTIONS="-XX:+UseG1GC" \
  -e DB_USERNAME="triagain" \
  -e DB_PASSWORD="da6412^^Adb" \
  -e JWT_SECRET="dGVzdC1zZWNyZXQta2V5LWZvci1sb2NhbC1kZXZlbG9wbWVudC1vbmx5LXRyaWFnYWlu" \
  devjian/triagain:loadtest \
  --spring.profiles.active=prod,loadtest \
  --triagain.crew.lock-strategy=PESSIMISTIC \
  --server.tomcat.accept-count=256 \
  '--spring.flyway.ignore-migration-patterns=*:missing,*:future'

# 3. 기동 — CONDITIONAL + accept-count 256 (07-13 결정 반영)
docker run -d --name triagain-loadtest \
  -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://triagain-test-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432/triagain" \
  -e DB_USERNAME="triagain" \
  -e DB_PASSWORD="da6412^^Adb" \
  -e JWT_SECRET="dGVzdC1zZWNyZXQta2V5LWZvci1sb2NhbC1kZXZlbG9wbWVudC1vbmx5LXRyaWFnYWlu" \
  devjian/triagain:loadtest \
  --spring.profiles.active=prod,loadtest \
  --triagain.crew.lock-strategy=CONDITIONAL \
  --server.tomcat.accept-count=256 \
  '--spring.flyway.ignore-migration-patterns=*:missing,*:future'

# G1
docker run -d --name triagain-loadtest \
  -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://triagain-test-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432/triagain" \
  -e JAVA_TOOL_OPTIONS="-XX:+UseG1GC" \
  -e DB_USERNAME="triagain" \
  -e DB_PASSWORD="da6412^^Adb" \
  -e JWT_SECRET="dGVzdC1zZWNyZXQta2V5LWZvci1sb2NhbC1kZXZlbG9wbWVudC1vbmx5LXRyaWFnYWlu" \
  devjian/triagain:loadtest \
  --spring.profiles.active=prod,loadtest \
  --triagain.crew.lock-strategy=CONDITIONAL \
  --server.tomcat.accept-count=256 \
  '--spring.flyway.ignore-migration-patterns=*:missing,*:future'



# 3-1 로그 확인
 docker logs -f triagain-loadtest

  export DB_URL="jdbc:postgresql://triagain-test-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432/triagain"
  export DB_USERNAME="triagain"
  export DB_PASSWORD="da6412^^Adb"
  export JWT_SECRET="bG9hZHRlc3Qtand0LXNlY3JldC1rZXktMjAyNi10cmlhZ2Fpbg=="   # ← base64로 교체
  export APPLE_REFRESH_KEY="dGVzdC1rZXktMzItYnl0ZXMtZm9yLWFlcy0yNTYtISE="
  export INTERNAL_API_KEY="loadtest-internal-key"

  export PGHOST=triagain-test-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com
  export PGPORT=5432
  export PGUSER=triagain
  export PGDATABASE=triagain
  export PGPASSWORD='da6412^^Adb'

  psql -c "SELECT 1;"          # 1 나오면 접속 OK
  psql -f 07_rush_reset.sql    # 그다음 리셋
   psql -c "SELECT id, max_members, current_members FROM crews WHERE id='loadtest-rush-crew-1';"



// 나중에 G1 GC
  -e JAVA_TOOL_OPTIONS="-XX:+UseG1GC" \    # ← 이 한 줄이 G1 스위치



# 4. 확인 (기동 15초쯤 기다렸다가)
curl -f http://localhost:8080/actuator/health
docker logs triagain-loadtest 2>&1 | grep -iE "lock-strategy|Started"


##

// 발급하기!

 /Users/jian/Projects/triagain/triagain-back/load-test && ./scripts/generate-tokens.sh http://13.125.7.72:8080 800


# 모니터링!
./start-monitoring.sh 13.125.7.72



---이거--
핵심 변경은 딱 하나예요: 세 채널 전부 컨테이너 netns 안에서 재야 합니다. 이제 8080 리스너(backlog 256짜리)는 컨테이너 안에 살고, 호스트에서 그냥 재면 docker-proxy/DNAT 층이 섞여서 다른 걸 재게 돼요. 방법은 기존 명령 앞에 sudo nsenter -t $CPID -n만 붙이는 겁니다.

0) 공통 준비 — CPID 캡처 (모든 채널이 이걸 씀)

CPID=$(docker inspect -f '{{.State.Pid}}' triagain-loadtest)
echo "container pid: $CPID"
command -v tcpdump || sudo yum install -y tcpdump   # 새 박스라 설치 확인 1회

1) nstat 와이드 시계열 (1초, 주력)

CPID=$(docker inspect -f '{{.State.Pid}}' triagain-loadtest)
LOG="$HOME/nstat-ts-$(date
echo "logging to: $LOG (netns pid=$CPID)"

while true; do
  ts=$(date '+%H:%M:%S')
  [ -d /proc/$CPID ] || { echo "$ts !! CPID 사망 — 컨테이너 재생성됨, 루프 재시작
필요"; break; }
  sudo nsenter -t "$CPID" -n nstat -asz 2>/dev/null | awk -v t="$ts" '
    /ListenOverflow|ListenDncookie|BacklogDrop|OutRsts|AttemptFails|EstabResets|AbortOn|MemoryPressure/ {
      print t, $1, $2
      fflush()
    }'
  sleep 1
done | tee -a "$LOG"

2) ss 빠른 상태 (0.3초, 보

CPID=$(docker inspect -f '{{.State.Pid}}' triagain-loadtest)
SSLOG=~/ss-state-$(date +%H%M%S).log
echo "logging to: $SSLOG"
while true; do
  ts=$(date '+%H:%M:%S')
  [ -d /proc/$CPID ] || { echo "$ts !! CPID 사망(컨테이너 재기동됨)"; break; }
  q=$(sudo nsenter -t "$CPID" -n ss -ltnH 'sport = :8080' | awk '{print "acceptQ="$2"/"$3; exit}')
  st=$(sudo nsenter -t "$CPID" -n ss -tanH 'sport = :8080' | awk '{c[$1]++} END{for(s in c) printf "%s=%d ", s, c[s]}')
  echo "$ts $q $st"
  sleep 1
done | tee "$SSLOG"

////



nsenter fork 비용 때문에 반  합쳤어요 (vu700 때 모니터자체가 CPU 먹으면 안 되니까):

CPID=$(docker inspect -f '{{.State.Pid}}' triagain-loadtest)
SSLOG=~/ss-state-$(date +%H
while true; do
  ts=$(date '+%H:%M:%S')
  [ -d /proc/$CPID ] || { echo "$ts !! CPID 사망"; break; }
  out=$(sudo nsenter -t "$C
    "ss -lnt 'sport = :8080' | awk 'NR==2{print \"acceptQ=\"\$2\"/\"\$3}'; \
     ss -tan 'sport = :8080D{for(s in c)printf \"%s=%d\",s,c[s]}'")
  echo "$ts $out"; sleep 0.
done | tee "$SSLOG"

읽는 법은 동일한데 acceptQ가 이제 x/256으로 나옵니다 (아까 실측 확인한 그 값 —
기존 세션의 /100, /256과 같

3) tcpdump — 이것도 netns

호스트에서 -i any로 뜨면 같ns5에서 한 번(DNAT 전,목적지=호스트IP), veth에서 한 번(DNAT 후, 목적지=컨테이너IP). ISN 불변식·RST
카운트가 전부 2배로 깨집니  면 앱 스택이 실제로 보는패킷만 잡혀서 기존 C11/C12/A5 분석과 같은 축이 유지돼요:

CPID=$(docker inspect -f '{{.State.Pid}}' triagain-loadtest)
sudo nsenter -t "$CPID" -n tcpdump-$(date +%H%M%S).pcap \
  'tcp port 8080 and (tcp[tt) != 0)' \
  > ~/tcpdump-startup.log 2>&1 &
echo $! > ~/tcpdump.pid

(-w ~/... 경로는 그대로 호 ter -n은 네트워크만 바꾸고파일시스템은 호스트 그대로라서요. 종료는 기존처럼 sudo kill $(cat ~/tcpdump.pid).)

 v

# docekr 로그
docker logs triagain-loadtest > ~/boot-g1-pessimistic-날짜.log 2>&1


docker logs triagain-loadtest > ~/boot-g1-CONDITIONAL-A11A12-0714.log 2>&1


// 실시간
docker logs -f triagain-loadtest > ~/server-boot-pessimistic-C17C18-0714.log


 v1. 제일 확실 — JVM한테 직접 물어보기:
bashdocker exec triagain-loadtest sh -c 'java -XX:+PrintFlagsFinal -version 2>/dev/null | grep -E "Use.*GC.*true|MaxHeapSize|InitialHeapSize"'
UseSerialGC = true든 UseG1GC = true든 true인 놈이 현재 선택된 GC야. MaxHeapSize도 같이 나와서 힙 크기까지 확인돼.
⚠️ 단, 이건 "새 java 프로세스를 같은 환경에서 띄워본 것"이라 이론상 100% 동일 보장은 아니야. 돌고 있는 바로 그 프로세스를 보려면:

2. 실행 중인 JVM 직접 조회 (jcmd/jinfo가 이미지에 있다면):
bashdocker exec triagain-loadtest sh -c 'jcmd 1 VM.flags 2>/dev/null || jinfo -flags 1 2>/dev/null' | tr ' ' '\n' | grep -iE 'gc|heap'
JRE-alpine이라 jcmd가 없을 수도 있어 — 그럼 1번 결과로 충분해.
3. Prometheus 메트릭으로 (loadtest 프로파일 덕에 이미 노출 중!):

bashcurl -s http://localhost:8080/actuator/prometheus | grep 'jvm_gc' | grep -oP 'gc="[^"]+"' | sort -u
gc="Copy"/gc="MarkSweepCompact" → SerialGC, gc="G1 Young Generation" → G1. 이건 진짜 돌고 있는 프로세스의 실측이라 교차검증으로 딱이야.
