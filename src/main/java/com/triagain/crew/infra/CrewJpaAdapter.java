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

    @Override
    public Crew save(Crew crew) {
        CrewJpaEntity crewEntity = CrewJpaEntity.fromDomain(crew);
        crewJpaRepository.save(crewEntity);

        List<CrewMemberJpaEntity> existingMembers = crewMemberJpaRepository.findByCrewId(crew.getId());
        List<String> existingMemberIds = existingMembers.stream()
                .map(m -> m.toDomain().getId())
                .toList();

        for (CrewMember member : crew.getMembers()) {
            if (!existingMemberIds.contains(member.getId())) {
                crewMemberJpaRepository.save(CrewMemberJpaEntity.fromDomain(member));
            }
        }

        List<CrewMemberJpaEntity> members = crewMemberJpaRepository.findByCrewId(crew.getId());
        return crewEntity.toDomainWithMembers(members);
    }

    @Override
    public Optional<Crew> findById(String id) {
        return crewJpaRepository.findById(id)
                .map(entity -> {
                    List<CrewMemberJpaEntity> members = crewMemberJpaRepository.findByCrewId(id);
                    return entity.toDomainWithMembers(members);
                });
    }

    @Override
    public Optional<Crew> findByIdWithLock(String id) {
        return crewJpaRepository.findByIdWithLock(id)
                .map(entity -> {
                    List<CrewMemberJpaEntity> members = crewMemberJpaRepository.findByCrewId(id);
                    return entity.toDomainWithMembers(members);
                });
    }

    @Override
    public Optional<Crew> findByInviteCode(String inviteCode) {
        return crewJpaRepository.findByInviteCode(inviteCode)
                .map(entity -> {
                    List<CrewMemberJpaEntity> members = crewMemberJpaRepository.findByCrewId(entity.getId());
                    return entity.toDomainWithMembers(members);
                });
    }
}
