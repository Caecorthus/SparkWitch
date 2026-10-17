package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaTargetingPacketGuard;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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
        ServerPlayerEntity player = context.player();
        if (VendettaTargetingPacketGuard.shouldBlock(player, payload)) {
            ci.cancel();
            return;
        }
        if (payload == null || payload.getId() == null) {
            return;
        }
        if (!GrandWitchFearService.shouldBlockRoleSkillPayload(player, payload.getId().id())) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.execute(() -> GrandWitchFearService.sendSkillBlocked(player));
        }
        ci.cancel();
    }

    /**
     * The check above runs on the network thread, so Fear bought by a handler already queued ahead of this payload
     * would slip past it. Like the stun guard, re-check on the server thread, atomically with the handler. Only
     * fear-blocked ids are wrapped; every other payload is scheduled unchanged.
     * 上面的检查在网络线程上运行，排在此数据包之前、已入队的处理器买下的恐惧会漏过它。与眩晕守卫相同，在服务端主线程上、
     * 与处理器原子地再检查一次。仅包装受恐惧限制的 id；其他数据包原样调度。
     */
    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V")
    )
    private void sparkwitch$recheckFearOnServerThread(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!GrandWitchFearService.isBlockedRoleSkillPayload(payloadId)) {
            original.call(server, handler);
            return;
        }
        ServerPlayerEntity player = context.player();
        original.call(server, (Runnable) () -> {
            if (GrandWitchFearService.shouldBlockRoleSkillPayload(player, payloadId)) {
                GrandWitchFearService.sendSkillBlocked(player);
            } else {
                handler.run();
            }
        });
    }
}
