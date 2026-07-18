package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.render.WraithSteveProjection;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies Steve after other player texture hooks. / 在其他玩家贴图 hook 后应用 Steve。 */
@Mixin(value = PlayerEntityRenderer.class, priority = 100)
public abstract class WraithPlayerSkinMixin {
    @ModifyReturnValue(
            method = "getTexture(Lnet/minecraft/client/network/AbstractClientPlayerEntity;)Lnet/minecraft/util/Identifier;",
            at = @At("RETURN")
    )
    private Identifier sparkwitch$projectSteveTexture(
            Identifier original,
            AbstractClientPlayerEntity player
    ) {
        return WraithSteveProjection.shouldAnonymizePlayer(player)
                ? WraithSteveProjection.steveTexture()
                : original;
    }
}
