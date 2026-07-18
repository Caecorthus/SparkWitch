package dev.caecorthus.sparkwitch.client.witchmaiden;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Pure live inventory-owner and target-roster gates. / 背包所有者与实时目标列表的纯门控。 */
public final class FocusedFootstepsInventoryRules {
    private FocusedFootstepsInventoryRules() {
    }

    public static boolean ownerEligible(
            boolean confirmedServer,
            boolean runningRound,
            boolean exactRole,
            boolean playingAndAlive,
            boolean focusedSkillSelected
    ) {
        return confirmedServer
                && runningRound
                && exactRole
                && playingAndAlive
                && focusedSkillSelected;
    }

    public static List<UUID> candidates(
            UUID ownerUuid,
            Collection<UUID> onlineUuids,
            Predicate<UUID> assignedToMatch,
            Predicate<UUID> dead
    ) {
        return onlineUuids.stream()
                .filter(uuid -> !ownerUuid.equals(uuid))
                .filter(assignedToMatch)
                .filter(uuid -> !dead.test(uuid))
                .toList();
    }
}
