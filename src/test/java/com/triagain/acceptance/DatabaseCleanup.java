package com.triagain.acceptance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 시나리오 간 DB 초기화 — 매 Cucumber 시나리오 전에 모든 테이블 TRUNCATE */
@Component
public class DatabaseCleanup {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    @Transactional
    public void execute() {
        entityManager.flush();
        entityManager.clear();

        List<String> tableNames = entityManager.createNativeQuery(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
        ).getResultList();

        entityManager.createNativeQuery("SET CONSTRAINTS ALL DEFERRED").executeUpdate();
        for (String tableName : tableNames) {
            entityManager.createNativeQuery("TRUNCATE TABLE \"" + tableName + "\" CASCADE").executeUpdate();
        }
    }
}
