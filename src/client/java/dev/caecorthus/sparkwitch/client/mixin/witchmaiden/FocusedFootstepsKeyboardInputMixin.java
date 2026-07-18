package dev.caecorthus.sparkwitch.client.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.client.witchmaiden.FocusedFootstepsInputController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies forced forward input after vanilla resolves every key. / 在原版完成按键解析后强制正向输入。 */
@Mixin(KeyboardInput.class)
public abstract class FocusedFootstepsKeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void sparkwitch$applyFocusedFootstepsInput(
            boolean slowDown,
            float slowDownFactor,
            CallbackInfo ci
    ) {
        FocusedFootstepsInputController.applyPlanarInput(
                (KeyboardInput) (Object) this,
                MinecraftClient.getInstance().player
        );
    }
}
