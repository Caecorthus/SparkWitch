package dev.caecorthus.sparkwitch.client.mixin.bear;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.bear.launcher.M32FireInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * macOS Ctrl + left-click with the M32A1 (client intent only; the server validates every shot), the launcher's
 * {@code PotionLauncherMacControlClickMixin} for the M32A1. Vanilla 1.21.1 {@code onMouseButton} reads
 * {@code MinecraftClient.IS_SYSTEM_MAC} once, as the guard of its Ctrl-click branch, so a sprinting (Left Ctrl) left
 * click would aim instead of firing. This hook answers false for that read only when
 * {@code M32FireInput.keepsMacLeftPress} says so; releases are never touched, and it chains with every other answer
 * for the same read (AXMC, launcher, Death Ray). Off macOS the read is false and nothing is evaluated.
 * macOS 上持 M32A1 的 Ctrl + 左键（仅为客户端意图；服务端复核每次发射），即炮筒 {@code PotionLauncherMacControlClickMixin} 用于
 * M32A1。原版 1.21.1 {@code onMouseButton} 只读取一次 {@code MinecraftClient.IS_SYSTEM_MAC}，作为 Ctrl 点击分支的条件，因此疾跑
 * （左 Ctrl）时的左键会瞄准而不是发射。仅当 {@code M32FireInput.keepsMacLeftPress} 判定时，本钩子让这一次读取返回 false；从不改动
 * 松开，并与同一读取上的其他应答（AXMC、炮筒、死亡射线）串联。非 macOS 时读取值为 false，不做任何判断。
 */
@Mixin(Mouse.class)
public abstract class M32MacControlClickMixin {
    @Shadow
    private int controlLeftClicks;

    @ModifyExpressionValue(method = "onMouseButton",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;IS_SYSTEM_MAC:Z",
                    opcode = Opcodes.GETSTATIC))
    private boolean sparkwitch$keepM32LeftPressOnMac(boolean systemMac,
                                                     @Local(argsOnly = true, ordinal = 0) int button,
                                                     @Local(argsOnly = true, ordinal = 1) int action) {
        return systemMac && !M32FireInput.keepsMacLeftPress(MinecraftClient.getInstance(), button, action,
                controlLeftClicks);
    }
}
