package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithCollisionRules;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Final veto for player-body collision against Wraith-transparent players.
 * 对冤魂透明玩家的身体碰撞做最终兜底。
 *
 * <p>这里不再只看 Wraith 玩法组件，而是统一接受两种信号：
 * active 冤魂本地状态，以及服务器同步下来的 {@code noellesroles:no_collision} 效果。
 * 这样冤魂本人和其他普通玩家客户端看到的碰撞结果就会一致。</p>
 *
 * <p>这里保持 {@code WrapMethod}，因为它能在实体碰撞链路里直接否决最终结果；
 * 只要任一方是冤魂透明态，玩家互相碰撞就返回 {@code false}。</p>
 */
@Mixin(value = Entity.class, priority = 1100)
public abstract class WraithEntityCollisionMixin {
    @WrapMethod(method = "collidesWith")
    private boolean sparkwitch$ignoreWraithPlayerBodyCollision(Entity other, Operation<Boolean> original) {
        if (WraithCollisionRules.shouldIgnorePlayerBodyCollision((Entity) (Object) this, other)) {
            // 这里用最终否决，避免普通玩家客户端把站立冤魂当成实体墙。
            return false;
        }
        return original.call(other);
    }
}
