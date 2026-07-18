package dev.caecorthus.sparkwitch.roles.special.wraith;

/**
 * Wraith communication restrictions remain active after promotion.
 * 冤魂升变后仍保留通讯限制。
 */
public final class WraithCommunicationRules {
    private WraithCommunicationRules() {
    }

    public static boolean canSendText(boolean activeWraith) {
        return !activeWraith;
    }

    public static boolean canSendVoice(boolean activeWraith) {
        return !activeWraith;
    }
}
