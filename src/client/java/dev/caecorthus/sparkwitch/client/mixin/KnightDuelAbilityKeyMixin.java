package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both duelists lose every G-key skill until the duel ends. / 决斗期间双方的所有 G 键技能失效。 */
@Mixin(KeyBinding.class)
public abstract class KnightDuelAbilityKeyMixin {
    @Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockDuelAbilityKey(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        InputUtil.Key key = ((KeyBindingAccessor) (Object) this).sparkwitch$getBoundKey();
        if (key.getCategory() != InputUtil.Type.KEYSYM || key.getCode() != GLFW.GLFW_KEY_G) {
            return;
        }
        if (KnightDuelWorldComponent.KEY.get(player.getWorld()).isDuelist(player.getUuid())) {
            cir.setReturnValue(false);
        }
    }
}
