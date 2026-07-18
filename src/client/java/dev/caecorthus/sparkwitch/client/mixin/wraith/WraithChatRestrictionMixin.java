package dev.caecorthus.sparkwitch.client.mixin.wraith;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.wraith.WraithClientState;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps Wathe text chat blocked for every active Wraith phase. / 在冤魂所有激活阶段持续沿用 Wathe 文字聊天限制。 */
@Mixin(value = WatheClient.class, remap = false, priority = 100)
public abstract class WraithChatRestrictionMixin {
    @ModifyReturnValue(method = "shouldDisableChat", at = @At("RETURN"))
    private static boolean sparkwitch$keepWraithChatClosed(boolean original) {
        return original || (WraithClientState.isActive(MinecraftClient.getInstance().player)
                && WatheClient.gameComponent != null
                && WatheClient.gameComponent.getGameStatus()
                != GameWorldComponent.GameStatus.INACTIVE);
    }
}
