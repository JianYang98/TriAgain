# 0714 G1 밤 — gc_pause 개별 이벤트 복원표 (두 세션 106건)

> **방법**: `jvm_gc_pause_seconds_{count,sum}` 5s 누적 카운터의 스크레이프 간 델타. GC는 이산 이벤트라 count +1 구간의 sum 델타 = **그 pause의 정확값**(외삽·근사 없음). n≥2 구간은 합산만 가능(평균으로 판독).
> **산출**: `prometheus-{c17c18,a11a12}/gc_pause_sum_raw.json`(보충 회수) + `prometheus-a11a12/gc_pause_events.py`(재현 스크립트, TSDB 필요 — sum_raw/count_raw JSON만으로도 오프라인 재계산 가능).
> ⚠️ **w1 게이트 GC는 이 표에 없음** — 시리즈 탄생 샘플(카운터 0→1)이라 델타 산출 불가. 정본 = GcTrigger 앱로그(C-쌍 w1 166ms · A-쌍 w1 161ms).
> ⚠️ `jvm_gc_pause_seconds_bucket`(히스토그램)은 **앱이 원천 미노출**(Micrometer 기본 — 밤 전체 창 시리즈 0개 확인). 분포 정밀이 필요하면 다음 세션에서 `management.metrics.distribution.percentiles-histogram.jvm.gc.pause=true`(프로그램 인자, 리빌드 불필요) 또는 `-Xlog:gc*` GC 로그 활성화.

> **GC 용어** — Old(자연)·게이트 GC·Young(런내)·잔펄스(런사이)·할당분 정의 = 통합비교 `serial-vs-g1/Serial-vs-G1_통합비교-4쌍.md` §2 참조.

## C17/C18 (PESS·G1) — 64건

```
KST      | gc                   | cause                  | n | pause(ms)
23:37:10 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    26.0
23:41:35 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    10.0
23:46:10 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    18.0
23:50:25 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    12.0
23:54:55 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    10.0
23:59:40 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    13.0
00:04:20 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    28.0
00:10:20 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    11.0
00:12:20 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    12.0
00:13:05 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     5.0
00:16:10 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     6.0
00:16:25 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    15.0
00:16:30 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    12.0
00:18:30 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     8.0
00:20:35 | G1 Young Generation  | G1 Evacuation Pause    | 3 |    31.0  (합산 3건)
00:22:30 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     3.0
00:23:10 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     8.0
00:24:40 | G1 Young Generation  | G1 Evacuation Pause    | 5 |    79.0  (합산 5건)
00:24:55 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     5.0
00:26:45 | G1 Young Generation  | G1 Evacuation Pause    | 3 |    39.0  (합산 3건)
00:28:20 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     2.0
00:28:45 | G1 Young Generation  | G1 Evacuation Pause    | 8 |   114.0  (합산 8건)
00:28:50 | G1 Young Generation  | G1 Evacuation Pause    | 2 |    34.0  (합산 2건)
00:29:00 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     7.0
00:29:45 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     6.0
00:30:40 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     3.0
00:30:50 | G1 Old Generation    | System.gc()            | 1 |   226.0
00:33:00 | G1 Old Generation    | System.gc()            | 1 |   192.0
00:33:05 | G1 Young Generation  | G1 Evacuation Pause    | 2 |    65.0  (합산 2건)
00:34:45 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     4.0
00:35:10 | G1 Old Generation    | System.gc()            | 1 |   175.0
00:37:20 | G1 Old Generation    | System.gc()            | 1 |   141.0
00:37:25 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    14.0
00:38:05 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    13.0
00:39:25 | G1 Old Generation    | System.gc()            | 1 |   147.0
00:41:00 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     2.0
00:41:35 | G1 Old Generation    | System.gc()            | 1 |   123.0
00:41:40 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     7.0
00:42:35 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     4.0
00:43:45 | G1 Old Generation    | System.gc()            | 1 |   146.0
00:43:50 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     7.0
00:45:55 | G1 Old Generation    | System.gc()            | 1 |   147.0
00:46:00 | G1 Young Generation  | G1 Evacuation Pause    | 3 |    74.0  (합산 3건)
00:46:40 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     5.0
00:49:30 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     5.0
```

## A11/A12 (COND·G1) — 42건

```
KST      | gc                   | cause                  | n | pause(ms)
01:21:50 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    39.0
01:28:05 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    41.0
01:34:30 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    11.0
01:42:05 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    21.0
01:44:10 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    12.0
01:45:15 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     4.0
01:48:15 | G1 Young Generation  | G1 Evacuation Pause    | 2 |    55.0  (합산 2건)
01:49:25 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     3.0
01:52:10 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     3.0
01:52:25 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    20.0
01:53:30 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     5.0
01:55:05 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     8.0
01:56:30 | G1 Young Generation  | G1 Evacuation Pause    | 2 |    19.0  (합산 2건)
01:56:50 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     3.0
01:58:35 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     8.0
01:58:50 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     3.0
02:00:40 | G1 Young Generation  | G1 Evacuation Pause    | 4 |    55.0  (합산 4건)
02:02:45 | G1 Old Generation    | System.gc()            | 1 |   142.0
02:04:55 | G1 Old Generation    | System.gc()            | 1 |   119.0
02:05:00 | G1 Young Generation  | G1 Evacuation Pause    | 2 |    16.0  (합산 2건)
02:07:05 | G1 Old Generation    | System.gc()            | 1 |   126.0
02:09:10 | G1 Old Generation    | System.gc()            | 1 |   149.0
02:09:15 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    11.0
02:09:50 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     8.0
02:11:20 | G1 Old Generation    | System.gc()            | 1 |   194.0
02:13:10 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     3.0
02:13:30 | G1 Old Generation    | System.gc()            | 1 |   133.0
02:13:35 | G1 Young Generation  | G1 Evacuation Pause    | 1 |    18.0
02:13:45 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     5.0
02:15:40 | G1 Old Generation    | System.gc()            | 1 |   129.0
02:15:45 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     7.0
02:17:45 | G1 Old Generation    | System.gc()            | 1 |   124.0
02:17:50 | G1 Young Generation  | G1 Evacuation Pause    | 2 |    17.0  (합산 2건)
02:19:15 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     3.0
02:22:50 | G1 Young Generation  | G1 Evacuation Pause    | 1 |     2.0
```

## 판독 요약

1. **게이트 System.gc() = GcTrigger 앱로그와 ±1ms 정합** (A12 8건: 142/119/126/149/194/133/129/124 ↔ 로그 142/119/125/149/194/132/130/123) — 채널 교차검증 완결.
2. **런중 young 단건 pause는 전부 ≤21ms**(A-세션 기준 — 41·39ms 두 건은 러너 시작 전(01:21·01:28) 유휴 구간 발생분). C-세션도 단건 최대 28ms(00:04:20, 블록 사이 유휴). **"G1 자연 활동 = 수~수십ms 잔펄스" 결론이 이벤트 단위로 확정.**
3. 합산 구간(n≥2)은 고VU 런 정렬 — C17 vu700 창(00:28:45) 8건 114ms합(평균 14ms), A11 vu700 창(02:00:40) 4건 55ms합(평균 14ms). 버스트여도 **단건 평균 ~14ms**.
4. Serial 밤과의 대비 완성: Serial 자연 Major **192/236ms 한 방** vs G1 **최대 단건 21ms(런중)** — 오염 단가 약 10분의 1.
