package dev.caecorthus.sparkwitch.client.mixin.potiongunner;

import dev.caecorthus.sparkwitch.client.potiongunner.PotionScopeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the first-person hand and launcher while scoped (HEAD cancel, the Seeker remote-view precedent; Iris can
 * bypass {@code setRenderHand(false)}). Other players still see the two-handed pose. Client-only.
 * 开镜时隐藏第一人称手与炮筒（HEAD 取消，参照搜寻者遥控视角；Iris 可能绕过 {@code setRenderHand(false)}）。
 * 其他玩家仍能看到双手持握姿势。纯客户端。
 */
@Mixin(GameRenderer.class)
public abstract class PotionScopeGameRendererMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$hideScopedHand(CallbackInfo ci) {
        if (PotionScopeClient.isScoped(client)) {
            ci.cancel();
        }
    }
}
