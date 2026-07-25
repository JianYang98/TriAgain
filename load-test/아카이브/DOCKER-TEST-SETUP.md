
Docker 이미지를 다른 머신으로 옮기는 길은 두 가지예요:

[정석]  내 맥 → docker push → Docker Hub → docker pull → EC2
[우회]  내 맥 → docker save → 파일(tgz) → 파일질라/scp → docker load → EC2

Hub가 오늘처럼 말썽이면 우회로를 쓰는 건데, 핵심은 "이미지를 파일 하나로 얼려서 → 직접 나르고 → 다시 녹이는" 흐름입니다.

1. tgz 만들기 (내 맥에서)

docker save devjian/triagain:loadtest | gzip > ~/Downloads/triagain-loadtest.tgz

- docker save: 로컬 Docker 저장소에 있는 이미지를 tar 아카이브로 직렬화해서 표준출력으로 쏟아냄. 이미지의 모든 레이어 + 메타데이터(엔트리포인트, ENV, 태그)가 전부 담겨요. 그래서 load하면 태그까지 그대로 복원됩니다.
- | gzip >: save 출력은 압축이 안 된 생 tar라서 gzip으로 압축해서 파일로 저장. (우리 경우 192MB → 182MB밖에 안 줄었는데, jar가 이미 zip 압축이라 그래요. 그래도 관례상 압축함)
- ⚠️ 헷갈리기 쉬운 짝: docker save/load는 이미지용, docker export/import는 컨테이너용이에요. 이미지 옮길 땐 항상 save/load.

2. EC2에서 각 명령의 이유

docker load < ~/triagain-loadtest.tgz
"얼린 이미지를 녹여서 Docker 저장소에 등록". tar를 읽어 레이어들을 /var/lib/docker/ 아래에 풀고, devjian/triagain:loadtest라는 태그까지 복원해요. gzip은 load가 알아서 풀어주니 미리 gunzip 할 필요 없음. 이거 없이 tgz만 갖고 있으면 Docker는 그 이미지의 존재를 몰라서 docker run 못 합니다.

docker images
load가 진짜 됐는지 눈으로 검증. devjian/triagain  loadtest  ...  192MB 줄이 보여야 성공이에요. 확인 포인트는 ① 태그가 loadtest인가(⛔ latest 아님!) ② 크기가 ~192MB로 맞나. 전송 중 파일이 깨졌으면 load 단계에서 에러가 나므로, 여기까지 통과하면 무결성도 사실상 확인된 것.

rm ~/triagain-loadtest.tgz
load가 끝나면 tgz는 임무 종료 — 같은 데이터가 두 벌 있는 상태라서요. 레이어들은 이미 /var/lib/docker/에 복사됐고, 홈 디렉토리의 182MB tgz는 그냥 잉여 사본이에요. t3.micro는 루트 볼륨이 보통 8GB뿐이라 (프로드 이미지 + loadtest 이미지 + 로그까지 쌓이면) 182MB도 아까워서 지우는 겁니다. 단, load 성공 확인(docker images) 후에 지워야 해요 — 순서가 뒤집히면 다시 업로드해야 하니까.

보너스: 나중에 혼자 할 때 체크리스트

1. 맥: docker images로 옮길 이미지·태그 확인
2. 맥: docker save <이미지:태그> | gzip > 파일.tgz
3. 전송 (파일질라 SFTP / scp -i 키.pem 파일.tgz ec2-user@IP:~)
4. EC2: docker load < 파일.tgz → docker images 확인
5. EC2: rm 파일.tgz
