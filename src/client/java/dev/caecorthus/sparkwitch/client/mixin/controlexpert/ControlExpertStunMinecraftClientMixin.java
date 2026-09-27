package dev.caecorthus.sparkwitch.client.mixin.controlexpert;

import dev.caecorthus.sparkwitch.client.controlexpert.ControlExpertStunClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-tick backup of the stun start edge: before vanilla reads input, drop any item use (without a release) and
 * any block breaking. It never cancels input handling, so other mods' hooks keep running.
 * 眩晕起始边沿的逐刻兜底：在原版读取输入之前，丢弃任何物品使用（不触发松手）与方块挖掘。
 * 从不取消输入处理，其他模组的钩子照常运行。
 */
@Mixin(MinecraftClient.class)
public abstract class ControlExpertStunMinecraftClientMixin {
    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void sparkwitch$settleStunnedInput(CallbackInfo ci) {
        ControlExpertStunClient.settleInput((MinecraftClient) (Object) this);
    }
}
