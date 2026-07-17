package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps Wathe's task HUD available to an active game-dead Wraith.
 * 冤魂虽保留对局死亡身份，仍可显示 Wathe 任务界面。
 */
@Mixin(value = MoodRenderer.class, remap = false)
public abstract class WraithMoodRendererMixin {
    @Redirect(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/client/WatheClient;isPlayerPlayingAndAlive()Z"
            )
    )
    private static boolean sparkwitch$renderTasksForActiveWraith() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        return WatheClient.isPlayerPlayingAndAlive() || WraithClientState.isActive(player);
    }
}
