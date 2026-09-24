package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.gui.OwnerInventoryPresenter;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LimitedInventoryScreen.class)
public abstract class WitchSkillInventoryScreenMixin extends LimitedHandledScreen<PlayerScreenHandler> {
    @Shadow @Final public ClientPlayerEntity player;
    public WitchSkillInventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @WrapMethod(method = "method_25394(Lnet/minecraft/class_332;IIF)V")
    private void sparkwitch$scopeOwnerInventory(DrawContext context, int mouseX, int mouseY, float delta, Operation<Void> original) {
        // WrapMethod surrounds the complete body including all injected TAILs; neither TAIL owns cleanup.
        try (var call = OwnerInventoryPresenter.begin(this, player, textRenderer)) {
            original.call(context, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "method_25394(Lnet/minecraft/class_332;IIF)V", at = @At("TAIL"))
    private void sparkwitch$renderOwnerSkill(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OwnerInventoryPresenter.draw(this, player, textRenderer, context, mouseX, mouseY);
    }
}
