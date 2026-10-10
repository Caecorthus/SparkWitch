package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.hooks.DeathRayClientHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * macOS Ctrl + left-click while the Death Ray is active (client intent only; the server validates every ray). Vanilla
 * 1.21.1 {@code onMouseButton} reads {@code MinecraftClient.IS_SYSTEM_MAC} exactly once, as the guard of its Ctrl-click
 * branch, so a sprinting (Left Ctrl) left click would use the held item and no ray would fire. This hook answers false
 * for that one read only when {@code DeathRayClientHooks.keepsMacLeftPress} says so (a left press, no screen, the
 * Death Ray owning the attack exactly as {@code tryFire} decides, no remapped release pending): the press stays
 * button 0 and is not counted, so its release stays button 0 too. Releases are never touched. It chains with every
 * other answer for the same read (the AXMC, the launcher); off macOS the read is false and nothing is evaluated.
 * 死亡射线生效时 macOS 上的 Ctrl + 左键（仅为客户端意图；服务端复核每次射线）。原版 1.21.1 {@code onMouseButton} 只读取
 * 一次 {@code MinecraftClient.IS_SYSTEM_MAC}，作为 Ctrl 点击分支的条件，因此疾跑（左 Ctrl）时的左键会使用手持物品，射线不会
 * 发射。仅当 {@code DeathRayClientHooks.keepsMacLeftPress} 判定（左键按下、无界面、死亡射线按 {@code tryFire} 的同一判断
 * 接管攻击、没有待重映射的松开）时，本钩子让这一次读取返回 false：按下保持为按键 0 且不计数，因此它的松开也保持为按键 0。
 * 从不改动松开。它与同一读取上的其他应答（AXMC、炮筒）串联；非 macOS 时读取值为 false，不做任何判断。
 */
@Mixin(Mouse.class)
public abstract class DeathRayMacControlClickMixin {
    @Shadow
    private int controlLeftClicks;

    @ModifyExpressionValue(method = "onMouseButton",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;IS_SYSTEM_MAC:Z",
                    opcode = Opcodes.GETSTATIC))
    private boolean sparkwitch$keepDeathRayLeftPressOnMac(boolean systemMac,
                                                          @Local(argsOnly = true, ordinal = 0) int button,
                                                          @Local(argsOnly = true, ordinal = 1) int action) {
        return systemMac && !DeathRayClientHooks.keepsMacLeftPress(MinecraftClient.getInstance(), button, action,
                controlLeftClicks);
    }
}
