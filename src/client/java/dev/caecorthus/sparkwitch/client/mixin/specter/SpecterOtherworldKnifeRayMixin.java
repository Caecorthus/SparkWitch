package dev.caecorthus.sparkwitch.client.mixin.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterRules;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterSecondSkillComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side half of the rewritten Otherworld knife path. While the Specter's Otherworld is open, the knife ray is
 * Wathe's ordinary 3-block living-player ray, not the Knight's marked-duel-knife ray. Priority 900 runs before the
 * Knight ray mixin (1000) and cancels it for the Specter; the server mixin enforces the same authority.
 * 重写后的里世界刀刺客户端半边。幽魂里世界开启时，刀的射线使用 Wathe 原本的 3 格存活玩家射线，而不是骑士带标记小刀的
 * 射线。priority 900 先于骑士射线 mixin（1000）并对其 cancel；服务端 mixin 执行同一权威。
 */
@Mixin(value = KnifeItem.class, priority = 900)
public abstract class SpecterOtherworldKnifeRayMixin {
    private static final double KNIFE_REACH = 3.0D;

    @Inject(method = "getKnifeTarget", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$otherworldKnifeRay(PlayerEntity player, CallbackInfoReturnable<HitResult> cir) {
        if (player == null) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (!SpecterRules.isSpecter(game.getRole(player))) {
            return;
        }
        boolean otherworld = SpecterOtherworldComponent.KEY.get(player.getWorld()).isActive()
                || SpecterSecondSkillComponent.KEY.get(player).isActive();
        if (!otherworld) {
            return;
        }
        HitResult hit = ProjectileUtil.getCollision(
                player,
                entity -> entity instanceof PlayerEntity target
                        && target != player
                        && GameFunctions.isPlayerAliveAndSurvival(target),
                KNIFE_REACH
        );
        cir.setReturnValue(hit);
        cir.cancel();
    }
}
