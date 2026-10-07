package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianRecordedAction;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianServerHooks;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server knife-stab receiver, two hooks. HEAD (priority 2300, after SparkTraits' priority-2200 guards: Last Escape,
 * forced melee cooldown, raised-knife release) ends a validated puppet target; Wathe returns at once for a non-player
 * id, so cancelling there skips nothing Wathe would have done. The {@code recordItemUse} anchor records the stab for a
 * recording Magician, i.e. only a stab Wathe and SparkTraits accepted (a parried or out-of-range stab never replays).
 * Wathe member: class-level {@code remap = false}.
 * 服务端刀刺接收器，两个钩子。HEAD（优先级 2300，排在 SparkTraits 优先级 2200 的守卫之后：最后逃脱、强制近战冷却、举刀释放）
 * 结束经校验的皮套目标；Wathe 遇到非玩家 id 会立即返回，因此在此取消不会跳过 Wathe 本会执行的任何逻辑。
 * {@code recordItemUse} 锚点为录制中的魔术师记录这一刺，即只记录 Wathe 与 SparkTraits 已接受的刺击（被格挡或超距的刺击不会回放）。
 * Wathe 成员：类级 {@code remap = false}。
 */
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false, priority = 2300)
public abstract class MagicianKnifeStabPayloadReceiverMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$stabPuppet(KnifeStabPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayerEntity attacker = context.player();
        if (attacker.getServerWorld().getEntityById(payload.target()) instanceof MagicianPlaybackEntity puppet) {
            MagicianPuppetHits.onKnifeStabPayload(attacker, puppet);
            ci.cancel();
        }
    }

    @Inject(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/record/GameRecordManager;recordItemUse(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/nbt/NbtCompound;)V"))
    private void sparkwitch$recordAcceptedStab(KnifeStabPayload payload, ServerPlayNetworking.Context context,
                                               CallbackInfo ci) {
        MagicianServerHooks.record(context.player(), MagicianRecordedAction.Type.KNIFE_STAB);
    }
}
