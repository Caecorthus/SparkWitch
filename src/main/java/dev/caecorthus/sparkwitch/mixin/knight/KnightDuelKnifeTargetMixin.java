package dev.caecorthus.sparkwitch.mixin.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelKnives;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
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

/**
 * Server authority for the marked duel knife: a {@code KnifeStabPayload} made with it (main-hand knife, else off
 * hand, as Wathe's {@code use} picks) is accepted only from the duel target onto the Knight; every other marked-knife
 * stab is dropped before Wathe's receiver kills, records or sets a cooldown. Stabs with any other knife pass through
 * untouched. The handler body runs on the server thread.
 * 带标记决斗小刀的服务端权威：用它发出的 {@code KnifeStabPayload}（主手小刀，否则副手，与 Wathe {@code use} 的选手顺序
 * 一致）只接受决斗对手刺向骑士；其他所有带标记小刀的刺击在 Wathe 接收器击杀、记录或写冷却之前被丢弃。其他小刀的刺击
 * 原样放行。处理器本体在服务端线程执行。
 */
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false)
public abstract class KnightDuelKnifeTargetMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$limitDuelKnifeStab(KnifeStabPayload payload, ServerPlayNetworking.Context context,
                                               CallbackInfo ci) {
        ServerPlayerEntity player = context.player();
        if (!(player.getWorld() instanceof ServerWorld world)
                || !KnightDuelKnives.isDuelKnife(KnightDuelKnives.stabKnife(player))) {
            return;
        }
        Entity hit = world.getEntityById(payload.target());
        if (!KnightDuelKnives.allowsMarkedStab(KnightDuelWorldComponent.KEY.get(world), player,
                hit instanceof PlayerEntity victim ? victim.getUuid() : null)) {
            ci.cancel();
        }
    }
}
