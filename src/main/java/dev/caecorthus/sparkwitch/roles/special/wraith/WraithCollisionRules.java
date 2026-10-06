package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Shared Wraith body-collision rules.
 * 冤魂身体碰撞的统一判断入口。
 *
 * <p>碰撞预测会在每个客户端本地执行：冤魂本人通常能读到自己的 active 状态，
 * 但其他玩家客户端未必能及时用玩法组件判断“目标是 active 冤魂”。因此这里同时接受
 * {@code noellesroles:no_collision} 状态效果作为同步信号。这个效果本来就由服务器挂在
 * active 冤魂身上并同步给追踪该实体的客户端，不会新增 Entity/DataTracker 字段，
 * 也就不会引入之前的实体数据协议编号错位问题。</p>
 */
public final class WraithCollisionRules {
    private static final Identifier NO_COLLISION = Identifier.of("noellesroles", "no_collision");

    private WraithCollisionRules() {
    }

    public static boolean shouldIgnorePlayerBodyCollision(Entity self, Entity other) {
        return self instanceof PlayerEntity
                && other instanceof PlayerEntity
                && (isCollisionTransparent(self) || isCollisionTransparent(other));
    }

    public static boolean isCollisionTransparent(Entity entity) {
        if (entity instanceof PlayerEntity player && WraithStateService.isActive(player)) {
            return true;
        }
        return hasNoCollisionEffect(entity);
    }

    private static boolean hasNoCollisionEffect(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        return Registries.STATUS_EFFECT.getEntry(NO_COLLISION)
                .map(living::hasStatusEffect)
                .orElse(false);
    }
}
