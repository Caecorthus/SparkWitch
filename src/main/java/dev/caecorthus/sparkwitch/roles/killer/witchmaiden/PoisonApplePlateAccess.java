package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Stable role-owned bridge implemented only by the Wathe platter block entity mixin.
 * 仅由 Wathe 餐盘方块实体 mixin 实现的巫女角色私有桥接。
 */
public interface PoisonApplePlateAccess {
    boolean sparkwitch$isPoisonAppleArmed();

    boolean sparkwitch$armPoisonApple(UUID placerUuid, UUID matchUuid);

    @Nullable UUID sparkwitch$recordSuccessfulTake(UUID matchUuid);

    /**
     * The player who armed this platter's Poison Apple (server state only), or {@code null} when none is armed.
     * 布置此餐盘毒苹果的玩家（仅服务端状态）；未布置时为 {@code null}。
     */
    @Nullable UUID sparkwitch$poisonApplePlacer();

    void sparkwitch$clearPoisonApple();

    void sparkwitch$clearIfMatchChanged(@Nullable UUID matchUuid);
}
