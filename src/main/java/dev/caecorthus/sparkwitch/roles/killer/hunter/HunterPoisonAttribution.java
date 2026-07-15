package dev.caecorthus.sparkwitch.roles.killer.hunter;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Binds a trap poison application to its placer, actual poisoner, and natural expiry tick.
 * 将一次毒捕兽夹中毒绑定到放置者、实际下毒者与自然到期 tick，避免其他毒源冒领奖励。
 */
public record HunterPoisonAttribution(
        @Nullable UUID placerUuid,
        @Nullable UUID poisonerUuid,
        long expectedExpiryTick
) {

    public static HunterPoisonAttribution forTrap(
            @Nullable UUID placerUuid,
            @Nullable UUID poisonerUuid,
            long appliedAtTick,
            int poisonTicks
    ) {
        return new HunterPoisonAttribution(
                placerUuid,
                poisonerUuid,
                appliedAtTick + Math.max(0, poisonTicks)
        );
    }

    @Nullable
    public UUID effectivePoisonerUuid() {
        return poisonerUuid != null ? poisonerUuid : placerUuid;
    }

    public boolean matchesConfirmedPoisonDeath(
            boolean poisonDeath,
            long currentTick,
            @Nullable UUID livePoisonerUuid
    ) {
        UUID expectedPoisoner = effectivePoisonerUuid();
        return poisonDeath
                && expectedPoisoner != null
                && expectedPoisoner.equals(livePoisonerUuid)
                && currentTick <= expectedExpiryTick;
    }
}
