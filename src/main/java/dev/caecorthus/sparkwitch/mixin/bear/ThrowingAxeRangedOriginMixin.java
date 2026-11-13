package dev.caecorthus.sparkwitch.mixin.bear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.util.hitscan.RangedOriginScope;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.entity.ThrowingAxeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * BEAR D3: a NoellesRoles throwing-axe kill runs inside a {@code RangedOriginScope} one tick back along the axe's flight
 * (position minus velocity), so the Rys-T front test sees where the axe came from. Wraps the same {@code killPlayer}
 * call as {@code JudgeThrowingAxeAttributionMixin}; MixinExtras chains both and neither changes the arguments. The
 * axe's UUID is the attack source: one axe, one ricochet roll per wearer, even across the ticks it flies.
 * BEAR D3：NoellesRoles 飞斧击杀在沿飞斧飞行方向回退一刻（位置减速度）的 {@code RangedOriginScope} 内执行，因此 Rys-T
 * 正面判定看到飞斧来自何方。与 {@code JudgeThrowingAxeAttributionMixin} 包装同一个 {@code killPlayer} 调用；MixinExtras 会把两者
 * 串联，二者都不改参数。飞斧的 UUID 即攻击来源：一把飞斧对每名佩戴者只掷一次跳弹骰，即使跨越它飞行的多个刻。
 */
@Mixin(ThrowingAxeEntity.class)
public abstract class ThrowingAxeRangedOriginMixin {
    @WrapOperation(method = "onEntityHit", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"))
    private void sparkwitch$scopeAxeOrigin(ServerPlayerEntity victim, boolean body, ServerPlayerEntity killer,
                                          Identifier reason, Operation<Void> original) {
        Entity axe = (Entity) (Object) this;
        RangedOriginScope.run(axe.getPos().subtract(axe.getVelocity()), axe.getUuid(),
                () -> original.call(victim, body, killer, reason));
    }
}
