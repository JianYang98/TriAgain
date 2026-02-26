package com.triagain.common.util;

import java.util.UUID;

public final class IdGenerator {

    private IdGenerator() {
    }

    /** PREFIX-UUID 형식 ID 생성 — 엔티티별 식별 가능한 고유 ID */
    public static String generate(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
