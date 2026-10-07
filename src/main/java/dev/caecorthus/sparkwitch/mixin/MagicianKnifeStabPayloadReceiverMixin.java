package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackManager;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianServerHooks;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 Wathe 匕首原始处理前收束魔术师皮套。 */
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false, priority = 1200)
public abstract class MagicianKnifeStabPayloadReceiverMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void sparkwitch$breakPlayback(KnifeStabPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayerEntity attacker = context.player();
        if (!attacker.getItemCooldownManager().isCoolingDown(dev.doctor4t.wathe.index.WatheItems.KNIFE)) MagicianServerHooks.record(attacker, dev.caecorthus.sparkwitch.roles.killer.magician.MagicianRecordedAction.Type.KNIFE_STAB);
        if (attacker.getServerWorld().getEntityById(payload.target()) instanceof MagicianPlaybackEntity playback) {
            MagicianPlaybackManager.stopByWeapon(playback, attacker, attacker.getMainHandStack().getTranslationKey());
            ci.cancel();
        }
    }
}
