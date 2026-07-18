package dev.caecorthus.sparkwitch.client.mixin.wraith;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.wraith.WraithSteveProjection;
import dev.doctor4t.wathe.client.render.entity.PlayerBodyEntityRenderer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Makes every corpse anonymous in the local Wraith view. / 在本地冤魂视角中匿名显示所有尸体。 */
@Mixin(value = PlayerBodyEntityRenderer.class, priority = 100)
public abstract class WraithCorpseSkinMixin {
    @ModifyReturnValue(
            method = "getTexture(Ldev/doctor4t/wathe/entity/PlayerBodyEntity;)Lnet/minecraft/util/Identifier;",
            at = @At("RETURN")
    )
    private Identifier sparkwitch$projectCorpseSteveTexture(
            Identifier originalTexture,
            PlayerBodyEntity body
    ) {
        return WraithSteveProjection.shouldAnonymizeCorpses()
                ? WraithSteveProjection.steveTexture()
                : originalTexture;
    }
}
