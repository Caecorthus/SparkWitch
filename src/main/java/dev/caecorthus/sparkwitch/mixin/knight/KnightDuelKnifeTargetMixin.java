package dev.caecorthus.sparkwitch.mixin.knight;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** During a duel the Knight's cross sword only hits the target and the target's knife only hits the Knight. / 决斗中十字剑只命中目标，对手的刀只命中骑士。 */
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false)
public abstract class KnightDuelKnifeTargetMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$limitDuelStab(KnifeStabPayload payload, ServerPlayNetworking.Context context,
                                          CallbackInfo ci) {
        ServerPlayerEntity player = context.player();
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
        if (!duel.isDuelist(player.getUuid())) {
            return;
        }
        Entity hit = world.getEntityById(payload.target());
        UUID hitId = hit instanceof PlayerEntity target ? target.getUuid() : null;
        if (duel.isKnight(player.getUuid())) {
            if (player.getMainHandStack().isOf(SparkWitchItems.crossSword()) && !duel.isTarget(hitId)) {
                ci.cancel();
            }
        } else if ((player.getMainHandStack().isOf(WatheItems.KNIFE)
                || player.getOffHandStack().isOf(WatheItems.KNIFE)) && !duel.isKnight(hitId)) {
            ci.cancel();
        }
    }
}
