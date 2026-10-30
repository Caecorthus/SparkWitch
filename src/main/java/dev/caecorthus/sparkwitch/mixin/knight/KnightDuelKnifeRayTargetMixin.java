package dev.caecorthus.sparkwitch.mixin.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * During a duel the knife/cross-sword ray only accepts the specified opponent, so the sent stab id is always the
 * duel target (the Knight) and never a bystander. Everything else keeps Wathe's original knife ray.
 * 决斗中，小刀/十字剑的射线只接受指定对手，攒出的刺杀 id 永远是对决目标，不会再打到路人；
 * 其他逻辑保持 Wathe 原本的射线行为。
 */
@Mixin(KnifeItem.class)
public abstract class KnightDuelKnifeRayTargetMixin {
    @Inject(method = "getKnifeTarget", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$restrictDuelKnifeRay(PlayerEntity player, CallbackInfoReturnable<HitResult> cir) {
        if (player == null) {
            return;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(player.getWorld());
        if (!duel.isDuelist(player.getUuid())) {
            return;
        }
        UUID targetId = duel.isKnight(player.getUuid()) ? duel.target() : duel.knight();
        if (targetId == null) {
            return;
        }
        PlayerEntity target = player.getWorld().getPlayerByUuid(targetId);
        if (target == null) {
            cir.setReturnValue(null);
            return;
        }
        cir.setReturnValue(ProjectileUtil.getCollision(player,
                entity -> entity == target && GameFunctions.isPlayerAliveAndSurvival(target), 3.0D));
    }
}
