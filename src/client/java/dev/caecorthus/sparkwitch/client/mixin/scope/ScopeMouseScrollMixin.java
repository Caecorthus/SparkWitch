package dev.caecorthus.sparkwitch.client.mixin.scope;

import dev.caecorthus.sparkwitch.client.scope.ScopeRules;
import dev.caecorthus.sparkwitch.client.scope.ScopeRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scope wheel zoom (client presentation only). Injected at the first read of {@code eventDeltaHorizontalWheel} in
 * {@code onMouseScroll}: vanilla gets there only for its own window with no overlay, no screen open and a player, after
 * every HEAD injector has run, so the HEAD locks on the same method (Control Expert stun, Seeker remote view, Rift
 * session, Kidnapper control, SparkStrength's stunned Engineer) still swallow the wheel first, and a screen still gets
 * its scroll. Here the active {@code ScopeProfile} is offered the wheel delta vanilla would accumulate
 * ({@link ScopeRules#wheelNotches}); when it consumes it, the method returns before vanilla accumulates the delta, so
 * the hotbar ({@code scrollInHotbar}, with SparkStrength's and Wathe's wrappers on it) and the spectator branch never
 * see it. Unscoped, nothing changes. The scope gate already excludes spectators, third person and other cameras.
 * 开镜滚轮缩放（仅客户端展示）。注入在 {@code onMouseScroll} 中第一次读取 {@code eventDeltaHorizontalWheel} 处：原版只有在
 * 本窗口、无遮罩、未打开界面且存在玩家时才会执行到这里，且此时所有 HEAD 注入都已运行，因此同一方法上的 HEAD 锁（控场专家
 * 眩晕、搜寻者遥控视角、隙行者会话、绑匪控制、SparkStrength 的工程师眩晕）仍会先吞掉滚轮，界面也仍会收到滚动。这里把原版
 * 将要累加的滚轮增量（{@link ScopeRules#wheelNotches}）交给当前生效的 {@code ScopeProfile}；被消耗时方法在原版累加之前返回，
 * 因此快捷栏（{@code scrollInHotbar} 及其上的 SparkStrength 与 Wathe 包装）与旁观分支都不会收到它。未开镜时不做任何改变。
 * 开镜条件本身已排除旁观者、第三人称与其他相机。
 */
@Mixin(Mouse.class)
public abstract class ScopeMouseScrollMixin {
    @Inject(
            method = "onMouseScroll",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Mouse;eventDeltaHorizontalWheel:D",
                    opcode = Opcodes.GETFIELD, ordinal = 0),
            cancellable = true
    )
    private void sparkwitch$scopeWheelZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        double notches = ScopeRules.wheelNotches(vertical, client.options.getDiscreteMouseScroll().getValue(),
                client.options.getMouseWheelSensitivity().getValue());
        if (ScopeRuntime.onMouseWheel(notches)) {
            ci.cancel();
        }
    }
}
