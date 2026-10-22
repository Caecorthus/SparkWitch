package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

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
    /**
     * The registered entry, resolved on first use: STATUS_EFFECT is a static registry, so the reference stored under the
     * id at registration (the same object NoellesRoles keeps in {@code ModEffects.NO_COLLISION}) never changes. Absence
     * is not latched, so a missing effect is looked up again exactly as before.
     * 首次使用时解析的已注册条目：STATUS_EFFECT 是静态注册表，注册时存于该 id 下的引用（与 NoellesRoles 保存在
     * {@code ModEffects.NO_COLLISION} 中的是同一对象）永不改变。缺失不会被锁定，因此缺失时与此前一样重新查找。
     */
    private static volatile RegistryEntry<StatusEffect> noCollision;

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
        RegistryEntry<StatusEffect> effect = noCollisionEffect();
        return effect != null && living.hasStatusEffect(effect);
    }

    private static @Nullable RegistryEntry<StatusEffect> noCollisionEffect() {
        RegistryEntry<StatusEffect> effect = noCollision;
        if (effect == null) {
            effect = Registries.STATUS_EFFECT.getEntry(NO_COLLISION).orElse(null);
            noCollision = effect;
        }
        return effect;
    }
}
