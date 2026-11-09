package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.headset.HeadsetScreenFrames;
import dev.caecorthus.sparkwitch.headset.HeadsetSlot;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla survival inventory: draws a slot frame under the headset slot at (77,44), the free cell above the offhand,
 * after vanilla's background (the slot and its empty icon are drawn by vanilla afterwards). Presentation only.
 * 原版生存背包：在原版背景之后，于耳机栏位 (77,44)（副手上方的空格）下绘制栏位框（栏位及其空图标随后由原版绘制）。仅为展示。
 */
@Mixin(InventoryScreen.class)
public abstract class HeadsetInventoryScreenMixin extends AbstractInventoryScreen<PlayerScreenHandler> {
    protected HeadsetInventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V", at = @At("TAIL"))
    private void sparkwitch$drawHeadsetFrame(DrawContext context, float delta, int mouseX, int mouseY,
                                             CallbackInfo ci) {
        if (HeadsetScreenFrames.shown(handler)) {
            HeadsetScreenFrames.drawFrame(context, x + HeadsetSlot.X, y + HeadsetSlot.Y);
        }
    }
}
