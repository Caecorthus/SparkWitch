package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsRules;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only target confirmation; server cooldown sync is still authoritative.
 * 仅客户端保存目标确认，最终仍以服务端冷却同步为权威。
 */
public final class FocusedFootstepsClientState {
    private int page;
    private int acknowledgedCooldownTicks;
    private int cooldownTicks;
    private @Nullable UUID pendingTarget;
    private @Nullable UUID activeTarget;

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public int clampPage(int candidateCount) {
        page = FocusedFootstepsPageLayout.window(candidateCount, page).page();
        return page;
    }

    public boolean beginRequest(UUID targetUuid, int currentCooldownTicks) {
        if (targetUuid == null || currentCooldownTicks > 0 || pendingTarget != null) {
            return false;
        }
        pendingTarget = targetUuid;
        acknowledgedCooldownTicks = Math.max(0, currentCooldownTicks);
        cooldownTicks = Math.max(0, currentCooldownTicks);
        return true;
    }

    /**
     * Predicts only the pending shop display; server sync remains authoritative.
     * 仅预测待确认的商店显示，仍以服务端同步为准。
     */
    public int displayCooldownTicks(int currentCooldownTicks) {
        int normalizedCooldown = Math.max(0, currentCooldownTicks);
        return normalizedCooldown > 0 ? normalizedCooldown : cooldownTicks;
    }

    public void observe(
            int currentCooldownTicks,
            boolean validOwnerState,
            Predicate<UUID> targetAvailable
    ) {
        int normalizedCooldown = Math.max(0, currentCooldownTicks);
        if (!validOwnerState) {
            clearRoundState();
            return;
        }

        if (activeTarget != null && !targetAvailable.test(activeTarget)) {
            activeTarget = null;
        }

        if (normalizedCooldown > 0) {
            cooldownTicks = normalizedCooldown;
        } else if (pendingTarget == null && cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (FocusedFootstepsRules.effectTicksFromCooldown(cooldownTicks) <= 0) {
            activeTarget = null;
        }
    }

    /** Resolves the role-owned request even when the generic component sync is delayed. / 即使通用组件同步延迟，也能结算角色自有请求。 */
    public void resolveUseResult(
            boolean accepted,
            int currentCooldownTicks,
            boolean validOwnerState,
            Predicate<UUID> targetAvailable
    ) {
        int normalizedCooldown = Math.max(0, currentCooldownTicks);
        UUID requestedTarget = pendingTarget;
        pendingTarget = null;
        cooldownTicks = normalizedCooldown;
        acknowledgedCooldownTicks = normalizedCooldown;

        if (!accepted || !validOwnerState || requestedTarget == null
                || !targetAvailable.test(requestedTarget)) {
            if (requestedTarget != null) {
                activeTarget = null;
            }
            return;
        }
        activeTarget = requestedTarget;
    }

    /** Resolves one pending cast from the owner's ordered authoritative sync. / 通过所有者的有序权威同步确认一次请求。 */
    public void acknowledgeOwnerSync(
            int currentCooldownTicks,
            boolean validOwnerState,
            Predicate<UUID> targetAvailable
    ) {
        int normalizedCooldown = Math.max(0, currentCooldownTicks);
        if (!validOwnerState) {
            clearRoundState();
            return;
        }

        if (pendingTarget != null) {
            UUID acknowledgedTarget = pendingTarget;
            pendingTarget = null;
            if (FocusedFootstepsRules.confirmsSuccessfulUse(
                    acknowledgedCooldownTicks,
                    normalizedCooldown
            ) && targetAvailable.test(acknowledgedTarget)) {
                activeTarget = acknowledgedTarget;
            }
        }

        acknowledgedCooldownTicks = normalizedCooldown;
        cooldownTicks = normalizedCooldown;
        if (activeTarget != null && (!targetAvailable.test(activeTarget)
                || FocusedFootstepsRules.effectTicksFromCooldown(cooldownTicks) <= 0)) {
            activeTarget = null;
        }
    }

    public Optional<UUID> pendingTarget() {
        return Optional.ofNullable(pendingTarget);
    }

    public boolean isRequestPending() {
        return pendingTarget != null;
    }

    public Optional<UUID> activeTarget() {
        return Optional.ofNullable(activeTarget);
    }

    public int remainingEffectTicks() {
        return activeTarget == null ? 0 : FocusedFootstepsRules.effectTicksFromCooldown(cooldownTicks);
    }

    public void clearRoundState() {
        pendingTarget = null;
        activeTarget = null;
        acknowledgedCooldownTicks = 0;
        cooldownTicks = 0;
    }

    public void clearConnection() {
        clearRoundState();
        page = 0;
    }
}
