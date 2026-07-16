package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Excludes both Black Raven loadout items before Wathe's death-drop loop. */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsBlackRavenDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeBlackRavenLoadout(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (stack.isOf(SparkWitchItems.featherBlade()) || stack.isOf(SparkWitchItems.blackRavenLedger())) {
            cir.setReturnValue(false);
        }
    }
}
