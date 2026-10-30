package dev.caecorthus.sparkwitch.mixin.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightDuelBodyCollision;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Both sides: soft cramming ({@code tickCramming} → {@code pushAway}) between a duelist and any player is cancelled,
 * like the Wraith fallback. The duel teams' NEVER rule would also stop it, but it lives in separately synced scoreboard
 * state; keying the push on the same duel component as the solid-wall veto keeps both paths in step.
 * 双端：决斗者与任意玩家之间的轻推挤（{@code tickCramming} → {@code pushAway}）被取消，与冤魂兜底相同。决斗队伍的
 * NEVER 规则同样能阻止推挤，但它属于另行同步的计分板状态；推挤与实体墙否决读取同一个决斗组件，两条路径始终一致。
 */
@Mixin(LivingEntity.class)
public abstract class KnightDuelLivingEntityCollisionMixin {
    @Inject(method = "pushAway(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$skipDuelistPush(Entity other, CallbackInfo ci) {
        if (KnightDuelBodyCollision.ignoresPlayerBody((Entity) (Object) this, other)) {
            ci.cancel();
        }
    }
}
