package com.triagain.crew.port.out;

import java.util.List;
import java.util.Map;

public interface UserPort {

    /** 유저 ID 목록으로 프로필 정보 일괄 조회 — 크루 상세의 참가자 현황에 사용 */
    Map<String, UserProfile> findProfilesByIds(List<String> userIds);

    record UserProfile(String nickname, String profileImageUrl) {}
}
