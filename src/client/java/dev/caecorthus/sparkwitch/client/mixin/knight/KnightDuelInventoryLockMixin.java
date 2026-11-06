package dev.caecorthus.sparkwitch.client.mixin.knight;

import dev.caecorthus.sparkwitch.client.knight.KnightDuelUiLock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks every open path of the inventory (including the wathe {@code LimitedInventoryScreen}, which carries the shop
 * and the skills panel) while the local player is an active duelist. Wathe substitutes its limited screen inside
 * {@code handleInputEvents}, but the actual request still reaches {@code setScreen}, so this covers E and any other path.
 * 本地玩家处于进行中的决斗时，拦截打开物品栏的所有路径（包括承载商店与技能面板的 Wathe
 * {@code LimitedInventoryScreen}）。Wathe 在 {@code handleInputEvents} 内替换界面，但真正的请求仍会到达
 * {@code setScreen}，因此 E 与其他打开路径都会被覆盖。
 */
@Mixin(MinecraftClient.class)
public abstract class KnightDuelInventoryLockMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockDuelInventory(Screen screen, CallbackInfo ci) {
        if (KnightDuelUiLock.blocks(screen)) {
            ci.cancel();
        }
    }
}
