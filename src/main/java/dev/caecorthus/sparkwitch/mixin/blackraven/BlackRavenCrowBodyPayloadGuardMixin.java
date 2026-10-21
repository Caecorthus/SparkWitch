package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowBodyGuards;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowForm;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam (Fabric networking internals, like the stun, Seeker and Riftwalker payload guards): wraps the single
 * main-thread hand-off in {@code receive} so a payload the frozen Raven Form body may not send
 * ({@link BlackRavenCrowBodyGuards#blocksPayloadInCrowForm}: Wathe's knife, gun and shop and every role skill except
 * Raven Form's own ids and Perception) is dropped silently on the server thread, atomically with the handler it would
 * have run. Only those ids are wrapped; every other payload is scheduled unchanged. Server authority: a modified
 * client cannot fire the body's gun while it flies the crow. Chains with the other {@code MinecraftServer#execute} wraps.
 * 外部接缝（Fabric 网络内部实现，与眩晕、搜寻者、隙行者的数据包拦截相同）：包装 {@code receive} 中唯一一次移交主线程的调用，使冻结本体不得发送的
 * 数据包（Wathe 刀、枪与商店，以及化鸦自身 id 与感知以外的所有职业技能）在服务端主线程上、与其原本要执行的处理器原子地被静默丢弃。只包装这些 id；
 * 其他数据包原样调度。服务端权威：修改过的客户端无法在操控乌鸦时用本体开枪。与其他 {@code MinecraftServer#execute} 包装链式叠加。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class BlackRavenCrowBodyPayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V")
    )
    private void sparkwitch$dropCrowBodyPayload(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!BlackRavenCrowBodyGuards.blocksPayloadInCrowForm(payloadId)) {
            original.call(server, handler);
            return;
        }
        original.call(server, (Runnable) () -> {
            if (!BlackRavenCrowForm.isInCrowForm(context.player())) {
                handler.run();
            }
        });
    }
}
