package com.triagain.common.api;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.triagain.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/** 강제 GC 트리거 — 부하테스트 pre-GC 게이트 전용, loadtest 프로필에서만 활성화 */
@Slf4j
@RestController
@RequestMapping("/internal/gc")
@Profile("loadtest")
public class GcTriggerController {

	/** System.gc()를 동기 실행하고 힙 회수량·소요시간 반환 — k6 setup() 게이트용 */
	@PostMapping
	public ResponseEntity<ApiResponse<GcTriggerResponse>> trigger() {
		Runtime rt = Runtime.getRuntime();
		long beforeUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;
		long startNanos = System.nanoTime();

		System.gc();

		long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
		long afterUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;
		log.info("[GcTrigger] 완료: {}ms, heap {}MB → {}MB", durationMs, beforeUsedMb, afterUsedMb);
		return ResponseEntity.ok(ApiResponse.ok(
				new GcTriggerResponse(beforeUsedMb, afterUsedMb, durationMs)));
	}

	public record GcTriggerResponse(long heapUsedBeforeMb, long heapUsedAfterMb, long durationMs) {
	}
}
