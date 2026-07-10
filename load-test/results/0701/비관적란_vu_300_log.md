running (0m00.9s), 000/200 VUs, 200 complete and 0 interrupted iterations
rush ✓ [=================================] 200 VUs  00.9s/30s  200/200 iters, 1 per VU
jian@jianui-MacBookPro load-test %
jian@jianui-MacBookPro load-test %
jian@jianui-MacBookPro load-test %
jian@jianui-MacBookPro load-test %
jian@jianui-MacBookPro load-test %  TAG=A_max10_vu300
  k6 run --env BASE_URL=http://15.164.69.243:8080 \
    --env TARGET_VUS=300 --env MAX_MEMBERS=10 \
    --env RUN_TAG=$TAG --out json=results/raw/crew-rush-jian_$TAG.json \
    k6/crew-rush-jian.js


         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: k6/crew-rush-jian.js
        output: json (results/raw/crew-rush-jian_A_max10_vu300.json)

     scenarios: (100.00%) 1 scenario, 300 max VUs, 1m0s max duration (incl. graceful stop):
              * rush: 1 iterations for each of 300 VUs (maxDuration: 30s, exec: rushExec, gracefulStop: 30s)

WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59777->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59717->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59754->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59735->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59773->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59766->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59734->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59769->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59725->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59762->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59758->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59760->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59726->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59772->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59770->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59765->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59727->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59761->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59753->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59771->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59743->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59759->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59733->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59764->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59750->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59757->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59778->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59744->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59746->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59767->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59776->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59751->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59732->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59742->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59745->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59737->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59749->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59747->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59779->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59740->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59708->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59763->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59728->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59729->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59731->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59775->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59741->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59704->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59697->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59730->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59736->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59768->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59738->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59748->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59720->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59755->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59702->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59705->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59739->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59706->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59701->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59696->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59700->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59774->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59723->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59703->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59709->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59714->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59719->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59756->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59721->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59718->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59698->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59713->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59710->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59715->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59711->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59716->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59722->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59724->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59712->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59707->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59804->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59691->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59795->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59690->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59803->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59802->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59687->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59685->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59695->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59692->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59688->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59683->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59699->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59693->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59689->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59694->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:59752->15.164.69.243:8080: read: connection reset by peer"
INFO[0001] [k6-reporter v2.3.0] Generating HTML summary report  source=console
      ✗ valid status (201/409)
       ↳  67% — ✓ 201 / ✗ 99
      ✓ no server error (5xx)
      ✗ no conn reset (status 0)
       ↳  67% — ✓ 201 / ✗ 99
      ✓ 409 has expected code

      checks.........................: 83.50% ✓ 1002       ✗ 198
      data_received..................: 81 kB  70 kB/s
      data_sent......................: 115 kB 100 kB/s
      http_req_blocked...............: avg=50.76ms  min=16.16ms  med=48.66ms  max=133.48ms p(90)=76.73ms  p(95)=87.16ms
      http_req_connecting............: avg=50.7ms   min=16.14ms  med=48.63ms  max=133.47ms p(90)=76.72ms  p(95)=87.07ms
      http_req_duration..............: avg=360.93ms min=58ms     med=303.99ms max=1.06s    p(90)=746.24ms p(95)=785.31ms
        { expected_response:true }...: avg=146.13ms min=109.58ms med=143.12ms max=189.38ms p(90)=174.73ms p(95)=182.06ms
      http_req_failed................: 96.66% ✓ 290        ✗ 10
      http_req_receiving.............: avg=259.72µs min=0s       med=29µs     max=11.15ms  p(90)=93.7µs   p(95)=549.05µs
      http_req_sending...............: avg=8.96µs   min=4µs      med=8µs      max=54µs     p(90)=12µs     p(95)=15µs
      http_req_tls_handshaking.......: avg=0s       min=0s       med=0s       max=0s       p(90)=0s       p(95)=0s
      http_req_waiting...............: avg=360.67ms min=57.99ms  med=301.91ms max=1.06s    p(90)=744.85ms p(95)=782.57ms
      http_reqs......................: 300    260.944672/s
      iteration_duration.............: avg=411.96ms min=126.89ms med=346.04ms max=1.14s    p(90)=795.84ms p(95)=837.17ms
      iterations.....................: 300    260.944672/s
    ✓ join_5xx.......................: 0      0/s
    ✗ join_dropped...................: 99     86.111742/s
      join_full......................: 191    166.134774/s
    ✓ join_success...................: 10     8.698156/s
      scenario_d_duration............: avg=360.93ms min=58ms     med=303.99ms max=1.06s    p(90)=746.24ms p(95)=785.31ms
      vus............................: 1      min=1        max=1
      vus_max........................: 300    min=300      max=300
=========
 🏁 RUSH  A_max10_vu300
    정원 10명 · 동시참가 300 VU

  성공_success: 10
  정원초과_full: 191
  충돌_conflict: 0
  5xx_err5xx: 0
  드롭_dropped_연결실패: 99
  기타_other: 0
  p95: 785.3 ms
  ─────────────────────────
  판정: ❌ FAIL  (정합성 ✗ · 충돌 0 · p95 785.3ms)


running (0m01.1s), 000/300 VUs, 300 complete and 0 interrupted iterations
rush ✓ [=================================] 300 VUs  01.1s/30s  300/300 iters, 1 per VU
ERRO[0001] thresholds on metrics 'join_dropped' have been crossed
jian@jianui-MacBookPro load-test %
