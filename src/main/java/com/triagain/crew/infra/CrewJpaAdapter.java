package com.triagain.crew.infra;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
}
