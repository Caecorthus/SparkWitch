package dev.caecorthus.sparkwitch.client.saboteur;

/** Pure client gate for sending Saboteur's dedicated ability request. */
public final class SaboteurClientAbilityRules {
    private SaboteurClientAbilityRules() {
    }

    public static boolean shouldSend(
            boolean confirmedServer,
            boolean exactSaboteurRole,
            boolean promotedWraith,
            boolean channelAvailable
    ) {
        return confirmedServer
                && exactSaboteurRole
                && promotedWraith
                && channelAvailable;
    }
}
