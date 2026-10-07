package dev.caecorthus.sparkwitch.client.mixin.riftwalker;

import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionClient;
import net.minecraft.client.gui.hud.SpectatorHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Occupants are alive spectators (D3), so vanilla offers them the spectator menu: number keys ({@code selectSlot}),
 * menu scrolling ({@code cycleSlot}) and middle-click ({@code useSelectedCommand}, which could request a spectator
 * teleport). All three are cancelled at HEAD while inside; the server also refuses spectator teleports (P2). The menu
 * itself is replaced by the arrow bar in {@code RiftSessionInGameHudMixin}. First statement: the {@code isActive()} gate.
 * 门内的人是活着的旁观者（D3），原版会为其提供旁观者菜单：数字键（{@code selectSlot}）、菜单滚动（{@code cycleSlot}）与中键
 * （{@code useSelectedCommand}，可能发出旁观者传送请求）。门内时三者都在 HEAD 取消；服务端同样拒绝旁观者传送（P2）。菜单本身由
 * {@code RiftSessionInGameHudMixin} 替换为箭头栏。第一条语句为 {@code isActive()} 门槛。
 */
@Mixin(SpectatorHud.class)
public abstract class RiftSessionSpectatorHudMixin {
    @Inject(method = "selectSlot", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noSpectatorSlotInsideRift(int slot, CallbackInfo ci) {
        if (!RiftSessionClient.isActive()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "cycleSlot", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noSpectatorCycleInsideRift(int offset, CallbackInfo ci) {
        if (!RiftSessionClient.isActive()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "useSelectedCommand", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noSpectatorCommandInsideRift(CallbackInfo ci) {
        if (!RiftSessionClient.isActive()) {
            return;
        }
        ci.cancel();
    }
}
