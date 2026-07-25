# A10 스윕 — 2026-07-13 19:19:35 시작 (순서: 20 400 50 300 100 500 200 700) | CONDITIONAL·256·PRE_GC=on
> ⚠️ 위 19:19:35 헤더는 false start(사용자 지시로 발사 전 정지, 런 0개) — 아래 재개분이 정본.

# A10 스윕(재개) — 2026-07-13 21:07:23 시작 (순서: 20 400 50 300 100 500 200 700) | CONDITIONAL·256·PRE_GC=on | 같은 JVM(18:22:49 기동)·A9 종료 후 ~1.5h 휴지 → 워밍업 3런(vu10/50/200 전부 on) 선행
- **A10_max10_vu20** 21:13:47~21:13:55 exit=0 arm=on | success=10 5xx=0 drop=0 p95=172.3ms
  - arm증빙: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=101 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=4
  - GC after : Copy/Allocation Failure=101 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=5
- **A10_max10_vu400** 21:15:56~21:16:04 exit=99 arm=on | success=10 5xx=0 drop=71 p95=1159.6ms
  - arm증빙: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=101 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=5
  - GC after : Copy/Allocation Failure=104 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=6
- **A10_max10_vu50** 21:18:05~21:18:12 exit=0 arm=on | success=10 5xx=0 drop=0 p95=251.3ms
  - arm증빙: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=105 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=6
  - GC after : Copy/Allocation Failure=105 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=7
- **A10_max10_vu300** 21:20:14~21:20:22 exit=99 arm=on | success=10 5xx=0 drop=6 p95=875.5ms
  - arm증빙: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=106 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=7
  - GC after : Copy/Allocation Failure=108 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=8
- **A10_max10_vu100** 21:22:22~21:22:29 exit=0 arm=on | success=10 5xx=0 drop=0 p95=449.9ms
  - arm증빙: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=109 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=8
  - GC after : Copy/Allocation Failure=109 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=9
- **A10_max10_vu500** 21:24:30~21:24:37 exit=99 arm=on | success=10 5xx=0 drop=143 p95=1125.2ms
  - arm증빙: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=110 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=9
  - GC after : Copy/Allocation Failure=113 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=10
- **A10_max10_vu200** 21:26:38~21:26:46 exit=0 arm=on | success=10 5xx=0 drop=0 p95=572.9ms
  - arm증빙: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=113 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=10
  - GC after : Copy/Allocation Failure=115 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=11
- **A10_max10_vu700** 21:28:46~21:28:54 exit=99 arm=on | success=10 5xx=0 drop=257 p95=1144.2ms
  - arm증빙: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=116 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=11
  - GC after : Copy/Allocation Failure=119 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=12
[runner] ✅ A10 완료 (워밍업3+A10 8) — 2026-07-13 21:29:04
