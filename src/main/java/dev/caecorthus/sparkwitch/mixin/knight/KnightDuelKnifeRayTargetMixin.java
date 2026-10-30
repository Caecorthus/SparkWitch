package dev.caecorthus.sparkwitch.mixin.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelKnives;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelRules;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Client ray (Wathe calls {@code getKnifeTarget} from {@code onStoppedUsing} and the knife crosshair): only a stab
 * made with the marked duel knife ({@link KnightDuelKnives#stabKnife}) is narrowed. Held by the duel target it only
 * accepts the Knight; anywhere else, and when the Knight is not under the crosshair, it returns null, so neither a
 * bystander nor a sleeping player on a bed can be picked. A player's own knife keeps Wathe's original ray.
 * {@code KnightDuelKnifeTargetMixin} enforces the same rule on the server.
 * 客户端射线（Wathe 在 {@code onStoppedUsing} 与小刀准星中调用 {@code getKnifeTarget}）：只收窄用带标记决斗小刀
 * （{@link KnightDuelKnives#stabKnife}）进行的刺击。由决斗对手持有时只接受骑士；其他情况或骑士不在准星下时返回 null，
 * 因此既不会选中路人，也不会选中床上睡觉的玩家。玩家自己的小刀保持 Wathe 原本的射线。服务端由
 * {@code KnightDuelKnifeTargetMixin} 执行同一规则。
 */
@Mixin(KnifeItem.class)
public abstract class KnightDuelKnifeRayTargetMixin {
    @Inject(method = "getKnifeTarget", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$restrictDuelKnifeRay(PlayerEntity player, CallbackInfoReturnable<HitResult> cir) {
        if (player == null || !KnightDuelKnives.isDuelKnife(KnightDuelKnives.stabKnife(player))) {
            return;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(player.getWorld());
        UUID knightId = duel.isTarget(player.getUuid()) ? duel.knight() : null;
        PlayerEntity knight = knightId == null ? null : player.getWorld().getPlayerByUuid(knightId);
        if (knight == null) {
            cir.setReturnValue(null);
            return;
        }
        HitResult hit = ProjectileUtil.getCollision(player,
                entity -> entity == knight && GameFunctions.isPlayerAliveAndSurvival(knight),
                KnightDuelRules.KNIFE_RANGE);
        cir.setReturnValue(hit instanceof EntityHitResult ? hit : null);
    }
}
