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

    void sparkwitch$clearPoisonApple();

    void sparkwitch$clearIfMatchChanged(@Nullable UUID matchUuid);
}
