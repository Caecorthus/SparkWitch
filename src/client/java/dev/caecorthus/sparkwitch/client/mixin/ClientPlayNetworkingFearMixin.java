package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.GrandWitchFearClientHooks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels role-skill packets locally while Fear is active.
 * 恐惧期间在客户端取消角色技能包，避免技能界面点击继续发包。
 */
@Mixin(value = ClientPlayNetworking.class, remap = false)
public abstract class ClientPlayNetworkingFearMixin {
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$cancelFearedRoleSkill(CustomPayload payload, CallbackInfo ci) {
        if (GrandWitchFearClientHooks.shouldBlockRoleSkillPayload(payload)) {
            ci.cancel();
        }
    }
}
