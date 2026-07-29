package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

final class FocusedFootstepsMovementRules {
    private FocusedFootstepsMovementRules() {
    }

    static boolean shouldUseServerFallback(boolean clientChannelAvailable, boolean rooted, boolean pigGodFrozen) {
        return !clientChannelAvailable && !rooted && !pigGodFrozen;
    }

    static boolean shouldSprint(boolean running, boolean rooted, boolean pigGodFrozen) {
        return running && !rooted && !pigGodFrozen;
    }
}
