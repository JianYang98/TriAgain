# 부하테스트 지표 수동 수집기

## 개요

부하테스트를 할 때 앱 EC2에서 지표를 수집하는 도구이다.  
앱 EC2는 측정 대상 앱이 도는 서버(운영 EC2 또는 테스트 EC2)이고, k6를 돌리는 러너 EC2와 구분한다.

앱 EC2에서 collect on/off 명령으로 수집기를 켜고 끈다.  
사용할 실제 앱 컨테이너·포트는 실행 전에 확인해야 한다.  
JFR 코드는 포함돼 있으며 도구 준비 후 선택할 수 있다.

수집 항목은 mpstat·vmstat·ss·nstat·tcpdump·JFR·Docker 앱 로그다.

소스 파일은 collect, manual_collect.py, manual_collectors.py, manual_network.py, manual_diagnostics.py다.  
각 명령은 Python 3와 Linux /proc를 사용한다.  
mpstat와 vmstat는 실행 호스트에 설치돼 있어야 한다.

## 앱 EC2에서 사용할 명령

앱 EC2의 ~/load-test/collectors/에 파일을 배치한다.  
ON/OFF는 관측 대상 앱 EC2에서 실행한다.  
먼저 실행 중인 앱 컨테이너 이름을 확인한다.

```sh
docker ps --format 'table {{.Names}}\t{{.Status}}'
```

아래 예시의 triagain-app을 확인한 컨테이너 이름으로 바꾼다.  
RUN_ID는 사용자가 지정한다.

```sh
cd ~/load-test/collectors
./collect on --run-id prod-20261113-01 --container triagain-app
# 원하는 시점에 같은 폴더에서 실행한다.
./collect off
```

컨테이너 이름으로 ID·프로세스·시작 시각을 확인하고 기록한다.  
mpstat·vmstat는 호스트를, ss·tcpdump는 컨테이너 네트워크를, nstat는 컨테이너와 호스트 양쪽을 관측한다.  
k6는 사용자가 별도로 실행하고 수집 종료 시점은 collect off가 결정한다.  
기본 ON은 mpstat·vmstat·ss·nstat·tcpdump·앱 로그를 선택한다.  
ON 출력에 FAIL이 있으면 일부 수집이 계속 켜져 있을 수 있다. 오류를 확인하고 OFF로 마무리한다.  
부하 없이 ON/OFF해도 수집 동작을 확인할 수 있다.

## 파일과 시각

ON 당시의 한국 날짜를 사용해 ~/load-test/test-results/YYYYMMDD/RUN_ID/를 생성한다.  
예를 들어 11월 13일 23:29에 tcp-check로 시작하면 다음 날 OFF해도
~/load-test/test-results/20261113/tcp-check/에서 마무리한다.  
RUN_ID는 날짜와 독립된 폴더 이름이며 공백·한글도 사용할 수 있다.  
경로 구분자·제어문자와 단독 . 또는 ..는 입력 오류로 처리한다.

- mpstat.log는 전체 평균·CPU별 1초 통계를 기록한다.
- vmstat.log는 헤더와 1초 통계를 기록한다. 첫 보고의 평균 통계는 이후 구간과 구분한다.
- 시각은 2026-10-14T18:42:38.463+09:00 형식이다. 줄을 읽은 시각을 표시한다.
- run.json은 RUN_ID·대상 이름·시작/종료 시각·프로세스 식별·결과를 보존한다.
- 각 항목의 .status.json과 .worker.log는 실행 상태와 오류 확인용이다.

활성 RUN은 ~/load-test/.collector-state/에 기록해 재접속 후 OFF에 사용한다.  
한 번에 한 RUN을 수집하며 기존 결과 폴더의 재사용을 거절한다.  
OFF는 기록된 PID의 Linux 시작 시각·명령·프로세스 그룹을 확인한 뒤 종료한다.  
종료가 확인되지 않으면 활성 정보를 유지하고 실패를 표시한다.  
OFF 결과에는 항목별 종료 상태와 결과 폴더 절대경로가 표시된다.

## OFF 후 파일 확인

OFF에 표시된 결과 폴더로 이동한다. 아래는 예시 경로다.

