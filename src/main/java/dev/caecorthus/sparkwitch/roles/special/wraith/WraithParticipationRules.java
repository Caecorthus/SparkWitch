package dev.caecorthus.sparkwitch.roles.special.wraith;

/** Pure common policy for restrictions shared by base and promoted active Wraiths. */
public final class WraithParticipationRules {
    private WraithParticipationRules() {
    }

    public static boolean mayUseTextChat(boolean activeWraith) {
        return activeWraith;
    }

    public static boolean mayJump(boolean activeWraith, boolean mapAllowsJump) {
        return !activeWraith || mapAllowsJump;
    }

    public static boolean mayGenerateGroundParticles(boolean activeWraith) {
        return !activeWraith;
    }
}
