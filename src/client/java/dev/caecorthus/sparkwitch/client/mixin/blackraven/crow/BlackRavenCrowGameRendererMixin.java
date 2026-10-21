package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only render fixes while flying the crow: {@code updateCrosshairTarget} RETURN (every exit) replaces the target
 * with the block the crow's eye looks at within reach (or a miss) and clears {@code targetedEntity}, so nothing near the
 * crow can be attacked, used or picked by another ability; {@code renderHand} HEAD cancels the body's first-person hand
 * and item (Iris can bypass {@code setRenderHand(false)}). First statement: the {@code isActive()} gate.
 * 操控乌鸦期间的纯客户端渲染修正：updateCrosshairTarget 在所有返回点把目标替换为乌鸦眼睛在使用距离内看着的方块（或未命中），并清空
 * targetedEntity，乌鸦附近的任何东西都不会被攻击、使用或被其他技能选中；renderHand 在开头取消本体的第一人称手与物品（Iris 可能绕过
 * setRenderHand(false)）。
 */
@Mixin(GameRenderer.class)
public abstract class BlackRavenCrowGameRendererMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("RETURN"))
    private void sparkwitch$crowCrosshairTarget(float tickDelta, CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        BlackRavenCrowPilotClient.overrideCrosshairTarget(client, tickDelta);
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$hideBodyHandAsCrow(CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        ci.cancel();
    }
}
