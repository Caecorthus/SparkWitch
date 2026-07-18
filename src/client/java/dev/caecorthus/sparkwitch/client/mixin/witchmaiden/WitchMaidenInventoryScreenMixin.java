package dev.caecorthus.sparkwitch.client.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.client.witchmaiden.WitchMaidenInventoryUi;
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

/** Attaches the role-owned avatar row to Wathe's existing inventory screen. / 将角色自有头像栏挂到 Wathe 现有背包。 */
@Mixin(LimitedInventoryScreen.class)
public abstract class WitchMaidenInventoryScreenMixin extends LimitedHandledScreen<PlayerScreenHandler> {
    @Unique
    private WitchMaidenInventoryUi sparkwitch$witchMaidenUi;

    protected WitchMaidenInventoryScreenMixin(
            PlayerScreenHandler handler,
            PlayerInventory inventory,
            Text title
    ) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sparkwitch$attachWitchMaidenUi(CallbackInfo ci) {
        sparkwitch$witchMaidenUi = WitchMaidenInventoryUi.attach(
                (LimitedInventoryScreen) (Object) this,
                this::addDrawableChild
        );
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderWitchMaidenEmptyState(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        if (sparkwitch$witchMaidenUi != null) {
            sparkwitch$witchMaidenUi.renderEmptyState(context);
        }
    }
}
