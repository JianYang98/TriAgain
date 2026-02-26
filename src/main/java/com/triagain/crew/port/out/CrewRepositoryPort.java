package com.triagain.crew.port.out;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;

import java.util.List;
import java.util.Optional;

public interface CrewRepositoryPort {

    Crew save(Crew crew);

    CrewMember saveMember(CrewMember member);

    Optional<Crew> findById(String id);

    Optional<Crew> findByIdWithLock(String id);

    Optional<Crew> findByInviteCode(String inviteCode);

    List<Crew> findAllByUserId(String userId);
}
