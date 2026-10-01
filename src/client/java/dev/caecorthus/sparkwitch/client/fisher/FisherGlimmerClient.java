package dev.caecorthus.sparkwitch.client.fisher;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherSpiritComponent;
import net.minecraft.entity.player.PlayerEntity;

/** Reads the synced Glimmerfish flag of any player on this client. / 在本客户端读取任意玩家已同步的灵光标记。 */
public final class FisherGlimmerClient {
    private FisherGlimmerClient() {
    }

    public static boolean isGlimmering(PlayerEntity player) {
        return player != null && FisherSpiritComponent.KEY.maybeGet(player)
                .map(FisherSpiritComponent::isActive)
                .orElse(false);
    }

    /** Owner-only countdown; other clients only receive the flag. / 仅拥有者收到倒计时，其他客户端只收到标记。 */
    public static int remainingTicks(PlayerEntity player) {
        return player == null ? 0 : FisherSpiritComponent.KEY.maybeGet(player)
                .map(FisherSpiritComponent::remainingTicks)
                .orElse(0);
    }
}
