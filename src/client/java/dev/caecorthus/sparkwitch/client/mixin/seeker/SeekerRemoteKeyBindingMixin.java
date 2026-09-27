package dev.caecorthus.sparkwitch.client.mixin.seeker;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteKeyDrain;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client prediction of the possession lock (the server rejects every blocked action on its own): while viewing, every
 * key outside {@code SeekerRemoteViewRules.ALLOWED_KEYS} and the voice-chat category reads as released. HEAD returns
 * before other mods' RETURN hooks on these methods, so a blocked press is neither recorded nor replayed;
 * {@code wasPressed} also zeroes the queue. The held flag is never changed. Each handler's first statement is the
 * {@code SeekerRemoteViewClient.isActive()} gate, so nothing changes outside a session.
 * 附身锁的客户端预测（服务端会独立拒绝所有被阻止的行为）：观看期间，{@code SeekerRemoteViewRules.ALLOWED_KEYS}
 * 与语音聊天分类之外的按键都视为未按下。HEAD 先于其他模组在这些方法上的 RETURN 钩子返回，被阻止的按键既不会被记录也不会被重放；
 * {@code wasPressed} 还会清零积压次数。按住标记从不改动。每个处理器的第一条语句都是
 * {@code SeekerRemoteViewClient.isActive()} 门槛，会话之外不做任何改变。
 */
@Mixin(KeyBinding.class)
public abstract class SeekerRemoteKeyBindingMixin implements SeekerRemoteKeyDrain {
    @Shadow
    private int timesPressed;

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$releaseWhileViewing(CallbackInfoReturnable<Boolean> cir) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        if (!SeekerRemoteViewClient.allowsKey((KeyBinding) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$discardPressWhileViewing(CallbackInfoReturnable<Boolean> cir) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        if (!SeekerRemoteViewClient.allowsKey((KeyBinding) (Object) this)) {
            timesPressed = 0;
            cir.setReturnValue(false);
        }
    }

    @Override
    public void sparkwitch$drainPresses() {
        timesPressed = 0;
    }
}
