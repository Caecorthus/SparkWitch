package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.impl.GrandWitchFearService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side safety net for NoellesRoles role-skill payloads during Fear.
 * 服务端兜底拦截恐惧期间的 NoellesRoles 角色技能包，防止客户端绕过。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class ServerPlayNetworkAddonFearMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$cancelFearedRoleSkill(
            ServerPlayNetworking.PlayPayloadHandler<?> payloadHandler,
            CustomPayload payload,
            CallbackInfo ci
    ) {
        if (payload == null || payload.getId() == null) {
            return;
        }
        ServerPlayerEntity player = context.player();
        if (!GrandWitchFearService.shouldBlockRoleSkillPayload(player, payload.getId().id())) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.execute(() -> GrandWitchFearService.sendSkillBlocked(player));
        }
        ci.cancel();
    }
}
