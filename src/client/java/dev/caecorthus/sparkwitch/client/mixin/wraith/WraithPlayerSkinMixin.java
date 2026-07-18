package dev.caecorthus.sparkwitch.client.mixin.wraith;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.wraith.WraithSteveProjection;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies the terminal Steve texture to an anonymous player projection. / 为匿名玩家投影应用最终 Steve 贴图。 */
@Mixin(value = PlayerEntityRenderer.class, priority = 100)
public abstract class WraithPlayerSkinMixin {
    @ModifyReturnValue(
            method = "getTexture(Lnet/minecraft/client/network/AbstractClientPlayerEntity;)Lnet/minecraft/util/Identifier;",
            at = @At("RETURN")
    )
    private Identifier sparkwitch$projectSteveTexture(
            Identifier originalTexture,
            AbstractClientPlayerEntity player
    ) {
        return WraithSteveProjection.shouldAnonymizePlayer(player)
                ? WraithSteveProjection.steveTexture()
                : originalTexture;
    }
}