```sh
cd ~/load-test/test-results/20261113/prod-20261113-01
ls -lh
tail -n 5 mpstat.log
tail -n 5 vmstat.log
cat run.json
cat tcpdump.stderr.log
```


| 파일                                           | 확인할 내용                        |
| -------------------------------------------- | ----------------------------- |
| mpstat.log / vmstat.log                      | 한국 시각·RUN\_ID·통계 표본, 마지막 STOP |
| network-limits.txt                           | ON 당시 큐·커널 설정·소켓 상태           |
| ss-samples.jsonl                             | 큐·SYN-RECV 표본과 조회 returncode  |
| nstat-ts.jsonl                               | 관심 TCP 카운터·증분·표본 경과시간         |
| nstat-before.txt / nstat-after.txt           | 앱 netns의 전후 원문                |
| nstat-host-before.txt / nstat-host-after.txt | 호스트의 전후 원문                    |
| nstat-before.json / nstat-after.json         | 스냅샷 시각·원문·조회 상태               |
| tcp-delta.txt                                | 두 영역의 전체 카운터 전후 차이            |
| network.pcap / tcpdump.stderr.log            | 패킷과 캡처·누락 통계                  |
| app.log                                      | ON\~OFF 요청 구간 앱 stdout·stderr |
| run.json / \*.status.json / \*.worker.log    | 실행 정보·종료 상태·오류 출력             |


app.log가 0바이트라도 해당 구간에 앱 출력이 없었다면 정상이다.  
STOP exit=-15는 SIGTERM으로 해당 명령을 종료했다는 의미다.  
stopped는 수집 종료, saved는 구간 자료 저장 결과다.  
stopped-with-errors·stop-incomplete·FAIL은 항목별 원인을 확인한다.  
worker.log는 별도 오류가 없으면 비어 있을 수 있다.  
파일 존재와 종료 성공만으로 부하 구간 전체의 수집 완전성을 판정하지 않는다.

## Mac에서 결과 가져오기

Mac 터미널에서 운영 또는 테스트 SSH 호스트와 실제 OFF 경로를 지정한다.  
APP_HOST는 SSH config의 별칭 또는 ec2-user@주소다.  
키 인증에 별도 파일이 필요하면 scp에 -i /실제/키경로.pem 옵션을 추가한다.  
아래 실행 이름·날짜·호스트를 실제 값으로 바꾼다.

```sh
RUN_ID="prod-20261113-01"
APP_HOST="ec2-user@실제앱주소"
APP_OUT="/home/ec2-user/load-test/test-results/20261113/$RUN_ID"

mkdir "./$RUN_ID" && scp -r "${APP_HOST}:${APP_OUT}" "./$RUN_ID/app"
```

새 Mac 폴더의 app/ 아래에 이번 수집 파일이 놓인다.  
러너 EC2의 k6 결과는 사용자가 별도로 같은 Mac RUN 폴더의 k6/에 복사한다.  
서버의 결과 원본과 다운로드한 사본은 각각 보관한다.

## 수집기 추가

manual_collectors.py의 COLLECTORS에 이름·command·filename·stamp_all을 등록한다.  
이 연결 방식은 stdout에 주기적으로 출력하고 SIGTERM으로 종료하는 명령에 적용한다.  
JFR recording과 OFF 시점의 앱 로그 추출은 각 수명에 맞는 추가 연결이 필요하다.  
시간·날짜·RUN 경로·프로세스 관리는 manual_collect.py가 담당한다.  
반복 조회 방식은 kind에 작업 함수를 연결하고, recording·구간 추출은 ON/OFF lifecycle에 연결한다.

## 네트워크·JFR·앱 로그

기본값은 --port 8080, --ss-interval 0.3, --nstat-interval 1이다.  
네트워크 조회와 캡처에는 호스트 도구 ss·nstat·tcpdump·nsenter 및 sudo -n 권한이 필요하다.  
ON은 network-limits.txt에 큐·커널 설정을, nstat-before.txt와 nstat-host-before.txt에 원시 카운터를 남긴다.  
OFF는 종료 스냅샷과 tcp-delta.txt에 전체 카운터 전후 차이를 보존한다.  
각 JSONL의 timestamp는 한국 시각이다. nstat 실패·누락·reset은 delta null로 표시한다.

