package dev.caecorthus.sparkwitch.roles.special.wraith;

/** Pure common policy for restrictions shared by base and promoted active Wraiths. */
public final class WraithParticipationRules {
    private WraithParticipationRules() {
    }

    public static boolean mayUseTextChat(
            boolean activeWraith,
            boolean guardianAngel,
            boolean creative
    ) {
        return activeWraith && WraithCommunicationPolicy.mayCommunicate(true, guardianAngel, creative);
    }

    public static boolean mayJump(boolean activeWraith, boolean mapAllowsJump) {
        return !activeWraith || mapAllowsJump;
    }

    public static boolean mayGenerateGroundParticles(boolean activeWraith) {
        return !activeWraith;
    }

    public static boolean mayPickUpGroundItems(boolean activeWraith) {
        return !activeWraith;
    }
}
