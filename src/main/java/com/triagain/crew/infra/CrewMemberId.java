package com.triagain.crew.infra;

import java.io.Serializable;
import java.util.Objects;

/** crew_members 복합 PK — (user_id, crew_id) */
public class CrewMemberId implements Serializable {

    private String userId;
    private String crewId;

    protected CrewMemberId() {
    }

    public CrewMemberId(String userId, String crewId) {
        this.userId = userId;
        this.crewId = crewId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrewMemberId that = (CrewMemberId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(crewId, that.crewId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, crewId);
    }
}