tcpdump는 앱 netns의 any 인터페이스에서 양방향 대상 TCP 포트를 캡처한다.  
--peer-ip를 주면 해당 주소로 좁힌다.  
기본 --snaplen 128, --pcap-max-mb 128(십진 MB)이다.  
network.pcap은 한도 도달 시 추가 캡처를 종료하고 마지막 버퍼까지만 초과 저장할 수 있다.  
capture-limit-reached는 ON/OFF 전체 구간 확보가 제한됐음을 뜻한다.  
잘린 패킷에는 일부 애플리케이션 데이터도 포함될 수 있다.  
캡처 통계는 tcpdump.stderr.log에 보존한다.

JFR은 컨테이너 안 jcmd로 제어한다. --jcmd로 실행 경로를 지정할 수 있다.  
기본 --jvm-pid 1, --jfr-settings default, --jfr-max-mb 64다.  
설정 default는 JDK 제공 설정이다.  
maxsize는 recording의 보존 한도여서 긴 기록의 앞부분이 제한될 수 있다.  
이번 RUN 전용 recording 이름을 만들고 OFF에서 종료해 recording.jfr로 복사한다.  
--only는 수집 목록을 바꿀 때만 지정한다. JFR은 도구를 준비한 후 --only 목록에 jfr을 포함해 선택한다.  
jfr을 명시했는데 jcmd가 없으면 JFR 시작 실패를 표시하고 나머지 항목을 계속 시작한다.  
그 경우 ON 반환코드는 2이며 다른 수집은 계속되므로 반드시 collect off로 종료한다.

OFF는 ON~OFF 요청 시각의 stdout·stderr를 Docker timestamp와 함께 app.log에 저장한다.  
조회 실패는 실패 파일을 보존하고 활성 RUN을 유지해 OFF를 다시 시도할 수 있다.  
수집 프로세스가 모두 끝났으나 자료가 불완전하면 stopped-with-errors로 기록한다.

## 코드 구조 (코드 읽기용)

위 절들은 사용법이고, 이 절은 코드를 읽을 때 쓰는 지도다. 함수별 세부는 각 docstring에 있다.

### 파일별 책임


| 파일                     | 책임                                                                                   |
| ---------------------- | ------------------------------------------------------------------------------------ |
| collect                | `python3 manual_collect.py "$@"` 실행만 한다                                              |
| manual\_collect.py     | CLI(on·off·\_\_worker), 락, RUN 폴더·시각(now), run.json·active.json, 워커 생성·정지, ON/OFF 순서 |
| manual\_collectors.py  | 워커로 도는 항목 정의(COLLECTORS)와 워커 없는 항목 이름(LIFECYCLE\_COLLECTORS)                         |
| manual\_network.py     | ss·nstat 반복 조회(run\_ss·run\_nstat), nstat 전후 스냅샷, app.log 추출                         |
| manual\_diagnostics.py | 명령 실행 래퍼(command), 컨테이너 동일성 확인, network-limits.txt, JFR 시작·종료, tcpdump 캡처            |


common.sh·mpstat-run.sh·vmstat-run.sh는 이 경로에서 호출되지 않는다.  
worker()의 command 방식이 common.sh stamp_run과 같은 규칙(LC_ALL=C·stdbuf·배너 호스트명 가림·데이터 줄 스탬프)을
파이썬으로 다시 구현했고, 줄 앞 시각 형식이 다르다(셸=UTC epoch ms, 여기=한국 시각 ISO).

### ON → OFF 흐름

