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

/**
 * Server-side safety net for Vendetta target payloads and NoellesRoles role-skill payloads during Fear.
 * 服务端兜底拦截：仇杀目标数据包，以及恐惧期间的 NoellesRoles 角色技能包，防止客户端绕过。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class ServerPlayNetworkAddonFearMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    /**
     * Fabric runs {@code receive} on the Netty thread (vanilla {@code ServerPlayNetworkHandler#onCustomPayload} has no
     * {@code forceMainThread}); only the Runnable handed to {@code MinecraftServer#execute} runs on the server thread.
     * So the network thread only picks payloads by class and id; Vendetta's target lookup and pair check and the Fear
     * state are read on the server thread, atomically with the handler, so a refused payload never reaches it. Vendetta
     * drops silently before Fear; every other payload is scheduled unchanged, like the stun, Seeker, Rift and crow
     * wrappers.
     * Fabric 在 Netty 线程上运行 {@code receive}（原版 {@code ServerPlayNetworkHandler#onCustomPayload} 不调用
     * {@code forceMainThread}）；只有交给 {@code MinecraftServer#execute} 的 Runnable 在服务端主线程上运行。因此网络线程只按类与
     * id 挑选数据包；仇杀的目标查找与配对判定以及恐惧状态都在服务端主线程上、与处理器原子地读取，被拒绝的数据包永远到不了处理器。
     * 仇杀先于恐惧静默丢弃；其他数据包原样调度，与眩晕、搜寻者、裂隙与乌鸦包装器相同。
     */
    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V")
    )
    private void sparkwitch$guardTargetAndFearedPayloads(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!VendettaTargetingPacketGuard.isTargetPayload(payload)
                && !GrandWitchFearService.isBlockedRoleSkillPayload(payloadId)) {
            original.call(server, handler);
            return;
        }
        original.call(server, (Runnable) () -> {
            ServerPlayerEntity player = context.player();
            if (VendettaTargetingPacketGuard.shouldBlock(player, payload)) {
                return;
            }
            if (GrandWitchFearService.shouldBlockRoleSkillPayload(player, payloadId)) {
                if (!GrandWitchFearService.isSilentlyBlockedPayload(payloadId)) {
                    GrandWitchFearService.sendSkillBlocked(player);
                }
                return;
            }
            handler.run();
        });
    }
}
