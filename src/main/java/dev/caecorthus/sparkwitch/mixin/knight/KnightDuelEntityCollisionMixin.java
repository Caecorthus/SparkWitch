package dev.caecorthus.sparkwitch.mixin.knight;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightDuelBodyCollision;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Both sides: Wathe's default-priority {@code collidesWith} wrapper makes running players a solid wall for each other
 * and never consults team collision rules, so this priority-1100 wrapper (outside Wathe's, like the Wraith one)
 * vetoes it whenever a duelist is in the pair. Rays use {@code canHit}, not this method, so duelists stay hittable.
 * 双端：Wathe 默认优先级的 {@code collidesWith} 包装让对局中的玩家互为实体墙，且从不读取队伍碰撞规则；因此本优先级 1100
 * 的包装（位于 Wathe 外层，与冤魂相同）在玩家对中有决斗者时直接否决。射线使用 {@code canHit} 而非此方法，决斗者仍可被命中。
 */
@Mixin(value = Entity.class, priority = 1100)
public abstract class KnightDuelEntityCollisionMixin {
    @WrapMethod(method = "collidesWith")
    private boolean sparkwitch$passThroughDuelists(Entity other, Operation<Boolean> original) {
        if (KnightDuelBodyCollision.ignoresPlayerBody((Entity) (Object) this, other)) {
            return false;
        }
        return original.call(other);
    }
}
