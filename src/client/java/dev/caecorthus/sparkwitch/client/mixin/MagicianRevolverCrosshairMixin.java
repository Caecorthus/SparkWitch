package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.At;

/** Wathe 的准星逻辑只识别 PlayerEntity；补充魔术师皮套作为左轮有效目标。 */
@Mixin(CrosshairRenderer.class)
public abstract class MagicianRevolverCrosshairMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(value = "FIELD", target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;CROSSHAIR:Lnet/minecraft/util/Identifier;")
    )
    private static Identifier sparkwitch$showPlaybackTargetCrosshair(
            Identifier original,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(WatheItems.REVOLVER)
                || player.getItemCooldownManager().isCoolingDown(WatheItems.REVOLVER)) return original;
        HitResult hit = ProjectileUtil.getCollision(player, entity ->
                entity instanceof MagicianPlaybackEntity
                        || (entity instanceof PlayerEntity target
                        && GameFunctions.isPlayerAliveAndSurvival(target)
                        && !target.isInvisible()), 30.0F);
        return hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof MagicianPlaybackEntity
                ? Identifier.of("wathe", "hud/crosshair_target")
                : original;
    }
}
