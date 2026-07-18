package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithClientState;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps Wathe's task HUD available to active Wraith participants. / 让激活冤魂继续使用 Wathe 任务界面。 */
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
        return WatheClient.isPlayerPlayingAndAlive()
                || WraithClientState.isActive(MinecraftClient.getInstance().player);
    }
}
