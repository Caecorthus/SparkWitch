package dev.caecorthus.sparkwitch.client.mixin.kidnapper;

import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 鼠标滚轮不走 KeyBinding，因此单独阻止迷药控制期间的快捷栏切换。 */
@Mixin(Mouse.class)
public abstract class KidnapperControlledMouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockControlledScroll(
            long window,
            double horizontal,
            double vertical,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null
                && GameFunctions.isPlayerAliveAndSurvival(client.player)
                && KidnapperControlComponent.KEY.get(client.player).isControlled()) {
            ci.cancel();
        }
    }
}
