package com.triagain.crew.port.out;

import com.triagain.crew.domain.model.Crew;

import java.util.Optional;

public interface CrewRepositoryPort {

    Crew save(Crew crew);

    Optional<Crew> findById(String id);

    Optional<Crew> findByIdWithLock(String id);

    Optional<Crew> findByInviteCode(String inviteCode);
}
