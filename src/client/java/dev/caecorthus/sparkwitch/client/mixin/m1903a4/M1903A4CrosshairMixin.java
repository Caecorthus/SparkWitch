package dev.caecorthus.sparkwitch.client.mixin.m1903a4;

import dev.caecorthus.sparkwitch.item.m1903a4.M1903A4Combat;
import dev.caecorthus.sparkwitch.item.m1903a4.M1903A4Item;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Wathe's revolver crosshair pair for the M1903A4: the normal crosshair while not charging (or charging at nothing),
 * the aimed crosshair while charging with a player on the ray. Both identifiers are Wathe's own sprites, so no new
 * textures are shipped.
 * M1903A4 使用 Wathe 左轮的两张准星：未蓄力（或蓄力但没瞄到人）时用常态准星，蓄力且射线上有玩家时用瞄准准星。两个
 * id 都是 Wathe 自带贴图，不新增资源。
 */
@Mixin(CrosshairRenderer.class)
public abstract class M1903A4CrosshairMixin {
    @Unique
    private static final Identifier SPARKWITCH$CROSSHAIR = Identifier.of("wathe", "hud/crosshair");
    @Unique
    private static final Identifier SPARKWITCH$CROSSHAIR_TARGET = Identifier.of("wathe", "hud/crosshair_target");

    @Redirect(
            method = "renderCrosshair",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V")
    )
    private static void sparkwitch$sniperCrosshair(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && player.getMainHandStack().getItem() instanceof M1903A4Item) {
            context.drawGuiTexture(M1903A4Combat.aimsAtTarget(player)
                    ? SPARKWITCH$CROSSHAIR_TARGET
                    : SPARKWITCH$CROSSHAIR, x, y, width, height);
            return;
        }
        context.drawGuiTexture(texture, x, y, width, height);
    }
}
