package com.triagain.crew.port.out;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;

import java.util.List;
import java.util.Optional;

public interface CrewRepositoryPort {

    /** 크루 저장 — 생성·수정 시 사용 */
    Crew save(Crew crew);

    /** 크루 멤버 저장 — 가입·역할 변경 시 사용 */
    CrewMember saveMember(CrewMember member);

    /** ID로 크루 조회 — 크루 상세·수정 시 사용 */
    Optional<Crew> findById(String id);

    /** 비관적 락으로 크루 조회 — 동시 참여 시 정원 초과 방지 */
    Optional<Crew> findByIdWithLock(String id);

    /** 초대코드로 크루 조회 — 크루 참여 시 사용 */
    Optional<Crew> findByInviteCode(String inviteCode);

    /** 유저의 크루 목록 조회 — 홈 화면 크루 리스트에 사용 */
    List<Crew> findAllByUserId(String userId);
}
