package dev.caecorthus.sparkwitch.mixin.natsume;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeHypnosisGuards;
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
 * External seam (Fabric networking internals, like the stun, Seeker, Riftwalker and crow-body payload guards): wraps the
 * single main-thread hand-off in {@code receive} so an item or skill payload from a hypnotised sender
 * ({@link NatsumeHypnosisGuards#blocksPayloadWhileLocked}: the stun deny-list plus the stab and Natsume ids) is dropped
 * on the server thread, atomically with the handler it would have run. On Netty only the id is checked; the lock is read
 * inside the scheduled Runnable. Every other payload is scheduled unchanged. Server authority: a modified client cannot
 * shoot, stab or cast while hypnotised. Chains with the other {@code MinecraftServer#execute} wraps.
 * 外部接缝（Fabric 网络内部实现，与眩晕、搜寻者、隙行者、化鸦本体的数据包拦截相同）：包装 {@code receive} 中唯一一次移交主线程的调用，
 * 使被催眠发送者的道具与技能数据包（眩晕拦截名单加上刺击与夏目安安 id）在服务端主线程上、与其原本要执行的处理器原子地被丢弃。Netty
 * 线程上只判断 id；锁状态在被调度的 Runnable 内读取。其他数据包原样调度。服务端权威：修改过的客户端在被催眠时无法开枪、刺击或施法。
 * 与其他 {@code MinecraftServer#execute} 包装链式叠加。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class NatsumeHypnosisPayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V")
    )
    private void sparkwitch$dropHypnotisedPayload(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!NatsumeHypnosisGuards.blocksPayloadWhileLocked(payloadId)) {
            original.call(server, handler);
            return;
        }
        original.call(server, (Runnable) () -> {
            if (!NatsumeHypnosisGuards.refusesPayload(context.player(), payloadId)) {
                handler.run();
            }
        });
    }
}
