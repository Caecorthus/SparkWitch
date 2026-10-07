package dev.caecorthus.sparkwitch.client.mixin.riftwalker;

import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionClient;
import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionKeyAccess;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client prediction of the in-gate input lock (the server guards every payload on its own): while inside, every key
 * outside {@code RiftSessionInputRules.ALLOWED_KEYS} and the voice-chat category reads as released, so attack, use,
 * pick, hotbar (incl. the spectator menu), drop, inventory, jump, sprint, perspective, spectator outlines and skill keys
 * never reach vanilla or other mods. HEAD returns before other mods' RETURN hooks; {@code wasPressed} also zeroes the
 * queue; the held flag is never changed. The duck methods give {@code RiftSessionClient} the raw state of the keys it
 * maps itself (Shift, A/D, use, 1/2). Each handler's first statement is the {@code isActive()} gate.
 * 门内输入锁的客户端预测（服务端会独立守卫所有数据包）：在门内时，{@code RiftSessionInputRules.ALLOWED_KEYS} 与语音聊天分类之外的
 * 按键都视为未按下，因此攻击、使用、选取、快捷栏（含旁观者菜单）、丢弃、背包、跳跃、疾跑、视角、旁观者描边与技能键都不会到达原版或
 * 其他模组。HEAD 先于其他模组的 RETURN 钩子返回；{@code wasPressed} 还会清零积压次数；按住标记从不改动。鸭子方法让
 * {@code RiftSessionClient} 读取其自行映射的按键（Shift、A/D、使用、1/2）的原始状态。每个处理器第一条语句都是 {@code isActive()} 门槛。
 */
@Mixin(KeyBinding.class)
public abstract class RiftSessionKeyBindingMixin implements RiftSessionKeyAccess {
    @Shadow
    private int timesPressed;

    @Shadow
    private boolean pressed;

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$releaseInsideRift(CallbackInfoReturnable<Boolean> cir) {
        if (!RiftSessionClient.isActive()) {
            return;
        }
        if (!RiftSessionClient.allowsKey((KeyBinding) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$discardPressInsideRift(CallbackInfoReturnable<Boolean> cir) {
        if (!RiftSessionClient.isActive()) {
            return;
        }
        if (!RiftSessionClient.allowsKey((KeyBinding) (Object) this)) {
            timesPressed = 0;
            cir.setReturnValue(false);
        }
    }

    @Override
    public void sparkwitch$riftDrainPresses() {
        timesPressed = 0;
    }

    @Override
    public int sparkwitch$riftTakePresses() {
        int presses = timesPressed;
        timesPressed = 0;
        return presses;
    }

    @Override
    public boolean sparkwitch$riftRawPressed() {
        return pressed;
    }
}
