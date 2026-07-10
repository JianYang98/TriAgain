jian@jianui-MacBookPro load-test %    TAG=A4_max10_vu20
k6 run --env BASE_URL=http://15.164.69.243:8080 \
--env TARGET_VUS=20 --env MAX_MEMBERS=10 \
--env RUN_TAG=$TAG --out json=results/raw/crew-rush-jian_$TAG.json \
k6/crew-rush-jian.js


         /\      Grafana   /‾‾/                                                                                                                                                      
    /\  /  \     |\  __   /  /                                                                                                                                                       
/  \/    \    | |/ /  /   ‾‾\                                                                                                                                                     
/          \   |   (  |  (‾)  |                                                                                                                                                    
/ __________ \  |_|\_\  \_____/


     execution: local
        script: k6/crew-rush-jian.js
        output: json (results/raw/crew-rush-jian_A4_max10_vu20.json)

     scenarios: (100.00%) 1 scenario, 20 max VUs, 1m0s max duration (incl. graceful stop):
              * rush: 1 iterations for each of 20 VUs (maxDuration: 30s, exec: rushExec, gracefulStop: 30s)

INFO[0002] [k6-reporter v2.3.0] Generating HTML summary report  source=console
✓ valid status (201/409)
✓ no server error (5xx)
✓ no conn reset (status 0)
✓ 409 has expected code

      checks.........................: 100.00% ✓ 80        ✗ 0 
      data_received..................: 8.7 kB  18 kB/s
      data_sent......................: 7.6 kB  16 kB/s
      http_req_blocked...............: avg=55.6ms   min=55.32ms  med=55.53ms  max=56.16ms  p(90)=55.84ms  p(95)=56.12ms 
      http_req_connecting............: avg=55.54ms  min=55.31ms  med=55.49ms  max=55.78ms  p(90)=55.74ms  p(95)=55.74ms 
      http_req_duration..............: avg=398.14ms min=363ms    med=408.1ms  max=433.33ms p(90)=419.36ms p(95)=431.74ms
        { expected_response:true }...: avg=375.46ms min=363ms    med=371.4ms  max=398.4ms  p(90)=392.59ms p(95)=395.49ms
      http_req_failed................: 50.00%  ✓ 10        ✗ 10
      http_req_receiving.............: avg=811.95µs min=20µs     med=48µs     max=14.36ms  p(90)=316µs    p(95)=1.45ms  
      http_req_sending...............: avg=24.05µs  min=6µs      med=20.5µs   max=60µs     p(90)=43.6µs   p(95)=49.54µs 
      http_req_tls_handshaking.......: avg=0s       min=0s       med=0s       max=0s       p(90)=0s       p(95)=0s      
      http_req_waiting...............: avg=397.3ms  min=362.22ms med=408.04ms max=433.26ms p(90)=419.3ms  p(95)=431.62ms
      http_reqs......................: 20      40.810663/s
      iteration_duration.............: avg=454.22ms min=419.67ms med=463.79ms max=489.68ms p(90)=475.86ms p(95)=487.45ms
      iterations.....................: 20      40.810663/s
    ✓ join_5xx.......................: 0       0/s
    ✓ join_dropped...................: 0       0/s
      join_full......................: 10      20.405332/s
    ✓ join_success...................: 10      20.405332/s
      scenario_d_duration............: avg=398.14ms min=363ms    med=408.1ms  max=433.33ms p(90)=419.36ms p(95)=431.74ms
=========
🏁 RUSH  A4_max10_vu20
정원 10명 · 동시참가 20 VU

성공_success: 10
정원초과_full: 10
충돌_conflict: 0
5xx_err5xx: 0
드롭_dropped_연결실패: 0
기타_other: 0
p95: 431.7 ms
─────────────────────────
판정: ✅ PASS  (정합성 OK · 충돌 0 · p95 431.7ms)


running (0m00.5s), 00/20 VUs, 20 complete and 0 interrupted iterations
rush ✓ [======================================] 20 VUs  00.5s/30s  20/20 iters, 1 per VU

jian@jianui-MacBookPro load-test %    TAG=A4_max10_vu50
k6 run --env BASE_URL=http://15.164.69.243:8080 \
--env TARGET_VUS=50 --env MAX_MEMBERS=10 \
--env RUN_TAG=$TAG --out json=results/raw/crew-rush-jian_$TAG.json \
k6/crew-rush-jian.js


         /\      Grafana   /‾‾/                                                                                                                                                      
    /\  /  \     |\  __   /  /                                                                                                                                                       
/  \/    \    | |/ /  /   ‾‾\                                                                                                                                                     
/          \   |   (  |  (‾)  |                                                                                                                                                    
/ __________ \  |_|\_\  \_____/


     execution: local
        script: k6/crew-rush-jian.js
        output: json (results/raw/crew-rush-jian_A4_max10_vu50.json)

     scenarios: (100.00%) 1 scenario, 50 max VUs, 1m0s max duration (incl. graceful stop):
              * rush: 1 iterations for each of 50 VUs (maxDuration: 30s, exec: rushExec, gracefulStop: 30s)

INFO[0000] [k6-reporter v2.3.0] Generating HTML summary report  source=console
✓ valid status (201/409)
✓ no server error (5xx)
✓ no conn reset (status 0)
✓ 409 has expected code

      checks.........................: 100.00% ✓ 200        ✗ 0 
      data_received..................: 21 kB   52 kB/s
      data_sent......................: 19 kB   48 kB/s
      http_req_blocked...............: avg=54.89ms  min=41.73ms  med=52.13ms  max=70.85ms  p(90)=70.4ms   p(95)=70.51ms 
      http_req_connecting............: avg=54.84ms  min=41.59ms  med=52.1ms   max=70.81ms  p(90)=70.38ms  p(95)=70.49ms 
      http_req_duration..............: avg=224.87ms min=90.52ms  med=218.17ms max=338.65ms p(90)=317.49ms p(95)=326.81ms
        { expected_response:true }...: avg=123.68ms min=90.52ms  med=118.87ms max=165.47ms p(90)=143.07ms p(95)=154.27ms
      http_req_failed................: 80.00%  ✓ 40         ✗ 10
      http_req_receiving.............: avg=269.37µs min=7µs      med=24µs     max=9.73ms   p(90)=122.9µs  p(95)=628.19µs
      http_req_sending...............: avg=9.98µs   min=5µs      med=8µs      max=62µs     p(90)=14µs     p(95)=18.54µs 
      http_req_tls_handshaking.......: avg=0s       min=0s       med=0s       max=0s       p(90)=0s       p(95)=0s      
      http_req_waiting...............: avg=224.59ms min=90.44ms  med=218.1ms  max=337.98ms p(90)=317.46ms p(95)=326.79ms
      http_reqs......................: 50      126.845282/s
      iteration_duration.............: avg=280.21ms min=142.18ms med=269.56ms max=389.8ms  p(90)=376.87ms p(95)=384.48ms
      iterations.....................: 50      126.845282/s
    ✓ join_5xx.......................: 0       0/s
    ✓ join_dropped...................: 0       0/s
      join_full......................: 40      101.476225/s
    ✓ join_success...................: 10      25.369056/s
      scenario_d_duration............: avg=224.87ms min=90.52ms  med=218.17ms max=338.65ms p(90)=317.49ms p(95)=326.81ms
=========
🏁 RUSH  A4_max10_vu50
정원 10명 · 동시참가 50 VU

성공_success: 10
정원초과_full: 40
충돌_conflict: 0
5xx_err5xx: 0
드롭_dropped_연결실패: 0
기타_other: 0
p95: 326.8 ms
─────────────────────────
판정: ✅ PASS  (정합성 OK · 충돌 0 · p95 326.8ms)


running (0m00.4s), 00/50 VUs, 50 complete and 0 interrupted iterations
rush ✓ [======================================] 50 VUs  00.4s/30s  50/50 iters, 1 per VU

-out json=results/raw/crew-rush-jian_$TAG.json \
k6/crew-rush-jian.js

         /\      Grafana   /‾‾/                                                                                                                                                      
    /\  /  \     |\  __   /  /                                                                                                                                                       
/  \/    \    | |/ /  /   ‾‾\                                                                                                                                                     
/          \   |   (  |  (‾)  |                                                                                                                                                    
/ __________ \  |_|\_\  \_____/


     execution: local
        script: k6/crew-rush-jian.js
        output: json (results/raw/crew-rush-jian_A4_max10_vu100.json)

     scenarios: (100.00%) 1 scenario, 100 max VUs, 1m0s max duration (incl. graceful stop):
              * rush: 1 iterations for each of 100 VUs (maxDuration: 30s, exec: rushExec, gracefulStop: 30s)

INFO[0000] [k6-reporter v2.3.0] Generating HTML summary report  source=console
✓ valid status (201/409)
✓ no server error (5xx)
✓ no conn reset (status 0)
✓ 409 has expected code

      checks.........................: 100.00% ✓ 400        ✗ 0 
      data_received..................: 41 kB   68 kB/s
      data_sent......................: 38 kB   64 kB/s
      http_req_blocked...............: avg=27.66ms  min=16.07ms  med=30.94ms  max=78.55ms  p(90)=32.96ms  p(95)=36.97ms 
      http_req_connecting............: avg=27.58ms  min=16.04ms  med=30.91ms  max=78.54ms  p(90)=32.94ms  p(95)=36.96ms 
      http_req_duration..............: avg=354.47ms min=88.03ms  med=348.2ms  max=569.65ms p(90)=530.21ms p(95)=553.49ms
        { expected_response:true }...: avg=121.4ms  min=88.03ms  med=123.52ms max=149.96ms p(90)=143.78ms p(95)=146.87ms
      http_req_failed................: 90.00%  ✓ 90         ✗ 10
      http_req_receiving.............: avg=428.22µs min=12µs     med=43µs     max=18.96ms  p(90)=107µs    p(95)=1.27ms  
      http_req_sending...............: avg=58.8µs   min=5µs      med=14.49µs  max=424µs    p(90)=276.8µs  p(95)=324.14µs
      http_req_tls_handshaking.......: avg=0s       min=0s       med=0s       max=0s       p(90)=0s       p(95)=0s      
      http_req_waiting...............: avg=353.98ms min=87.63ms  med=348.17ms max=569.61ms p(90)=530.03ms p(95)=553.42ms
      http_reqs......................: 100     166.746983/s
      iteration_duration.............: avg=382.7ms  min=108.16ms med=382.91ms max=594.77ms p(90)=562.11ms p(95)=585.38ms
      iterations.....................: 100     166.746983/s
    ✓ join_5xx.......................: 0       0/s
    ✓ join_dropped...................: 0       0/s
      join_full......................: 90      150.072285/s
    ✓ join_success...................: 10      16.674698/s
      scenario_d_duration............: avg=354.47ms min=88.03ms  med=348.2ms  max=569.65ms p(90)=530.21ms p(95)=553.49ms
=========
🏁 RUSH  A4_max10_vu100
정원 10명 · 동시참가 100 VU

성공_success: 10
정원초과_full: 90
충돌_conflict: 0
5xx_err5xx: 0
드롭_dropped_연결실패: 0
기타_other: 0
p95: 553.5 ms
─────────────────────────
판정: ✅ PASS  (정합성 OK · 충돌 0 · p95 553.5ms)


running (0m00.6s), 000/100 VUs, 100 complete and 0 interrupted iterations
rush ✓ [======================================] 100 VUs  00.6s/30s  100/100 iters, 1 per VU

jian@jianui-MacBookPro load-test % TAG=A4_max10_vu200
k6 run --env BASE_URL=http://15.164.69.243:8080 \
--env TARGET_VUS=200 --env MAX_MEMBERS=10 \
--env RUN_TAG=$TAG --out json=results/raw/crew-rush-jian_$TAG.json \
k6/crew-rush-jian.js

         /\      Grafana   /‾‾/                                                                                                                                                      
    /\  /  \     |\  __   /  /                                                                                                                                                       
/  \/    \    | |/ /  /   ‾‾\                                                                                                                                                     
/          \   |   (  |  (‾)  |                                                                                                                                                    
/ __________ \  |_|\_\  \_____/


     execution: local
        script: k6/crew-rush-jian.js
        output: json (results/raw/crew-rush-jian_A4_max10_vu200.json)

     scenarios: (100.00%) 1 scenario, 200 max VUs, 1m0s max duration (incl. graceful stop):
              * rush: 1 iterations for each of 200 VUs (maxDuration: 30s, exec: rushExec, gracefulStop: 30s)

INFO[0001] [k6-reporter v2.3.0] Generating HTML summary report  source=console
✓ valid status (201/409)
✓ no server error (5xx)
✓ no conn reset (status 0)
✓ 409 has expected code

      checks.........................: 100.00% ✓ 800        ✗ 0    
      data_received..................: 81 kB   83 kB/s
      data_sent......................: 77 kB   79 kB/s
      http_req_blocked...............: avg=42.03ms  min=16.68ms  med=40.25ms  max=88.1ms   p(90)=45.1ms   p(95)=47.21ms 
      http_req_connecting............: avg=41.98ms  min=16.66ms  med=40.24ms  max=88.08ms  p(90)=45.09ms  p(95)=47.2ms  
      http_req_duration..............: avg=526.92ms min=73.52ms  med=531.23ms max=922.15ms p(90)=865.16ms p(95)=895.03ms
        { expected_response:true }...: avg=126.26ms min=73.52ms  med=129.94ms max=149.44ms p(90)=145.69ms p(95)=147.56ms
      http_req_failed................: 95.00%  ✓ 190        ✗ 10   
      http_req_receiving.............: avg=580.42µs min=7µs      med=31µs     max=22.41ms  p(90)=236.7µs  p(95)=1.62ms  
      http_req_sending...............: avg=10.71µs  min=4µs      med=8µs      max=178µs    p(90)=13.3µs   p(95)=23.04µs 
      http_req_tls_handshaking.......: avg=0s       min=0s       med=0s       max=0s       p(90)=0s       p(95)=0s      
      http_req_waiting...............: avg=526.33ms min=68.56ms  med=528.41ms max=921.87ms p(90)=865.13ms p(95)=894.97ms
      http_reqs......................: 200     206.849832/s
      iteration_duration.............: avg=569.29ms min=110.28ms med=574.64ms max=962.91ms p(90)=901.96ms p(95)=940.33ms
      iterations.....................: 200     206.849832/s
    ✓ join_5xx.......................: 0       0/s
    ✓ join_dropped...................: 0       0/s
      join_full......................: 190     196.507341/s
    ✓ join_success...................: 10      10.342492/s
      scenario_d_duration............: avg=526.92ms min=73.52ms  med=531.23ms max=922.15ms p(90)=865.16ms p(95)=895.03ms
      vus............................: 4       min=4        max=4  
      vus_max........................: 200     min=200      max=200
=========
🏁 RUSH  A4_max10_vu200
정원 10명 · 동시참가 200 VU

성공_success: 10
정원초과_full: 190
충돌_conflict: 0
5xx_err5xx: 0
드롭_dropped_연결실패: 0
기타_other: 0
p95: 895.0 ms
─────────────────────────
판정: ✅ PASS  (정합성 OK · 충돌 0 · p95 895.0ms)


running (0m01.0s), 000/200 VUs, 200 complete and 0 interrupted iterations
rush ✓ [======================================] 200 VUs  01.0s/30s  200/200 iters, 1 per VU
jian@jianui-MacBookPro load-test % 

jian@jianui-MacBookPro load-test % TAG=A4_max10_vu300
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
        output: json (results/raw/crew-rush-jian_A4_max10_vu300.json)

     scenarios: (100.00%) 1 scenario, 300 max VUs, 1m0s max duration (incl. graceful stop):
              * rush: 1 iterations for each of 300 VUs (maxDuration: 30s, exec: rushExec, gracefulStop: 30s)

WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64015->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64018->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64005->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64034->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64022->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64019->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64051->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64035->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64007->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64043->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64025->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64024->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64045->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64050->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64041->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64048->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64030->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64033->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64023->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64032->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64044->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64026->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64046->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64047->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64027->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64036->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64014->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64053->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64049->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64028->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64054->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64040->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64042->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64037->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64039->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64038->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64052->15.164.69.243:8080: read: connection reset by peer"
INFO[0001] [k6-reporter v2.3.0] Generating HTML summary report  source=console
✗ valid status (201/409)                                                                                                                                                       
↳  87% — ✓ 263 / ✗ 37
✓ no server error (5xx)
✗ no conn reset (status 0)                                                                                                                                                     
↳  87% — ✓ 263 / ✗ 37
✓ 409 has expected code

      checks.........................: 93.83% ✓ 1126       ✗ 74   
      data_received..................: 106 kB 85 kB/s
      data_sent......................: 115 kB 92 kB/s
      http_req_blocked...............: avg=68.28ms  min=29.31ms  med=80.53ms  max=119.62ms p(90)=84.28ms  p(95)=85.91ms 
      http_req_connecting............: avg=68.24ms  min=29.27ms  med=80.52ms  max=119.59ms p(90)=84.27ms  p(95)=85.9ms  
      http_req_duration..............: avg=606.79ms min=94.18ms  med=607.78ms max=1.15s    p(90)=1.06s    p(95)=1.1s    
        { expected_response:true }...: avg=139.4ms  min=94.18ms  med=136.64ms max=198.26ms p(90)=161.68ms p(95)=179.97ms
      http_req_failed................: 96.66% ✓ 290        ✗ 10   
      http_req_receiving.............: avg=1.49ms   min=0s       med=34µs     max=51.82ms  p(90)=488.9µs  p(95)=4.92ms  
      http_req_sending...............: avg=30.25µs  min=5µs      med=16µs     max=299µs    p(90)=62.1µs   p(95)=87µs    
      http_req_tls_handshaking.......: avg=0s       min=0s       med=0s       max=0s       p(90)=0s       p(95)=0s      
      http_req_waiting...............: avg=605.26ms min=93.84ms  med=605.55ms max=1.15s    p(90)=1.05s    p(95)=1.1s    
      http_reqs......................: 300    240.953791/s
      iteration_duration.............: avg=675.5ms  min=125.16ms med=691.97ms max=1.23s    p(90)=1.14s    p(95)=1.18s   
      iterations.....................: 300    240.953791/s
    ✓ join_5xx.......................: 0      0/s
    ✗ join_dropped...................: 37     29.717634/s
      join_full......................: 253    203.204364/s
    ✓ join_success...................: 10     8.031793/s
      scenario_d_duration............: avg=606.79ms min=94.18ms  med=607.78ms max=1.15s    p(90)=1.06s    p(95)=1.1s    
      vus............................: 96     min=96       max=96 
      vus_max........................: 300    min=300      max=300
=========
🏁 RUSH  A4_max10_vu300
정원 10명 · 동시참가 300 VU

성공_success: 10
정원초과_full: 253
충돌_conflict: 0
5xx_err5xx: 0
드롭_dropped_연결실패: 37
기타_other: 0
p95: 1105.0 ms
─────────────────────────
판정: ❌ FAIL  (정합성 ✗ · 충돌 0 · p95 1105.0ms)


running (0m01.2s), 000/300 VUs, 300 complete and 0 interrupted iterations
rush ✓ [======================================] 300 VUs  01.2s/30s  300/300 iters, 1 per VU
ERRO[0002] thresholds on metrics 'join_dropped' have been crossed
jian@jianui-MacBookPro load-test %

ian@jianui-MacBookPro load-test %  TAG=A4_max10_vu400
k6 run --env BASE_URL=http://15.164.69.243:8080 \
--env TARGET_VUS=400 --env MAX_MEMBERS=10 \
--env RUN_TAG=$TAG --out json=results/raw/crew-rush-jian_$TAG.json \
k6/crew-rush-jian.js

         /\      Grafana   /‾‾/                                                                                                                                                      
    /\  /  \     |\  __   /  /                                                                                                                                                       
/  \/    \    | |/ /  /   ‾‾\                                                                                                                                                     
/          \   |   (  |  (‾)  |                                                                                                                                                    
/ __________ \  |_|\_\  \_____/


     execution: local
        script: k6/crew-rush-jian.js
        output: json (results/raw/crew-rush-jian_A4_max10_vu400.json)

     scenarios: (100.00%) 1 scenario, 400 max VUs, 1m0s max duration (incl. graceful stop):
              * rush: 1 iterations for each of 400 VUs (maxDuration: 30s, exec: rushExec, gracefulStop: 30s)

WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64457->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64447->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64463->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64445->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64459->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64467->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64455->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64453->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64446->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64452->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64458->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64451->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64454->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64440->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64450->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64460->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64462->15.164.69.243:8080: read: connection reset by peer"
INFO[0001] [k6-reporter v2.3.0] Generating HTML summary report  source=console
✗ valid status (201/409)                                                                                                                                                       
↳  95% — ✓ 383 / ✗ 17
✓ no server error (5xx)
✗ no conn reset (status 0)                                                                                                                                                     
↳  95% — ✓ 383 / ✗ 17
✓ 409 has expected code

      checks.........................: 97.87% ✓ 1566       ✗ 34   
      data_received..................: 154 kB 107 kB/s
      data_sent......................: 153 kB 107 kB/s
      http_req_blocked...............: avg=60.5ms   min=10.69ms med=56.49ms  max=149.21ms p(90)=93.57ms  p(95)=97.1ms  
      http_req_connecting............: avg=60.4ms   min=10.67ms med=56.46ms  max=149.13ms p(90)=93.54ms  p(95)=97.09ms 
      http_req_duration..............: avg=691.78ms min=87.69ms med=700.54ms max=1.38s    p(90)=1.21s    p(95)=1.27s   
        { expected_response:true }...: avg=106.69ms min=87.69ms med=105.9ms  max=125.52ms p(90)=123.43ms p(95)=124.47ms
      http_req_failed................: 97.50% ✓ 390        ✗ 10   
      http_req_receiving.............: avg=1.54ms   min=0s      med=53µs     max=110.7ms  p(90)=313.7µs  p(95)=3.44ms  
      http_req_sending...............: avg=178.13µs min=6µs     med=43.5µs   max=3.53ms   p(90)=468.8µs  p(95)=572.49µs
      http_req_tls_handshaking.......: avg=0s       min=0s      med=0s       max=0s       p(90)=0s       p(95)=0s      
      http_req_waiting...............: avg=690.05ms min=87.44ms med=698.56ms max=1.38s    p(90)=1.21s    p(95)=1.27s   
      http_reqs......................: 400    278.637685/s
      iteration_duration.............: avg=753.1ms  min=99.27ms med=762.03ms max=1.4s     p(90)=1.27s    p(95)=1.35s   
      iterations.....................: 400    278.637685/s
    ✓ join_5xx.......................: 0      0/s
    ✗ join_dropped...................: 17     11.842102/s
      join_full......................: 373    259.829641/s
    ✓ join_success...................: 10     6.965942/s
      scenario_d_duration............: avg=691.78ms min=87.69ms med=700.54ms max=1.38s    p(90)=1.21s    p(95)=1.27s   
      vus............................: 192    min=192      max=192
      vus_max........................: 400    min=400      max=400
=========
🏁 RUSH  A4_max10_vu400
정원 10명 · 동시참가 400 VU

성공_success: 10
정원초과_full: 373
충돌_conflict: 0
5xx_err5xx: 0
드롭_dropped_연결실패: 17
기타_other: 0
p95: 1279.7 ms
─────────────────────────
판정: ❌ FAIL  (정합성 ✗ · 충돌 0 · p95 1279.7ms)


running (0m01.4s), 000/400 VUs, 400 complete and 0 interrupted iterations
rush ✓ [======================================] 400 VUs  01.4s/30s  400/400 iters, 1 per VU
ERRO[0002] thresholds on metrics 'join_dropped' have been crossed
jian@jianui-MacBookPro load-test %
ian@jianui-MacBookPro load-test %     TAG=A4_max10_vu500
k6 run --env BASE_URL=http://15.164.69.243:8080 \
--env TARGET_VUS=500 --env MAX_MEMBERS=10 \
--env RUN_TAG=$TAG --out json=results/raw/crew-rush-jian_$TAG.json \
k6/crew-rush-jian.js


         /\      Grafana   /‾‾/                                                                                                                                                      
    /\  /  \     |\  __   /  /                                                                                                                                                       
/  \/    \    | |/ /  /   ‾‾\                                                                                                                                                     
/          \   |   (  |  (‾)  |                                                                                                                                                    
/ __________ \  |_|\_\  \_____/


     execution: local
        script: k6/crew-rush-jian.js
        output: json (results/raw/crew-rush-jian_A4_max10_vu500.json)

     scenarios: (100.00%) 1 scenario, 500 max VUs, 1m0s max duration (incl. graceful stop):
              * rush: 1 iterations for each of 500 VUs (maxDuration: 30s, exec: rushExec, gracefulStop: 30s)

WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64986->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64995->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64962->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64974->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64977->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64983->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64994->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64993->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64989->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64990->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64964->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64972->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64963->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64968->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64976->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64985->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64957->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64966->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64997->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64953->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64971->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64965->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64991->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64980->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64939->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64956->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64947->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64940->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64944->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64959->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64945->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64967->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64941->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64950->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64927->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64970->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64969->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64996->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64908->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64984->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64920->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64924->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64942->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64934->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64929->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64930->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64916->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64914->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64918->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64926->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64936->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64932->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64933->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64949->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64961->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64937->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64921->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64915->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64931->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64954->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64923->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64958->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64896->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64952->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64951->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64987->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64973->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64935->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64890->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64943->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64960->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64888->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64909->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64948->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64894->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64912->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64922->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64881->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64897->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64925->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64907->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64919->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64917->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64955->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64826->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64893->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64842->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64899->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64898->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64845->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64828->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64822->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64883->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64886->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64891->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64904->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64905->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64818->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64831->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64851->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64815->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64838->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64821->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64819->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64805->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64910->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64860->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64880->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64902->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64884->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64882->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64903->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64913->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64876->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64901->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64887->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64878->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64879->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64809->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64906->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64833->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64885->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64852->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64837->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64840->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64874->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64873->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64830->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64862->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64836->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64857->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64839->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64875->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64843->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64869->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64865->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64911->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64877->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64823->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64847->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64889->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64829->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64859->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64846->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64895->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64848->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64863->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64858->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64854->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64835->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64855->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64861->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64844->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64832->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64850->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64867->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64817->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64856->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64871->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64870->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64841->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64816->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64853->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64812->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64813->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64799->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64811->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64824->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64803->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64804->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64806->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64810->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64800->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64801->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64814->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64849->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64820->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64834->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64802->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64808->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64827->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64825->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64798->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64807->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64998->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64992->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64979->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64982->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64988->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:64978->15.164.69.243:8080: read: connection reset by peer"
INFO[0001] [k6-reporter v2.3.0] Generating HTML summary report  source=console
✗ valid status (201/409)                                                                                                                                                       
↳  62% — ✓ 310 / ✗ 190
✓ no server error (5xx)
✗ no conn reset (status 0)                                                                                                                                                     
↳  62% — ✓ 310 / ✗ 190
✓ 409 has expected code

      checks.........................: 81.00% ✓ 1620       ✗ 380  
      data_received..................: 124 kB 99 kB/s
      data_sent......................: 192 kB 153 kB/s
      http_req_blocked...............: avg=77.6ms   min=38.65ms  med=64.08ms  max=207.12ms p(90)=118.23ms p(95)=122.7ms 
      http_req_connecting............: avg=77.54ms  min=38.64ms  med=64.02ms  max=207.1ms  p(90)=118.21ms p(95)=122.67ms
      http_req_duration..............: avg=453.38ms min=101.24ms med=351.21ms max=1.11s    p(90)=950.91ms p(95)=994.35ms
        { expected_response:true }...: avg=211.84ms min=102.65ms med=212.76ms max=309.74ms p(90)=230.99ms p(95)=270.37ms
      http_req_failed................: 98.00% ✓ 490        ✗ 10   
      http_req_receiving.............: avg=506.87µs min=0s       med=23µs     max=76.83ms  p(90)=105.1µs  p(95)=524.99µs
      http_req_sending...............: avg=26.69µs  min=6µs      med=13.5µs   max=302µs    p(90)=58µs     p(95)=80.04µs 
      http_req_tls_handshaking.......: avg=0s       min=0s       med=0s       max=0s       p(90)=0s       p(95)=0s      
      http_req_waiting...............: avg=452.85ms min=101.19ms med=351.17ms max=1.11s    p(90)=950.84ms p(95)=994.25ms
      http_reqs......................: 500    399.09549/s
      iteration_duration.............: avg=534.04ms min=143.19ms med=392.87ms max=1.23s    p(90)=1.03s    p(95)=1.11s   
      iterations.....................: 500    399.09549/s
    ✓ join_5xx.......................: 0      0/s
    ✗ join_dropped...................: 190    151.656286/s
      join_full......................: 300    239.457294/s
    ✓ join_success...................: 10     7.98191/s
      scenario_d_duration............: avg=453.38ms min=101.24ms med=351.21ms max=1.11s    p(90)=950.91ms p(95)=994.35ms
      vus............................: 97     min=97       max=97 
      vus_max........................: 500    min=500      max=500
=========
🏁 RUSH  A4_max10_vu500
정원 10명 · 동시참가 500 VU

성공_success: 10
정원초과_full: 300
충돌_conflict: 0
5xx_err5xx: 0
드롭_dropped_연결실패: 190
기타_other: 0
p95: 994.4 ms
─────────────────────────
판정: ❌ FAIL  (정합성 ✗ · 충돌 0 · p95 994.4ms)


running (0m01.3s), 000/500 VUs, 500 complete and 0 interrupted iterations
rush ✓ [======================================] 500 VUs  01.3s/30s  500/500 iters, 1 per VU
ERRO[0001] thresholds on metrics 'join_dropped' have been crossed
jian@jianui-MacBookPro load-test % 

jian@jianui-MacBookPro load-test %
jian@jianui-MacBookPro load-test % TAG=A4_max10_vu700    
k6 run --env BASE_URL=http://15.164.69.243:8080 \
--env TARGET_VUS=700 --env MAX_MEMBERS=10 \
--env RUN_TAG=$TAG --out json=results/raw/crew-rush-jian_$TAG.json \
k6/crew-rush-jian.js


         /\      Grafana   /‾‾/                                                                                                                                                      
    /\  /  \     |\  __   /  /                                                                                                                                                       
/  \/    \    | |/ /  /   ‾‾\                                                                                                                                                     
/          \   |   (  |  (‾)  |                                                                                                                                                    
/ __________ \  |_|\_\  \_____/


     execution: local
        script: k6/crew-rush-jian.js
        output: json (results/raw/crew-rush-jian_A4_max10_vu700.json)

     scenarios: (100.00%) 1 scenario, 700 max VUs, 1m0s max duration (incl. graceful stop):
              * rush: 1 iterations for each of 700 VUs (maxDuration: 30s, exec: rushExec, gracefulStop: 30s)

WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49338->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65530->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49334->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49336->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49332->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49333->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49326->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49335->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49325->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49327->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49329->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65294->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65299->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65297->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65301->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65300->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65312->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65309->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65311->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65302->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65308->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65296->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65314->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65310->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65315->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65305->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65304->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65298->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65303->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65307->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65319->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65323->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65316->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65322->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65324->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65321->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65325->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65317->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65320->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65318->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65330->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65332->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65337->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65338->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65328->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65342->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65334->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65327->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65326->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65345->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65347->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65349->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65335->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65344->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65336->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65339->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65343->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65346->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65340->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65348->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65353->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65352->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65362->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65354->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65350->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65356->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65365->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65357->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65361->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65351->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65359->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65366->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65363->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65367->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65364->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65386->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65378->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65374->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65369->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65387->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65377->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65382->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65376->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65375->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65371->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65379->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65373->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65372->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65380->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65401->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65383->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65399->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65391->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65390->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65384->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65392->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65393->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65388->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65397->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65394->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65395->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65405->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65408->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65409->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65406->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65396->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65418->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65404->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65415->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65419->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65410->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65411->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65403->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65407->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65400->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65414->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65420->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65412->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65402->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65417->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65416->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65521->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65526->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65518->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65513->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65522->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65523->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65529->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65520->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65528->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65524->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65509->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65519->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65515->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65527->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65525->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65511->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65512->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65517->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65507->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65516->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65514->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65510->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65505->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65502->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65504->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65503->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65506->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65496->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65501->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65497->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65493->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65499->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65498->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65495->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65492->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65489->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65491->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49243->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49244->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65490->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49247->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49249->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49251->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49240->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49213->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49217->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49219->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65427->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49239->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49201->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49237->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49242->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49234->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49235->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49227->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49220->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49241->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65485->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65488->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49228->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49221->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65500->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65432->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49252->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49245->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49218->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49230->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49224->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49216->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49226->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49215->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49214->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49232->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49211->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49229->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49250->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49225->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49212->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49233->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49223->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49222->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49236->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49231->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49238->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49204->15.164.69.243:8080: read: connection reset by peer"
WARN[0000] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65426->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49173->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49328->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49318->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49320->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49323->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49321->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49319->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49315->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49313->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49310->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49309->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49298->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49301->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49317->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49294->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49316->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49290->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49293->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49292->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:49253->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65306->15.164.69.243:8080: read: connection reset by peer"
WARN[0001] Request Failed                                error="Post \"http://15.164.69.243:8080/crews/loadtest-rush-crew-1/join\": read tcp 192.168.0.11:65313->15.164.69.243:8080: read: connection reset by peer"
INFO[0002] [k6-reporter v2.3.0] Generating HTML summary report  source=console
✗ valid status (201/409)                                                                                                                                                       
↳  67% — ✓ 471 / ✗ 229
✓ no server error (5xx)
✗ no conn reset (status 0)                                                                                                                                                     
↳  67% — ✓ 471 / ✗ 229
✓ 409 has expected code

      checks.........................: 83.64% ✓ 2342       ✗ 458  
      data_received..................: 189 kB 106 kB/s
      data_sent......................: 269 kB 151 kB/s
      http_req_blocked...............: avg=110.94ms min=14.84ms  med=114.56ms max=296.41ms p(90)=167.81ms p(95)=214.11ms
      http_req_connecting............: avg=110.88ms min=14.82ms  med=114.54ms max=296.37ms p(90)=167.8ms  p(95)=214.09ms
      http_req_duration..............: avg=770.87ms min=199.94ms med=646.13ms max=1.72s    p(90)=1.44s    p(95)=1.54s   
        { expected_response:true }...: avg=233.38ms min=199.94ms med=233.74ms max=256.45ms p(90)=250.82ms p(95)=253.64ms
      http_req_failed................: 98.57% ✓ 690        ✗ 10   
      http_req_receiving.............: avg=774.25µs min=0s       med=33.5µs   max=50.92ms  p(90)=175.7µs  p(95)=1.24ms  
      http_req_sending...............: avg=19.97µs  min=4µs      med=11µs     max=312µs    p(90)=38µs     p(95)=55.04µs 
      http_req_tls_handshaking.......: avg=0s       min=0s       med=0s       max=0s       p(90)=0s       p(95)=0s      
      http_req_waiting...............: avg=770.08ms min=199.85ms med=643.73ms max=1.72s    p(90)=1.44s    p(95)=1.54s   
      http_reqs......................: 700    394.172109/s
      iteration_duration.............: avg=882.59ms min=222.91ms med=706.25ms max=1.76s    p(90)=1.56s    p(95)=1.68s   
      iterations.....................: 700    394.172109/s
    ✓ join_5xx.......................: 0      0/s
    ✗ join_dropped...................: 229    128.95059/s
      join_full......................: 461    259.590489/s
    ✓ join_success...................: 10     5.63103/s
      scenario_d_duration............: avg=770.87ms min=199.94ms med=646.13ms max=1.72s    p(90)=1.44s    p(95)=1.54s   
      vus............................: 294    min=294      max=294
      vus_max........................: 700    min=700      max=700
=========
🏁 RUSH  A4_max10_vu700
정원 10명 · 동시참가 700 VU

성공_success: 10
정원초과_full: 461
충돌_conflict: 0
5xx_err5xx: 0
드롭_dropped_연결실패: 229
기타_other: 0
p95: 1541.1 ms
─────────────────────────
판정: ❌ FAIL  (정합성 ✗ · 충돌 0 · p95 1541.1ms)


running (0m01.8s), 000/700 VUs, 700 complete and 0 interrupted iterations
rush ✓ [======================================] 700 VUs  01.8s/30s  700/700 iters, 1 per VU
ERRO[0002] thresholds on metrics 'join_dropped' have been crossed 