```text
collect on   (락 보유, 워커를 띄우고 바로 반환)
 1 입력 검증: Linux·RUN_ID·active.json 없음·--only·옵션
 2 test-results/<ON 날짜>/<RUN_ID>/ 생성 (이미 있으면 실패)
 3 run.json(starting) + active.json 기록        ← 이 시점부터 OFF 대상
 4 docker inspect → 컨테이너 ID·PID·StartedAt·ticks 기록   [실패 → 롤백 후 오류]
 5 network-limits.txt · nstat-before · JFR 시작            [실패 → errors에 적고 계속]
 6 항목마다 launch_worker → status=running 최대 5초 대기     [실패 → errors에 적고 계속]
 7 state = running | running-partial, 반환 0 | 2

collect off  (락 보유)
 1 off_requested_at 기록 (재시도해도 첫 값 유지)
 2 nstat-after 스냅샷 · tcp-delta.txt          ← 워커 정지 전, 파일이 있으면 건너뜀
 3 stop_workers: 항목마다 차례로 신원 확인 → 그룹 SIGTERM → 최대 10초 대기 → status 읽기
 4 JFR 정지·복사 · app.log 추출 (started_at ~ off_requested_at)
 5 최종 state 저장 → 워커 정지와 JFR·app-log 저장이 모두 확인되면 active.json 삭제
```

ON 4번부터의 예외 중 5·6번 항목별 오류가 아닌 것은 띄운 워커를 멈춘 뒤(롤백) 다시 던진다.  
이때 run["jfr"]가 있으면 active.json을 남겨 OFF가 JFR.stop을 시도하게 한다.  
OFF를 다시 실행하면 이미 있는 nstat-after.json·tcp-delta.txt·app.log와 saved 상태의 JFR은 건너뛴다.

### 워커 구조와 수집 방식

```text
collect on (python, 바로 끝남)
 └─ manual_collect.py __worker --name <항목>    start_new_session=True → 새 세션, pgid = 워커 pid
     └─ 측정 명령 (mpstat / vmstat / sudo nsenter … ss·nstat·tcpdump)   같은 프로세스 그룹
```

- ON은 워커를 기다리지 않는다. 워커는 새 세션이라 제어 터미널이 없어 SSH가 끊겨도 SIGHUP을 받지 않는다.
- OFF는 워커 그룹 전체에 SIGTERM을 보낸다. privileged 항목(ss·nstat·tcpdump)은 그룹에 sudo로 띄운
root 소유 자식이 있어 `sudo -n kill -TERM -- -<pgid>`를 쓴다.
- 워커는 SIGTERM을 받으면 플래그만 세우고, 수집을 마무리한 뒤 `<항목>.status.json`에 최종 state를 쓴다.


|         | command 방식                     | kind 방식                                                                     | lifecycle                                  |
| ------- | ------------------------------ | --------------------------------------------------------------------------- | ------------------------------------------ |
| 항목      | mpstat·vmstat                  | ss·nstat·tcpdump                                                            | jfr·app-log                                |
| 정의      | COLLECTORS의 command            | COLLECTORS의 kind → worker()의 functions                                      | LIFECYCLE\_COLLECTORS (워커 없음)              |
| 실행      | 장기 실행 명령 1개의 stdout을 줄마다 받아 기록 | 파이썬 루프가 간격마다 명령 실행. tcpdump는 장기 실행 1개 + 크기 한도                               | JFR은 ON 시작·OFF 정지·복사, app-log는 OFF에서 1회 추출 |
| 행 시각    | 줄을 읽은 시각                       | ss=두 조회 후, nstat=조회 전                                                       | —                                          |
| 컨테이너 확인 | 없음(호스트 관측)                     | 시작 시 same\_container(docker inspect), ss·nstat은 표본마다 \_check\_target(/proc) | stop\_jfr에서 same\_container                |
| 정상 종료   | 종료 요청 + exit 0·-15·-2          | 종료 요청 + 함수 정상 반환 (ss·nstat은 성공 표본 1개 이상, tcpdump는 exit 0·-15)               | OFF outcome saved·exists                   |


### 상태 파일과 결과 파일


| 파일                 | 위치                | 쓰는 쪽             | 읽는 쪽                      | 역할                                            |
| ------------------ | ----------------- | ---------------- | ------------------------- | --------------------------------------------- |
| lock               | .collector-state/ | on·off (flock)   | —                         | on/off를 한 번에 하나씩 실행(대기). 워커는 잡지 않는다           |
| active.json        | .collector-state/ | ON 생성·OFF 삭제     | ON(존재 확인)·OFF             | 활성 RUN의 결과 폴더 경로. 파일이 있으면 활성 RUN이 있다          |
| run.json           | 결과 폴더             | ON·OFF           | kind 워커(시작 시 1회)·OFF      | 옵션·컨테이너 신원·워커 pid/ticks·errors·outcomes·state |
| `<항목>.status.json` | 결과 폴더             | 워커               | ON(ready 확인)·OFF(outcome) | 워커 상태                                         |
| `<항목>.worker.log`  | 결과 폴더             | 워커 stdout·stderr | 사람                        | traceback 등                                   |
| jfr-stop.json      | 결과 폴더             | stop\_jfr        | 코드에서 읽지 않음                | JFR.stop 결과 기록                                |


