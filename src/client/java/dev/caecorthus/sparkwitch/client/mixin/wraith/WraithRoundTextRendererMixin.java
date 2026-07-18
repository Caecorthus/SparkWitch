package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithClientState;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Preserves the Wraith task goal while the restricted phase owns opening presentation. / 受限阶段由冤魂身份接管开场展示时保留任务目标。 */
@Mixin(value = RoundTextRenderer.class, priority = 2000)
public abstract class WraithRoundTextRendererMixin {
    @Shadow
    private static RoleAnnouncementTexts.RoleAnnouncementText role;

    @Shadow
    private static int targets;

    @ModifyArg(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I",
                    ordinal = 2
            ),
            index = 1
    )
    private static Text sparkwitch$preserveWraithGoal(Text displayedGoal) {
        if (WraithClientState.isRestricted(MinecraftClient.getInstance().player)) {
            return role.goalText.apply(targets);
        }
        return displayedGoal;
    }
}
