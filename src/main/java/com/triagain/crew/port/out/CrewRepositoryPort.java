package com.triagain.crew.port.out;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CrewRepositoryPort {

    /** 크루 저장 — 생성·수정 시 사용 */
    Crew save(Crew crew);

    /** 크루 멤버 저장 — 가입·역할 변경 시 사용 */
    CrewMember saveMember(CrewMember member);

    /** 조건부 원자적 멤버 수 증가 — current_members < max_members일 때만 +1 (1=성공, 0=정원 초과) */
    int incrementMembersIfNotFull(String id);

    /** 멤버 저장 + 즉시 flush — 유니크 제약 위반을 호출 지점에서 DataIntegrityViolationException으로 잡기 위함 (전략 C 전용) */
    CrewMember saveMemberAndFlush(CrewMember member);

    /** ID로 크루 조회 — 크루 상세·수정 시 사용 */
    Optional<Crew> findById(String id);

    /** 비관적 락으로 크루 조회 — 동시 참여 시 정원 초과 방지 */
    Optional<Crew> findByIdWithLock(String id);

    /** 낙관적 락으로 멤버 수 갱신 — version 불일치 시 0 반환 */
    int updateCurrentMembersWithVersion(String id, int currentMembers, Long version);

    /** 초대코드로 크루 조회 — 크루 참여 시 사용 */
    Optional<Crew> findByInviteCode(String inviteCode);

    /** 유저의 크루 목록 조회 — 홈 화면 크루 리스트에 사용 */
    List<Crew> findAllByUserId(String userId);

    /** 여러 크루 ID로 일괄 조회 — 스케줄러 배치 처리에 사용 */
    List<Crew> findAllByIds(List<String> ids);

    /** 기간 만료된 ACTIVE 크루 조회 — 크루 종료 스케줄러에서 사용 */
    List<Crew> findActiveCrewsEndedBefore(LocalDate date);

    /** 시작일 도래한 RECRUITING 크루 조회 — 서버 시작 시 활성화 보정에 사용 */
    List<Crew> findRecruitingCrewsStartedOnOrBefore(LocalDate date);

    /** 크루 삭제 — 멤버 포함 hard delete */
    void deleteById(String crewId);

    /** 크루 + 연관 데이터 FK-safe hard delete — leaf→root 순서로 완전 삭제 */
    void deleteCrewWithAssociations(String crewId);

    /** 특정 크루의 특정 멤버 삭제 — 크루 탈퇴 시 사용 */
    void deleteMemberByCrewIdAndUserId(String crewId, String userId);

    /** 크루 검색 결과 + hasNext 페이지네이션 정보 */
    record CrewSearchPage(List<Crew> crews, boolean hasNext) {}

    /** 공개 크루 검색 — 키워드/카테고리 필터 + 페이지네이션 (page: 0-based 페이지 번호) */
    CrewSearchPage searchPublicCrews(String keyword, CrewCategory category, LocalDate minEndDate, int page, int size);
}