- run.json state: starting → running | running-partial → stopped | stopped-with-errors | stop-incomplete
- status.json state: running → stopped | ended-unexpectedly | capture-limit-reached | failed
- 워커 항목의 OFF outcome: identity-mismatch·stop-failed·still-running 중 하나, 아니면 그룹이 사라진 뒤의
status.json state(파일이 없으면 ended-unexpectedly). 최종 status 없이 죽은 워커는 "running"이 남는다.


| 최종 state            | 조건                                            | active.json  |
| ------------------- | --------------------------------------------- | ------------ |
| stop-incomplete     | 워커 정지 미확인, 또는 JFR·app-log 저장 실패               | 유지 → OFF 재시도 |
| stopped             | errors 없음, outcome 전부 stopped·saved·exists    | 삭제           |
| stopped-with-errors | 그 밖 (capture-limit-reached·failed·nstat 오류 등) | 삭제           |


errors는 run.json에 쌓이고 OFF 재시도 때 지우지 않는다.

### 코드에서 확인되는 설계 의도

- 덮어쓰기 금지: 결과 파일은 "x"·"xb" 모드, 결과 폴더는 exist_ok=False로 만든다. RUN_ID 재사용과 같은 항목의 이중 시작이 실패로 드러난다.
- 원자 저장: save()는 임시 파일에 쓴 뒤 replace한다. 다른 프로세스가 반쯤 쓰인 JSON을 읽지 않는다.
- 접근 제한: umask 077·폴더 0700. pcap·앱 로그를 포함한 결과는 소유자만 읽는다.
- PID 신원 확인: pid에 더해 시작 ticks·프로세스 그룹·cmdline이 맞을 때만 신호를 보낸다. 맞지 않으면 신호 없이 active.json을 남긴다.
- 컨테이너 고정: ON에서 기록한 컨테이너 ID·PID·StartedAt·ticks와 다르면 kind 워커와 OFF의 nstat-after·JFR 단계가 오류로 처리한다. app.log 추출은 이 확인을 하지 않는다.
- 고정 간격: ss·nstat은 시작 시각 기준 눈금까지 자고, 밀리면 놓친 눈금을 몰아 찍지 않는다(지금 시각을 새 기준으로 삼는다).
- 실패도 행으로: 실패한 ss·nstat 표본도 returncode·stderr와 함께 남는다. nstat delta는 직전 행이 성공일 때만 숫자다.
- 호스트명 가림: mpstat·vmstat 배너의 호스트명을 (host)로 바꾼다. common.sh 주석에 적힌 이유는 EC2 호스트명에 사설 IP가 들어 있다는 것이다.
- nstat `-s`: history 파일을 건드리지 않는다(tcp-collect.sh 주석).
- 그 밖의 값(ss 0.3초·정지 대기 10초·ready 대기 5초 등)을 고른 이유는 코드·주석에 기록이 없다.

### 데이터 해석 주의

- 실제 표본 간격은 설정값보다 길 수 있다. ss는 표본마다 sudo 명령 2개를 실행한다. 행의 timestamp·elapsed_s로 본다.
- OFF는 항목을 하나씩 멈추므로 항목마다 마지막 표본 시각이 다르다.
- nstat-after 스냅샷은 워커를 멈추기 전에 찍는다.
- ss 행 시각은 조회 후, nstat 행 시각은 조회 전이다.
- OFF의 SIGTERM은 실행 중이던 ss·nstat 조회에도 가므로 마지막 행이 실패 행일 수 있다.

## 확인 범위

테스트 EC2에서 짧은 무부하 ON/OFF만 확인했다. 운영·부하 구간의 수집 완전성은 별도로 확인해야 한다.