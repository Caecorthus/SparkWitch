package dev.caecorthus.sparkwitch.client.mixin.medium;

import dev.caecorthus.sparkwitch.client.medium.MediumHauntDrawer;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attaches the Medium's role-owned 招魂 drawer to Wathe's inventory screen (owner 2026-11-13: 招魂 lives in the
 * inventory, never in the witch skill panel). The drawer is one widget added at init; the render head only re-checks
 * its gate and layout, because a widget's own render is final and skipped while it is hidden.
 * 将通灵师自有的招魂抽屉挂到 Wathe 背包界面（所有者 2026-11-13：招魂位于背包页面，绝不进入魔女技能面板）。抽屉是 init 时加入的
 * 一个控件；渲染开头只重新检查其门槛与布局，因为控件自身的 render 为 final，且隐藏时不会被调用。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class MediumHauntInventoryScreenMixin extends LimitedHandledScreen<PlayerScreenHandler> {
    @Unique
    private MediumHauntDrawer sparkwitch$hauntDrawer;

    protected MediumHauntInventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    // Keep Yarn: intermediary selectors crash runClient; remapJar emits the intermediary form verifyClientMixinSelectors checks.
    // 保持 Yarn：intermediary 选择器会使 runClient 崩溃；remapJar 生成的 intermediary 形式由 verifyClientMixinSelectors 校验。
    @Inject(method = "init()V", at = @At("TAIL"))
    private void sparkwitch$attachHauntDrawer(CallbackInfo ci) {
        sparkwitch$hauntDrawer = MediumHauntDrawer.attach((LimitedInventoryScreen) (Object) this,
                this::addDrawableChild);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"))
    private void sparkwitch$refreshHauntDrawer(DrawContext context, int mouseX, int mouseY, float delta,
                                               CallbackInfo ci) {
        if (sparkwitch$hauntDrawer != null) {
            sparkwitch$hauntDrawer.refresh();
        }
    }
}
