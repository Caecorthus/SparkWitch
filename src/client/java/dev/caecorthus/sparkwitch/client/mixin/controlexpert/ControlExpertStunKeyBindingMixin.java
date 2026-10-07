package dev.caecorthus.sparkwitch.client.mixin.controlexpert;

import dev.caecorthus.sparkwitch.client.controlexpert.ControlExpertKeyDrain;
import dev.caecorthus.sparkwitch.client.controlexpert.ControlExpertStunClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client prediction of the stun lock: every key except the voice-chat category reads as released. HEAD cancels
 * return before other mods' RETURN hooks on these methods (ability-press capture, instinct toggles), so a press
 * during the stun is neither recorded nor replayed; {@code wasPressed} also zeroes the queue. The held flag is
 * never changed, so a key still held when the stun ends works again at once.
 * 眩晕锁的客户端预测：除语音聊天分类外的所有按键都视为未按下。HEAD 取消会先于其他模组在这些方法上的
 * RETURN 钩子返回（技能按键捕获、本能切换），因此眩晕期间的按键既不会被记录也不会被重放；
 * {@code wasPressed} 还会清零排队次数。按住标记从不改动，眩晕结束时仍按住的键会立即恢复作用。
 */
@Mixin(KeyBinding.class)
public abstract class ControlExpertStunKeyBindingMixin implements ControlExpertKeyDrain {
    @Shadow
    private int timesPressed;

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$releaseWhileStunned(CallbackInfoReturnable<Boolean> cir) {
        if (ControlExpertStunClient.blocksKey(((KeyBinding) (Object) this).getCategory())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$discardPressWhileStunned(CallbackInfoReturnable<Boolean> cir) {
        if (ControlExpertStunClient.blocksKey(((KeyBinding) (Object) this).getCategory())) {
            timesPressed = 0;
            cir.setReturnValue(false);
        }
    }

    @Override
    public void sparkwitch$discardQueuedPresses() {
        timesPressed = 0;
    }
}
