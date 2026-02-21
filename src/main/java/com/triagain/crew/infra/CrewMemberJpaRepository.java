package com.triagain.crew.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrewMemberJpaRepository extends JpaRepository<CrewMemberJpaEntity, String> {

    List<CrewMemberJpaEntity> findByCrewId(String crewId);
}
