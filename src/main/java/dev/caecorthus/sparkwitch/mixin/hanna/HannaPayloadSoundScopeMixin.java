package dev.caecorthus.sparkwitch.mixin.hanna;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaSoundScope;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hanna float silence, scope seam 4 of 4 (Fabric networking internals, the same seam as the Fear, stun, Seeker,
 * Riftwalker, crow-body, Knight and Natsume payload guards): custom payloads. Wathe's gun shot, knife stab and every
 * role skill arrive as custom payloads whose receivers play their sounds without a source, so the receiver has to run
 * inside {@link HannaSoundScope}.
 * <p>Fabric runs {@code receive} on the Netty thread; only the Runnable handed to {@code MinecraftServer#execute} runs
 * on the server thread. The scope therefore wraps the RUN of that Runnable, never the enqueue, and the sender is read
 * on the server thread. Nothing is filtered or dropped: every payload is scheduled and its handler runs exactly once.
 * Chains with the other {@code execute} wraps in either order (a payload another guard drops simply plays nothing
 * inside the scope).
 * 汉娜浮游静音，作用域接缝 4/4（Fabric 网络内部实现，与恐惧、眩晕、搜寻者、隙行者、化鸦本体、骑士、夏目安安的数据包拦截是同一
 * 接缝）：自定义数据包。Wathe 的开枪、刺击以及所有职业技能都以自定义数据包到达，其接收器以无来源方式播放声音，因此接收器必须在
 * {@link HannaSoundScope} 内运行。
 * <p>Fabric 在 Netty 线程上运行 {@code receive}；只有交给 {@code MinecraftServer#execute} 的 Runnable 在服务端主线程上运行。
 * 因此作用域包裹的是该 Runnable 的执行而非入队，发送者也在服务端线程上读取。不过滤、不丢弃：每个数据包都被调度，其处理器恰好
 * 执行一次。与其他 {@code execute} 包装以任意顺序链式叠加（被其他拦截丢弃的数据包只是在作用域内什么都不播放）。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class HannaPayloadSoundScopeMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V")
    )
    private void sparkwitch$scopeHannaPayloadSounds(MinecraftServer server, Runnable handler,
                                                    Operation<Void> original) {
        original.call(server, (Runnable) () -> HannaSoundScope.run(context.player(), handler));
    }
}
