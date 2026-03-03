package com.triagain.user.port.out;

import com.triagain.user.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(String id);

    /** 유저 ID 목록으로 일괄 조회 — 크루 상세 참가자 현황에 사용 */
    List<User> findAllByIds(List<String> ids);
}
