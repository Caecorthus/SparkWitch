package dev.caecorthus.sparkwitch.client.mixin.noa;

import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 诺亚's sanity bar keeps Wathe's own mood icon art, swapped for a cyan-shaded copy of the same sprite.
 * <p>The cyan copies are generated from Wathe's own {@code mood_happy/mid/depressive} pixels, so the shape and shading
 * match exactly; only the hue changes. Below 0% the vanilla sprite is used untouched (owner: "理智小于 0%
 * 调用 Wathe 原版的").
 * 诺亚的理智条沿用 Wathe 自己的心情图标画面，只是换成同一张 sprite 的青色版。
 * <p>青色图由 Wathe 的 {@code mood_happy/mid/depressive} 逐像素生成，形状与明暗完全一致，只换色相。
 * 理智低于 0% 时照原版绘制（所有者：“理智小于 0% 调用 Wathe 原版的”）。
 */
@Mixin(MoodRenderer.class)
public abstract class NoaMoodRendererMixin {

    private static final Identifier CYAN_HAPPY = Id.of("noa_mood_happy");
    private static final Identifier CYAN_MID = Id.of("noa_mood_mid");
    private static final Identifier CYAN_DEPRESSIVE = Id.of("noa_mood_depressive");
    /** 夏目安安's sanity bar is red-tinted. 夏目安安的理智条是红色调。 */
    private static final Identifier RED_HAPPY = Id.of("natsume_mood_happy");
    private static final Identifier RED_MID = Id.of("natsume_mood_mid");
    private static final Identifier RED_DEPRESSIVE = Id.of("natsume_mood_depressive");

    private interface Id {
        static Identifier of(String name) {
            return Identifier.of("sparkwitch", "textures/gui/" + name + ".png");
        }
    }

    @Redirect(
            method = "renderCivilian",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V")
    )
    private static void sparkwitch$swapMoodIcon(DrawContext context, Identifier sprite,
                                                int x, int y, int width, int height) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null || client.player.getWorld() == null) {
            context.drawGuiTexture(sprite, x, y, width, height);
            return;
        }
        var role = GameWorldComponent.KEY.get(client.player.getWorld()).getRole(client.player);
        boolean isNoa = role != null && NoaRules.ROLE_ID.equals(role.identifier());
        boolean isNatsume = role != null && dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeRules
                .ROLE_ID.equals(role.identifier());
        boolean nonNegative = PlayerMoodComponent.KEY.get(client.player).getMood() >= 0.0F;
        if ((!isNoa && !isNatsume) || !nonNegative) {
            context.drawGuiTexture(sprite, x, y, width, height);
            return;
        }
        String path = sprite.getPath();
        Identifier cyan;
        if (isNatsume) {
            cyan = path.endsWith("mood_happy") ? RED_HAPPY
                    : path.endsWith("mood_mid") ? RED_MID
                    : path.endsWith("mood_depressive") ? RED_DEPRESSIVE
                    : null;
        } else {
            cyan = path.endsWith("mood_happy") ? CYAN_HAPPY
                    : path.endsWith("mood_mid") ? CYAN_MID
                    : path.endsWith("mood_depressive") ? CYAN_DEPRESSIVE
                    : null;
        }
        if (cyan == null) {
            context.drawGuiTexture(sprite, x, y, width, height);
            return;
        }
        // Same footprint, same 14x17 source size. 同样的位置与 14x17 尺寸。
        context.drawTexture(cyan, x, y, 0, 0, width, height, 14, 17);
    }
}
