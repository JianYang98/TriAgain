package com.triagain.user.port.in;

import java.util.List;
import java.util.Map;

/** 타 Context에서 유저 프로필 정보 조회 시 사용하는 Input Port */
public interface UserProfileQueryUseCase {

    /** 유저 ID 목록으로 프로필 정보 일괄 조회 — 크루 상세의 참가자 현황에 사용 */
    Map<String, UserProfileDto> findProfilesByIds(List<String> userIds);

    record UserProfileDto(String nickname, String profileImageUrl) {}
}