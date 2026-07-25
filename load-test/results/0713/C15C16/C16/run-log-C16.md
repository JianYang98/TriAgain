
# C16 스윕 — 2026-07-13 17:15:18 시작 (순서: 20 400 50 300 100 500 200 700) | PESSIMISTIC·256·PRE_GC=on·reset=박스경유·컨테이너 연속(C15와 같은 JVM)
- **C16_max10_vu20** 17:15:19~17:15:25 exit=0 | success=10 5xx=0 drop=0 p95=403.7ms
  - gate: '!! pre-GC 줄 없음'
  - GC before: Copy/Allocation Failure=79 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=2
  - GC after : Copy/Allocation Failure=79 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=3
!! ABORT: pre-GC 게이트 증빙 누락 (C16_max10_vu20)
  - (정정 17:18) 위 ABORT는 러너 추출패턴 버그의 오탐 — 게이트 실제 성공(heap 86→57MB·160ms, k6 파이프 시 logrus 이스케이프 형식 미대응). vu20 런 데이터 유효, vu400부터 재개(재개 갭 ~4분 특이기록).

# C16 스윕 — 2026-07-13 17:17:47 시작 (순서: 400 50 300 100 500 200 700) | PESSIMISTIC·256·PRE_GC=on·reset=박스경유·컨테이너 연속(C15와 같은 JVM)
- **C16_max10_vu400** 17:17:48~17:17:55 exit=99 | success=10 5xx=0 drop=53 p95=1222.2ms
  - gate: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=80 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=3
  - GC after : Copy/Allocation Failure=82 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=4
- **C16_max10_vu50** 17:19:56~17:20:03 exit=0 | success=10 5xx=0 drop=0 p95=940.0ms
  - gate: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=83 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=4
  - GC after : Copy/Allocation Failure=83 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=5
- **C16_max10_vu300** 17:22:04~17:22:13 exit=0 | success=10 5xx=0 drop=0 p95=1156.8ms
  - gate: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=84 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=5
  - GC after : Copy/Allocation Failure=86 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=6
- **C16_max10_vu100** 17:24:13~17:24:20 exit=0 | success=10 5xx=0 drop=0 p95=417.6ms
  - gate: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=87 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=6
  - GC after : Copy/Allocation Failure=87 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=7
- **C16_max10_vu500** 17:26:21~17:26:30 exit=99 | success=10 5xx=0 drop=55 p95=1639.9ms
  - gate: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=88 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=7
  - GC after : Copy/Allocation Failure=91 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=8
- **C16_max10_vu200** 17:28:31~17:28:38 exit=0 | success=10 5xx=0 drop=0 p95=672.5ms
  - gate: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=92 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=8
  - GC after : Copy/Allocation Failure=93 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=9
- **C16_max10_vu700** 17:30:39~17:30:48 exit=99 | success=10 5xx=0 drop=109 p95=1997.4ms
  - gate: [pre-GC] {\"success\":true,\
  - GC before: Copy/Allocation Failure=94 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=9
  - GC after : Copy/Allocation Failure=99 MarkSweepCompact/Allocation Failure=1 MarkSweepCompact/System.gc()=10
[runner] ✅ C16 8런 스윕 완료 — 2026-07-13 17:30:58
