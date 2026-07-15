package dev.caecorthus.sparkwitch.client.mixin.hunter;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reuses Wathe's target crosshair only when the loaded shotgun has an unobstructed target.
 * 仅当已装填的霰弹枪锁定无遮挡目标时复用 Wathe 的目标准星。
 */
@Mixin(CrosshairRenderer.class)
public abstract class DoubleBarrelShotgunCrosshairMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;CROSSHAIR:Lnet/minecraft/util/Identifier;"
            )
    )
    private static Identifier sparkwitch$showShotgunTargetCrosshair(
            Identifier original,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof DoubleBarrelShotgunItem shotgun)
                || DoubleBarrelShotgunItem.getLoadedShells(stack) <= 0
                || player.getItemCooldownManager().isCoolingDown(shotgun)
                || DoubleBarrelShotgunItem.findTarget(player) == null) {
            return original;
        }
        return Identifier.of("wathe", "hud/crosshair_target");
    }
}
