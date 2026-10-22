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
 * Priority 900 applies this wrapper before every default-priority {@code execute} wrapper (stun, Seeker, Rift, crow
 * body, and other mods'): the first-applied wrapper is innermost, so its Runnable runs first on the server thread.
 * Vendetta and Fear are therefore judged before every other guard, as the former HEAD inject was, and a payload
 * refused by Fear and another guard still shows the Fear message.
 * 服务端兜底拦截：仇杀目标数据包，以及恐惧期间的 NoellesRoles 角色技能包，防止客户端绕过。
 * 优先级 900 使本包装器先于所有默认优先级的 {@code execute} 包装器（眩晕、搜寻者、裂隙、乌鸦本体及其他模组的）应用：
 * 最先应用的包装器位于最内层，其 Runnable 在服务端主线程上最先运行。因此仇杀与恐惧先于其他所有拦截判定，与原先的 HEAD
 * 注入一致；同时被恐惧和其他拦截拒绝的数据包仍会显示恐惧提示。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false, priority = 900)
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
