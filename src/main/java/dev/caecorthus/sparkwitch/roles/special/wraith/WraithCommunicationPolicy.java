package dev.caecorthus.sparkwitch.roles.special.wraith;

/** Shared policy for active Wraith chat, voice, and Wathe dead-group access. */
public final class WraithCommunicationPolicy {
    private WraithCommunicationPolicy() {
    }

    public static boolean mayCommunicate(boolean activeWraith, boolean guardianAngel, boolean creative) {
        return !activeWraith || guardianAngel || creative;
    }

    public static boolean shouldBlockCommunication(boolean activeWraith, boolean guardianAngel, boolean creative) {
        return activeWraith && !mayCommunicate(true, guardianAngel, creative);
    }

    public static boolean usesDeadVoiceGroup(boolean activeWraith, boolean guardianAngel) {
        return activeWraith && guardianAngel;
    }
}
