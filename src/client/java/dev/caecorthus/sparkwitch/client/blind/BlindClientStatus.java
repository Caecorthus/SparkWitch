package dev.caecorthus.sparkwitch.client.blind;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Remaining-tick view of the owner-synced {@code sparkwitch:blind} component for the HUD. Cooldowns count until the
 * skill is ready again and include an active window (C2). Computed from the client's own world time, so the numbers
 * count down between syncs; presentation belongs to the HUD owner.
 * 面向 HUD 的 {@code sparkwitch:blind} 组件剩余刻视图。冷却表示距再次可用的刻数，包含持续窗口（C2）。
 * 依据客户端自己的世界时间计算，因此两次同步之间也会倒数；如何展示由 HUD 负责方决定。
 */
public record BlindClientStatus(int caneCooldownTicks, int caneActiveTicks,
                                int attuneCooldownTicks, int attuneActiveTicks) {
    public static final BlindClientStatus NONE = new BlindClientStatus(0, 0, 0, 0);

    public static BlindClientStatus of(@Nullable PlayerEntity player) {
        if (player == null) {
            return NONE;
        }
        BlindComponent component = BlindComponent.KEY.getNullable(player);
        if (component == null) {
            return NONE;
        }
        long now = player.getWorld().getTime();
        return new BlindClientStatus(
                component.caneCooldownRemaining(now),
                component.caneActiveRemaining(now),
                component.attuneCooldownRemaining(now),
                component.attuneActiveRemaining(now));
    }

    public boolean caneReady() {
        return caneCooldownTicks == 0;
    }

    public boolean caneActive() {
        return caneActiveTicks > 0;
    }

    public boolean attuneReady() {
        return attuneCooldownTicks == 0;
    }

    public boolean attuneActive() {
        return attuneActiveTicks > 0;
    }
}
