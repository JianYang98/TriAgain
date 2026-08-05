package com.triagain.common.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 제네릭 청크 처리 엔진 — 배치 스케줄러에서 청크 단위 트랜잭션 실행에 사용 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkProcessor {

	private final TransactionTemplate transactionTemplate;

	/** 리스트를 청크 단위로 트랜잭션 처리, 청크 실패 시 건별 분리 재시도 (도메인 변이 없는 경우) */
	public <T> ChunkProcessingResult<T> execute(List<T> items, int chunkSize, Consumer<T> processor) {
		return execute(items, chunkSize, processor, Function.identity());
	}

	/** 리스트를 청크 단위로 트랜잭션 처리, 청크 실패 시 rehydrator로 fresh 인스턴스 재조회 후 건별 재시도 */
	public <T> ChunkProcessingResult<T> execute(
			List<T> items, int chunkSize, Consumer<T> processor, Function<T, T> rehydrator) {
		int successCount = 0;
		List<FailedItem<T>> failedItems = new ArrayList<>();
		List<T> successItems = new ArrayList<>();

		for (int i = 0; i < items.size(); i += chunkSize) {
			List<T> chunk = items.subList(i, Math.min(i + chunkSize, items.size()));

			try {
				transactionTemplate.executeWithoutResult(status -> {
					for (T item : chunk) {
						processor.accept(item);
					}
				});
				successCount += chunk.size();
				successItems.addAll(chunk);
			} catch (Exception e) {
				log.warn("청크 처리 실패 ({}~{}건), 건별 분리 재시도", i, i + chunk.size() - 1, e);

				for (T item : chunk) {
					try {
						T fresh = transactionTemplate.execute(status -> {
							T rehydrated = rehydrator.apply(item);
							processor.accept(rehydrated);
							return rehydrated;
						});
						successCount++;
						successItems.add(fresh);
					} catch (Exception itemEx) {
						failedItems.add(new FailedItem<>(item, itemEx.getMessage()));
					}
				}
			}
		}

		return new ChunkProcessingResult<>(successCount, failedItems.size(), failedItems, successItems);
	}
}
