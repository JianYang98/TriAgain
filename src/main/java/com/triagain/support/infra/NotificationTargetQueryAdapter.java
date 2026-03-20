package com.triagain.support.infra;

import com.triagain.support.port.out.NotificationTargetQueryPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationTargetQueryAdapter implements NotificationTargetQueryPort {

    private final EntityManager entityManager;

    /** 마감 임박 미인증자 조회 — deadlineTime 윈도우 + 자정 크로싱 처리 */
    @Override
    @SuppressWarnings("unchecked")
    public List<ReminderTarget> findReminderTargets(LocalTime deadlineFrom, LocalTime deadlineTo, LocalDate targetDate) {
        String sql = """
                SELECT DISTINCT cm.user_id, u.fcm_token, c.id AS crew_id, c.name AS crew_name
                FROM crews c
                JOIN crew_members cm ON cm.crew_id = c.id
                JOIN challenges ch ON ch.user_id = cm.user_id AND ch.crew_id = c.id AND ch.status = 'IN_PROGRESS'
                LEFT JOIN verifications v ON v.user_id = cm.user_id AND v.crew_id = c.id AND v.target_date = :targetDate
                JOIN users u ON u.id = cm.user_id
                WHERE c.status = 'ACTIVE'
                  AND (
                    (:fromTime <= :toTime AND c.deadline_time >= :fromTime AND c.deadline_time < :toTime)
                    OR
                    (:fromTime > :toTime AND (c.deadline_time >= :fromTime OR c.deadline_time < :toTime))
                  )
                  AND v.id IS NULL
                """;

        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("targetDate", targetDate)
                .setParameter("fromTime", deadlineFrom)
                .setParameter("toTime", deadlineTo)
                .getResultList();

        return rows.stream()
                .map(row -> new ReminderTarget(
                        (String) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3]
                ))
                .toList();
    }

    /** 오늘 시작 크루의 전체 멤버 조회 — 크루 시작 알림용 */
    @Override
    @SuppressWarnings("unchecked")
    public List<CrewStartTarget> findCrewStartTargets(LocalDate startDate) {
        String sql = """
                SELECT cm.user_id, u.fcm_token, c.id AS crew_id, c.name AS crew_name
                FROM crews c
                JOIN crew_members cm ON cm.crew_id = c.id
                JOIN users u ON u.id = cm.user_id
                WHERE c.status = 'ACTIVE'
                  AND c.start_date = :startDate
                """;

        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("startDate", startDate)
                .getResultList();

        return rows.stream()
                .map(row -> new CrewStartTarget(
                        (String) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3]
                ))
                .toList();
    }
}
