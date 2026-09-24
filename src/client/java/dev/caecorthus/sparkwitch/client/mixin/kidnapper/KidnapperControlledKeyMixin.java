package dev.caecorthus.sparkwitch.client.mixin.kidnapper;

import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 迷药控制期间禁用攻击、使用和快捷栏数字键，但保留移动输入。 */
@Mixin(value = KeyBinding.class, priority = 5000)
public abstract class KidnapperControlledKeyMixin {
    @Unique
    private void sparkwitch$lockControlledKeys(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !GameFunctions.isPlayerAliveAndSurvival(client.player)) {
            return;
        }

        KeyBinding key = (KeyBinding) (Object) this;
        boolean useKey = key.equals(client.options.useKey);
        boolean attackKey = key.equals(client.options.attackKey);
        boolean hotbarKey = false;
        for (KeyBinding hotbarBinding : client.options.hotbarKeys) {
            if (key.equals(hotbarBinding)) {
                hotbarKey = true;
                break;
            }
        }
        if (KidnapperControlComponent.KEY.get(client.player).isControlled()
                && (useKey || attackKey || hotbarKey)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "wasPressed", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$lockWasPressed(CallbackInfoReturnable<Boolean> cir) {
        sparkwitch$lockControlledKeys(cir);
    }

    @Inject(method = "isPressed", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$lockIsPressed(CallbackInfoReturnable<Boolean> cir) {
        sparkwitch$lockControlledKeys(cir);
    }
}
