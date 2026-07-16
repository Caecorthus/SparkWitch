package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Broad Steve adapter for consumers that read player skin metadata directly. / 为直接读取玩家皮肤元数据的渲染路径提供广义 Steve 适配。 */
@Mixin(value = AbstractClientPlayerEntity.class, priority = 400)
public abstract class BlackRavenSkinTexturesMixin {
    private static final SkinTextures SPARKWITCH$STEVE_SKIN = new SkinTextures(
            Identifier.ofVanilla("textures/entity/player/wide/steve.png"),
            null,
            null,
            null,
            SkinTextures.Model.WIDE,
            true
    );

    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$useSteveDuringPerception(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || player == client.player
                || !BlackRavenClientState.isPerceptionActive(client.player)) {
            return;
        }
        cir.setReturnValue(SPARKWITCH$STEVE_SKIN);
    }
}
