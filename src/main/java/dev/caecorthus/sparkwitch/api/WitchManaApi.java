package dev.caecorthus.sparkwitch.api;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Stable server-authoritative facade for downstream mana cleanup.
 * 供下游清理魔力的稳定服务端权威门面。
 */
public final class WitchManaApi {
    private WitchManaApi() {
    }

    /**
     * Disables the player's mana system and clears mana and regeneration progress; null is a no-op.
     * 禁用玩家的魔力系统，并清空魔力值和自然回复进度；传入 null 时不执行操作。
     */
    public static void clearMana(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        WitchPlayerComponent.KEY.get(player).clearMana();
    }
}
