package com.triagain.support.domain.vo;

import java.util.EnumSet;
import java.util.Set;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;

public enum EmojiType {
	LIKE, FIRE, CLAP, HEART, LAUGH;

	/** v1 활성 세트 — 이모지 확장은 이 한 줄만 바꾼다 */
	private static final Set<EmojiType> ACTIVE = EnumSet.of(LIKE);

	/** 요청 이모지 검증 — null/blank는 S003, 정의 밖·비활성 값은 S006 */
	public static EmojiType requireActive(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BusinessException(ErrorCode.EMOJI_REQUIRED);
		}

		EmojiType emojiType;
		try {
			emojiType = EmojiType.valueOf(raw);
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.EMOJI_NOT_SUPPORTED);
		}

		if (!ACTIVE.contains(emojiType)) {
			throw new BusinessException(ErrorCode.EMOJI_NOT_SUPPORTED);
		}
		return emojiType;
	}
}
