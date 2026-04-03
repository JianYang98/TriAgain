package com.triagain.user.infra;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.infra.ChallengeJpaRepository;
import com.triagain.crew.infra.CrewJpaRepository;
import com.triagain.crew.infra.CrewMemberJpaRepository;
import com.triagain.user.port.out.CrewMembershipPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserCrewMembershipAdapter implements CrewMembershipPort {

    private final EntityManager entityManager;
    private final CrewMemberJpaRepository crewMemberJpaRepository;
    private final CrewJpaRepository crewJpaRepository;
    private final ChallengeJpaRepository challengeJpaRepository;

    @Override
    public List<MembershipInfo> findAllByUserId(String userId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT cm.crew_id,
                       cm.role,
                       c.status,
                       (SELECT COUNT(*) FROM crew_members cm2 WHERE cm2.crew_id = cm.crew_id)
                FROM crew_members cm
                JOIN crews c ON c.id = cm.crew_id
                WHERE cm.user_id = :userId
                """)
                .setParameter("userId", userId)
                .getResultList();

        return rows.stream()
                .map(row -> new MembershipInfo(
                        (String) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).intValue()
                ))
                .toList();
    }

    @Override
    public void removeMember(String crewId, String userId) {
        crewMemberJpaRepository.deleteByCrewIdAndUserId(crewId, userId);
        // currentMembers 감소
        entityManager.createNativeQuery(
                "UPDATE crews SET current_members = current_members - 1 WHERE id = :crewId")
                .setParameter("crewId", crewId)
                .executeUpdate();
    }

    /** 크루 + 연관 데이터 하드 삭제 — 리프 → 루트 순서 (FK 관계 기반) */
    @Override
    public void deleteCrewWithAllData(String crewId) {
        // 1) reviews (report_id FK → reports)
        entityManager.createNativeQuery(
                "DELETE FROM reviews WHERE report_id IN (SELECT id FROM reports WHERE verification_id IN (SELECT id FROM verifications WHERE crew_id = :crewId))")
                .setParameter("crewId", crewId).executeUpdate();
        // 2) reports (verification_id FK → verifications)
        entityManager.createNativeQuery(
                "DELETE FROM reports WHERE verification_id IN (SELECT id FROM verifications WHERE crew_id = :crewId)")
                .setParameter("crewId", crewId).executeUpdate();
        // 3) reactions (verification_id FK → verifications)
        entityManager.createNativeQuery(
                "DELETE FROM reactions WHERE verification_id IN (SELECT id FROM verifications WHERE crew_id = :crewId)")
                .setParameter("crewId", crewId).executeUpdate();
        // 4) upload_session → crew_id null 처리
        entityManager.createNativeQuery(
                "UPDATE upload_sessions SET crew_id = NULL WHERE crew_id = :crewId")
                .setParameter("crewId", crewId).executeUpdate();
        // 5) verifications
        entityManager.createNativeQuery(
                "DELETE FROM verifications WHERE crew_id = :crewId")
                .setParameter("crewId", crewId).executeUpdate();
        // 6) challenges
        entityManager.createNativeQuery(
                "DELETE FROM challenges WHERE crew_id = :crewId")
                .setParameter("crewId", crewId).executeUpdate();
        // 7) notifications (CREW 타겟)
        entityManager.createNativeQuery(
                "DELETE FROM notifications WHERE target_type = 'CREW' AND target_id = :crewId")
                .setParameter("crewId", crewId).executeUpdate();
        // 8) crew_members
        crewMemberJpaRepository.deleteByCrewId(crewId);
        // 9) crews
        crewJpaRepository.deleteById(crewId);
    }

    @Override
    public void endActiveChallenges(String userId, String crewId) {
        challengeJpaRepository.findByUserIdAndCrewIdAndStatus(userId, crewId, ChallengeStatus.IN_PROGRESS)
                .ifPresent(entity -> {
                    Challenge challenge = entity.toDomain();
                    challenge.end();
                    challengeJpaRepository.save(com.triagain.crew.infra.ChallengeJpaEntity.fromDomain(challenge));
                });
    }
}