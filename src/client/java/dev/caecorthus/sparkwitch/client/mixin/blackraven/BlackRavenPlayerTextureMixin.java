package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Terminal Steve texture override after lower-priority skin transformations. / 在其他皮肤转换后执行的终端 Steve 贴图覆盖。 */
@Mixin(value = PlayerEntityRenderer.class, priority = 400)
public abstract class BlackRavenPlayerTextureMixin {
    private static final Identifier SPARKWITCH$STEVE_TEXTURE =
            Identifier.ofVanilla("textures/entity/player/wide/steve.png");

    @ModifyReturnValue(
            method = "getTexture(Lnet/minecraft/client/network/AbstractClientPlayerEntity;)Lnet/minecraft/util/Identifier;",
            at = @At("RETURN")
    )
    private Identifier sparkwitch$useFinalSteveTexture(
            Identifier originalTexture,
            AbstractClientPlayerEntity player
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null
                && player != client.player
                && BlackRavenClientState.isPerceptionActive(client.player)
                ? SPARKWITCH$STEVE_TEXTURE
                : originalTexture;
    }
}
