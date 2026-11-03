package dev.caecorthus.sparkwitch.client.mixin.cat;

import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rewrites the first announcement line for the cat's transformation.
 * <p>Mixin demands every static field be private, so all state here is private.
 * Mixin 要求所有静态字段必须是 private，因此这里的字段全部私有。
 */
@Mixin(value = RoundTextRenderer.class, remap = false)
public abstract class CatTransitionWelcomeMixin {

    /** Sentinel {@code targets} value marking an announcement as cat-originated. */
    /** 哨兵 targets 值：标记这次报幕来自猫。 */
    private static final int SPARKWITCH$CAT_TARGETS = -999;

    /** Whether the announcement currently on screen belongs to the cat. */
    /** 当前屏幕上的报幕是否来自猫。 */
    private static boolean sparkwitch$catTransition;

    /** The role name text captured from startWelcome, used as the “%s” in the replacement line. */
    /** 从 startWelcome 捕获的角色名文本，用作替换行的 “%s”。 */
    private static Text sparkwitch$catRoleText = Text.empty();

    @Inject(method = "startWelcome", at = @At("TAIL"), require = 1)
    private static void sparkwitch$trackCatTransition(RoleAnnouncementTexts.RoleAnnouncementText role,
                                                      int killers, int targets, CallbackInfo ci) {
        sparkwitch$catTransition = targets == SPARKWITCH$CAT_TARGETS;
        sparkwitch$catRoleText = role == null ? Text.empty() : role.roleText;
    }

    @Redirect(method = "renderHud",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"),
            require = 0)
    private static int sparkwitch$replaceWelcomeText(DrawContext context, TextRenderer renderer,
                                                     Text text, int x, int y, int color) {
        // Only the welcome line sits at y == -12; premise and goal keep Wathe's own text.
        // 只有欢迎语在 y == -12；前提与目标保留 Wathe 原文。
        if (sparkwitch$catTransition && y == -12) {
            Text replacement = Text.translatable("announcement.sparkwitch.cat.transition.header",
                    sparkwitch$catRoleText);
            return context.drawTextWithShadow(renderer, replacement,
                    -renderer.getWidth(replacement) / 2, y, color);
        }
        return context.drawTextWithShadow(renderer, text, x, y, color);
    }
}
