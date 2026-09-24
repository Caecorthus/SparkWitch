package dev.caecorthus.sparkwitch.mixin.bellringer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellRingerEchoRuntime;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bell Echo seams inside Wathe's mood loop: hold normal task generation, keep the Echo completion out of
 * {@code TaskComplete.EVENT}, and scale drain while an Echo task is active. Never {@code @Redirect}
 * (SparkTraits already redirects call sites here); coexists with the Wraith HEAD cancel. Every target
 * descriptor uses only Wathe/Fabric/JDK types, so {@code remap = false} holds in dev and production.
 * Pinned Wathe 1.5.6 bytecode: serverTick has exactly one {@code generateTask()} call, one
 * {@code Event.invoker()} call (TaskComplete) with a single TrainTask local in scope, and one GETSTATIC
 * {@code MOOD_DRAIN}; clientTick has one GETSTATIC {@code MOOD_DRAIN}.
 * Wathe 情绪循环中的钟声回响接口：暂停常规任务生成、使回响任务完成不触发 {@code TaskComplete.EVENT}、
 * 并在回响任务期间放大消耗。禁止使用 {@code @Redirect}（SparkTraits 已重定向此处调用点）；与冤魂的 HEAD 取消共存。
 * 所有目标描述符仅含 Wathe/Fabric/JDK 类型，因此 {@code remap = false} 在开发与生产环境均成立。
 * 锁定 Wathe 1.5.6 字节码：serverTick 恰有一次 {@code generateTask()} 调用、一次 {@code Event.invoker()}
 * 调用（TaskComplete，作用域内仅一个 TrainTask 局部变量）和一次 GETSTATIC {@code MOOD_DRAIN}；
 * clientTick 恰有一次 GETSTATIC {@code MOOD_DRAIN}。
 */
@Mixin(value = PlayerMoodComponent.class, remap = false)
public abstract class PlayerMoodComponentBellEchoMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    /**
     * Server authority: no normal task is generated while an Echo task is active; Wathe null-checks the
     * result and simply re-rolls its next-task timer.
     * 服务端权威：回响任务期间不生成常规任务；Wathe 会对结果判空并重新抽取下一次任务计时。
     */
    @WrapOperation(
            method = "serverTick",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/PlayerMoodComponent;generateTask()Ldev/doctor4t/wathe/cca/PlayerMoodComponent$TrainTask;"
            ),
            require = 1,
            allow = 1
    )
    private PlayerMoodComponent.TrainTask sparkwitch$holdTasksDuringBellEcho(
            PlayerMoodComponent instance,
            Operation<PlayerMoodComponent.TrainTask> original
    ) {
        if (BellRingerEchoRuntime.holdsNormalTasks(player)) {
            return null;
        }
        return original.call(instance);
    }

    /**
     * Server authority: the Echo task keeps Wathe's +0.5 mood and TaskCompletePayload (both run before this
     * call) but swaps the TaskComplete invoker for a no-op, so no mod's listener pays or counts it.
     * 服务端权威：回响任务保留 Wathe 的 +0.5 理智与 TaskCompletePayload（二者都在此调用之前执行），
     * 但将 TaskComplete 调用器替换为空实现，使任何模组的监听器都不会为其发放奖励或计数。
     */
    @ModifyExpressionValue(
            method = "serverTick",
            at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/api/event/Event;invoker()Ljava/lang/Object;"),
            require = 1,
            allow = 1
    )
    private Object sparkwitch$suppressBellEchoTaskComplete(
            Object invoker,
            @Local PlayerMoodComponent.TrainTask task
    ) {
        return BellRingerEchoRuntime.consumeEchoCompletion(player, task)
                ? BellRingerEchoRuntime.SUPPRESSED_TASK_COMPLETE
                : invoker;
    }

    /**
     * Both sides: multiplies the per-task drain constant, so it composes multiplicatively with the
     * SparkTraits and SparkStrength {@code setMood} argument modifiers instead of replacing them.
     * The client reads the owner-synced Echo marker for its local drain prediction.
     * 双端：放大单任务消耗常量，从而与 SparkTraits、SparkStrength 对 {@code setMood} 参数的修改相乘叠加而非覆盖。
     * 客户端依据仅同步给拥有者的回响标记进行本地消耗预测。
     */
    @ModifyExpressionValue(
            method = {"serverTick", "clientTick"},
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/game/GameConstants;MOOD_DRAIN:F",
                    opcode = Opcodes.GETSTATIC
            ),
            require = 2,
            allow = 2
    )
    private float sparkwitch$scaleBellEchoDrain(float drain) {
        return BellRingerEchoRuntime.scaleDrain(player, drain);
    }
}
