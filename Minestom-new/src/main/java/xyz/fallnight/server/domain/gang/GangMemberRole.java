package xyz.fallnight.server.domain.gang;

public enum GangMemberRole {
    LEADER,
    OFFICER,
    MEMBER,
    RECRUIT;

    public GangMemberRole promote() {
        return switch (this) {
            case RECRUIT -> MEMBER;
            case MEMBER -> OFFICER;
            case OFFICER, LEADER -> this;
        };
    }

    public GangMemberRole demote() {
        return switch (this) {
            case LEADER, OFFICER -> MEMBER;
            case MEMBER -> RECRUIT;
            case RECRUIT -> RECRUIT;
        };
    }

    public boolean officerOrAbove() {
        return this == LEADER || this == OFFICER;
    }
}
