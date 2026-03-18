package com.triagain.crew.infra;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CrewJpaAdapter implements CrewRepositoryPort {

    private final CrewJpaRepository crewJpaRepository;
    private final CrewMemberJpaRepository crewMemberJpaRepository;

    /** 크루 저장 — 생성·수정 시 사용 */
    @Override
    public Crew save(Crew crew) {
        CrewJpaEntity crewEntity = CrewJpaEntity.fromDomain(crew);
        crewJpaRepository.save(crewEntity);

        List<CrewMemberJpaEntity> members = crewMemberJpaRepository.findByCrewId(crew.getId());
        return crewEntity.toDomainWithMembers(members);
    }

    /** 크루 멤버 저장 — 가입·역할 변경 시 사용 */
    @Override
    public CrewMember saveMember(CrewMember member) {
        CrewMemberJpaEntity entity = CrewMemberJpaEntity.fromDomain(member);
        crewMemberJpaRepository.save(entity);
        return entity.toDomain();
    }

    /** ID로 크루 조회 — 크루 상세·수정 시 사용 */
    @Override
    public Optional<Crew> findById(String id) {
        return crewJpaRepository.findById(id)
                .map(entity -> {
                    List<CrewMemberJpaEntity> members = crewMemberJpaRepository.findByCrewId(id);
                    return entity.toDomainWithMembers(members);
                });
    }

    /** 비관적 락으로 크루 조회 — 동시 참여 시 정원 초과 방지 */
    @Override
    public Optional<Crew> findByIdWithLock(String id) {
        return crewJpaRepository.findByIdWithLock(id)
                .map(entity -> {
                    List<CrewMemberJpaEntity> members = crewMemberJpaRepository.findByCrewId(id);
                    return entity.toDomainWithMembers(members);
                });
    }

    /** 초대코드로 크루 조회 — 크루 참여 시 사용 */
    @Override
    public Optional<Crew> findByInviteCode(String inviteCode) {
        return crewJpaRepository.findByInviteCode(inviteCode)
                .map(entity -> {
                    List<CrewMemberJpaEntity> members = crewMemberJpaRepository.findByCrewId(entity.getId());
                    return entity.toDomainWithMembers(members);
                });
    }

    /** 유저의 크루 목록 조회 — 홈 화면 크루 리스트에 사용 */
    @Override
    public List<Crew> findAllByUserId(String userId) {
        List<String> crewIds = crewMemberJpaRepository.findByUserId(userId).stream()
                .map(m -> m.toDomain().getCrewId())
                .toList();

        if (crewIds.isEmpty()) {
            return List.of();
        }

        return crewJpaRepository.findAllById(crewIds).stream()
                .map(CrewJpaEntity::toDomain)
                .toList();
    }

    /** 여러 크루 ID로 일괄 조회 — 스케줄러 배치 처리에 사용 */
    @Override
    public List<Crew> findAllByIds(List<String> ids) {
        return crewJpaRepository.findAllById(ids).stream()
                .map(CrewJpaEntity::toDomain)
                .toList();
    }

    /** 기간 만료된 ACTIVE 크루 조회 — 크루 종료 스케줄러에서 사용 */
    @Override
    public List<Crew> findActiveCrewsEndedBefore(LocalDate date) {
        return crewJpaRepository.findAllByStatusAndEndDateBefore(CrewStatus.ACTIVE, date).stream()
                .map(CrewJpaEntity::toDomain)
                .toList();
    }

    /** 시작일 도래한 RECRUITING 크루 조회 — 서버 시작 시 활성화 보정에 사용 */
    @Override
    public List<Crew> findRecruitingCrewsStartedOnOrBefore(LocalDate date) {
        return crewJpaRepository.findAllByStatusAndStartDateLessThanEqual(CrewStatus.RECRUITING, date).stream()
                .map(CrewJpaEntity::toDomain)
                .toList();
    }

    /** 크루 삭제 — 멤버 먼저 삭제 후 크루 삭제 */
    @Override
    public void deleteById(String crewId) {
        crewMemberJpaRepository.deleteByCrewId(crewId);
        crewJpaRepository.deleteById(crewId);
    }

    /** 특정 크루의 특정 멤버 삭제 — 크루 탈퇴 시 사용 */
    @Override
    public void deleteMemberByCrewIdAndUserId(String crewId, String userId) {
        crewMemberJpaRepository.deleteByCrewIdAndUserId(crewId, userId);
    }

    /** 공개 크루 검색 — 키워드/카테고리 필터 + 페이지네이션 */
    @Override
    public List<Crew> searchPublicCrews(String keyword, CrewCategory category,
                                        LocalDate minEndDate, int offset, int limit) {
        int page = offset / limit;
        return crewJpaRepository.searchPublicCrews(
                CrewVisibility.PUBLIC, keyword, category, minEndDate,
                PageRequest.of(page, limit)
        ).stream()
                .map(CrewJpaEntity::toDomain)
                .toList();
    }

    /** 초대코드로 크루 검색 — visibility 무관, 검색 결과용 */
    @Override
    public Optional<Crew> findByInviteCodeForSearch(String inviteCode) {
        return crewJpaRepository.findByInviteCode(inviteCode)
                .map(CrewJpaEntity::toDomain);
    }
}
