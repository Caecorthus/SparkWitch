package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side input lock while flying the crow (the server refuses body actions on its own): the locked keys of
 * {@code BlackRavenCrowClientRules.LOCKED_KEYS} (attack, use, pick, drop, swap hands, inventory, hotbar 1-9, sprint and
 * movement) read as released for everyone; their queued presses are swallowed, and a drop or use press is handed to
 * the crow (feather drop, block use). Every other key keeps working (instinct, Perception, key 2, chat, voice). The
 * held flag is never changed; flight reads the movement keys physically. First statement: the {@code isActive()} gate.
 * 操控乌鸦期间的客户端输入锁（服务端会独立拒绝本体动作）：LOCKED_KEYS 中被锁定的按键（攻击、使用、选取、丢弃、换手、背包、快捷栏 1-9、
 * 疾跑与移动）对所有调用方都视为未按下；其积压按下被吞掉，丢弃或使用键的按下交给乌鸦（丢羽、方块交互）。其余按键照常（本能、感知、
 * 技能键 2、聊天、语音）。按住标记从不改动；飞行直接读取移动键的物理状态。
 */
@Mixin(KeyBinding.class)
public abstract class BlackRavenCrowKeyBindingMixin {
    @Shadow
    private int timesPressed;

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$releaseLockedKeyAsCrow(CallbackInfoReturnable<Boolean> cir) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        if (BlackRavenCrowPilotClient.locksKey((KeyBinding) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$swallowLockedPressAsCrow(CallbackInfoReturnable<Boolean> cir) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        KeyBinding key = (KeyBinding) (Object) this;
        if (BlackRavenCrowPilotClient.locksKey(key)) {
            BlackRavenCrowPilotClient.onSwallowedPresses(key, timesPressed);
            timesPressed = 0;
            cir.setReturnValue(false);
        }
    }
}
