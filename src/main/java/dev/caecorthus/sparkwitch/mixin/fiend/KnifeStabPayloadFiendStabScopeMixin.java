package dev.caecorthus.sparkwitch.mixin.fiend;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendStabScope;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Server knife-stab receiver (Wathe member, {@code remap = false}): scopes the whole receive, including the HEAD
 * replacement handlers of SparkStrength (Veteran, Silencer, Coroner) and SparkTraits, so a Fiend stab counts as a
 * hand-held knife even when the Veteran's last stab removed the knife first. The receiver always runs exactly once with
 * the original arguments; only a thread-local marker is added while the target is a Fiend.
 * 服务端刀刺接收器（Wathe 成员，{@code remap = false}）：包裹整个接收过程，包括 SparkStrength（老兵、沉默者、验尸官）与
 * SparkTraits 的 HEAD 替换处理器，因此即使老兵最后一刺先移除了刀，对魔人的刺击仍算手持刀。接收器始终以原参数恰好执行
 * 一次；仅在目标为魔人时附加一个线程局部标记。
 */
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false)
public abstract class KnifeStabPayloadFiendStabScopeMixin {
    @WrapMethod(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V")
    private void sparkwitch$scopeFiendStab(KnifeStabPayload payload, ServerPlayNetworking.Context context,
                                          Operation<Void> original) {
        FiendStabScope.runStab(context.player(), payload.target(), () -> original.call(payload, context));
    }
}
